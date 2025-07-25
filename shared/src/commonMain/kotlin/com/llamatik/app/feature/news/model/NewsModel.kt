package com.llamatik.app.feature.news.model

import kotlinx.serialization.Serializable

@Serializable
class NewsModel(
    val id: Int,
    val title: String,
    val subtitle: String,
    val body: String,
    val image: String,
    val released: String,
    val modified: String?
)