package com.llamatik.network.router

import com.llamatik.core.platform.InferenceBackend
import com.llamatik.network.client.LlamatikServerClient
import com.llamatik.network.client.LlamatikServerConfig
import com.llamatik.network.discovery.NodeRegistry
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class NetworkInferenceRouter(
    private val nodeRegistry: NodeRegistry,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    var onlineMode: Boolean = false

    fun getBackend(): InferenceBackend? {
        if (!onlineMode) return null
        val node = nodeRegistry.getBestNode() ?: return null
        val client = LlamatikServerClient(LlamatikServerConfig(node.host, node.port), httpClient)
        return RemoteInferenceBackend(client, scope)
    }
}
