package com.selenus.artemis.jupiter

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal class OkHttpApiClient(
    connectTimeoutMs: Long,
    readTimeoutMs: Long,
    writeTimeoutMs: Long
) : HttpApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    override fun get(url: String, headers: Map<String, String>): HttpApiClient.Response {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        val response = client.newCall(builder.build()).execute()
        return HttpApiClient.Response(response.code, response.body.string())
    }

    override fun post(url: String, body: String, headers: Map<String, String>): HttpApiClient.Response {
        val mediaType = "application/json".toMediaType()
        val builder = Request.Builder().url(url).post(body.toRequestBody(mediaType))
        headers.forEach { (k, v) -> builder.header(k, v) }
        val response = client.newCall(builder.build()).execute()
        return HttpApiClient.Response(response.code, response.body.string())
    }
}

internal actual fun createDefaultHttpApiClient(
    connectTimeoutMs: Long,
    readTimeoutMs: Long,
    writeTimeoutMs: Long
): HttpApiClient = OkHttpApiClient(connectTimeoutMs, readTimeoutMs, writeTimeoutMs)
