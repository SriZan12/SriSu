package com.srisu.srisu.features.sparks

import com.srisu.srisu.features.chat.matrix.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

/** Real SDK secret-storage crypto on each target; no fake cipher or plaintext transport. */
class SparkRecoveryTest {
    private val owner = "@one:test.invalid"
    private val answer = SparkFixtures.one
    private val session = SparkFixtures.session.copy(answers = listOf(answer), submittedBy = listOf(1))
    private val text = "Synthetic recovery answer, only in an encrypted backup."

    @Test fun freshDeviceRecoversWithoutOldDeviceOrMegolmStore() = runBlocking {
        val server = VaultTransport()
        val oldPhone = SparkRecoveryVault(server, owner)
        val key = newSparkRecoveryKey()
        assertEquals(SparkRecoveryStatus.NotSetUp, oldPhone.status(null))
        oldPhone.create(key)
        oldPhone.backup(key, session, answer, text)
        assertFalse(server.values.toString().contains(text))
        assertFalse(server.values.toString().contains(key))
        val newPhone = SparkRecoveryVault(server, owner)
        assertEquals(SparkRecoveryStatus.Locked, newPhone.status(null))
        newPhone.unlock(key)
        assertEquals(SparkRecoveryStatus.Ready, newPhone.status(key))
        assertEquals(text, newPhone.recover(key, session, answer))
    }
    @Test fun wrongKeyAccountAndReplacementAreRejectedWithoutChangingBackup() = runBlocking {
        val server = VaultTransport(); val vault = SparkRecoveryVault(server, owner)
        val key = newSparkRecoveryKey(); vault.create(key); vault.backup(key, session, answer, text)
        val before = server.values.toMap()
        assertTrue(runCatching { vault.unlock(newSparkRecoveryKey()) }.isFailure)
        assertTrue(runCatching { vault.create(newSparkRecoveryKey()) }.isFailure)
        assertTrue(runCatching { SparkRecoveryVault(server, "@other:test.invalid").unlock(key) }.isFailure)
        assertTrue(runCatching { SparkRecoveryVault(server, "@other:test.invalid").recover(key, session, answer) }.isFailure)
        assertEquals(before, server.values)
        assertEquals(text, vault.recover(key, session, answer))
    }
    @Test fun corruptedBackupAndTransplantedQuestionAreRejected() = runBlocking {
        val server = VaultTransport(); val vault = SparkRecoveryVault(server, owner)
        val key = newSparkRecoveryKey(); vault.create(key); vault.backup(key, session, answer, text)
        assertTrue(runCatching { vault.recover(key, session.copy(question = "A different question"), answer) }.isFailure)
        assertTrue(runCatching { vault.recover(key, session.copy(epoch = 9), answer) }.isFailure)
        assertTrue(runCatching { vault.recover(key, session.copy(matrixRoomId = "!other:test.invalid"), answer) }.isFailure)
        val name = server.values.keys.single { ".answer." in it }
        val raw = server.values.getValue(name)
        val encrypted = raw.getValue("encrypted").jsonObject
        val id = encrypted.keys.single()
        val data = encrypted.getValue(id).jsonObject
        server.values[name] = JsonObject(raw + ("encrypted" to JsonObject(mapOf(id to JsonObject(data + ("mac" to JsonPrimitive("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")))))))
        assertTrue(runCatching { vault.recover(key, session, answer) }.isFailure)
    }
    @Test fun revealGateRequiresTheExactReturnedEnvelopeAndCanonicalOrderIsStable() = runBlocking {
        val server = VaultTransport(); val vault = SparkRecoveryVault(server, owner)
        val key = newSparkRecoveryKey(); vault.create(key); vault.backup(key, session, answer, text)
        assertTrue(runCatching { vault.recover(key, session.copy(answers = emptyList()), answer) }.isFailure)
        val reversed = answer.copy(envelope = JsonObject(answer.envelope.entries.reversed().associate { it.toPair() }))
        assertEquals(text, vault.recover(key, session.copy(answers = listOf(reversed)), reversed))
        val changed = answer.copy(envelope = JsonObject(answer.envelope + ("ciphertext" to JsonPrimitive("changed"))))
        assertNull(vault.recover(key, session.copy(answers = listOf(changed)), changed))
    }
    @Test fun lostUploadAcknowledgementRetriesSameImmutableBackup() = runBlocking {
        val server = VaultTransport(); val vault = SparkRecoveryVault(server, owner)
        val key = newSparkRecoveryKey(); vault.create(key)
        server.failAfterWrite = true
        assertTrue(runCatching { vault.backup(key, session, answer, text) }.isFailure)
        val writes = server.writes
        vault.backup(key, session, answer, text)
        assertEquals(writes, server.writes)
        assertEquals(text, vault.recover(key, session, answer))
    }
    @Test fun interruptedSetupResumesSameKeyAndTransportFailureIsNotAnEmptyBackup() = runBlocking {
        val server = VaultTransport(); val vault = SparkRecoveryVault(server, owner)
        val key = newSparkRecoveryKey()
        assertEquals(SparkRecoveryStatus.Pending, vault.status(key))
        server.failAfterWrite = true
        assertTrue(runCatching { vault.create(key) }.isFailure)
        vault.create(key)
        assertEquals(SparkRecoveryStatus.Ready, vault.status(key))
        server.offline = true
        assertTrue(runCatching { vault.status(null) }.isFailure)
    }
}

private class VaultTransport : SparkRecoveryTransport {
    val values = mutableMapOf<String, JsonObject>()
    var writes = 0
    var failAfterWrite = false
    var offline = false
    override suspend fun get(name: String): JsonObject? { check(!offline); return values[name] }
    override suspend fun put(name: String, content: JsonObject) {
        check(!offline); writes++; values[name] = content
        if (failAfterWrite) { failAfterWrite = false; error("Synthetic lost acknowledgement") }
    }
}
