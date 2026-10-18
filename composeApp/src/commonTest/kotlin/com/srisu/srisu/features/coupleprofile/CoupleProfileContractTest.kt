package com.srisu.srisu.features.coupleprofile

import com.srisu.srisu.core.data.remote.ApiJson
import com.srisu.srisu.features.coupleprofile.data.ProfileInviteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlinx.serialization.decodeFromString

class CoupleProfileContractTest {
    @Test
    fun storyInviteDecodesDomainRecordWithoutLegacyChatIdentifiers() {
        val result = ApiJson.decodeFromString<ProfileInviteResult>(
            """{"id":7,"request_id":"123e4567-e89b-12d3-a456-426614174000","prompt":"How did you meet?","created_by":1,"created_at":"2026-10-09T00:00:00Z"}"""
        )

        assertEquals(7, result.id)
        assertEquals("123e4567-e89b-12d3-a456-426614174000", result.requestId)
        assertEquals("How did you meet?", result.prompt)
        assertEquals(1, result.createdBy)
    }

    @Test
    fun storyInviteRejectsRetiredChatSideEffectShape() {
        assertFails {
            ApiJson.decodeFromString<ProfileInviteResult>(
                """{"message_id":7,"room_id":"123e4567-e89b-12d3-a456-426614174000"}"""
            )
        }
    }
}
