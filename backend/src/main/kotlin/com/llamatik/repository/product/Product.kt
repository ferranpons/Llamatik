package com.llamatik.repository.product

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

@Suppress("MagicNumber")
object Product : Table() {
    val id: Column<Int> = integer("id").autoIncrement().uniqueIndex()
    val title = varchar("title", 128)
    val description = varchar("description", 512)
    val cover = varchar("cover", 512)
    val images = varchar("images", 512)
    val video = varchar("video", 32)
    val category = integer("category")
    val miles = integer("miles")
    val released = varchar("released", 128)
    val modified = varchar("modified", 128)
    val versionRequired = varchar("versionRequired", 32)
    val bundle = varchar("bundle", 32)
    val price = double("price")
    val previousPrice = double("previousPrice")
    val isSteamCompatible = bool("isSteamCompatible")
    val isEarlyAccess = bool("isEarlyAccess")
    val isPrePurchase = bool("isPrePurchase")
}
