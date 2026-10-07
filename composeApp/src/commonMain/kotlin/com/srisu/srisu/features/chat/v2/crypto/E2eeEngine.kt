package com.srisu.srisu.features.chat.v2.crypto

import com.srisu.srisu.features.chat.v2.domain.ChatV2Scope

/** Wire JSON is owned by a reviewed protocol adapter and remains opaque to chat storage. */
data class OpaqueE2eeEnvelope(
    val protocolId: String,
    val wireJson: String,
) {
    init {
        require(protocolId.isNotBlank())
        require(wireJson.isNotBlank())
    }
}

data class E2eeSealContext(
    val scope: ChatV2Scope,
    val operationId: String,
    val conversationId: String,
    val membershipEpoch: Long,
    val contentKind: String,
    val replyToId: String?,
    val attachmentIds: List<String>,
)

sealed interface E2eeEngineAvailability {
    data class Ready(val protocolId: String) : E2eeEngineAvailability
    data class Unavailable(val reason: String) : E2eeEngineAvailability
}

interface E2eeEngine {
    val availability: E2eeEngineAvailability

    /**
     * A production adapter must authenticate every field in [context], advance its
     * ratchet exactly once, and make that advancement crash-consistent with
     * [persistExactEnvelope]. Implementations need a recovery journal/two-phase
     * protocol if key state and the Room outbox do not share one transaction.
     * Plaintext may exist only for this call and must never be logged or persisted.
     */
    suspend fun sealTextAndPersist(
        context: E2eeSealContext,
        plaintext: String,
        persistExactEnvelope: suspend (OpaqueE2eeEnvelope) -> Unit,
    ): Result<Unit>
}

/** Production fail-closed adapter until a reviewed multiplatform protocol implementation is wired. */
class UnavailableE2eeEngine : E2eeEngine {
    override val availability: E2eeEngineAvailability =
        E2eeEngineAvailability.Unavailable("A secure chat protocol adapter is required.")

    override suspend fun sealTextAndPersist(
        context: E2eeSealContext,
        plaintext: String,
        persistExactEnvelope: suspend (OpaqueE2eeEnvelope) -> Unit,
    ): Result<Unit> =
        Result.failure(E2eeUnavailableException())
}

class E2eeUnavailableException : IllegalStateException("Secure messaging is not available on this build.")
