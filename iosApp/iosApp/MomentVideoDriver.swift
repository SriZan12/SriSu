import Foundation
import UIKit
import PhotosUI
import AVFoundation
import AVKit
import UniformTypeIdentifiers
import ComposeApp

/// File-backed video preparation. Scratch files are private, protected and excluded from backup.
final class IosMomentVideoPicker: NSObject, MomentVideoPicker, PHPickerViewControllerDelegate {
    private var callback:((MomentVideoSelection)->Void)?
    private var epoch=0
    private var export:AVAssetExportSession?
    private var picker:PHPickerViewController?
    private let queue=DispatchQueue(label:"com.srisu.moment.video",qos:.userInitiated)
    private func root()->UIViewController? {
        var view=UIApplication.shared.connectedScenes.compactMap {$0 as? UIWindowScene}.flatMap(\.windows).first(where:\.isKeyWindow)?.rootViewController
        while let shown=view?.presentedViewController {view=shown}
        return view
    }
    func pick(result:@escaping (MomentVideoSelection)->Void) {
        guard callback==nil else {return}
        callback=result;epoch+=1
        var config=PHPickerConfiguration();config.filter = .videos;config.selectionLimit=1
        let picker=PHPickerViewController(configuration:config);picker.delegate=self;self.picker=picker
        guard let root=root() else {finish(nil,error:"The video picker could not open.");return}
        root.present(picker,animated:true)
    }
    func picker(_ picker:PHPickerViewController,didFinishPicking results:[PHPickerResult]) {
        picker.dismiss(animated:true);self.picker=nil
        guard let provider=results.first?.itemProvider else {finish(nil);return}
        let own=epoch
        provider.loadFileRepresentation(forTypeIdentifier:UTType.movie.identifier) {[weak self] url,error in
            guard let self else {return}
            guard let url,error==nil else {DispatchQueue.main.async {if self.epoch==own {self.finish(nil,error:"This video could not be opened. Try again.")}};return}
            // The provider URL only lives during this callback. Copy with bounded file I/O.
            do {
                var dir=FileManager.default.urls(for:.cachesDirectory,in:.userDomainMask)[0].appendingPathComponent("SriSuMomentVideos",isDirectory:true)
                try FileManager.default.createDirectory(at:dir,withIntermediateDirectories:true,attributes:[.protectionKey:FileProtectionType.complete])
                var values=URLResourceValues();values.isExcludedFromBackup=true;try dir.setResourceValues(values)
                for old in (try? FileManager.default.contentsOfDirectory(at:dir,includingPropertiesForKeys:[.contentModificationDateKey])) ?? [] {
                    if let date=try? old.resourceValues(forKeys:[.contentModificationDateKey]).contentModificationDate,Date().timeIntervalSince(date)>7*86400 {try? FileManager.default.removeItem(at:old)}
                }
                let size=try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0
                guard size>0 && size<=100*1024*1024 else {throw ChatMediaError.invalid}
                let local=dir.appendingPathComponent(UUID().uuidString+".mp4")
                try FileManager.default.copyItem(at:url,to:local)
                try FileManager.default.setAttributes([.protectionKey:FileProtectionType.complete],ofItemAtPath:local.path)
                self.queue.async {self.prepare(local,own:own)}
            } catch {DispatchQueue.main.async {if self.epoch==own {self.finish(nil,error:"Choose a video up to 60 seconds and 100 MiB.")}}}
        }
    }
    private func prepare(_ local:URL,own:Int) {
        let asset=AVURLAsset(url:local)
        let duration=CMTimeGetSeconds(asset.duration)
        guard duration.isFinite && duration>=0.1 && duration<=60 && !asset.hasProtectedContent,
            let track=asset.tracks(withMediaType:.video).first else {
            try? FileManager.default.removeItem(at:local)
            DispatchQueue.main.async {if self.epoch==own {self.finish(nil,error:"Choose a playable video up to 60 seconds.")}};return
        }
        let size=track.naturalSize.applying(track.preferredTransform)
        let width=abs(size.width),height=abs(size.height)
        guard width<=4096 && height<=4096 && width*height<=9_000_000 else {
            try? FileManager.default.removeItem(at:local)
            DispatchQueue.main.async {if self.epoch==own {self.finish(nil,error:"Choose a video up to 4K resolution.")}};return
        }
        let generator=AVAssetImageGenerator(asset:asset);generator.appliesPreferredTrackTransform=true;generator.maximumSize=CGSize(width:320,height:320)
        let poster=(try? generator.copyCGImage(at:.zero,actualTime:nil)).flatMap {UIImage(cgImage:$0).jpegData(compressionQuality:0.75)}?.momentKotlinBytes()
        let prepared=local.deletingLastPathComponent().appendingPathComponent(UUID().uuidString+".mp4")
        let deliver:(URL)->Void={file in
            let bytes=(try? file.resourceValues(forKeys:[.fileSizeKey]).fileSize) ?? 0
            DispatchQueue.main.async {
                guard own==self.epoch && self.callback != nil else {try? FileManager.default.removeItem(at:file);return}
                guard bytes>0 && bytes<=100*1024*1024 else {try? FileManager.default.removeItem(at:file);self.finish(nil,error:"This video is too large. Choose a smaller clip.");return}
                self.finish(LocalMomentVideo(path:file.path,size:Int64(bytes),durationMs:Int64(duration*1000),width:Int32(width),height:Int32(height)),poster:poster)
            }
        }
        guard let session=AVAssetExportSession(asset:asset,presetName:AVAssetExportPreset1280x720) else {deliver(local);return}
        session.outputURL=prepared;session.outputFileType = .mp4;session.shouldOptimizeForNetworkUse=true;session.metadata=[]
        DispatchQueue.main.async {
            guard own==self.epoch else {try? FileManager.default.removeItem(at:local);return}
            self.export=session
            DispatchQueue.main.asyncAfter(deadline:.now()+150) { [weak self, weak session] in
                guard let self, let session, self.export === session else {return}
                session.cancelExport()
                self.finish(nil,error:"Preparing this video took too long. Try a shorter clip.")
            }
            session.exportAsynchronously {
                self.queue.async {
                    if session.status == .completed {
                        try? FileManager.default.setAttributes([.protectionKey:FileProtectionType.complete],ofItemAtPath:prepared.path)
                        let originalSize=(try? local.resourceValues(forKeys:[.fileSizeKey]).fileSize) ?? 0
                        let preparedSize=(try? prepared.resourceValues(forKeys:[.fileSizeKey]).fileSize) ?? 0
                        if originalSize>0 && preparedSize>=originalSize {
                            try? FileManager.default.removeItem(at:prepared);deliver(local)
                        } else {try? FileManager.default.removeItem(at:local);deliver(prepared)}
                    } else {
                        try? FileManager.default.removeItem(at:prepared)
                        if session.status == .cancelled {try? FileManager.default.removeItem(at:local)} else {deliver(local)}
                    }
                }
            }
        }
    }
    private func finish(_ video:LocalMomentVideo?,poster:KotlinByteArray?=nil,error:String?=nil) {
        let result=callback;callback=nil;export=nil
        result?(MomentVideoSelection(video:video,poster:poster,error:error))
    }
    func cancel() {epoch+=1;export?.cancelExport();export=nil;picker?.dismiss(animated:true);picker=nil;finish(nil)}
    func close() {cancel()}
}

/// Native HLS playback. Cookies are scoped to one asset, held only by this player.
final class IosMomentVideo: NSObject, IosMomentVideoPlayer {
    let controller:UIViewController
    private let view=AVPlayerViewController()
    private let player:AVPlayer
    private let callback:(MomentPlaybackEvent)->Void
    private var timer:Any?
    private var observers:[NSObjectProtocol]=[]
    private var observedReady:NSKeyValueObservation?
    private var observedStatus:NSKeyValueObservation?
    private var observedTime:NSKeyValueObservation?
    private var firstFrame=false
    private var interrupted=false
    private var desired=false
    private var closed=false
    init(source:MomentPlayback,callback:@escaping (MomentPlaybackEvent)->Void) {
        self.callback=callback
        let url=source.url.hasPrefix("/") ? URL(fileURLWithPath:source.url) : (URL(string:source.url) ?? URL(fileURLWithPath:source.url))
        var options:[String:Any]=[:]
        if !source.cookieValue.isEmpty,let host=url.host {
            var properties:[HTTPCookiePropertyKey:Any]=[.name:source.cookieName,.value:source.cookieValue,.domain:host,
                .path:source.cookiePath,.expires:Date().addingTimeInterval(TimeInterval(source.expiresIn))]
            // Any value for .secure enables it, including "FALSE". Only development
            // loopback origins use HTTP; production cookies remain HTTPS-only.
            if url.scheme=="https" {properties[.secure]="TRUE"}
            if let cookie=HTTPCookie(properties:properties) {options[AVURLAssetHTTPCookiesKey]=[cookie]}
        }
        let asset=AVURLAsset(url:url,options:options)
        let item=AVPlayerItem(asset:asset);item.preferredForwardBufferDuration=6
        player=AVPlayer(playerItem:item);player.automaticallyWaitsToMinimizeStalling=true
        controller=view
        super.init()
        view.player=player;view.showsPlaybackControls=false;view.videoGravity = .resizeAspect
        observedReady=view.observe(\.isReadyForDisplay,options:[.new]) {[weak self] _,_ in
            guard let self,self.view.isReadyForDisplay else {return};self.firstFrame=true;self.emit("ready")
        }
        observedStatus=item.observe(\.status,options:[.new]) {[weak self] item,_ in if item.status == .failed {self?.emit("failed")} }
        observedTime=player.observe(\.timeControlStatus,options:[.new]) {[weak self] player,_ in
            self?.emit(player.timeControlStatus == .waitingToPlayAtSpecifiedRate ? "buffering" : player.rate>0 ? "playing":"ready")
        }
        timer=player.addPeriodicTimeObserver(forInterval:CMTime(seconds:0.25,preferredTimescale:600),queue:.main) {[weak self] _ in
            guard let self,!self.closed else {return}
            self.emit(item.status == .failed ? "failed" : self.player.timeControlStatus == .waitingToPlayAtSpecifiedRate ? "buffering" : self.player.rate>0 ? "playing":"ready")
        }
        let center=NotificationCenter.default
        observers.append(center.addObserver(forName:.AVPlayerItemDidPlayToEndTime,object:item,queue:.main) {[weak self] _ in self?.emit("ended")})
        observers.append(center.addObserver(forName:.AVPlayerItemFailedToPlayToEndTime,object:item,queue:.main) {[weak self] _ in self?.emit("failed")})
        observers.append(center.addObserver(forName:AVAudioSession.interruptionNotification,object:nil,queue:.main) {[weak self] _ in self?.interrupt()})
        observers.append(center.addObserver(forName:UIApplication.willResignActiveNotification,object:nil,queue:.main) {[weak self] _ in self?.interrupt()})
        observers.append(center.addObserver(forName:AVAudioSession.routeChangeNotification,object:nil,queue:.main) {[weak self] note in
            if (note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt)==AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue {self?.interrupt()}
        })
    }
    private func emit(_ state:String) {
        guard !closed else {return}
        let position=CMTimeGetSeconds(player.currentTime()),duration=CMTimeGetSeconds(player.currentItem?.duration ?? .zero)
        callback(MomentPlaybackEvent(state:state,positionMs:position.isFinite ? Int64(max(0,position)*1000):0,
            durationMs:duration.isFinite ? Int64(max(0,duration)*1000):0,firstFrame:firstFrame))
    }
    private func interrupt() {interrupted=true;player.pause();emit("paused")}
    func update(playing:Bool,muted:Bool) {
        guard !closed else {return}
        player.isMuted=muted
        let starting=playing && !desired
        if playing != desired {interrupted=false};desired=playing
        if playing && !interrupted {
            if starting,let item=player.currentItem,CMTimeGetSeconds(item.duration).isFinite,
                CMTimeGetSeconds(player.currentTime())>=CMTimeGetSeconds(item.duration)-0.05 {player.seek(to:.zero)}
            if !muted {try? AVAudioSession.sharedInstance().setCategory(.playback,mode:.moviePlayback);try? AVAudioSession.sharedInstance().setActive(true)}
            player.play()
        } else {player.pause()}
    }
    func close() {
        guard !closed else {return};closed=true;player.pause()
        if let timer {player.removeTimeObserver(timer)};timer=nil;observedReady=nil;observedStatus=nil;observedTime=nil
        observers.forEach {NotificationCenter.default.removeObserver($0)};observers=[]
        player.replaceCurrentItem(with:nil);view.player=nil
        try? AVAudioSession.sharedInstance().setActive(false,options:.notifyOthersOnDeactivation)
    }
    deinit {close()}
}


private extension Data {
    func momentKotlinBytes()->KotlinByteArray {
        let bytes=KotlinByteArray(size:Int32(count))
        withUnsafeBytes {(buffer:UnsafeRawBufferPointer) in
            for index in 0..<count {bytes.set(index:Int32(index),value:Int8(bitPattern:buffer[index]))}
        }
        return bytes
    }
}
