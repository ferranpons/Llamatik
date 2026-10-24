package com.llamatik.network.discovery

data class DiscoveredNode(
    val id: String,
    val host: String,
    val port: Int,
    val name: String,
)
