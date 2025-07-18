package com.dcshub.repository.user

import com.dcshub.models.DatabaseUser

interface UserRepository {
    suspend fun addUser(
        email: String,
        name: String,
        passwordHash: String
    ): DatabaseUser?

    suspend fun findUser(userId: Int): DatabaseUser?
    suspend fun findUserByEmail(email: String): DatabaseUser?
}
