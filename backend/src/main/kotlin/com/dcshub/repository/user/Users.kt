package com.dcshub.repository.user

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

@Suppress("MagicNumber")
object Users : Table() {
    val userId: Column<Int> = integer("id").autoIncrement().uniqueIndex()
    val email = varchar("email", 128).uniqueIndex()
    val name = varchar("name", 256)
    val passwordHash = varchar("password_hash", 64)
}
