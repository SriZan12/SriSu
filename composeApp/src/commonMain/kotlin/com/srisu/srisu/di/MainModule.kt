package com.srisu.srisu.di

import com.srisu.srisu.features.chat.presentation.chat.vm.ChatViewModel
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.features.home.connection.presentation.coupleconnection.vm.CoupleConnectionViewModel
import com.srisu.srisu.features.home.connection.presentation.singleconnection.vm.SingleConnectionViewModel
import com.srisu.srisu.features.home.entertainment.vm.EntertainmentViewModel
import com.srisu.srisu.features.home.profile.presentation.vm.EditProfileViewModel
import com.srisu.srisu.features.home.profile.presentation.vm.ProfileViewModel
import com.srisu.srisu.features.home.suggestions.presentation.vm.SuggestionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mainModule = module {
    viewModel {
        EntertainmentViewModel(
            suggestionRepository = get(),
            sessionStorage = get(),
        )
    }

    viewModel {
        SuggestionViewModel(
            suggestionRepository = get(),
            connectionRepository = get(),
            connectivityObserver = get(),
            sessionUtils = get(),
            dispatchers = get(),
        )
    }

    viewModel {
        ProfileViewModel(
            profileRepository = get(),
            connectivityObserver = get(),
            sessionUtils = get(),
        )
    }

    viewModel {
        EditProfileViewModel(
            profileRepository = get(),
            connectivityObserver = get(),
            sessionStorage = get(),
            dispatchers = get(),
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
            sessionStorage = get(),
        )
    }

    viewModel {
        CoupleConnectionViewModel(
             connectionRepository = get()
        )
    }

    viewModel {
        ChatViewModel(repository = get())
    }
}
