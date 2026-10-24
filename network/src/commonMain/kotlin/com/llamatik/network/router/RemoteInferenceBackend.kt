package com.llamatik.network.router

import com.llamatik.core.platform.GenStream
import com.llamatik.core.platform.InferenceBackend
import com.llamatik.network.client.LlamatikServerClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RemoteInferenceBackend(
    private val client: LlamatikServerClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : InferenceBackend {

    override fun generateStream(prompt: String, stream: GenStream) {
        scope.launch {
            runCatching {
                client.streamGenerate(prompt).collect { token ->
                    stream.onDelta(token)
                }
                stream.onComplete()
            }.onFailure { e ->
                stream.onError(e.message ?: "Remote inference failed")
            }
        }
    }
}
