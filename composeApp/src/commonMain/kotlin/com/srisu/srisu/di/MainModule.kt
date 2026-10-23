package com.srisu.srisu.di

import com.srisu.srisu.features.chat.matrix.MatrixChatEntryViewModel
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.features.home.connection.presentation.coupleconnection.vm.CoupleConnectionViewModel
import com.srisu.srisu.features.home.profile.presentation.vm.EditProfileViewModel
import com.srisu.srisu.features.home.profile.presentation.vm.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mainModule = module {
    viewModel { com.srisu.srisu.features.moments.presentation.HomeMomentsViewModel(get(), get()) }
    viewModel { com.srisu.srisu.features.moments.presentation.MomentComposerViewModel(get(), get()) }
    viewModel { com.srisu.srisu.features.moments.presentation.MomentViewerViewModel(get(), get()) }
    viewModel { com.srisu.srisu.features.moments.presentation.MomentDiscoveryViewModel(get(), get()) }
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
            sessionStorage = get(),
            lifetime = get(),
        )
    }

    viewModel {
        CoupleConnectionViewModel(
             connectionRepository = get()
        )
    }

    viewModel {
        SecureChatViewModel(repository = get(), partners = get(), storage = get(), sessions = get(), moments = get())
    }

    viewModel { MatrixChatEntryViewModel(resolver = get(), sessions = get()) }
}
