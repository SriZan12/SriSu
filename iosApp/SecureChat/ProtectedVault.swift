import CryptoKit
import Foundation
import Security
import SQLite3

/// Account-scoped protected records, protocol state and durable send/receive journals.
final class ProtectedVault {
    enum Failure: Error { case sqlite(Int32), keychain(OSStatus), missingKey, corruptRecord, nestedTransaction, injected }
    private static let service = "com.srisu.couplechat.v1.protocol-store"
    private let scope: String
    private let key: SymmetricKey
    private var db: OpaquePointer?
    private let lock = NSRecursiveLock()
    private var inTransaction = false
    let directory: URL

    init(directory: URL, scope: String) throws {
        self.directory = directory
        self.scope = scope
        let file = directory.appendingPathComponent("records.sqlite")
        let exists = FileManager.default.fileExists(atPath: file.path)
        key = try Self.storageKey(scope: scope, allowCreate: !exists)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.protectionKey: FileProtectionType.complete])
        var protectedDirectory = directory
        var attributes = URLResourceValues()
        attributes.isExcludedFromBackup = true
        try protectedDirectory.setResourceValues(attributes)
        try check(sqlite3_open_v2(file.path, &db, SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE | SQLITE_OPEN_FULLMUTEX, nil))
        do {
            try execute("PRAGMA journal_mode=WAL")
            try execute("PRAGMA synchronous=FULL")
            try execute("CREATE TABLE IF NOT EXISTS records (key TEXT PRIMARY KEY NOT NULL, value BLOB NOT NULL)")
        } catch {
            sqlite3_close(db)
            db = nil
            throw error
        }
    }

    deinit { sqlite3_close(db) }

    private static func storageKey(scope: String, allowCreate: Bool) throws -> SymmetricKey {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: scope]
        var read = query
        read[kSecReturnData as String] = true
        var result: CFTypeRef?
        let status = SecItemCopyMatching(read as CFDictionary, &result)
        if status == errSecSuccess, let bytes = result as? Data, bytes.count == 32 { return SymmetricKey(data: bytes) }
        guard status == errSecItemNotFound else { throw Failure.keychain(status) }
        guard allowCreate else { throw Failure.missingKey }
        let key = SymmetricKey(size: .bits256)
        var insert = query
        insert[kSecValueData as String] = key.withUnsafeBytes { Data($0) }
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let added = SecItemAdd(insert as CFDictionary, nil)
        if added == errSecDuplicateItem { return try storageKey(scope: scope, allowCreate: false) }
        guard added == errSecSuccess else { throw Failure.keychain(added) }
        return key
    }

    static func deleteStorageKey(scope: String) throws {
        let status = SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: scope] as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw Failure.keychain(status) }
    }

    func transaction<T>(failBeforeCommit: Bool = false, _ work: () throws -> T) throws -> T {
        lock.lock()
        defer { lock.unlock() }
        guard !inTransaction else { throw Failure.nestedTransaction }
        try execute("BEGIN IMMEDIATE")
        inTransaction = true
        defer { inTransaction = false }
        do {
            let result = try work()
            if failBeforeCommit { throw Failure.injected }
            try execute("COMMIT")
            return result
        } catch {
            try execute("ROLLBACK")
            throw error
        }
    }

    func get(_ name: String) throws -> Data? {
        lock.lock()
        defer { lock.unlock() }
        var statement: OpaquePointer?
        try check(sqlite3_prepare_v2(db, "SELECT value FROM records WHERE key=?", -1, &statement, nil))
        defer { sqlite3_finalize(statement) }
        try bind(name, to: statement)
        let status = sqlite3_step(statement)
        if status == SQLITE_DONE { return nil }
        guard status == SQLITE_ROW, let bytes = sqlite3_column_blob(statement, 0) else { throw Failure.sqlite(status) }
        let data = Data(bytes: bytes, count: Int(sqlite3_column_bytes(statement, 0)))
        return try AES.GCM.open(AES.GCM.SealedBox(combined: data), using: key, authenticating: context(name))
    }

    func put(_ name: String, _ value: Data) throws {
        lock.lock()
        defer { lock.unlock() }
        guard let sealed = try AES.GCM.seal(value, using: key, authenticating: context(name)).combined else { throw Failure.corruptRecord }
        var statement: OpaquePointer?
        try check(sqlite3_prepare_v2(db, "INSERT OR REPLACE INTO records(key,value) VALUES(?,?)", -1, &statement, nil))
        defer { sqlite3_finalize(statement) }
        try bind(name, to: statement)
        try sealed.withUnsafeBytes { bytes in
            try check(sqlite3_bind_blob(statement, 2, bytes.baseAddress, Int32(bytes.count), unsafeBitCast(-1, to: sqlite3_destructor_type.self)))
        }
        guard sqlite3_step(statement) == SQLITE_DONE else { throw Failure.sqlite(sqlite3_errcode(db)) }
    }

    func remove(_ name: String) throws {
        lock.lock()
        defer { lock.unlock() }
        var statement: OpaquePointer?
        try check(sqlite3_prepare_v2(db, "DELETE FROM records WHERE key=?", -1, &statement, nil))
        defer { sqlite3_finalize(statement) }
        try bind(name, to: statement)
        guard sqlite3_step(statement) == SQLITE_DONE else { throw Failure.sqlite(sqlite3_errcode(db)) }
    }

    func page(prefix: String, before: String, limit: Int) throws -> [String] {
        lock.lock()
        defer { lock.unlock() }
        var statement: OpaquePointer?
        let ceiling = prefix + "\u{ffff}"
        try check(sqlite3_prepare_v2(db, "SELECT key FROM records WHERE key>=? AND key<? AND key<? ORDER BY key DESC LIMIT ?", -1, &statement, nil))
        defer { sqlite3_finalize(statement) }
        for (index, value) in [prefix, ceiling, before.isEmpty ? ceiling : before].enumerated() {
            try check(sqlite3_bind_text(statement, Int32(index + 1), value, -1, unsafeBitCast(-1, to: sqlite3_destructor_type.self)))
        }
        try check(sqlite3_bind_int(statement, 4, Int32(limit)))
        var rows: [String] = []
        while true {
            let status = sqlite3_step(statement)
            if status == SQLITE_DONE { return rows }
            guard status == SQLITE_ROW, let name = sqlite3_column_text(statement, 0) else { throw Failure.sqlite(status) }
            rows.append(String(cString: name))
        }
    }

    func count(prefix: String) throws -> Int {
        lock.lock(); defer { lock.unlock() }
        var statement: OpaquePointer?
        try check(sqlite3_prepare_v2(db, "SELECT count(*) FROM records WHERE key>=? AND key<?", -1, &statement, nil))
        defer { sqlite3_finalize(statement) }
        try bind(prefix, to: statement)
        try check(sqlite3_bind_text(statement, 2, prefix + "\u{ffff}", -1, unsafeBitCast(-1, to: sqlite3_destructor_type.self)))
        guard sqlite3_step(statement) == SQLITE_ROW else { throw Failure.sqlite(sqlite3_errcode(db)) }
        return Int(sqlite3_column_int(statement, 0))
    }

    private func context(_ name: String) -> Data { Data((scope + "\u{0}" + name).utf8) }
    private func bind(_ name: String, to statement: OpaquePointer?) throws {
        try check(sqlite3_bind_text(statement, 1, name, -1, unsafeBitCast(-1, to: sqlite3_destructor_type.self)))
    }
    private func check(_ result: Int32) throws { if result != SQLITE_OK { throw Failure.sqlite(result) } }
    private func execute(_ sql: String) throws { try check(sqlite3_exec(db, sql, nil, nil, nil)) }
}
