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

    /**
     * Issue [requests] as a JSON-RPC 2.0 batch and return one response per
     * request, in the same order.
     *
     * Behaviour on per-item errors: the raw JSON-RPC response envelope is
     * returned unchanged, so callers are responsible for inspecting the
     * `error` field on each entry. For a typed success/failure split use
     * [callBatchTyped].
     */
    suspend fun callBatch(requests: List<Pair<String, JsonElement?>>): List<JsonObject>

    /**
     * Strongly-typed batch variant. Returns one [BatchItemResult] per input
     * request, with [BatchItemResult.Ok] for `result`-bearing responses and
     * [BatchItemResult.Err] carrying the decoded JSON-RPC error (code +
     * message + data) for responses that contained an `error` field.
     *
     * The default implementation walks [callBatch] so existing transports
     * do not need to change. Overrides may choose to short-circuit network
     * work when every item succeeds.
     */
    suspend fun callBatchTyped(
        requests: List<Pair<String, JsonElement?>>
    ): List<BatchItemResult> = callBatch(requests).map { obj ->
        val err = obj["error"]
        if (err != null && err !is kotlinx.serialization.json.JsonNull) {
            val errObj = err as? JsonObject
            val code = errObj?.get("code")
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content?.toIntOrNull() ?: -32000
            val message = errObj?.get("message")
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content ?: err.toString()
            BatchItemResult.Err(code = code, message = message, data = errObj?.get("data"))
        } else {
            BatchItemResult.Ok(obj)
        }
    }
}

/**
 * Per-item outcome inside a JSON-RPC batch response.
 *
 * Splits success and failure at the type level so callers cannot
 * accidentally ignore error entries the way they could with a flat
 * `List<JsonObject>`. The [Err.code] is the JSON-RPC 2.0 error code
 * (e.g. `-32602` for invalid params).
 */
sealed class BatchItemResult {
    data class Ok(val envelope: JsonObject) : BatchItemResult()
    data class Err(
        val code: Int,
        val message: String,
        val data: JsonElement? = null
    ) : BatchItemResult()

    val isSuccess: Boolean get() = this is Ok
    val isError: Boolean get() = this is Err
}

class RpcException(message: String) : RuntimeException(message)
class RpcHttpException(val code: Int) : RuntimeException("HTTP $code")
