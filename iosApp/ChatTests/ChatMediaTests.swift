import XCTest
import AVFoundation
import UIKit
import ImageIO
import ComposeApp
@testable import SriSu

/// All files below are generated color/audio fixtures in this test simulator.
final class ChatMediaTests:XCTestCase {
    #if CHAT_APP_EVALUATION
    @MainActor func testActualKmpRepositoriesWithTwoIosPartners() async throws {
        let fixture=FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0].appendingPathComponent("chat-app-fixture.json")
        defer {try? FileManager.default.removeItem(at:fixture)}
        let input=try String(contentsOf:fixture,encoding:.utf8)
        let completed=expectation(description:"real KMP repositories")
        ChatAppEvaluation.shared.run(fixture:input,driver:IosChatDriver()) {result in
            XCTAssertEqual(result,"PASS");completed.fulfill()
        }
        await fulfillment(of:[completed],timeout:300)
    }
    #endif
    private var folder:URL {FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0].appendingPathComponent("chat-test-fixtures",isDirectory:true)}
    @MainActor func testSafetyQrUsesNativeEncoderAndDecoder() throws {
        let value="srisu-safety:1:U3ludGhldGljIHFyIGZpeHR1cmU="
        let media=IosChatMedia()
        let encoded=try XCTUnwrap(media.safetyQr(text:value))
        let bytes=Data((0..<encoded.size).map {UInt8(bitPattern:encoded.get(index:$0))})
        let image=try XCTUnwrap(UIImage(data:bytes))
        XCTAssertEqual(IosChatMedia.decodedSafetyQr(image),value)
        try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
        try bytes.write(to:folder.appendingPathComponent("ios-safety-qr.png"))
        media.close()
    }
    func testAndroidPreparedVideoDecodes() throws {
        let file=folder.appendingPathComponent("android-prepared.mp4")
        XCTAssertTrue(FileManager.default.fileExists(atPath:file.path),"Supply the Android-generated synthetic fixture first")
        let asset=AVURLAsset(url:file)
        XCTAssertGreaterThan(CMTimeGetSeconds(asset.duration),0)
        let reader=try AVAssetReader(asset:asset)
        let track=try XCTUnwrap(asset.tracks(withMediaType:.video).first)
        let output=AVAssetReaderTrackOutput(track:track,outputSettings:[kCVPixelBufferPixelFormatTypeKey as String:kCVPixelFormatType_32BGRA])
        reader.add(output);XCTAssertTrue(reader.startReading());XCTAssertNotNil(output.copyNextSampleBuffer());reader.cancelReading()
        XCTAssertTrue(asset.commonMetadata.filter {$0.commonKey == .commonKeyLocation}.isEmpty)
    }
    func testPreparedVideoDecodesAndHasNoSourceLocation() async throws {
        try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
        let source=folder.appendingPathComponent("source.mp4")
        if FileManager.default.fileExists(atPath:source.path) {try FileManager.default.removeItem(at:source)}
        let writer=try AVAssetWriter(outputURL:source,fileType:.mp4)
        let location=AVMutableMetadataItem();location.identifier = .commonIdentifierLocation;location.value="+27.7000+085.3000/" as NSString
        writer.metadata=[location]
        let input=AVAssetWriterInput(mediaType:.video,outputSettings:[AVVideoCodecKey:AVVideoCodecType.h264,AVVideoWidthKey:320,AVVideoHeightKey:240])
        let adapter=AVAssetWriterInputPixelBufferAdaptor(assetWriterInput:input,sourcePixelBufferAttributes:[kCVPixelBufferPixelFormatTypeKey as String:kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String:320,kCVPixelBufferHeightKey as String:240,kCVPixelBufferIOSurfacePropertiesKey as String:[:]])
        writer.add(input);XCTAssertTrue(writer.startWriting());writer.startSession(atSourceTime:.zero)
        let generated=expectation(description:"synthetic video encoded")
        let queue=DispatchQueue(label:"chat.fixture.encoder");var index=0
        input.requestMediaDataWhenReady(on:queue) {
            while input.isReadyForMoreMediaData && index<20 {
                var buffer:CVPixelBuffer?
                guard let pool=adapter.pixelBufferPool,CVPixelBufferPoolCreatePixelBuffer(nil,pool,&buffer)==kCVReturnSuccess,let frame=buffer else {XCTFail("Fixture pixel buffer");generated.fulfill();return}
                CVPixelBufferLockBaseAddress(frame,[])
                if let address=CVPixelBufferGetBaseAddress(frame) {memset(address,96,CVPixelBufferGetDataSize(frame))}
                CVPixelBufferUnlockBaseAddress(frame,[])
                XCTAssertTrue(adapter.append(frame,withPresentationTime:CMTime(value:Int64(index),timescale:10)));index += 1
            }
            if index==20 {index += 1;input.markAsFinished();writer.finishWriting {generated.fulfill()} }
        }
        await fulfillment(of:[generated],timeout:20)
        XCTAssertEqual(writer.status,.completed)
        let prepared=expectation(description:"production video preparation")
        var output:Data?;var metadata:[String:Any]?
        ChatVideoPreparation.prepare(url:source) {result in
            switch result {case .success(let value):metadata=value.0;output=value.1
            case .failure(let error):XCTFail("Native video preparation rejected synthetic H.264: \(type(of:error))")}
            prepared.fulfill()
        }
        await fulfillment(of:[prepared],timeout:25)
        let bytes=try XCTUnwrap(output)
        XCTAssertLessThan(bytes.count,16*1024*1024);XCTAssertEqual(metadata?["mime"] as? String,"video/mp4")
        let destination=folder.appendingPathComponent("ios-prepared.mp4");try bytes.write(to:destination)
        let asset=AVURLAsset(url:destination)
        XCTAssertTrue(asset.commonMetadata.filter {$0.commonKey == .commonKeyLocation}.isEmpty)
        let reader=try AVAssetReader(asset:asset)
        let track=try XCTUnwrap(asset.tracks(withMediaType:.video).first)
        let frames=AVAssetReaderTrackOutput(track:track,outputSettings:[kCVPixelBufferPixelFormatTypeKey as String:kCVPixelFormatType_32BGRA])
        reader.add(frames);XCTAssertTrue(reader.startReading());XCTAssertNotNil(frames.copyNextSampleBuffer());reader.cancelReading()
    }
    func testPcmWaveAndThumbnailUseNativeDecoders() throws {
        try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
        let count=48000*2;var bytes=Data("RIFF".utf8)
        func append<T:FixedWidthInteger>(_ value:T) {var little=value.littleEndian;withUnsafeBytes(of:&little) {bytes.append(contentsOf:$0)}}
        append(UInt32(36+count));bytes.append(Data("WAVEfmt ".utf8));append(UInt32(16));append(UInt16(1));append(UInt16(1));append(UInt32(48000));append(UInt32(96000))
        append(UInt16(2));append(UInt16(16));bytes.append(Data("data".utf8));append(UInt32(count));bytes.append(Data(count:count))
        let player=try AVAudioPlayer(data:bytes);XCTAssertEqual(player.duration,1,accuracy:0.01);XCTAssertTrue(player.prepareToPlay())
        try bytes.write(to:folder.appendingPathComponent("voice.wav"))
        let image=UIGraphicsImageRenderer(size:CGSize(width:320,height:240)).image {context in UIColor.green.setFill();context.fill(CGRect(x:0,y:0,width:320,height:240))}
        let thumbnail=try XCTUnwrap(IosChatMedia.thumbnail(image));let jpeg=try XCTUnwrap(Data(base64Encoded:thumbnail))
        XCTAssertLessThanOrEqual(jpeg.count,6144);XCTAssertNotNil(UIImage(data:jpeg))
        let source=try XCTUnwrap(CGImageSourceCreateWithData(jpeg as CFData,nil))
        let props=CGImageSourceCopyPropertiesAtIndex(source,0,nil) as? [String:Any]
        XCTAssertNil(props?[kCGImagePropertyGPSDictionary as String])
        try jpeg.write(to:folder.appendingPathComponent("thumbnail.jpg"))
    }
    func testCorruptVideoIsRejected() async throws {
        try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
        let corrupt=folder.appendingPathComponent("corrupt.mp4");try Data("synthetic invalid media".utf8).write(to:corrupt)
        defer {try? FileManager.default.removeItem(at:corrupt)}
        let rejected=expectation(description:"corrupt media rejected")
        ChatVideoPreparation.prepare(url:corrupt) {result in if case .success = result {XCTFail("Invalid media accepted")};rejected.fulfill()}
        await fulfillment(of:[rejected],timeout:10)
    }
}
