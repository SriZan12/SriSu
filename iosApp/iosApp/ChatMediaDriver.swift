import ComposeApp
import UIKit
import PhotosUI
import AVFoundation
import AVKit
import UniformTypeIdentifiers
import ImageIO
import CoreImage

/// Foreground native media. Private preparation, recording and playback remain in
/// memory, except bounded video preparation and explicit exports, which use
/// protected, backup-excluded temporary files removed when their operation ends.
final class IosChatMedia: NSObject, NativeChatMedia, PHPickerViewControllerDelegate, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    typealias Result = (String, KotlinByteArray?) -> Void
    private var selection: Result?
    private var selectionEpoch=0
    private weak var selectionController:UIViewController?
    private var safetySelection=false
    private var recordingResult: Result?
    private var engine: AVAudioEngine?
    private var pcm = Data()
    private var levels: [Int] = []
    private var sampleRate = 48000.0
    private var lastMeter = 0
    private let recordingLock = NSLock()
    private var recordingEpoch = 0
    private var recording = false
    private var audioPlayer: AVAudioPlayer?
    private var videoSource: ChatMemoryVideo?
    private var videoController: ChatVideoController?
    private var playerCallback: ((String) -> Void)?
    private var timer: Timer?
    private var observers: [NSObjectProtocol] = []
    private var privacyCovers:[UIView]=[]
    private let session = AVAudioSession.sharedInstance()
    override init() {
        super.init()
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] _ in self?.interrupted() })
        observers.append(center.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] note in
            if (note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt) == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue { self?.interrupted() }
        })
        observers.append(center.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in self?.interrupt() })
        observers.append(center.addObserver(forName: UIApplication.willResignActiveNotification,object:nil,queue:.main) {[weak self] _ in self?.coverSnapshots()})
        observers.append(center.addObserver(forName: UIApplication.didBecomeActiveNotification,object:nil,queue:.main) {[weak self] _ in
            self?.privacyCovers.forEach {$0.removeFromSuperview()};self?.privacyCovers=[]
        })
        clearExports()
        ChatVideoPreparation.cleanInterruptedFiles()
    }
    deinit { for observer in observers {NotificationCenter.default.removeObserver(observer)} }
    private func root() -> UIViewController? {
        var value = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController
        while let presented = value?.presentedViewController { value = presented }
        return value
    }
    private func json(_ value: [String: Any]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject:value,options:[.sortedKeys]),let text = String(data:data,encoding:.utf8) else {return "{\"event\":\"error\"}"}
        return text
    }
    private func deliver(_ callback: Result?, _ value: [String: Any], _ data: Data? = nil) {
        DispatchQueue.main.async { [weak self] in guard let self else { return }; _ = callback?(self.json(value),data?.chatKotlinBytes()) }
    }
    func pick(source: String, result: @escaping Result) {
        DispatchQueue.main.async {
            self.selectionEpoch += 1;let epoch=self.selectionEpoch
            self.selectionController?.dismiss(animated:false)
            let guarded:Result = { [weak self] event,data in
                guard let self,epoch==self.selectionEpoch else {return}
                result(event,data)
            }
            self.selection=guarded;self.safetySelection=source.hasPrefix("safety-")
            guard let controller=self.root() else {self.deliver(result,["event":"error"]);self.selection=nil;return}
            if source=="camera" || source=="safety-camera" {
                guard UIImagePickerController.isSourceTypeAvailable(.camera) else {self.deliver(result,["event":"error"]);self.selection=nil;return}
                AVCaptureDevice.requestAccess(for:.video) {granted in DispatchQueue.main.async {
                    guard epoch==self.selectionEpoch,self.selection != nil else {return}
                    guard granted else {self.deliver(result,["event":"error","code":"permission"]);self.selection=nil;return}
                    let picker=UIImagePickerController();picker.sourceType = .camera;picker.mediaTypes=[UTType.image.identifier];picker.delegate=self
                    self.selectionController=picker;controller.present(picker,animated:true)
                }}
            } else {
                var config=PHPickerConfiguration();config.selectionLimit=1;config.filter=source=="video" ? .videos : .images
                config.preferredAssetRepresentationMode = .compatible
                let picker=PHPickerViewController(configuration:config);picker.delegate=self;self.selectionController=picker;controller.present(picker,animated:true)
            }
        }
    }
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        guard selectionController === picker else {picker.dismiss(animated:false);return}
        picker.dismiss(animated:true)
        selectionController=nil
        let callback=selection;selection=nil;let safety=safetySelection
        guard let provider=results.first?.itemProvider else {deliver(callback,["event":"cancelled"]);return}
        if provider.hasItemConformingToTypeIdentifier(UTType.movie.identifier) {
            provider.loadFileRepresentation(forTypeIdentifier:UTType.movie.identifier) {url,error in
                guard let url else {self.deliver(callback,["event":"error"]);return}
                // The provider owns this temporary source. Finish reading before
                // its completion block returns; never copy it into an app cache.
                let done=DispatchSemaphore(value:0)
                ChatVideoPreparation.prepare(url:url) {result in
                    switch result {case .success(let media):self.deliver(callback,media.0,media.1)
                    case .failure:self.deliver(callback,["event":"error","code":"limit"])}
                    done.signal()
                }
                _ = done.wait(timeout:.now()+180)
            }
        } else {
            provider.loadDataRepresentation(forTypeIdentifier:UTType.image.identifier) {data,error in
                guard let data,data.count<=32*1024*1024 else {self.deliver(callback,["event":"error","code":"limit"]);return}
                guard let source=CGImageSourceCreateWithData(data as CFData,nil),
                    let image=CGImageSourceCreateThumbnailAtIndex(source,0,[kCGImageSourceCreateThumbnailFromImageAlways:true,
                        kCGImageSourceCreateThumbnailWithTransform:true,kCGImageSourceThumbnailMaxPixelSize:2048] as CFDictionary) else {self.deliver(callback,["event":"error"]);return}
                if safety {self.safetyPhoto(UIImage(cgImage:image),callback)} else {self.photo(UIImage(cgImage:image),callback)}
            }
        }
    }
    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        guard selectionController === picker else {picker.dismiss(animated:false);return}
        picker.dismiss(animated:true);selectionController=nil;deliver(selection,["event":"cancelled"]);selection=nil
    }
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey:Any]) {
        guard selectionController === picker else {picker.dismiss(animated:false);return}
        picker.dismiss(animated:true);selectionController=nil;let callback=selection;selection=nil;let safety=safetySelection
        guard let image=info[.originalImage] as? UIImage else {deliver(callback,["event":"error"]);return}
        DispatchQueue.global(qos:.userInitiated).async {if safety {self.safetyPhoto(image,callback)} else {self.photo(image,callback)}}
    }
    func safetyQr(text: String) -> KotlinByteArray? {
        guard text.count<=1024,let filter=CIFilter(name:"CIQRCodeGenerator") else {return nil}
        filter.setValue(Data(text.utf8),forKey:"inputMessage");filter.setValue("M",forKey:"inputCorrectionLevel")
        guard let code=filter.outputImage else {return nil}
        let scaled=code.transformed(by:CGAffineTransform(scaleX:6,y:6))
        guard let cg=CIContext().createCGImage(scaled,from:scaled.extent) else {return nil}
        let format=UIGraphicsImageRendererFormat();format.scale=1;format.opaque=true
        let rendered=UIGraphicsImageRenderer(size:CGSize(width:cg.width+48,height:cg.height+48),format:format).image {ctx in
            UIColor.white.setFill();ctx.fill(CGRect(x:0,y:0,width:cg.width+48,height:cg.height+48))
            ctx.cgContext.interpolationQuality = .none
            UIImage(cgImage:cg).draw(at:CGPoint(x:24,y:24))
        }
        return rendered.pngData()?.chatKotlinBytes()
    }
    private func safetyPhoto(_ image: UIImage,_ callback: Result?) {
        guard let value=Self.decodedSafetyQr(image) else {deliver(callback,["event":"error"]);return}
        deliver(callback,["event":"qr","value":value])
    }
    static func decodedSafetyQr(_ image: UIImage) -> String? {
        guard let ci=CIImage(image:image),let detector=CIDetector(ofType:CIDetectorTypeQRCode,context:CIContext(),options:[CIDetectorAccuracy:CIDetectorAccuracyHigh]),
            let value=(detector.features(in:ci).first as? CIQRCodeFeature)?.messageString,value.count<=1024 else {
            return nil
        }
        return value
    }
    private func photo(_ image: UIImage,_ callback: Result?) {
        let ratio=min(1,2048/max(image.size.width,image.size.height))
        let size=CGSize(width:max(1,image.size.width*ratio),height:max(1,image.size.height*ratio))
        let format=UIGraphicsImageRendererFormat();format.scale=1;format.opaque=true
        let rendered=UIGraphicsImageRenderer(size:size,format:format).image {_ in image.draw(in:CGRect(origin:.zero,size:size))}
        guard let bytes=rendered.jpegData(compressionQuality:0.85),bytes.count<=16*1024*1024 else {deliver(callback,["event":"error","code":"limit"]);return}
        deliver(callback,["event":"ready","kind":"photo","mime":"image/jpeg","width":Int(size.width),"height":Int(size.height),
            "thumbnail":Self.thumbnail(rendered) ?? ""],bytes)
    }
    static func thumbnail(_ image: UIImage) -> String? {
        let ratio=120/max(image.size.width,image.size.height)
        let size=CGSize(width:max(1,image.size.width*ratio),height:max(1,image.size.height*ratio))
        let format=UIGraphicsImageRendererFormat();format.scale=1;format.opaque=true
        let small=UIGraphicsImageRenderer(size:size,format:format).image {_ in image.draw(in:CGRect(origin:.zero,size:size))}
        guard let data=small.jpegData(compressionQuality:0.45),data.count<=6144 else {return nil}
        return data.base64EncodedString()
    }
    func startRecording(result: @escaping Result) {
        playback(action:"stop",value:0);recordingEpoch += 1;let epoch=recordingEpoch;recordingResult=result
        session.requestRecordPermission {granted in DispatchQueue.main.async {
            guard epoch==self.recordingEpoch else {return}
            guard granted else {self.deliver(result,["event":"error","code":"permission"]);return}
            do {
                try self.session.setCategory(.playAndRecord,mode:.default,options:[.defaultToSpeaker,.allowBluetooth])
                try self.session.setPreferredSampleRate(48000);try self.session.setActive(true)
                let engine=AVAudioEngine();let input=engine.inputNode;let format=input.outputFormat(forBus:0)
                guard format.sampleRate>0 && format.sampleRate<=48000 && format.channelCount>0 &&
                    format.commonFormat == .pcmFormatFloat32 && !format.isInterleaved else {throw ChatMediaError.invalid}
                self.sampleRate=format.sampleRate;self.pcm=Data();self.levels=[];self.lastMeter=0;self.recording=true
                input.installTap(onBus:0,bufferSize:4800,format:format) {[weak self] buffer,_ in
                    guard let self,let channels=buffer.floatChannelData else {return}
                    self.recordingLock.lock();defer {self.recordingLock.unlock()}
                    guard self.recording else {return}
                    let remaining=Int(self.sampleRate*120)-self.pcm.count/2
                    let frames=min(Int(buffer.frameLength),remaining);var peak:Float=0
                    for frame in 0..<frames {
                        var value:Float=0
                        for channel in 0..<Int(buffer.format.channelCount) {value += channels[channel][frame]}
                        value=max(-1,min(1,value/Float(buffer.format.channelCount)));peak=max(peak,abs(value))
                        var sample=Int16(value*32767).littleEndian
                        withUnsafeBytes(of:&sample) {self.pcm.append(contentsOf:$0)}
                    }
                    let ms=Int(Double(self.pcm.count)*500/self.sampleRate)
                    if ms-self.lastMeter>=100 {
                        self.lastMeter=ms;let level=Int(peak*100);self.levels.append(level)
                        self.deliver(result,["event":"level","level":level,"durationMs":ms])
                    }
                    if remaining<=frames {self.recording=false;DispatchQueue.main.async {self.stopRecording(discard:false)}}
                }
                self.engine=engine;engine.prepare();try engine.start();self.deliver(result,["event":"recording"])
            } catch {self.stopRecording(discard:true);self.deliver(result,["event":"error","code":"interrupted"])}
        }}
    }
    func stopRecording(discard: Bool) {
        recordingEpoch += 1
        let device=engine;engine=nil
        device?.inputNode.removeTap(onBus:0);device?.stop()
        recordingLock.lock();recording=false;let data=pcm;let peaks=levels;pcm=Data();levels=[];recordingLock.unlock()
        let callback=recordingResult;recordingResult=nil
        try? session.setActive(false,options:.notifyOthersOnDeactivation)
        guard !discard && data.count>=Int(sampleRate/2) else {if callback != nil {deliver(callback,["event":"cancelled"])};return}
        var wav=Data("RIFF".utf8);wav.chatAppend(UInt32(36+data.count));wav.append(Data("WAVEfmt ".utf8));wav.chatAppend(UInt32(16))
        wav.chatAppend(UInt16(1));wav.chatAppend(UInt16(1));wav.chatAppend(UInt32(sampleRate));wav.chatAppend(UInt32(sampleRate*2))
        wav.chatAppend(UInt16(2));wav.chatAppend(UInt16(16));wav.append(Data("data".utf8));wav.chatAppend(UInt32(data.count));wav.append(data)
        let strideSize=max(1,peaks.count/100+1)
        let waveform=stride(from:0,to:peaks.count,by:strideSize).map {start in peaks[start..<min(start+strideSize,peaks.count)].max() ?? 0}
        deliver(callback,["event":"ready","kind":"voice","mime":"audio/wav","durationMs":Int(Double(data.count)*500/sampleRate),"waveform":waveform],wav)
    }
    private func interrupted() {
        let callback=recordingResult;stopRecording(discard:true)
        if callback != nil {deliver(callback,["event":"error","code":"interrupted"])}
        playback(action:"pause",value:0)
    }
    func play(bytes: KotlinByteArray, mime: String, result: @escaping (String)->Void) {
        stopRecording(discard:true);playback(action:"stop",value:0);playerCallback=result
        do {
            try session.setCategory(.playback,mode:.spokenAudio);try session.setActive(true)
            let data=bytes.chatData()
            if mime.hasPrefix("video/") {
                let source=ChatMemoryVideo(data:data);videoSource=source
                let controller=ChatVideoController();controller.player=source.player
                controller.finished = {[weak self] in self?.playback(action:"stop",value:0)}
                videoController=controller;root()?.present(controller,animated:true) {source.player.play()}
            } else {
                let player=try AVAudioPlayer(data:data);player.enableRate=true;player.prepareToPlay();audioPlayer=player
                guard player.play() else {throw ChatMediaError.invalid}
                _ = result(json(["event":"playing"]))
            }
            timer=Timer.scheduledTimer(withTimeInterval:0.2,repeats:true) {[weak self] _ in
                guard let self else {return}
                if let player=self.audioPlayer {
                    _ = result(self.json(["event":"position","positionMs":Int(player.currentTime*1000),"durationMs":Int(player.duration*1000)]))
                    if !player.isPlaying && player.currentTime==0 {_ = result(self.json(["event":"ended"]))}
                } else if let source=self.videoSource {
                    if source.player.status == .failed {_ = result(self.json(["event":"error"]));self.playback(action:"stop",value:0)}
                }
            }
        } catch {_ = result(json(["event":"error"]));playback(action:"stop",value:0)}
    }
    func playback(action: String, value: Double) {
        switch action {
        case "stop":
            timer?.invalidate();timer=nil;audioPlayer?.stop();audioPlayer=nil;videoSource?.player.pause();videoSource=nil
            let controller=videoController;videoController=nil;controller?.finished=nil;controller?.dismiss(animated:true)
            try? session.setActive(false,options:.notifyOthersOnDeactivation)
        case "pause":audioPlayer?.pause();videoSource?.player.pause();_ = playerCallback?(json(["event":"paused"]))
        case "resume":
            try? session.setActive(true)
            if let audioPlayer,audioPlayer.play() {_ = playerCallback?(json(["event":"playing"]))}
            videoSource?.player.play()
        case "seek":audioPlayer?.currentTime=max(0,value/1000);videoSource?.player.seek(to:CMTime(seconds:max(0,value/1000),preferredTimescale:600))
        case "speed":audioPlayer?.rate=Float(min(2,max(0.5,value)));videoSource?.player.rate=Float(value)
        default:break
        }
    }
    func export(bytes: KotlinByteArray, mime: String, result: @escaping (String)->Void) {
        do {
            let folder=FileManager.default.temporaryDirectory.appendingPathComponent("SriSuChatExports",isDirectory:true)
            try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true,attributes:[.protectionKey:FileProtectionType.complete])
            var url=folder.appendingPathComponent(UUID().uuidString).appendingPathExtension(mime=="image/jpeg" ? "jpg" : mime=="video/mp4" ? "mp4" : "wav")
            try bytes.chatData().write(to:url,options:[.atomic,.completeFileProtection])
            var flags=URLResourceValues();flags.isExcludedFromBackup=true;try url.setResourceValues(flags)
            let controller=UIActivityViewController(activityItems:[url],applicationActivities:nil)
            controller.completionWithItemsHandler={_,completed,_,error in try? FileManager.default.removeItem(at:url);_ = result(error != nil ? "error" : completed ? "saved" : "cancelled")}
            guard let parent=root() else {try? FileManager.default.removeItem(at:url);_ = result("error");return}
            controller.popoverPresentationController?.sourceView=parent.view;controller.popoverPresentationController?.sourceRect=parent.view.bounds
            parent.present(controller,animated:true)
        } catch {_ = result("error")}
    }
    private func clearExports() {
        let folder=FileManager.default.temporaryDirectory.appendingPathComponent("SriSuChatExports",isDirectory:true)
        if let files=try? FileManager.default.contentsOfDirectory(at:folder,includingPropertiesForKeys:nil) {for file in files {try? FileManager.default.removeItem(at:file)}}
    }
    private func coverSnapshots() {
        guard privacyCovers.isEmpty else {return}
        for window in UIApplication.shared.connectedScenes.compactMap({$0 as? UIWindowScene}).flatMap(\.windows) where !window.isHidden {
            let cover=UIView(frame:window.bounds);cover.autoresizingMask=[.flexibleWidth,.flexibleHeight];cover.backgroundColor = .systemBackground
            let label=UILabel(frame:cover.bounds);label.autoresizingMask=[.flexibleWidth,.flexibleHeight];label.text="SriSu";label.textAlignment = .center
            cover.addSubview(label);cover.accessibilityViewIsModal=true;window.addSubview(cover);privacyCovers.append(cover)
        }
    }
    func interrupt() {stopRecording(discard:true);playback(action:"stop",value:0)}
    func close() {interrupt();selectionEpoch += 1;selection=nil;selectionController?.dismiss(animated:false);selectionController=nil}
}

enum ChatMediaError: Error {case invalid}
private extension KotlinByteArray {
    func chatData() -> Data {var data=Data(count:Int(size));data.withUnsafeMutableBytes {(buffer:UnsafeMutableRawBufferPointer) in
        for index in 0..<size {buffer[Int(index)]=UInt8(bitPattern:get(index:index))}};return data}
}
private extension Data {
    func chatKotlinBytes() -> KotlinByteArray {let bytes=KotlinByteArray(size:Int32(count));withUnsafeBytes {(buffer:UnsafeRawBufferPointer) in
        for index in 0..<count {bytes.set(index:Int32(index),value:Int8(bitPattern:buffer[index]))}};return bytes}
    mutating func chatAppend<T:FixedWidthInteger>(_ value:T) {var little=value.littleEndian;Swift.withUnsafeBytes(of:&little) {append(contentsOf:$0)}}
}

private final class ChatVideoController: AVPlayerViewController {
    var finished:(()->Void)?
    override func viewDidDisappear(_ animated:Bool) {super.viewDidDisappear(animated);finished?()}
}
private final class ChatMemoryVideo: NSObject, AVAssetResourceLoaderDelegate {
    let data:Data
    let player:AVPlayer
    private let queue=DispatchQueue(label:"com.srisu.chat.media.playback")
    init(data:Data) {
        self.data=data
        let asset=AVURLAsset(url:URL(string:"srisu-private-media://current/clip.mp4")!)
        player=AVPlayer(playerItem:AVPlayerItem(asset:asset));super.init();asset.resourceLoader.setDelegate(self,queue:queue)
    }
    func resourceLoader(_ resourceLoader:AVAssetResourceLoader,shouldWaitForLoadingOfRequestedResource request:AVAssetResourceLoadingRequest)->Bool {
        request.contentInformationRequest?.contentType=UTType.mpeg4Movie.identifier
        request.contentInformationRequest?.contentLength=Int64(data.count);request.contentInformationRequest?.isByteRangeAccessSupported=true
        if let read=request.dataRequest {
            let offset=Int(read.currentOffset)
            guard offset>=0 && offset<=data.count else {request.finishLoading(with:ChatMediaError.invalid);return true}
            let count=read.requestsAllDataToEndOfResource ? data.count-offset : min(read.requestedLength,data.count-offset)
            read.respond(with:data.subdata(in:offset..<offset+count))
        }
        request.finishLoading();return true
    }
}
