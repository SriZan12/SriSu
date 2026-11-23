package com.srisu.srisu.features.profile

import com.srisu.srisu.features.auth.data.remote.response.InterestResponse.Interest

internal val repairInterests = listOf(
    "Cooking", "Coffee & cafés", "Photography", "Long walks and little adventures", "Music", "Travel"
).mapIndexed { index, name -> Interest(id = index + 1, name = name,
    category = Interest.Category(id = 1, name = "EVERYDAY_LIFE", label = "Everyday life")) }
