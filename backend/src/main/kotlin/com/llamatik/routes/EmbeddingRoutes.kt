package com.llamatik.routes

import com.llamatik.API_VERSION
import com.llamatik.api.EmbedRequest
import com.llamatik.api.EmbedResponse
import com.llamatik.llama.LlamaService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

const val EMBEDDINGS = "$API_VERSION/embeddings"
const val EMBEDDINGS_INIT = "$EMBEDDINGS/init"
const val EMBEDDINGS_EMBED = "$EMBEDDINGS/embed"

/**
 * Mirrors the Llamatik library API for embeddings.
 *
 * POST /v1/embeddings/embed
 */
fun Route.embeddingRoutes() {
    post(EMBEDDINGS_INIT) {
        val req = call.receive<com.llamatik.api.InitModelRequest>()
        val res = LlamaService.initModel(req.modelPath)
        res.fold(
            onSuccess = { call.respond(com.llamatik.api.InitModelResponse(ok = it)) },
            onFailure = { call.respond(HttpStatusCode.BadRequest, it.message ?: "Init failed") }
        )
    }

    post(EMBEDDINGS_EMBED) {
        val req = call.receive<EmbedRequest>()
        val res = LlamaService.embed(req.input)
        res.fold(
            onSuccess = { call.respond(EmbedResponse(embedding = it.toList())) },
            onFailure = { call.respond(HttpStatusCode.BadRequest, it.message ?: "Embedding failed") }
        )
    }
}
