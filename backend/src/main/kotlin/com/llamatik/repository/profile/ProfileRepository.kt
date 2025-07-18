package com.llamatik.repository.profile

import com.llamatik.app.common.model.GeoLocation
import com.llamatik.app.common.model.PlayerStats
import com.llamatik.app.common.model.Profile

interface ProfileRepository {

    suspend fun addProfile(
        id: Int = 0,
        name: String = "",
        nickname: String = "",
        description: String? = null,
        image: String? = null,
        location: GeoLocation? = null,
        preferredLanguage: String? = null,
        serversList: List<String>? = emptyList(),
        rank: Int? = null,
        country: String? = null,
        squadron: String? = null,
        squadronPatch: String? = null,
        playerStats: PlayerStats = PlayerStats(),
        medals: List<String>? = emptyList()
    ): Profile?

    suspend fun getProfile(userId: Int): Profile?

    suspend fun updateProfile(
        userId: Int,
        name: String?,
        nickname: String,
        description: String?,
        image: String?,
        location: String?,
        preferredLanguage: String? = null,
        serversList: List<String>? = emptyList(),
        rank: Int? = null,
        country: String? = null,
        squadron: String? = null,
        squadronPatch: String? = null,
        playerStats: PlayerStats = PlayerStats(),
        medals: List<String>? = emptyList()
    ): Profile?
}
