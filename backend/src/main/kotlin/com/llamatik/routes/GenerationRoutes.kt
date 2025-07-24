package com.llamatik.routes

import com.llamatik.API_VERSION
import com.llamatik.auth.JWT_CONFIGURATION
import com.llamatik.repository.generation.GenerationRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.resources.Resource
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

private const val GENERATION = "$API_VERSION/generation"
private const val GENERATION_GENERATE = "$GENERATION/generate"

@Resource(GENERATION_GENERATE)
class GenerationRoute

@Suppress("LongMethod", "TooGenericExceptionCaught", "CyclomaticComplexMethod")
fun Route.generation(
    generationRepository: GenerationRepository
) {
    authenticate(JWT_CONFIGURATION) {
        // TODO: Add Authorized Generation
    }

    get<GenerationRoute> {
        try {
            val parameters = call.receive<Parameters>()
            val prompt = parameters["prompt"] ?: ""
            val embedding = generationRepository.generate(prompt)
            call.respond(embedding.getOrThrow())
        } catch (e: Throwable) {
            call.respond(HttpStatusCode.BadRequest, e.message.toString())
        }
    }
}
