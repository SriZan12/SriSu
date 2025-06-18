package com.srisu.srisu.di

import com.srisu.srisu.features.home.HomeViewModel
import com.srisu.srisu.features.profile.vm.ProfileViewModel
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import org.koin.dsl.module

val mainModule = module {
    single {
        HomeViewModel(
            suggestionRepository = get(),
            sessionStorage = get()
        )
    }

    single {
        SuggestionViewModel(
            suggestionRepository = get(),
            connectivityObserver = get(),
        )
    }

    single {
        ProfileViewModel(
            profileRepository = get(),
            connectivityObserver = get(),
        )
    }
}