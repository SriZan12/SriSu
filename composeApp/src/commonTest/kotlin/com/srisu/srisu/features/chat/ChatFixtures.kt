package com.srisu.srisu.features.chat

import com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity
import com.srisu.srisu.features.chat.matrix.ChatPartnerSource
import com.srisu.srisu.features.chat.matrix.MatrixChatAvailability
import com.srisu.srisu.features.chat.matrix.MatrixChatMessage
import com.srisu.srisu.features.chat.matrix.MatrixChatRepository
import com.srisu.srisu.features.chat.matrix.MatrixChatRoomOwner
import com.srisu.srisu.features.chat.matrix.MatrixChatRoomState
import com.srisu.srisu.features.chat.matrix.MatrixMessageDelivery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Synthetic, non-sensitive conversation content used only by tests and render captures. */
object ChatFixtures {
    const val ROOM_ID = "6f0c2a8e-2f8b-4a55-9a51-3c7a1f1d2b10"
    private const val ME = "@me:example.test"
    private const val PARTNER = "@partner:example.test"

    /** 2026-10-16 08:02 in Asia/Kathmandu (UTC+05:45). */
    private const val YESTERDAY_MORNING = 1_792_117_020_000L
    private const val MINUTE = 60_000L
    private const val DAY = 24 * 60 * MINUTE

    const val MULTILINE_DRAFT = "Can we keep Saturday free?\nI'd love a slow morning, then the lake walk.\nMaybe dinner at the place we found?"

    private fun message(
        id: String,
        body: String,
        mine: Boolean,
        minutes: Long,
        delivery: MatrixMessageDelivery = MatrixMessageDelivery.SENT,
        unavailable: Boolean = false,
        retry: String? = null,
    ) = MatrixChatMessage(
        id = id,
        body = body,
        senderMatrixId = if (mine) ME else PARTNER,
        mine = mine,
        timestampMillis = YESTERDAY_MORNING + minutes * MINUTE,
        delivery = delivery,
        retryTransactionId = retry,
        unavailable = unavailable,
    )

    private fun ready(messages: List<MatrixChatMessage>) = MatrixChatRoomState(
        roomId = ROOM_ID,
        messages = messages,
        availability = MatrixChatAvailability.Ready(),
    )

    /** Synthetic partner; no photo URL so renders never fetch remote media. */
    val partner = ChatPartnerIdentity(name = "Aatithi Rai", photoUrl = null)

    fun empty() = ready(emptyList())

    /** Thirty alternating exchanges for scroll, jump-to-latest and pagination-stability checks. */
    fun longHistory() = ready(
        (0 until 30).map { index ->
            message(
                id = "h$index",
                body = if (index % 3 == 0) "Message $index — a slightly longer line so wrapping is exercised." else "Message $index",
                mine = index % 4 >= 2,
                minutes = index * 7L,
            )
        },
    )

    fun conversationMessages() = listOf(
        message("e1", "Morning! Did you sleep okay?", mine = false, minutes = 0),
        message("e2", "I had the strangest dream about our trip 😄", mine = false, minutes = 1),
        message("e3", "Morning ☀️ Finally slept well.", mine = true, minutes = 4),
        message("e4", "Tell me about the dream at lunch?", mine = true, minutes = 4),
        message("e5", "Deal. Same café as last week?", mine = false, minutes = 6),
        message("e6", "Yes 💛", mine = true, minutes = DAY / MINUTE + 30),
        message("e7", "I'm leaving work a little early today, so I can pick up the flowers on the way.", mine = false, minutes = DAY / MINUTE + 32),
    )

    fun conversation() = ready(conversationMessages())

    fun longAndGrouped() = ready(
        listOf(
            message("l1", "Okay, planning brain is on.", mine = false, minutes = 0),
            message(
                "l2",
                "For the weekend I was thinking we leave early on Saturday, stop for breakfast near the river, " +
                    "and then take the long way up to the viewpoint so we're there before it gets crowded. " +
                    "If it rains we can just stay in and finally finish that puzzle.",
                mine = false,
                minutes = 1,
            ),
            message("l3", "Yes yes yes", mine = true, minutes = 3),
            message("l4", "Saved the route here: https://example.com/weekend-route-with-a-very-long-path/and-more", mine = true, minutes = 3),
            message("l5", "Unbelievablysupercalifragilisticexpialidociouslyexcited", mine = true, minutes = 3),
            message("l6", "माया गर्छु 💛", mine = false, minutes = 9),
            message("l7", "👍", mine = true, minutes = 10),
        ),
    )

    fun deliveryStates() = ready(
        listOf(
            message("d1", "Did you get the tickets?", mine = false, minutes = 0),
            message("d2", "", mine = false, minutes = 1, unavailable = true),
            message("d3", "Yes! Sending the screenshot details now.", mine = true, minutes = 5),
            message("d4", "Row F, seats 11 and 12.", mine = true, minutes = 6, delivery = MatrixMessageDelivery.SENDING),
            message("txn:t1", "Doors open at 7.", mine = true, minutes = 6, delivery = MatrixMessageDelivery.FAILED, retry = "t1"),
        ),
    )

    fun offline() = conversation().copy(
        availability = MatrixChatAvailability.Offline("Secure chat is offline. Sending is paused until a fresh sync completes."),
    )

    fun loading() = MatrixChatRoomState(roomId = ROOM_ID, availability = MatrixChatAvailability.Loading, syncing = true)

    fun error() = MatrixChatRoomState(
        roomId = ROOM_ID,
        availability = MatrixChatAvailability.Error("Secure chat is not synchronized. Retry to reconnect.", retryable = true),
    )

    fun unavailableRoom() = MatrixChatRoomState(
        roomId = ROOM_ID,
        availability = MatrixChatAvailability.Unavailable("This conversation is no longer a private encrypted couple room."),
    )
}

class FixedPartnerSource(private val identity: ChatPartnerIdentity?) : ChatPartnerSource {
    val requests = mutableListOf<String>()
    override suspend fun partnerFor(roomId: String): ChatPartnerIdentity? {
        requests += roomId
        return identity
    }
}

/** In-memory repository that serves a fixed room state and records interactions. */
class ChatFixtureRepository(initial: MatrixChatRoomState) : MatrixChatRepository {
    val state = MutableStateFlow(initial)
    val sent = mutableListOf<String>()
    val retried = mutableListOf<String>()
    var sendResult: Result<String> = Result.success("event")

    override fun observeRoom(roomId: String): Flow<MatrixChatRoomState> = state
    override fun openRoom(roomId: String, owner: MatrixChatRoomOwner) = Unit
    override fun closeRoom(roomId: String, owner: MatrixChatRoomOwner) = Unit
    override suspend fun sendText(roomId: String, text: String): Result<String> {
        sent += text
        return sendResult
    }
    override suspend fun retryMessage(roomId: String, transactionId: String): Result<Unit> {
        retried += transactionId
        return Result.success(Unit)
    }
    override fun retryRoom(roomId: String, owner: MatrixChatRoomOwner) = Unit
    override suspend fun attachment(roomId: String, messageId: String, thumbnail: Boolean): Result<ByteArray> = Result.success(ChatRecoveryFixtures.image)
    override fun close() = Unit
}
