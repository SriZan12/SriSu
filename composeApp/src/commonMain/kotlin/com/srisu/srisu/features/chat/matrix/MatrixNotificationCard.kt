package com.srisu.srisu.features.chat.matrix

import com.srisu.srisu.features.notifications.validNotificationId
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import kotlinx.serialization.json.*

/** Only an opaque, sender/room-bound key leaves the encrypted feature-card body. */
internal fun MegolmEncryptedMessageEventContent.notificationCard(json: Json, key: String): EncryptedMessageEventContent {
    require(validNotificationId(key))
    val wire = json.encodeToJsonElement(MegolmEncryptedMessageEventContent.serializer(), this).jsonObject
    return EncryptedMessageEventContent.Unknown(algorithm,
        JsonObject(wire + ("org.srisu.notification_card" to JsonPrimitive(key))))
}
