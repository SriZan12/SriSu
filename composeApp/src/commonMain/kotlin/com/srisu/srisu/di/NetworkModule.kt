package com.srisu.srisu.di

import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.core.data.remote.BaseApiService
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.features.auth.domain.repository.AuthRepository
import com.srisu.srisu.features.home.connection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.home.profile.domain.repository.ProfileRepository
import com.srisu.srisu.features.chat.matrix.ChatPartnerSource
import com.srisu.srisu.features.chat.matrix.ConnectionChatPartnerSource
import com.srisu.srisu.features.chat.matrix.ConnectionCurrentCoupleRoomResolver
import com.srisu.srisu.features.chat.matrix.CurrentCoupleRoomResolver
import com.srisu.srisu.features.chat.matrix.MatrixChatRepository
import com.srisu.srisu.features.chat.matrix.MatrixSessionApiService
import com.srisu.srisu.features.chat.matrix.MatrixSessionRemote
import com.srisu.srisu.features.chat.matrix.TrixnityMatrixChatRepository
import com.srisu.srisu.core.session.SessionUtils
import org.koin.core.module.Module
import org.koin.dsl.onClose
import org.koin.dsl.module

val sharedNetworkModule = module {
    single<com.srisu.srisu.features.explore.data.ExploreSource> { com.srisu.srisu.features.explore.data.ExploreRepository(get(), get(), get()) }
    single { com.srisu.srisu.features.explore.data.FaveStore(get(), get(), get()) }
    single<com.srisu.srisu.features.plans.data.PlanSource> { com.srisu.srisu.features.plans.data.PlanRepository(get(), get()) }
    single { com.srisu.srisu.features.plans.data.PlanReminders(get(), get(), get(), get(), get()) }
    single<com.srisu.srisu.features.challenges.data.ChallengeSource> { com.srisu.srisu.features.challenges.data.ChallengeRepository(get(), get()) }
    single<com.srisu.srisu.features.sparks.data.SparkSource> { com.srisu.srisu.features.sparks.data.SparkRepository(get(), get()) }
    single<com.srisu.srisu.features.moments.data.MomentSource> { com.srisu.srisu.features.moments.data.MomentRepository(get(), get(), get()) }
    single { com.srisu.srisu.core.config.ApiEnvironment.configured() }
    single { com.srisu.srisu.core.lifecycle.ApplicationLifetime() } onClose { it?.close() }
    single { com.srisu.srisu.core.session.SessionCoordinator(get(org.koin.core.qualifier.named("platformSessionStorage"))) }
    single<com.srisu.srisu.core.session.SessionStorage> { get<com.srisu.srisu.core.session.SessionCoordinator>() }
    single { HttpClientFactory.create(sessions = get(), environment = get(), engine = get()) } onClose { it?.close() }
    single { get<com.srisu.srisu.core.data.local.CatalogueDatabase>().catalogue() }
    single { com.srisu.srisu.features.home.profile.data.InterestCatalogueRepository(get(), get(), get(), get()) }

    single { com.srisu.srisu.navigation.CoupleAccessCoordinator(get(), get(), get(), get()) }
    single { com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository(get(), get(), get()) }
    single { BaseApiService(httpClient = get()) }

    single { AuthApiService(httpClient = get(), environment = get()) } //apiService
    single { AuthRepository(authApiService = get()) } // Repo


    single { ProfileApiService(httpClient = get(), environment = get()) }
    single { ProfileRepository(profileApiService = get(), baseApiService = get(), catalogue = get()) }

    single { ConnectionApiService(httpClient = get()) }
    single { ConnectionRepository(connectionApiService = get()) }

    single<CurrentCoupleRoomResolver> { ConnectionCurrentCoupleRoomResolver(get()) }
    single<ChatPartnerSource> { ConnectionChatPartnerSource(get()) }

    single<MatrixSessionRemote> { MatrixSessionApiService(httpClient = get(), environment = get()) }
    // Eager so logout/account-switch privacy cleanup runs after process restart
    // even when the user never opens Couple Chat in that process.
    single<MatrixChatRepository>(createdAtStart = true) {
        TrixnityMatrixChatRepository(
            remote = get(),
            sessions = get(),
            environment = get(),
            engine = get(),
            platformStore = get(),
            lifetime = get(),
            sparkSource = get(),
        )
    } onClose { it?.close() }

}

expect val platformNetworkModule: Module
