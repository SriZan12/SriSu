package com.srisu.srisu.features.home.connection.data.remote.mappers

import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.response.CoupleConnectionRequestResponse

fun CoupleConnectionRequestResponse.Result.Receiver.toUser(): User {
    return User(
        bio = bio,
        city = city,
        country = country,
        dob = dob,
        fullName = fullName,
        gender = gender,
        id = id,
        isPhoneVerified = isPhoneVerified,
        isProfileComplete = isProfileComplete,
        mood = mood,
        phoneNumber = phoneNumber,
        profilePhoto = profilePhoto,
        updatedDate = updatedDate,
        username = username,
        zodiacSign = zodiacSign,
        userInterests = userInterests?.map { receiverInterest ->
            receiverInterest?.let {
                User.UserInterest(
                    id = it.id,
                    name = it.name,
                    user = it.user,
                    interest = it.interest,
                    removed = it.removed
                )
            }
        },
        userPhotos = userPhotos?.map { receiverPhoto ->
            receiverPhoto?.let {
                User.UserPhoto(
                    createdDate = it.createdDate,
                    id = it.id,
                    photo = it.photo,
                    updatedDate = it.updatedDate,
                    user = it.user,
                    removed = it.removed
                )
            }
        }
    )
}