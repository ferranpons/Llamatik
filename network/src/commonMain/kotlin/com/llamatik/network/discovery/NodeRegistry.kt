package com.llamatik.network.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NodeRegistry {
    private val _nodes = MutableStateFlow<List<DiscoveredNode>>(emptyList())
    val nodes: StateFlow<List<DiscoveredNode>> = _nodes.asStateFlow()

    fun addNode(node: DiscoveredNode) {
        _nodes.value = _nodes.value.filter { it.id != node.id } + node
    }

    fun removeNode(nodeId: String) {
        _nodes.value = _nodes.value.filter { it.id != nodeId }
    }

    fun addManualNode(host: String, port: Int = 8080, name: String = host): DiscoveredNode {
        val node = DiscoveredNode(id = "$host:$port", host = host, port = port, name = name)
        addNode(node)
        return node
    }

    fun getBestNode(): DiscoveredNode? = _nodes.value.firstOrNull()

    fun clear() {
        _nodes.value = emptyList()
    }
}
