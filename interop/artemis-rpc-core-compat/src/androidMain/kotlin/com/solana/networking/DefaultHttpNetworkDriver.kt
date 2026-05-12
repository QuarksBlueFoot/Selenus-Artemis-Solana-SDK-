package com.solana.networking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal actual fun defaultHttpNetworkDriver(): HttpNetworkDriver = UrlConnectionNetworkDriver()

private class UrlConnectionNetworkDriver : HttpNetworkDriver {
    override suspend fun makeHttpRequest(request: HttpRequest): String = withContext(Dispatchers.IO) {
        val method = request.method.uppercase()
        require(method == "GET" || method == "POST") {
            "Unsupported HTTP method: ${request.method}"
        }
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 30_000
            request.properties.forEach { (key, value) -> setRequestProperty(key, value) }
            if (method == "POST") {
                doOutput = true
                val bytes = (request.body ?: "").encodeToByteArray()
                outputStream.use { it.write(bytes) }
            }
        }
        try {
            val stream = runCatching { connection.inputStream }.getOrNull() ?: connection.errorStream
            stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            connection.disconnect()
        }
    }
}