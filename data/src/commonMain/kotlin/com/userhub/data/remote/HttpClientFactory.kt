package com.userhub.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds the shared Ktor client.
 *
 * The access token is injected rather than compiled in, so no credential lives in source control.
 * Logging defaults to [LogLevel.NONE]: full request/response logging would leak the Authorization
 * header and user PII, so verbose levels must be opted into explicitly for local debugging only and
 * never for a shipped build.
 */
fun createHttpClient(
    engine: HttpClientEngine,
    authToken: String,
    logLevel: LogLevel = LogLevel.NONE
): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
            }
        )
    }
    if (logLevel != LogLevel.NONE) {
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    println("HTTP: $message")
                }
            }
            level = logLevel
        }
    }
    defaultRequest {
        if (authToken.isNotEmpty()) {
            header("Authorization", "Bearer $authToken")
        }
        contentType(ContentType.Application.Json)
    }
}
