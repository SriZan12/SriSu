package com.srisu.srisu.features.chat

import com.srisu.srisu.features.chat.matrix.MATRIX_JWT_LOGIN_TYPE
import com.srisu.srisu.features.chat.matrix.MATRIX_PROTOCOL_ID
import com.srisu.srisu.features.chat.matrix.MatrixSessionDto
import com.srisu.srisu.features.chat.matrix.MatrixStorageScope
import com.srisu.srisu.features.chat.matrix.MatrixClientReuseDecision
import com.srisu.srisu.features.chat.matrix.coupleOnlyMembership
import com.srisu.srisu.features.chat.matrix.matrixClientReuseDecision
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.events.m.room.Membership
import com.srisu.srisu.features.chat.matrix.validatedDjangoRoomId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MatrixSessionTest {
    @Test
    fun acceptsPinnedJwtContractAndStableDjangoRoomUuid() {
        assertEquals(ROOM_UUID, validatedDjangoRoomId(ROOM_UUID.uppercase()))
        assertEquals(SELF_ID, validSession().validated(SELF_ID).selfUserId)
    }

    @Test
    fun rejectsWrongAccountOrUnsafeHomeserver() {
        assertFailsWith<IllegalArgumentException> { validSession().validated(PARTNER_ID) }
        assertFailsWith<IllegalArgumentException> {
            validSession().copy(homeserverUrl = "http://matrix.example.test/").validated(SELF_ID)
        }
        validSession().copy(homeserverUrl = "http://127.0.0.1:8008/")
            .validated(SELF_ID, allowInsecureHomeserver = true)
    }

    @Test
    fun rejectsRefreshTokensAndUnexpectedLoginType() {
        assertFailsWith<IllegalArgumentException> {
            validSession().copy(refreshTokenEnabled = true).validated(SELF_ID)
        }
        assertFailsWith<IllegalArgumentException> {
            validSession().copy(loginType = "m.login.token").validated(SELF_ID)
        }
        listOf(null, 299L, 901L, 86_400L).forEach { lifetime ->
            assertFailsWith<IllegalArgumentException> {
                validSession().copy(accessTokenExpiresInSeconds = lifetime).validated(SELF_ID)
            }
        }
    }

    @Test
    fun acceptsHomeserverOriginWithOrWithoutTrailingSlash() {
        validSession().copy(homeserverUrl = "https://matrix.example.test").validated(SELF_ID)
        validSession().copy(homeserverUrl = "https://matrix.example.test/").validated(SELF_ID)
    }

    @Test
    fun storageScopeSeparatesAccountsAndApiOrHomeserverEnvironments() {
        fun scope(
            accountId: Long = SELF_ID,
            api: String = "https://api.example.test/",
            matrix: String = "https://matrix.example.test/",
            relationshipId: Long = 77,
            epoch: Long = 1,
            roomId: String = "!couple:example.test",
        ) = MatrixStorageScope.create(accountId, api, matrix, relationshipId, epoch, roomId)

        val baseline = scope()
        assertEquals(baseline, scope(api = "https://api.example.test", matrix = "https://matrix.example.test"))
        assertNotEquals(baseline, scope(accountId = PARTNER_ID))
        assertNotEquals(baseline.namespace, scope(api = "https://staging.example.test/").namespace)
        assertNotEquals(baseline.namespace, scope(matrix = "https://matrix.staging.test/").namespace)
        assertNotEquals(baseline.namespace, scope(relationshipId = 78).namespace)
        assertNotEquals(baseline.namespace, scope(epoch = 2).namespace)
        assertNotEquals(baseline.namespace, scope(roomId = "!replacement:example.test").namespace)
    }

    @Test
    fun encryptedRoomMembershipMustBeExactlyTheCouple() {
        val own = "@srisu_1:matrix.example.test"
        val partner = "@srisu_2:matrix.example.test"
        val joined = mapOf(own to Membership.JOIN, partner to Membership.JOIN)
        assertTrue(coupleOnlyMembership(joined, own, partner))
        assertFalse(coupleOnlyMembership(mapOf(own to Membership.JOIN), own, partner))
        assertFalse(coupleOnlyMembership(joined + ("@intruder:matrix.example.test" to Membership.JOIN), own, partner))
        assertFalse(coupleOnlyMembership(joined + ("@invited:matrix.example.test" to Membership.INVITE), own, partner))
        assertFalse(coupleOnlyMembership(joined + ("@knocking:matrix.example.test" to Membership.KNOCK), own, partner))
        assertTrue(coupleOnlyMembership(joined + ("@former:matrix.example.test" to Membership.LEAVE), own, partner))
        assertFalse(coupleOnlyMembership(mapOf(own to Membership.JOIN), own, own))
    }

    @Test
    fun closedOrSoftLoggedOutClientIsNeverReusedWithFreshBootstrap() {
        assertEquals(
            MatrixClientReuseDecision.REUSE,
            matrixClientReuseDecision(sameIdentity = true, state = MatrixClient.LoginState.LOGGED_IN),
        )
        listOf(
            MatrixClient.LoginState.LOGGED_OUT_SOFT,
            MatrixClient.LoginState.LOCKED,
            null,
        ).forEach { state ->
            assertEquals(
                MatrixClientReuseDecision.REAUTHENTICATE,
                matrixClientReuseDecision(sameIdentity = true, state),
            )
        }
        assertEquals(
            MatrixClientReuseDecision.REPLACE,
            matrixClientReuseDecision(sameIdentity = false, state = MatrixClient.LoginState.LOGGED_IN),
        )
        assertEquals(
            MatrixClientReuseDecision.REPLACE,
            matrixClientReuseDecision(sameIdentity = true, state = MatrixClient.LoginState.LOGGED_OUT),
        )
    }

    private fun validSession() = MatrixSessionDto(
        protocolId = MATRIX_PROTOCOL_ID,
        homeserverUrl = "https://matrix.example.test/",
        roomId = "!couple:matrix.example.test",
        roomAlias = "#couple:matrix.example.test",
        membershipEpoch = 1,
        relationshipId = 9,
        selfUserId = SELF_ID,
        selfMatrixUserId = "@srisu_1:matrix.example.test",
        partnerUserId = PARTNER_ID,
        partnerMatrixUserId = "@srisu_2:matrix.example.test",
        initiatorUserId = SELF_ID,
        loginType = MATRIX_JWT_LOGIN_TYPE,
        loginToken = "synthetic.jwt.value",
        loginTokenExpiresAt = "2030-01-01T00:00:00Z",
        loginTokenOneTime = false,
        refreshTokenEnabled = false,
        accessTokenExpiresInSeconds = 300,
    )

    private companion object {
        const val ROOM_UUID = "123e4567-e89b-12d3-a456-426614174000"
        const val SELF_ID = 1L
        const val PARTNER_ID = 2L
    }
}
