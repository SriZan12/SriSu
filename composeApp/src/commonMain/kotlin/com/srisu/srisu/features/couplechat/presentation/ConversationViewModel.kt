package com.srisu.srisu.features.couplechat.presentation

import androidx.lifecycle.ViewModel
import com.srisu.srisu.features.couplechat.data.ConversationRepository

/** The repository owns durable state; this destination owns its active subscription. */
class ConversationViewModel(val conversation: ConversationRepository) : ViewModel() {
    private var active = false
    val state = conversation.state
    fun enter(roomId: String?) { if (!active) { active = true; conversation.enter(roomId) } }
    fun leave() { if (active) { active = false; conversation.leave() } }
    override fun onCleared() { leave() }
}
