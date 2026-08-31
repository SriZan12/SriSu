package com.srisu.srisu.features.home.couple.data.remote.mapper

import com.srisu.srisu.features.home.couple.data.remote.response.CoupleProfileResponse
import com.srisu.srisu.features.home.couple.presentation.state.CoupleProfileUiState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

private val shortMonthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

fun CoupleProfileResponse.toUiState(): CoupleProfileUiState {
    val orderedUsers = members
        .sortedBy { it.position }
        .mapNotNull { it.user }

    return CoupleProfileUiState(
        id = id,
        coupleConnectionId = coupleConnectionId,
        partnerId = partner?.id,
        profileComplete = profileComplete,
        coupleTitle = title.orEmpty(),
        partnerNames = orderedUsers
            .mapNotNull { it.fullName?.takeIf(String::isNotBlank) }
            .joinToString(" & "),
        tagline = relationshipTagline.orEmpty(),
        coverPhotoUrl = coverPhotoUrl ?: coverPhoto,
        firstPartnerPhotoUrl = orderedUsers.getOrNull(0)?.profilePhoto,
        secondPartnerPhotoUrl = orderedUsers.getOrNull(1)?.profilePhoto,
        daysTogether = daysTogether ?: 0,
        anniversaryDate = anniversaryDate,
        anniversary = anniversaryDate.toReadableAnniversary(),
        sharedDreams = sharedDreams,
        sharedInterests = sharedInterests,
        relationshipStrength = relationshipStrength ?: 0,
        journeyStory = journeyStory.orEmpty(),
    )
}

fun String?.toReadableAnniversary(): String {
    if (this.isNullOrBlank()) return "—"

    return runCatching {
        val date = LocalDate.parse(this)
        "${shortMonthNames[date.month.number - 1]} ${date.day}"
    }.getOrDefault(this)
}
