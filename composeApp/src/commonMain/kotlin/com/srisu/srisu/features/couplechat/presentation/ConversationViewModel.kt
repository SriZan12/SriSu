package com.srisu.srisu.features.couplechat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.couplechat.data.ConversationRepository
import com.srisu.srisu.features.couplechat.data.ConversationSecurity
import com.srisu.srisu.features.coupleprofile.data.CoupleProfile
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch

/** The repository owns durable chat state; this destination owns private profile imagery. */
private data class ParticipantIdentity(val account: Long?, val partner: Long?, val relationship: Long?, val room: String?, val archived: Boolean)

class ConversationViewModel(val conversation: ConversationRepository, private val profiles: CoupleProfileRepository) : ViewModel() {
    private val active = MutableStateFlow(false)
    val state = conversation.state
    val connectionState = conversation.connectionState
    private val _partnerPhoto = MutableStateFlow<ByteArray?>(null)
    val partnerPhoto = _partnerPhoto.asStateFlow()
    private val _romanticEffects = MutableSharedFlow<RomanticEffect>(extraBufferCapacity=1, onBufferOverflow=BufferOverflow.DROP_OLDEST)
    internal val romanticEffects = _romanticEffects.asSharedFlow()
    init {
        viewModelScope.launch {
            conversation.messageArrivals.collect { arrival ->
                val current = state.value
                if (active.value && current.accountId == arrival.accountId && current.roomId == arrival.roomId &&
                    current.security == ConversationSecurity.READY) {
                    RomanticPhrases.match(arrival.text)?.let { intensity ->
                        _romanticEffects.tryEmit(RomanticEffect(arrival.accountId, arrival.roomId, arrival.messageId, intensity))
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(state.map { ParticipantIdentity(it.accountId, it.partnerId, it.relationshipId, it.roomId,
                it.security == ConversationSecurity.ARCHIVED) }.distinctUntilChanged(), active) { identity, visible -> identity to visible }
                .collectLatest { (identity, visible) ->
                    _partnerPhoto.value = null
                    if (!visible || identity.archived) return@collectLatest
                    val account = identity.account ?: return@collectLatest
                    val partner = identity.partner ?: return@collectLatest
                    val relationship = identity.relationship ?: return@collectLatest
                    val profile = (profiles.load(relationship) as? NetworkAPIResult.Success)?.response ?: return@collectLatest
                    // A couple cover is never a participant avatar. Match both member IDs.
                    val photo = partnerPhotoUrl(profile, account, partner) ?: return@collectLatest
                    _partnerPhoto.value = profiles.image(photo)
                }
        }
    }
    fun enter(roomId: String?) { if (!active.value) { active.value = true; conversation.enter(roomId) } }
    fun coverWithMedia() { active.value=false;conversation.coverWithMedia() }
    fun leave() { if (active.value) { active.value = false; _partnerPhoto.value = null; conversation.leave() } }
    override fun onCleared() { leave() }
}

internal fun partnerPhotoUrl(profile: CoupleProfile, account: Long, partner: Long): String? {
    if (account == partner || profile.members?.any { it.id == account } != true) return null
    return profile.members.singleOrNull { it.id == partner }?.photoUrl
}
