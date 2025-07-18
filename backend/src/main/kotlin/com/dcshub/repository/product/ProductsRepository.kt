package com.dcshub.repository.product

import com.dcshub.app.common.model.ProductModel

interface ProductsRepository {
    suspend fun addProduct(
        title: String,
        description: String,
        cover: String,
        images: List<String>,
        video: String?,
        category: ProductCategory,
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
    ): ProductModel?

    suspend fun getProduct(petId: Int): ProductModel

    suspend fun getProducts(userId: Int): List<ProductModel>

    suspend fun delete(petId: Int)

    suspend fun updateProduct(
        productId: Int,
        title: String,
        description: String,
        cover: String,
        images: List<String>,
        video: String?,
        category: ProductCategory,
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
    ): ProductModel?
}
