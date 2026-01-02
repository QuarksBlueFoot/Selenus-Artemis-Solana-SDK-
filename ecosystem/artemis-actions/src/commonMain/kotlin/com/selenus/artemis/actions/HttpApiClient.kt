package com.selenus.artemis.actions

/**
 * Minimal HTTP client abstraction for KMP.
 * JVM actual uses OkHttp; other targets can plug in Ktor or platform HTTP.
 */
interface HttpApiClient {
    data class Response(val code: Int, val body: String)

    fun get(url: String, headers: Map<String, String> = emptyMap()): Response
    fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): Response
}

internal expect fun createDefaultHttpApiClient(
    connectTimeoutMs: Long,
    readTimeoutMs: Long,
    writeTimeoutMs: Long
): HttpApiClient
