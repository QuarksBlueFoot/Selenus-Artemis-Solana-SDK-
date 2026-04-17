/*
 * Drop-in source compatibility with com.solana.networking (rpc-core 0.2.5).
 *
 * rpc-core exposes an `HttpNetworkDriver` interface that concrete transports
 * implement (Ktor, OkHttp). `Rpc20Driver` wraps the driver to produce a
 * JSON-RPC driver. This shim re-publishes both at the same package path and
 * wires the default implementation to Artemis's internal HTTP client.
 */
package com.solana.networking

import com.selenus.artemis.rpc.HttpTransport
import com.solana.rpccore.JsonRpcDriver
import com.solana.rpccore.RpcRequest
import com.solana.rpccore.RpcResponse
import com.solana.rpccore.Rpc20Response
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * HTTP transport abstraction. Concrete implementations live in
 * `ktordriver` / `okiodriver` artifacts upstream; the shim provides a default
 * that forwards to the Artemis HTTP transport used by `artemis-rpc`.
 */
interface HttpNetworkDriver {
    suspend fun makeHttpRequest(request: HttpRequest): String
}

/** Minimal HTTP request descriptor. Matches upstream signature. */
data class HttpRequest(
    val url: String,
    val method: String = "POST",
    val properties: Map<String, String> = emptyMap(),
    val body: String? = null
)

/**
 * JSON-RPC 2.0 driver that wraps an [HttpNetworkDriver].
 *
 * Users pass this to `SolanaRpcClient`. The shim's implementation routes
 * through the provided driver, serializing requests and parsing responses
 * via kotlinx.serialization.
 */
open class Rpc20Driver(
    private val url: String,
    private val networkDriver: HttpNetworkDriver,
    private val json: Json = DEFAULT_JSON
) : JsonRpcDriver {

    override suspend fun <R> makeRequest(
        request: RpcRequest,
        resultSerializer: DeserializationStrategy<R>
    ): RpcResponse<R> {
        val body = buildJsonObject {
            put("jsonrpc", request.jsonrpc)
            put("id", request.id)
            put("method", request.method)
            request.params?.let { put("params", it) }
        }.toString()

        val httpResponse = networkDriver.makeHttpRequest(
            HttpRequest(
                url = url,
                method = "POST",
                properties = mapOf("Content-Type" to "application/json"),
                body = body
            )
        )

        val parsed = json.parseToJsonElement(httpResponse).jsonObject
        val error = parsed["error"]?.let { err ->
            if (err !is JsonObject) null else com.solana.rpccore.RpcError(
                code = err["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: -32603,
                message = err["message"]?.jsonPrimitive?.content ?: "unknown rpc error"
            )
        }
        val id = parsed["id"]?.jsonPrimitive?.content
        val rawResult: JsonElement? = parsed["result"]
        val typedResult: R? = rawResult?.let { json.decodeFromJsonElement(resultSerializer, it) }

        return Rpc20Response(result = typedResult, error = error, id = id)
    }

    companion object {
        internal val DEFAULT_JSON: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

/**
 * Default [HttpNetworkDriver] that hands the request off to Artemis's
 * platform HTTP transport. Callers who want a custom driver (Ktor, OkHttp
 * with interceptors, mocked test double) implement [HttpNetworkDriver]
 * directly.
 */
class ArtemisHttpNetworkDriver(
    private val transport: HttpTransport
) : HttpNetworkDriver {
    override suspend fun makeHttpRequest(request: HttpRequest): String {
        val response = transport.postJson(
            url = request.url,
            body = request.body ?: "",
            headers = request.properties
        )
        if (response.code !in 200..299) {
            throw IllegalStateException("HTTP ${response.code} from ${request.url}: ${response.body}")
        }
        return response.body
    }
}
