package com.dcshub.auth

import io.ktor.util.hex
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

val hashKey = hex(System.getenv("SECRET_KEY"))

val hmacKey = SecretKeySpec(hashKey, "HmacSHA1")

fun hash(password: String): String {
    val hmac = Mac.getInstance("HmacSHA1")
    hmac.init(hmacKey)
    return hex(hmac.doFinal(password.toByteArray(Charsets.UTF_8)))
}
