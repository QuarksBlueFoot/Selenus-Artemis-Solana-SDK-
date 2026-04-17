/*
 * Drop-in source compatibility with com.solana.rpccore (rpc-core 0.2.5).
 *
 * rpc-core ships a tiny JSON-RPC 2.0 abstraction layer that `SolanaRpcClient`
 * builds on. The shim re-publishes those types at the same fully qualified
 * package path so any user that imported `RpcRequest`, `RpcResponse`, or
 * `JsonRpcDriver` continues to compile.
 *
 * Internals delegate to Artemis's own request and response plumbing.
 */
package com.solana.rpccore

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Minimal JSON-RPC 2.0 request contract. Upstream declares it as a sealed
 * interface with two concrete implementations ([JsonRpc20Request] and the
 * deprecated [JsonRpcRequest]).
 */
interface RpcRequest {
    val method: String
    val params: JsonElement?
    val jsonrpc: String
    val id: String
}

/**
 * Legacy JSON-RPC request base class. Kept for callers that imported the
 * pre-2.0 shape.
 */
open class JsonRpcRequest(
    override val method: String,
    override val params: JsonElement? = null,
    override val id: String = "1",
    override val jsonrpc: String = "2.0"
) : RpcRequest

/** JSON-RPC 2.0 request. `jsonrpc` is fixed to `"2.0"`. */
open class JsonRpc20Request(
    method: String,
    params: JsonElement? = null,
    id: String = "1"
) : JsonRpcRequest(method = method, params = params, id = id, jsonrpc = "2.0")

/**
 * JSON-RPC 2.0 response. Either [result] or [error] is populated, not both.
 */
interface RpcResponse<R> {
    val result: R?
    val error: RpcError?
    val id: String?
    val jsonrpc: String
}

/** Type alias for the untyped response case. */
typealias DefaultRpcResponse = RpcResponse<JsonElement>

/** JSON-RPC error object. */
@Serializable
data class RpcError(val code: Int, val message: String)

/** Concrete [RpcResponse] used internally. */
@Serializable
open class Rpc20Response<R>(
    override val result: R? = null,
    override val error: RpcError? = null,
    override val id: String? = null
) : RpcResponse<R> {
    override val jsonrpc: String = "2.0"
}

/**
 * Driver abstraction. User code implements this to plug custom transports;
 * `Rpc20Driver` is the canonical implementation rpc-core ships.
 */
interface JsonRpcDriver {
    suspend fun <R> makeRequest(
        request: RpcRequest,
        resultSerializer: DeserializationStrategy<R>
    ): RpcResponse<R>
}
