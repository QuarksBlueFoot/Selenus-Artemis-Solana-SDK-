/*
 * Drop-in source compatibility for com.solana.mobilewalletadapter.walletlib.protocol.JsonRpc20Server.
 *
 * Upstream's class is the JSON-RPC 2.0 envelope encoder/decoder that
 * sits between the encrypted session and the typed
 * [MobileWalletAdapterServer]. The Artemis dispatcher inlines the
 * envelope handling into `WalletMwaServer`, so the FQN is exposed as
 * a constant carrier for the standard JSON-RPC error codes plus a
 * couple of helper methods Java callers occasionally reach for.
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.walletlib.protocol

import org.json.JSONObject

/**
 * JSON-RPC 2.0 server-side helpers. Mirrors upstream's `JsonRpc20Server`
 * static surface; the dispatcher path itself is owned by the Artemis
 * `WalletMwaServer` and consumers do not normally instantiate this.
 */
class JsonRpc20Server {
    companion object {
        const val ERROR_PARSE_ERROR: Int = -32700
        const val ERROR_INVALID_REQUEST: Int = -32600
        const val ERROR_METHOD_NOT_FOUND: Int = -32601
        const val ERROR_INVALID_PARAMS: Int = -32602
        const val ERROR_INTERNAL_ERROR: Int = -32603

        const val JSONRPC_VERSION: String = "2.0"

        /**
         * Build a JSON-RPC error envelope for the given fields. Java
         * callers occasionally use this directly when they wrap the
         * dispatcher with their own server.
         */
        @JvmStatic
        @JvmOverloads
        fun buildError(
            id: Int? = null,
            code: Int,
            message: String,
            data: Any? = null
        ): JSONObject {
            val error = JSONObject().apply {
                put("code", code)
                put("message", message)
                if (data != null) put("data", data)
            }
            return JSONObject().apply {
                put("jsonrpc", JSONRPC_VERSION)
                if (id != null) put("id", id) else put("id", JSONObject.NULL)
                put("error", error)
            }
        }

        /** Build a JSON-RPC success envelope for the given fields. */
        @JvmStatic
        fun buildResult(id: Int?, result: Any?): JSONObject = JSONObject().apply {
            put("jsonrpc", JSONRPC_VERSION)
            if (id != null) put("id", id) else put("id", JSONObject.NULL)
            put("result", result ?: JSONObject.NULL)
        }
    }

    /**
     * Marker for upstream `JsonRpc20Server.MethodHandlers`. Surfaced as
     * an empty interface so cross-cast `is JsonRpc20Server.MethodHandlers`
     * still resolves; the Artemis dispatcher uses
     * [com.selenus.artemis.wallet.mwa.walletlib.Scenario.Callbacks] for
     * the same role.
     */
    interface MethodHandlers
}
