package com.llamatik.network.discovery

sealed class DiscoveryEvent {
    data class NodeFound(val node: DiscoveredNode) : DiscoveryEvent()
    data class NodeLost(val nodeId: String) : DiscoveryEvent()
}
