package com.srisu.srisu.di

import com.srisu.srisu.features.chat.presentation.chat.vm.ChatViewModel
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.features.home.connection.presentation.coupleconnection.vm.CoupleConnectionViewModel
import com.srisu.srisu.features.home.profile.presentation.vm.EditProfileViewModel
import com.srisu.srisu.features.home.profile.presentation.vm.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mainModule = module {
    viewModel { com.srisu.srisu.features.coupleprofile.presentation.CoupleProfileViewModel(get(), get(), get(), get()) }


    viewModel { ProfileViewModel() }

    viewModel {
        EditProfileViewModel(
            profileRepository = get(),
            connectivityObserver = get(),
            sessionStorage = get()
        )
    }


    viewModel {
        FindPartnerViewModel(
            connectionRepository = get(),
            sessionStorage = get()
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
