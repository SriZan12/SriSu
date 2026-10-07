package com.srisu.srisu.features.chat

import com.srisu.srisu.core.data.remote.ApiJson
import com.srisu.srisu.features.chat.v2.crypto.E2eeSealContext
import com.srisu.srisu.features.chat.v2.crypto.UnavailableE2eeEngine
import com.srisu.srisu.features.chat.v2.data.chatV2RetryDelayMillis
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2CapabilitiesDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2OperationResultDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2MessageDto
import com.srisu.srisu.features.chat.v2.domain.ChatDraftPolicy
import com.srisu.srisu.features.chat.v2.domain.ChatV2Scope
import com.srisu.srisu.features.chat.v2.presentation.ChatEntryTarget
import com.srisu.srisu.features.chat.v2.presentation.ChatExperienceMode
import com.srisu.srisu.features.chat.v2.presentation.ChatV2RolloutPolicy
import com.srisu.srisu.core.config.ApiEnvironment
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatV2FoundationTest {
    @Test fun capabilityDefaultsFailClosed() {
        val parsed = ApiJson.decodeFromString<ChatV2CapabilitiesDto>("""{"api_version":2}""")
        assertFalse(parsed.encryptedWritesEnabled)
        assertEquals("adapter_required", parsed.protocolStatus)
        assertTrue(parsed.supportedKinds.isEmpty())
        assertTrue(parsed.requiresDeviceSession)
    }

    @Test fun exactCapabilityContractDecodesPinnedFieldNames() {
        val parsed = ApiJson.decodeFromString<ChatV2CapabilitiesDto>(
            """{"api_version":2,"encrypted_writes_enabled":true,"protocol_status":"ready","max_message_bytes":4096,"max_envelope_bytes":65536,"max_attachment_bytes":10485760,"supported_kinds":["text"],"requires_device_session":true}"""
        )
        assertEquals(2, parsed.apiVersion)
        assertTrue(parsed.encryptedWritesEnabled)
        assertEquals("ready", parsed.protocolStatus)
        assertEquals(4_096L, parsed.maxMessageBytes)
        assertEquals(65_536L, parsed.maxEnvelopeBytes)
        assertEquals(10_485_760L, parsed.maxAttachmentBytes)
        assertEquals(listOf("text"), parsed.supportedKinds)
        assertTrue(parsed.requiresDeviceSession)
    }

    @Test fun desiredStateNoOpAllowsNullChangeSequence() {
        val parsed = ApiJson.decodeFromString<ChatV2OperationResultDto>(
            """{"operation_id":"$OPERATION","replayed":false,"change_sequence":null,"message":{"id":9,"sequence":3,"revision":2,"sender_id":1,"content_kind":"text","envelope":{"ciphertext":"opaque"},"reply_to_id":null,"attachment_ids":[],"reactions":[],"is_tombstone":false,"hidden_for_viewer":false,"legacy_plaintext":false,"created_at":"2026-10-07T00:00:00Z","edited_at":null}}"""
        )
        assertEquals(OPERATION, parsed.operationId)
        assertEquals(null, parsed.changeSequence)
        assertEquals(9L, parsed.message.id)
    }

    @Test fun backendBigIntegerIdentifiersDecodeWithoutOverflow() {
        val parsed = ApiJson.decodeFromString<ChatV2MessageDto>(
            """{"id":3000000001,"operation_id":"$OPERATION","sequence":3000000002,"revision":3000000003,"sender_id":3000000004,"content_kind":"text","envelope":{"ciphertext":"opaque"},"reply_to_id":3000000005,"created_at":"2026-10-07T00:00:00Z"}"""
        )
        assertEquals(3_000_000_001L, parsed.id)
        assertEquals(3_000_000_003L, parsed.revision)
        assertEquals(3_000_000_004L, parsed.senderId)
        assertEquals(3_000_000_005L, parsed.replyToId)
        assertEquals(OPERATION, parsed.operationId)
    }

    @Test fun plaintextDraftPersistenceIsExplicitlyDisabled() {
        assertFalse(ChatDraftPolicy.PERSIST_PLAINTEXT_DRAFTS)
        assertTrue(ChatDraftPolicy.REASON.contains("not available"))
    }

    @Test fun unavailableProductionCryptoNeverInvokesPersistence() = runTest {
        var persisted = false
        val result = UnavailableE2eeEngine().sealTextAndPersist(
            context = E2eeSealContext(
                scope = ChatV2Scope("https://example.test/", 1, ROOM, 3),
                operationId = OPERATION,
                conversationId = ROOM,
                membershipEpoch = 7,
                contentKind = "text",
                replyToId = "12",
                attachmentIds = emptyList(),
            ),
            plaintext = "must not persist",
        ) { persisted = true }
        assertTrue(result.isFailure)
        assertFalse(persisted)
    }

    @Test fun retryJitterIsStableBoundedAndAttemptSensitive() {
        val first = chatV2RetryDelayMillis(OPERATION, 1)
        assertEquals(first, chatV2RetryDelayMillis(OPERATION, 1))
        assertTrue(first in 400L..600L)
        val third = chatV2RetryDelayMillis(OPERATION, 3)
        assertTrue(third in 1_600L..2_400L)
        assertTrue(first != third)
        assertTrue(chatV2RetryDelayMillis(OPERATION, 99) <= 30_000L)
    }

    @Test fun routingPolicyNeverFallsBackAfterSelectingSecureMode() {
        val experimental = ChatV2RolloutPolicy.configured(ApiEnvironment("http://localhost:8000/", development = true))
        assertEquals(ChatExperienceMode.SECURE_V2_EXPERIMENTAL, experimental.mode)
        assertEquals(ChatEntryTarget.SECURE_V2, experimental.entryTarget)
        assertTrue(experimental.exposesSecureAcceptanceAction)

        val secureRequired = ChatV2RolloutPolicy(ChatExperienceMode.SECURE_REQUIRED_UNAVAILABLE)
        assertEquals(ChatEntryTarget.SECURE_V2, secureRequired.entryTarget)

        val releasedCompatibility = ChatV2RolloutPolicy.configured(ApiEnvironment("https://api.example.test/"))
        assertEquals(ChatExperienceMode.LEGACY_COMPATIBILITY, releasedCompatibility.mode)
        assertEquals(ChatEntryTarget.LEGACY_COMPATIBILITY, releasedCompatibility.entryTarget)
        assertFalse(releasedCompatibility.exposesSecureAcceptanceAction)
    }

    companion object {
        private const val ROOM = "123e4567-e89b-12d3-a456-426614174000"
        private const val OPERATION = "123e4567-e89b-42d3-a456-426614174001"
    }
}
