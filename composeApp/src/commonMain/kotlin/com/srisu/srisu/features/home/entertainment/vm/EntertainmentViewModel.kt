package com.srisu.srisu.features.home.entertainment.vm

import androidx.lifecycle.ViewModel
import com.srisu.srisu.features.home.suggestions.domain.repository.SuggestionRepository
import com.srisu.srisu.core.session.SessionStorage

class EntertainmentViewModel(
    private val suggestionRepository: SuggestionRepository,
    private val sessionStorage: SessionStorage
) : ViewModel() {

    init {

//        val session = sessionStorage.getSession(sessionKey = Constants.SESSION_KEY)
//        AppLogger.log("SESSIONS = ${session?.let { Json.decodeFromString<Session>(it) }}")
    }




}