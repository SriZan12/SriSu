package com.srisu.srisu.core.data.repository.chat

import com.srisu.srisu.core.data.apiservice.chat.ChatApiService
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.response.chat.FindYourPartnerResponse

class ChatRepository(val chatApiService: ChatApiService) {

    suspend fun sendFindYourPartnerRequest(partnerNumber: String): ResultHandler<FindYourPartnerResponse?> {
        return chatApiService.sendFindYourPartnerRequest(partnerNumber)
    }
}