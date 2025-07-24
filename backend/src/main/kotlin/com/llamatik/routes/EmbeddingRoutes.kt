package com.llamatik.routes

import com.llamatik.API_VERSION
import com.llamatik.auth.JWT_CONFIGURATION
import com.llamatik.repository.embeddings.EmbeddingRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.resources.Resource
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

const val EMBEDDINGS = "$API_VERSION/embeddings"
const val EMBEDDINGS_EMBED = "$EMBEDDINGS/embed"

@Resource(EMBEDDINGS_EMBED)
class EmbedRoute

@Suppress("LongMethod", "TooGenericExceptionCaught", "CyclomaticComplexMethod")
fun Route.embeddings(
    embeddingRepository: EmbeddingRepository
) {
    authenticate(JWT_CONFIGURATION) {
        // TODO: Add Authorized Embedding
    }

    get<GenerationRoute> {
        try {
            val parameters = call.receive<Parameters>()
            val input = parameters["input"] ?: ""
            val embedding = embeddingRepository.getEmbedding(input)
            call.respond(embedding.getOrThrow())
        } catch (e: Throwable) {
            call.respond(HttpStatusCode.BadRequest, e.message.toString())
        }
    }
}
