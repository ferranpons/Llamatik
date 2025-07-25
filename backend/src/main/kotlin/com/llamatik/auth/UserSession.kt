package com.llamatik.auth

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(val userId: Int)
