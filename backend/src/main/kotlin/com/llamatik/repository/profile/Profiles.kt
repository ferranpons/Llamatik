package com.llamatik.repository.profile

import com.llamatik.repository.user.Users
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

@Suppress("MagicNumber")
object Profiles : Table() {
    val id: Column<Int> = integer("id").autoIncrement().uniqueIndex()
    val userId: Column<Int> = integer("userId").references(Users.userId)
    val name = varchar("name", 64)
    val description = varchar("description", 600)
    val image = varchar("image", 128)
    val location = varchar("location", 64)
    val rating = double("rating")
}
