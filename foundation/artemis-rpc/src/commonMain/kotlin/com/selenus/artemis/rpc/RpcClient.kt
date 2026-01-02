package com.selenus.artemis.rpc

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Common RPC client interface.
 *
 * JVM: implemented by JsonRpcClient (OkHttp).
 * Other targets: implement via HttpTransport.
 */
interface RpcClient {
    suspend fun call(method: String, params: JsonElement? = null): JsonObject
    suspend fun callBatch(requests: List<Pair<String, JsonElement?>>): List<JsonObject>
}

class RpcException(message: String) : RuntimeException(message)
class RpcHttpException(val code: Int) : RuntimeException("HTTP $code")
