package com.srisu.srisu.features.chat.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionChangedException
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStamp
import com.srisu.srisu.features.home.connection.domain.repository.ConnectionRepository
import com.srisu.srisu.utils.Constants.ConnectionStatus.ACCEPTED
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface MatrixChatEntryState {
    data object Resolving : MatrixChatEntryState
    data class Ready(val roomId: String) : MatrixChatEntryState
    data class Unavailable(val message: String) : MatrixChatEntryState
    data class Error(val message: String) : MatrixChatEntryState
}

interface CurrentCoupleRoomResolver {
    suspend fun resolve(): NetworkAPIResult<String?>
}

class ConnectionCurrentCoupleRoomResolver(
    private val repository: ConnectionRepository,
) : CurrentCoupleRoomResolver {
    override suspend fun resolve(): NetworkAPIResult<String?> =
        when (val result = repository.sendHaveCoupleConnectionRequested().result) {
            is NetworkAPIResult.Error -> NetworkAPIResult.Error(result.failure)
            is NetworkAPIResult.Success -> {
                val connection = result.response?.connection
                val roomId = connection
                    ?.takeIf { it.connectionStatus == ACCEPTED }
                    ?.chatRoomId
                    ?.let { runCatching { validatedDjangoRoomId(it) }.getOrNull() }
                NetworkAPIResult.Success<String?>(roomId, result.message, result.metadata)
            }
        }
}

/** Display-only partner identity for the conversation header; never used for authorization. */
data class ChatPartnerIdentity(val name: String, val photoUrl: String?)

interface ChatPartnerSource {
    /** Returns the current partner only when the active relationship owns [roomId]. */
    suspend fun partnerFor(roomId: String): ChatPartnerIdentity?
}

class ConnectionChatPartnerSource(
    private val repository: ConnectionRepository,
) : ChatPartnerSource {
    override suspend fun partnerFor(roomId: String): ChatPartnerIdentity? {
        val requested = normalizeDjangoRoomIdOrNull(roomId) ?: return null
        val connection = (repository.sendHaveCoupleConnectionRequested().result as? NetworkAPIResult.Success)
            ?.response?.connection
            ?.takeIf { it.connectionStatus == ACCEPTED }
            ?: return null
        if (connection.chatRoomId?.let(::normalizeDjangoRoomIdOrNull) != requested) return null
        val partner = connection.partner ?: return null
        val name = listOfNotNull(partner.fullName, partner.firstName, partner.username)
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)
            ?: return null
        return ChatPartnerIdentity(name = name, photoUrl = partner.profilePhoto?.takeIf(String::isNotBlank))
    }
}

class MatrixChatEntryViewModel(
    private val resolver: CurrentCoupleRoomResolver,
    private val sessions: SessionCoordinator,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MatrixChatEntryState>(MatrixChatEntryState.Resolving)
    val state = mutableState.asStateFlow()
    private var resolveJob: Job? = null
    private var resolveGeneration = 0L
    private var sessionStamp = sessions.stamp()

    init {
        viewModelScope.launch {
            sessions.state.collect { stamp ->
                if (stamp == sessionStamp) return@collect
                val previous = sessionStamp
                sessionStamp = stamp
                val restartSameAccount = previous.accountId != null &&
                    previous.accountId == stamp.accountId &&
                    (resolveJob?.isActive == true || mutableState.value is MatrixChatEntryState.Resolving)
                resolveGeneration += 1
                resolveJob?.cancel()
                resolveJob = null
                if (previous.accountId == stamp.accountId) {
                    if (restartSameAccount) resolve(force = true)
                } else {
                    mutableState.value = MatrixChatEntryState.Unavailable(
                        if (stamp.accountId == null) "Sign in to use secure chat."
                        else "Open conversations again for this account."
                    )
                }
            }
        }
    }

    fun resolve(force: Boolean = false) {
        if (!force && resolveJob?.isActive == true) return
        resolveJob?.cancel()
        val generation = ++resolveGeneration
        val expectedStamp: SessionStamp = sessions.stamp()
        sessionStamp = expectedStamp
        mutableState.value = MatrixChatEntryState.Resolving
        resolveJob = viewModelScope.launch {
            val result = try {
                resolver.resolve()
            } catch (_: SessionChangedException) {
                val current = sessions.stamp()
                if (generation == resolveGeneration && current.accountId == expectedStamp.accountId) {
                    sessionStamp = current
                    resolve(force = true)
                }
                return@launch
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == resolveGeneration && sessions.stamp() == expectedStamp) {
                    mutableState.value = MatrixChatEntryState.Error(
                        failure.message ?: "Secure chat could not be opened. Try again.",
                    )
                }
                return@launch
            }
            if (generation != resolveGeneration || sessions.stamp() != expectedStamp) return@launch
            when (result) {
                is NetworkAPIResult.Success -> {
                    mutableState.value = result.response
                        ?.let(MatrixChatEntryState::Ready)
                        ?: MatrixChatEntryState.Unavailable("Connect with your partner to start a private conversation.")
                }
                is NetworkAPIResult.Error -> {
                    mutableState.value = if (result.failure.status in listOf(401, 403, 404)) {
                        MatrixChatEntryState.Unavailable(result.failure.message)
                    } else {
                        MatrixChatEntryState.Error(result.failure.message)
                    }
                }
            }
        }
    }
}
