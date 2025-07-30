package com.srisu.srisu.features.profile.vm

import androidx.lifecycle.ViewModel
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.repository.profile.ProfileRepository
import com.srisu.srisu.features.profile.state.EditProfileUIState
import com.srisu.srisu.utils.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class EditProfileViewModel(
    private val connectivityObserver: ConnectivityObserver,
    private val profileRepository: ProfileRepository
): ViewModel() {

    private val _editProfileUIState: MutableStateFlow<EditProfileUIState> =
        MutableStateFlow(EditProfileUIState())

    val editProfileUIState = _editProfileUIState.asStateFlow()

    private fun <T> showSuccessMessage(data: T? = null, message: String) {
        this._editProfileUIState.value =
            this._editProfileUIState.value.copy(
                baseUIState = BaseUIState.Success(
                    data = data,
                    message = message
                )
            )
    }

    private fun showErrorMessage(errorType: String?, message: String?) {
        this._editProfileUIState.value =
            this._editProfileUIState.value.copy(
                baseUIState = BaseUIState.Error(
                    errorType = errorType,
                    message = message
                )
            )
    }

    private fun showLoading() {
        this._editProfileUIState.value =
            this._editProfileUIState.value.copy(baseUIState = BaseUIState.Loading)
    }

    fun idleScreen() {
        this._editProfileUIState.value = this._editProfileUIState.value.copy(baseUIState = BaseUIState.Idle)
    }

    init {

    }

    //Events
    fun updateFullName(fullName: String){
        _editProfileUIState.value = _editProfileUIState.value.copy(fullName = fullName)
    }

    fun updateUserName(userName: String){
        _editProfileUIState.value = _editProfileUIState.value.copy(userName = userName)
    }

    fun updateBio(bio: String){
        _editProfileUIState.value = _editProfileUIState.value.copy(bio = bio)
    }

    fun updateCountry(country: String){
        _editProfileUIState.value = _editProfileUIState.value.copy(country = country)
    }

    fun updateCity(city: String){
        _editProfileUIState.value = _editProfileUIState.value.copy(city = city)
    }

    fun updateInterests(interests: List<String>){
        _editProfileUIState.value = _editProfileUIState.value.copy(interests = interests)
    }

    fun updatePhotos(photos: List<String>){
        _editProfileUIState.value = _editProfileUIState.value.copy(photos = photos)
    }


}