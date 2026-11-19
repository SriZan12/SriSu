package com.srisu.srisu.features.chat.matrix

import com.srisu.srisu.features.challenges.data.*
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.room.RoomEventEncryptionService
import de.connect2x.trixnity.client.room.encrypt
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent

internal suspend fun sendChallengeCard(client: MatrixClient, session: ChallengeSession): String {
    val room = RoomId(requireNotNull(session.matrixRoomId))
    val reference = ChallengeReference(session.id, session.couple)
    val encrypted = client.di.getAll<RoomEventEncryptionService>().encrypt(
        RoomMessageEventContent.TextBased.Text(body = "An invitation for us: ${session.activity.title}", externalUrl = reference.url), room)?.getOrThrow()
    require(encrypted is MegolmEncryptedMessageEventContent)
    return client.api.room.sendMessageEvent(room, encrypted, txnId = "srisu-challenge-${session.id}").getOrThrow().full
}
