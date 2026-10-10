import ComposeApp
import Foundation

final class IosChatDriver: NativeChatDriver {
    private let engine = SecureChatEngine()
    func execute(accountId: String, command: String, payload: String) -> String {
        engine.execute(accountId: accountId, command: command, payload: payload)
    }
    func close(accountId: String) { engine.close(accountId: accountId) }
    func executeBinary(accountId: String, command: String, payload: String, bytes: KotlinByteArray) -> String {
        var data = Data(count: Int(bytes.size))
        data.withUnsafeMutableBytes { (pointer: UnsafeMutableRawBufferPointer) in
            for index in 0..<bytes.size { pointer[Int(index)] = UInt8(bitPattern:bytes.get(index:index)) }
        }
        return engine.executeBinary(accountId:accountId,command:command,payload:payload,bytes:data)
    }
    func readAttachment(accountId: String, descriptor: String, decrypt: Bool) -> KotlinByteArray? {
        guard let data = engine.readAttachment(accountId:accountId,descriptor:descriptor,decrypt:decrypt) else { return nil }
        let bytes = KotlinByteArray(size:Int32(data.count))
        data.withUnsafeBytes { (pointer: UnsafeRawBufferPointer) in
            for index in 0..<data.count { bytes.set(index:Int32(index),value:Int8(bitPattern:pointer[index])) }
        }
        return bytes
    }
}
