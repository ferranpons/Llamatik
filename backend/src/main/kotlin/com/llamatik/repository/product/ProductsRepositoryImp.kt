package com.llamatik.repository.product

import com.llamatik.repository.DatabaseFactory.dbQuery
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.update

class ProductsRepositoryImp : ProductsRepository {
    override suspend fun addProduct(
        title: String,
        description: String,
        cover: String,
        images: List<String>,
        video: String?,
        category: String,
        miles: Int,
        released: String,
        modified: String?,
        versionRequired: String,
        bundle: String,
        price: Double,
        previousPrice: Double,
        isSteamCompatible: Boolean,
        isEarlyAccess: Boolean,
        isPrePurchase: Boolean
    ): String? {
        var statement: InsertStatement<Number>? = null
        dbQuery {
            statement = Product.insert {
                it[Product.title] = title
                it[Product.description] = description
                it[Product.cover] = cover
                it[Product.images] = images.joinToString { "|" }
                it[Product.video] = video ?: ""
                //it[Product.category] = category
                it[Product.miles] = miles
                it[Product.released] = released
                it[Product.modified] = modified ?: ""
                it[Product.versionRequired] = versionRequired
                it[Product.bundle] = bundle
                it[Product.price] = price
                it[Product.previousPrice] = previousPrice
                it[Product.isSteamCompatible] = isSteamCompatible
                it[Product.isEarlyAccess] = isEarlyAccess
                it[Product.isPrePurchase] = isPrePurchase
            }
        }
        return ""
        //return rowToProductModel(statement?.resultedValues?.get(0))
    }
/*
    override suspend fun getProducts(productId: Int): List<ProductModel> {
        return dbQuery {
            Product.select(Product.id).where {
                Product.id.eq((productId)) // 3
            }.mapNotNull { rowToProductModel(it) }
        }
    }

    override suspend fun getProduct(petId: Int): ProductModel {
        return dbQuery {
            Product.select(Product.id).where {
                Product.id.eq((petId))
            }.mapNotNull { rowToProductModel(it) }
        }.first()
    }
*/
    override suspend fun delete(petId: Int) {
        return dbQuery {
            Product.deleteWhere {
                Product.id.eq((petId))
            }
        }
    }

    override suspend fun updateProduct(
        productId: Int,
        title: String,
        description: String,
        cover: String,
        images: List<String>,
        video: String?,
        category: String,
        miles: Int,
        released: String,
        modified: String?,
        versionRequired: String,
        bundle: String,
        price: Double,
        previousPrice: Double,
        isSteamCompatible: Boolean,
        isEarlyAccess: Boolean,
        isPrePurchase: Boolean
    ): String? {
        return dbQuery {
            Product.select(Product.id).where {
                Product.id.eq((productId))
            }.forUpdate()

            Product.update {
                id.eq(productId)
                title?.let { title ->
                    it[Product.title] = title
                }
                description?.let { description ->
                    it[Product.description] = description
                }
                images?.let { images ->
                    // it[Product.images] = images
                }
            }
/*
            Product.select(Product.id).where {
                Product.id.eq((productId))
            }.mapNotNull { rowToProductModel(it) }

 */
        }.toString()
    }
/*
    private fun rowToProductModel(row: ResultRow?): ProductModel? {
        if (row == null) {
            return null
        }
        return ProductModel(
            id = row[Product.id],
            title = row[Product.title],
            description = row[Product.description],
            cover = row[Product.cover],
            images = emptyList(), // row[Product.images],
            video = row[Product.video],
            category = "HELICOPTER",
            miles = row[Product.miles].toInt(),
            versionRequired = row[Product.versionRequired],
            bundle = row[Product.bundle],
            price = row[Product.price],
            discountedPrice = row[Product.previousPrice],
            isSteamCompatible = row[Product.isSteamCompatible],
            isEarlyAccess = row[Product.isEarlyAccess],
            isPrePurchase = row[Product.isPrePurchase]
        )
    }

 */
}
