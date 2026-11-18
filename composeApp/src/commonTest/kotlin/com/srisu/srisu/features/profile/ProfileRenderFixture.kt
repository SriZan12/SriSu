package com.srisu.srisu.features.profile
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.srisu.srisu.features.home.profile.presentation.screen.*
import com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.navigation.navhost.*
import com.srisu.srisu.utils.CountryModel

@Composable internal fun ProfileRenderFixture() {
    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={AppBottomBar({},BottomDestination.PROFILE,true)}) {padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            EditProfileForm(EditProfileUIState(fullName="Maya Sharma",userName="maya",bio="Coffee, mountain walks and our little moments.",
                country=CountryModel(name="Nepal",code="NP",prefix="+977"),city="Kathmandu",cities=listOf("Kathmandu"),
                currentInterests=listOf(User.UserInterest(name="Coffee",removed=false),User.UserInterest(name="Hiking",removed=false))),
                ProfileFormActions(),onExplore={},onNotifications={})
        }
    }
}
