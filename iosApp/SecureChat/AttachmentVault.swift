import CryptoKit
import Foundation

/// AES-256-GCM combined representation; fresh key per immutable bounded file.
/// Keys stay in protected history; this independent cache contains ciphertext only.
final class AttachmentVault {
    static let maxPlain = 16 * 1024 * 1024
    enum Failure: Error { case unavailable, integrity, storageFull, conflict, size }
    let vault: ProtectedVault
    let directory: URL
    init(_ vault: ProtectedVault) throws {
        self.vault = vault; directory = vault.directory.appendingPathComponent("media")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.protectionKey: FileProtectionType.complete])
        for marker in try FileManager.default.contentsOfDirectory(at:directory,includingPropertiesForKeys:nil) where marker.pathExtension == "pin" {
            let id=marker.deletingPathExtension().lastPathComponent
            if try vault.get("media/sealed/"+id) == nil {
                let orphan=directory.appendingPathComponent(id+".bin")
                if FileManager.default.fileExists(atPath:orphan.path) {try FileManager.default.removeItem(at:orphan)}
                try FileManager.default.removeItem(at:marker)
            }
        }
    }
    private func id(_ value: [String: Any], _ key: String) throws -> String {
        guard let raw = value[key] as? String, let id = UUID(uuidString: raw) else { throw Failure.integrity }
        return id.uuidString.lowercased()
    }
    private func context(_ value: [String: Any]) throws -> Data {
        Data(try ("SriSu/attachment/v1|" + id(value,"room_id") + "|" + id(value,"message_id") + "|" + id(value,"id")).utf8)
    }
    private func digest(_ bytes: Data) -> String { SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined() }
    private func file(_ value: [String: Any]) throws -> URL { directory.appendingPathComponent(try id(value,"id") + ".bin") }
    private func pin(_ value: [String: Any]) throws -> URL { directory.appendingPathComponent(try id(value,"id") + ".pin") }
    func seal(_ input: [String: Any], _ plain: Data) throws -> [String: Any] {
        guard !plain.isEmpty, plain.count <= Self.maxPlain else { throw Failure.size }
        let associated = try context(input), request = digest(plain), recordKey = "media/sealed/" + (try id(input,"id"))
        if let old = try vault.get(recordKey) {
            guard let record = try JSONSerialization.jsonObject(with: old) as? [String: Any], record["released"] as? Bool != true,
                let descriptor = record["descriptor"] as? [String: Any],
                record["request"] as? String == request, try context(descriptor) == associated else { throw Failure.conflict }
            _ = try read(descriptor,decrypt:false)
            return descriptor
        }
        let secret = SymmetricKey(size: .bits256)
        guard let sealed = try AES.GCM.seal(plain,using:secret,authenticating:associated).combined else { throw Failure.integrity }
        let descriptor: [String: Any] = ["id": try id(input,"id"),"room_id":try id(input,"room_id"),"message_id":try id(input,"message_id"),
            "algorithm":"aes256gcm-v1","key":secret.withUnsafeBytes { Data($0).base64EncodedString() },"ciphertext_size":sealed.count,"sha256":digest(sealed)]
        try save(descriptor,sealed,pinned:true)
        try vault.put(recordKey,JSONSerialization.data(withJSONObject:["request":request,"descriptor":descriptor],options:[.sortedKeys]))
        return descriptor
    }
    func receive(_ descriptor: [String: Any], _ ciphertext: Data) throws {
        _ = try authenticate(descriptor,ciphertext)
        try save(descriptor,ciphertext,pinned:false)
    }
    func read(_ descriptor: [String: Any], decrypt: Bool) throws -> Data {
        let path = try file(descriptor)
        guard let size = try FileManager.default.attributesOfItem(atPath:path.path)[.size] as? NSNumber,
            size.intValue >= 29, size.intValue <= Self.maxPlain+28 else { throw Failure.size }
        let bytes = try Data(contentsOf:path)
        try validate(descriptor,bytes)
        try FileManager.default.setAttributes([.modificationDate:Date()],ofItemAtPath:path.path)
        return decrypt ? try authenticate(descriptor,bytes) : bytes
    }
    private func validate(_ descriptor: [String: Any], _ ciphertext: Data) throws {
        guard ciphertext.count >= 29, ciphertext.count <= Self.maxPlain+28, descriptor["algorithm"] as? String == "aes256gcm-v1",
            (descriptor["ciphertext_size"] as? NSNumber)?.intValue == ciphertext.count,
            descriptor["sha256"] as? String == digest(ciphertext) else { throw Failure.integrity }
    }
    private func authenticate(_ descriptor: [String: Any], _ ciphertext: Data) throws -> Data {
        try validate(descriptor,ciphertext)
        guard let raw = descriptor["key"] as? String, let key = Data(base64Encoded:raw), key.count == 32 else { throw Failure.integrity }
        return try AES.GCM.open(AES.GCM.SealedBox(combined:ciphertext),using:SymmetricKey(data:key),authenticating:context(descriptor))
    }
    private func save(_ descriptor: [String: Any], _ ciphertext: Data, pinned: Bool) throws {
        let path = try file(descriptor)
        if FileManager.default.fileExists(atPath:path.path) {
            guard try read(descriptor,decrypt:false) == ciphertext else { throw Failure.conflict }
        } else {
            try makeSpace(ciphertext.count)
            try ciphertext.write(to:path,options:[.atomic,.completeFileProtection])
        }
        if pinned { try Data().write(to:pin(descriptor),options:[.atomic,.completeFileProtection]) }
    }
    private func makeSpace(_ requested: Int) throws {
        let keys: Set<URLResourceKey> = [.fileSizeKey,.contentModificationDateKey]
        let urls = try FileManager.default.contentsOfDirectory(at:directory,includingPropertiesForKeys:Array(keys)).filter { $0.pathExtension == "bin" }
        var entries: [(URL,Int,Date)] = try urls.map { let values = try $0.resourceValues(forKeys:keys); return ($0,values.fileSize ?? 0,values.contentModificationDate ?? .distantPast) }
        entries.sort { $0.2 < $1.2 }
        var size = entries.reduce(0) { $0+$1.1 }, count = entries.count
        for (path,length,_) in entries {
            if size+requested <= 256*1024*1024 && count < 200 { break }
            if !FileManager.default.fileExists(atPath:path.deletingPathExtension().appendingPathExtension("pin").path) {
                try FileManager.default.removeItem(at:path); size-=length; count-=1
            }
        }
        guard size+requested <= 256*1024*1024, count < 200 else { throw Failure.storageFull }
    }
    func release(_ input: [String: Any], delete: Bool) throws {
        let marker = try pin(input)
        if FileManager.default.fileExists(atPath:marker.path) { try FileManager.default.removeItem(at:marker) }
        let path = try file(input)
        if delete && FileManager.default.fileExists(atPath:path.path) { try FileManager.default.removeItem(at:path) }
        let key="media/sealed/" + (try id(input,"id"))
        if let existing=try vault.get(key),var record=try JSONSerialization.jsonObject(with:existing) as? [String:Any] {
            record.removeValue(forKey:"descriptor");record["released"]=true
            try vault.put(key,JSONSerialization.data(withJSONObject:record,options:[.sortedKeys]))
        }
    }
    func available(_ input:[String:Any]) throws -> [String] {
        guard let ids=input["ids"] as? [String],ids.count<=200 else {throw Failure.size}
        return try ids.compactMap {raw in
            let identifier=try id(["id":raw],"id")
            let path=directory.appendingPathComponent(identifier+".bin")
            guard FileManager.default.fileExists(atPath:path.path) else {return nil}
            let size=try path.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0
            return (29...Self.maxPlain+28).contains(size) ? identifier : nil
        }
    }
}
