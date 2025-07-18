package com.llamatik.repository.user

import com.llamatik.models.DatabaseUser
import com.llamatik.repository.DatabaseFactory.dbQuery
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement

class UserRepositoryImp : UserRepository {
    override suspend fun addUser(
        email: String,
        name: String,
        passwordHash: String
    ): DatabaseUser? {
        var statement: InsertStatement<Number>? = null // 1
        dbQuery {
            statement = Users.insert { user ->
                user[Users.email] = email
                user[Users.name] = name
                user[Users.passwordHash] = passwordHash
            }
        }

        return rowToUser(statement?.resultedValues?.get(0))
    }

    override suspend fun findUser(userId: Int) = dbQuery {
        Users.select(Users.userId).where { Users.userId.eq(userId) }
            .map { rowToUser(it) }.singleOrNull()
    }

    override suspend fun findUserByEmail(email: String) = dbQuery {
        Users.select(Users.email).where { Users.email.eq(email) }
            .map { rowToUser(it) }.singleOrNull()
    }

    private fun rowToUser(row: ResultRow?): DatabaseUser? {
        if (row == null) {
            return null
        }
        return DatabaseUser(
            userId = row[Users.userId],
            email = row[Users.email],
            name = row[Users.name],
            passwordHash = row[Users.passwordHash]
        )
    }
}
