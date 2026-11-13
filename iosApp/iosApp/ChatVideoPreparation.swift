import Foundation
import AVFoundation
import UniformTypeIdentifiers
import UIKit

/// AVFoundation rewrites supported H.264/AAC samples into a seekable MP4.
/// The bounded scratch file inherits complete iOS data protection, is excluded
/// from backup and is removed before returning bytes for attachment encryption.
final class ChatVideoPreparation: NSObject {
    typealias Completion=(Result<([String:Any],Data),Error>)->Void
    private let queue=DispatchQueue(label:"com.srisu.chat.video.prepare",qos:.userInitiated)
    private var scratch:URL?
    private var sampleBytes=0
    private var reader:AVAssetReader?
    private var writer:AVAssetWriter?
    private var remaining=0
    private var finished=false
    private var completion:Completion?
    private var metadata:[String:Any]=[:]
    private var keepAlive:ChatVideoPreparation?
    static func cleanInterruptedFiles() {
        let directory=FileManager.default.temporaryDirectory.appendingPathComponent("SriSuChatVideo",isDirectory:true)
        if let files=try? FileManager.default.contentsOfDirectory(at:directory,includingPropertiesForKeys:nil) {
            for file in files where file.pathExtension=="mp4" {try? FileManager.default.removeItem(at:file)}
        }
    }
    static func prepare(url:URL,completion:@escaping Completion) {
        let preparation=ChatVideoPreparation();preparation.completion=completion;preparation.keepAlive=preparation
        preparation.queue.async {preparation.start(url:url)}
    }
    private func start(url:URL) {
        do {
            let asset=AVURLAsset(url:url)
            let duration=CMTimeGetSeconds(asset.duration)
            guard duration.isFinite && duration>0 && duration<=120 && !asset.hasProtectedContent else {throw ChatMediaError.invalid}
            let videos=asset.tracks(withMediaType:.video),audios=asset.tracks(withMediaType:.audio)
            guard videos.count==1 && audios.count<=1,let video=videos.first else {throw ChatMediaError.invalid}
            let size=video.naturalSize
            guard size.width>0 && size.height>0 && size.width<=1920 && size.height<=1920 && size.width*size.height<=1920*1080 else {throw ChatMediaError.invalid}
            metadata=["event":"ready","kind":"video","mime":"video/mp4","width":Int(size.width),"height":Int(size.height),"durationMs":Int(duration*1000)]
            let generator=AVAssetImageGenerator(asset:asset);generator.appliesPreferredTrackTransform=true;generator.maximumSize=CGSize(width:120,height:120)
            if let frame=try? generator.copyCGImage(at:.zero,actualTime:nil),let thumbnail=IosChatMedia.thumbnail(UIImage(cgImage:frame)) {metadata["thumbnail"]=thumbnail}
            var directory=FileManager.default.temporaryDirectory.appendingPathComponent("SriSuChatVideo",isDirectory:true)
            try FileManager.default.createDirectory(at:directory,withIntermediateDirectories:true,attributes:[.protectionKey:FileProtectionType.complete])
            try FileManager.default.setAttributes([.protectionKey:FileProtectionType.complete],ofItemAtPath:directory.path)
            var resource=URLResourceValues();resource.isExcludedFromBackup=true;try directory.setResourceValues(resource)
            let file=directory.appendingPathComponent(UUID().uuidString+".mp4");scratch=file
            let reader=try AVAssetReader(asset:asset),writer=try AVAssetWriter(outputURL:file,fileType:.mp4)
            self.reader=reader;self.writer=writer
            writer.metadata=[]
            var streams:[(AVAssetReaderTrackOutput,AVAssetWriterInput)]=[]
            for track in videos+audios {
                guard let rawDescription=track.formatDescriptions.first else {throw ChatMediaError.invalid}
                let description=rawDescription as! CMFormatDescription
                let codec=CMFormatDescriptionGetMediaSubType(description)
                guard track.mediaType == .video ? codec==kCMVideoCodecType_H264 : codec==kAudioFormatMPEG4AAC else {throw ChatMediaError.invalid}
                let source=AVAssetReaderTrackOutput(track:track,outputSettings:nil);source.alwaysCopiesSampleData=false
                let sink=AVAssetWriterInput(mediaType:track.mediaType,outputSettings:nil,sourceFormatHint:description)
                if track.mediaType == .video {sink.transform=track.preferredTransform}
                sink.expectsMediaDataInRealTime=false
                guard reader.canAdd(source) && writer.canAdd(sink) else {throw ChatMediaError.invalid}
                reader.add(source);writer.add(sink);streams.append((source,sink))
            }
            guard writer.startWriting() && reader.startReading() else {throw ChatMediaError.invalid}
            try FileManager.default.setAttributes([.protectionKey:FileProtectionType.complete],ofItemAtPath:file.path)
            writer.startSession(atSourceTime:.zero);remaining=streams.count
            queue.asyncAfter(deadline:.now()+150) {[weak self] in if let self,!self.finished {self.fail()} }
            for (source,sink) in streams {
                var ended=false
                sink.requestMediaDataWhenReady(on:queue) {[weak self,weak sink,weak reader,weak writer] in
                    guard let self,let sink,let reader,let writer else {return}
                    guard !ended && !self.finished else {return}
                    while sink.isReadyForMoreMediaData && !self.finished {
                        if let sample=source.copyNextSampleBuffer() {
                            self.sampleBytes += CMSampleBufferGetTotalSampleSize(sample)
                            guard self.sampleBytes<=15*1024*1024 else {self.fail();return}
                            guard sink.append(sample) else {self.fail();return}
                        } else {
                            ended=true;sink.markAsFinished();self.remaining -= 1
                            if self.remaining==0 {
                                guard reader.status == .completed else {self.fail();return}
                                writer.finishWriting {
                                    self.queue.async {
                                        guard !self.finished else {return}
                                        guard writer.status == .completed else {self.fail();return}
                                        guard let file=self.scratch,let size=try? file.resourceValues(forKeys:[.fileSizeKey]).fileSize,
                                            size>0 && size<=16*1024*1024,let bytes=try? Data(contentsOf:file) else {self.fail();return}
                                        do {try FileManager.default.removeItem(at:file);self.scratch=nil} catch {self.fail();return}
                                        self.finished=true;let callback=self.completion;self.completion=nil;callback?(.success((self.metadata,bytes)))
                                        self.reader=nil;self.writer=nil;self.keepAlive=nil
                                    }
                                }
                            }
                            return
                        }
                    }
                }
            }
        } catch {fail()}
    }
    private func fail() {
        guard !finished else {return};finished=true
        reader?.cancelReading();writer?.cancelWriting();reader=nil;writer=nil
        if let file=scratch {try? FileManager.default.removeItem(at:file);scratch=nil}
        let callback=completion;completion=nil;callback?(.failure(ChatMediaError.invalid));keepAlive=nil
    }
}
