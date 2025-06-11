package com.srisu.srisu.features.home

import androidx.lifecycle.ViewModel
import com.srisu.srisu.core.data.repository.SuggestionRepository
import com.srisu.srisu.session.SessionStorage

class HomeViewModel(
    private val suggestionRepository: SuggestionRepository,
    private val sessionStorage: SessionStorage
) : ViewModel() {

    init {

//        val session = sessionStorage.getSession(sessionKey = Constants.SESSION_KEY)
//        AppLogger.log("SESSIONS = ${session?.let { Json.decodeFromString<Session>(it) }}")
    }




}