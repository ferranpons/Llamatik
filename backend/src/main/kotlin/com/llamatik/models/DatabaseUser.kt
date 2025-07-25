package com.llamatik.models

import java.io.Serializable

@Suppress("SerialVersionUIDInSerializableClass")
data class DatabaseUser(
    val userId: Int,
    val email: String,
    val name: String,
    val passwordHash: String
) : Serializable
