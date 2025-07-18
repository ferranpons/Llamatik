package com.dcshub.routes

import com.dcshub.API_VERSION
import com.dcshub.auth.JWT_CONFIGURATION
import com.dcshub.auth.UserSession
import com.dcshub.repository.product.ProductsRepository
import com.dcshub.repository.user.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.locations.KtorExperimentalLocationsAPI
import io.ktor.server.locations.Location
import io.ktor.server.locations.delete
import io.ktor.server.locations.get
import io.ktor.server.locations.patch
import io.ktor.server.locations.post
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

const val PRODUCTS = "$API_VERSION/products"
const val PRODUCTS_CREATE = "$PRODUCTS/create"
const val PRODUCTS_LIST = "$PRODUCTS/list"
const val PRODUCTS_GET_ONE = "$PRODUCTS/product"
const val PRODUCTS_UPDATE_ONE = "$PRODUCTS/product/update"
const val PRODUCTS_DELETE = "$PRODUCTS/delete"

@KtorExperimentalLocationsAPI
@Location(PRODUCTS_CREATE)
class ProductsCreateRoute

@KtorExperimentalLocationsAPI
@Location(PRODUCTS_LIST)
class ProductsListRoute

@KtorExperimentalLocationsAPI
@Location(PRODUCTS_GET_ONE)
class ProductsGetOneRoute

@KtorExperimentalLocationsAPI
@Location(PRODUCTS_UPDATE_ONE)
class ProductsUpdateOneRoute

@KtorExperimentalLocationsAPI
@Location(PRODUCTS_DELETE)
class ProductsDeleteRoute

@Suppress("LongMethod", "TooGenericExceptionCaught", "CyclomaticComplexMethod")
@KtorExperimentalLocationsAPI
fun Route.products(
    productsRepository: ProductsRepository,
    userRepository: UserRepository,
    productsMockRepository: ProductsMockRepository
) {
    authenticate(JWT_CONFIGURATION) {
        post<ProductsCreateRoute> {
            val productParameters = call.receive<Parameters>()
            val title = productParameters["title"] ?: ""
            val description = productParameters["description"] ?: ""
            val cover = productParameters["cover"] ?: ""
            val images = emptyList<String>()
            val video = productParameters["video"] ?: ""
            val category = ProductCategory.entries[productParameters["category"]?.toInt() ?: 0]
            val miles = productParameters["miles"]?.toInt() ?: -1
            val released = productParameters["gender"] ?: ""
            val modified = productParameters["modified"] ?: ""
            val versionRequired = productParameters["versionRequired"] ?: ""
            val bundle = productParameters["bundle"] ?: ""
            val price = productParameters["price"]?.toDouble() ?: -1.0
            val previousPrice = productParameters["previousPrice"]?.toDouble() ?: -1.0
            val isSteamCompatible = productParameters["isSteamCompatible"].toBoolean()
            val isEarlyAccess = productParameters["isEarlyAccess"].toBoolean()
            val isPrePurchase = productParameters["isPrePurchase"].toBoolean()

            val user = call.sessions.get<UserSession>()?.let {
                userRepository.findUser(it.userId)
            }
            if (user == null) {
                call.respond(HttpStatusCode.BadRequest, "Problems retrieving User")
                return@post
            }

            // val date = Calendar.getInstance().time
            // val formattedDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).format(date)

            try {
                val productModel = productsRepository.addProduct(
                    title = title,
                    description = description,
                    cover = cover,
                    images = images,
                    video = video,
                    category = category,
                    miles = miles,
                    released = released,
                    modified = modified,
                    versionRequired = versionRequired,
                    bundle = bundle,
                    price = price,
                    previousPrice = previousPrice,
                    isSteamCompatible = isSteamCompatible,
                    isEarlyAccess = isEarlyAccess,
                    isPrePurchase = isPrePurchase
                )
                productModel?.id?.let {
                    call.respond(HttpStatusCode.OK, productModel)
                }
            } catch (e: Throwable) {
                this@authenticate.application.log.error("Failed to add Pet", e)
                call.respond(HttpStatusCode.BadRequest, "Problems Uploading Pet")
            }
        }

        get<ProductsGetOneRoute> {
            val user = call.sessions.get<UserSession>()?.let { userRepository.findUser(it.userId) }
            val productParameters = call.receive<Parameters>()
            val productId = productParameters["id"]?.toInt() ?: -1

            if (user == null) {
                call.respond(HttpStatusCode.BadRequest, "Problems retrieving User")
                return@get
            }
            try {
                val productModel = productsRepository.getProduct(productId)
                call.respond(productModel)
            } catch (e: Throwable) {
                this@authenticate.application.log.error("Failed to get Pet", e)
                call.respond(HttpStatusCode.BadRequest, "Problems getting Pet")
            }
        }

        patch<ProductsUpdateOneRoute> {
            val productParameters = call.receive<Parameters>()
            val productId = productParameters["id"]?.toInt() ?: -1
            val title = productParameters["title"] ?: ""
            val description = productParameters["description"] ?: ""
            val cover = productParameters["cover"] ?: ""
            val images = emptyList<String>()
            val video = productParameters["video"] ?: ""
            val category = ProductCategory.entries[productParameters["category"]?.toInt() ?: 0]
            val miles = productParameters["miles"]?.toInt() ?: -1
            val released = productParameters["gender"] ?: ""
            val modified = productParameters["modified"] ?: ""
            val versionRequired = productParameters["versionRequired"] ?: ""
            val bundle = productParameters["bundle"] ?: ""
            val price = productParameters["price"]?.toDouble() ?: -1.0
            val previousPrice = productParameters["previousPrice"]?.toDouble() ?: -1.0
            val isSteamCompatible = productParameters["isSteamCompatible"].toBoolean()
            val isEarlyAccess = productParameters["isEarlyAccess"].toBoolean()
            val isPrePurchase = productParameters["isPrePurchase"].toBoolean()

            val user = call.sessions.get<UserSession>()?.let {
                userRepository.findUser(it.userId)
            }
            if (user == null) {
                call.respond(HttpStatusCode.BadRequest, "Problems retrieving User")
                return@patch
            }

            // val date = Calendar.getInstance().time
            // val formattedDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).format(date)

            try {
                val productModel = productsRepository.updateProduct(
                    productId = productId,
                    title = title,
                    description = description,
                    cover = cover,
                    images = images,
                    video = video,
                    category = category,
                    miles = miles,
                    released = released,
                    modified = modified,
                    versionRequired = versionRequired,
                    bundle = bundle,
                    price = price,
                    previousPrice = previousPrice,
                    isSteamCompatible = isSteamCompatible,
                    isEarlyAccess = isEarlyAccess,
                    isPrePurchase = isPrePurchase
                )
                productModel?.id?.let {
                    call.respond(HttpStatusCode.OK, productModel)
                    return@patch
                }
            } catch (e: Throwable) {
                this@authenticate.application.log.error("Failed to update Pet", e)
                call.respond(HttpStatusCode.BadRequest, "Problems updating Pet")
            }
        }

        delete<ProductsDeleteRoute> {
            val petsParameters = call.receive<Parameters>()
            val petId = petsParameters["id"]?.toInt()

            val user = call.sessions.get<UserSession>()?.let {
                userRepository.findUser(it.userId)
            }

            if (user == null) {
                call.respond(HttpStatusCode.BadRequest, "Problems retrieving User")
                return@delete
            }

            if (petId != null) {
                productsRepository.delete(petId)
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.BadRequest, "Problems deleting Pet")
                return@delete
            }
        }
    }

    get<ProductsListRoute> {
        try {
            //  val products = productsRepository.getProducts(user.userId)
            val products = productsMockRepository.getProducts()
            call.respond(products.getOrThrow())
        } catch (e: Throwable) {
            // this@authenticate.application.log.error("Failed to get Products", e)
            call.respond(HttpStatusCode.BadRequest, e.message.toString())
        }
    }
}
