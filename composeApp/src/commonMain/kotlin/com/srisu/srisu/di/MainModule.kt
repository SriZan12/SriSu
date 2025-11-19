package com.srisu.srisu.di

import com.srisu.srisu.features.chat.chatroom.ChatViewModel
import com.srisu.srisu.features.chat.chatroom.couple.findpartner.FindPartnerViewModel
import com.srisu.srisu.features.home.connection.coupleconnection.loverequest.vm.LoveRequestViewModel
import com.srisu.srisu.features.home.connection.singleconnection.vm.SingleConnectionViewModel
import com.srisu.srisu.features.home.home.vm.HomeViewModel
import com.srisu.srisu.features.profile.vm.EditProfileViewModel
import com.srisu.srisu.features.profile.vm.ProfileViewModel
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mainModule = module {
    viewModel {
        HomeViewModel(
            suggestionRepository = get(),
            sessionStorage = get()
        )
    }

    viewModel {
        SuggestionViewModel(
            suggestionRepository = get(),
            connectionRepository = get(),
            connectivityObserver = get(),
        )
    }

    viewModel {
        ProfileViewModel(
            profileRepository = get(),
            connectivityObserver = get(),
        )
    }

    viewModel {
        EditProfileViewModel(
            profileRepository = get(),
            connectivityObserver = get(),
            sessionStorage = get()
        )
    }

    viewModel {
        SingleConnectionViewModel(
            connectionRepository = get(),
            connectivityObserver = get()
        )
    }

    viewModel {
        FindPartnerViewModel(
            connectionRepository = get(),
            sessionStorage = get()
        )
    }

    viewModel {
        LoveRequestViewModel(
             connectionRepository = get()
        )
    }

    viewModel {
        ChatViewModel(repository = get())
    }
}
