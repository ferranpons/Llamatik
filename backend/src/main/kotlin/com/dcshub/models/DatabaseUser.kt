package com.dcshub.models

import io.ktor.server.auth.Principal
import java.io.Serializable

@Suppress("SerialVersionUIDInSerializableClass")
data class DatabaseUser(
    val userId: Int,
    val email: String,
    val name: String,
    val passwordHash: String
) : Serializable, Principal
