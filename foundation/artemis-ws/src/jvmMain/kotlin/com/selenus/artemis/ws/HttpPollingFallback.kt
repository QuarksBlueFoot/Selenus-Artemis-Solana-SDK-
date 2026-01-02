package com.selenus.artemis.ws

import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * HTTP polling fallback for WebSocket subscriptions.
 *
 * When the WebSocket connection is down, this fallback polls the RPC endpoint
 * via HTTP to keep account/program data flowing. Automatically activated and
 * deactivated by [SolanaWsClient] during disconnect/reconnect cycles.
 *
 * ```kotlin
 * val fallback = HttpPollingFallback("https://api.mainnet-beta.solana.com")
 * val ws = SolanaWsClient(
 *     url = "wss://api.mainnet-beta.solana.com",
 *     fallback = fallback
 * )
 * ```
 */
class HttpPollingFallback(
    private val rpcEndpoint: String,
    private val http: OkHttpClient = OkHttpClient(),
    private val config: Config = Config()
) : SolanaWsClient.WsFallback {

    data class Config(
        /** How long to wait between poll cycles (ms). */
        val pollIntervalMs: Long = 2_000,
        /** Max keys to poll in a single batch request. */
        val maxBatchSize: Int = 20
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()
    private var requestId = 1L

    // Track last-seen state to only emit changes
    private val lastSeen = mutableMapOf<String, String>()

    override suspend fun poll(activeKeys: List<String>, emit: suspend (WsEvent) -> Unit) {
        if (activeKeys.isEmpty()) return

        // Parse active keys to determine what kind of RPC call to make
        val accountKeys = activeKeys.filter { it.startsWith("acct:") }
        val sigKeys = activeKeys.filter { it.startsWith("sig:") }

        // Poll account subscriptions via getMultipleAccounts
        if (accountKeys.isNotEmpty()) {
            pollAccounts(accountKeys, emit)
        }

        // Poll signature subscriptions via getSignatureStatuses
        if (sigKeys.isNotEmpty()) {
            pollSignatures(sigKeys, emit)
        }
    }

    private suspend fun pollAccounts(keys: List<String>, emit: suspend (WsEvent) -> Unit) {
        // Extract pubkeys from keys like "acct:PUBKEY:confirmed:base64"
        val pubkeys = keys.mapNotNull { key ->
            val parts = key.split(":")
            if (parts.size >= 2) parts[1] else null
        }

        for (batch in pubkeys.chunked(config.maxBatchSize)) {
            val params = buildJsonArray {
                add(JsonArray(batch.map { JsonPrimitive(it) }))
                add(buildJsonObject {
                    put("encoding", "base64")
                    put("commitment", "confirmed")
                })
            }

            val response = rpcCall("getMultipleAccounts", params) ?: continue
            val value = response["value"]?.jsonArray ?: continue

            batch.forEachIndexed { index, pubkey ->
                val account = value.getOrNull(index)
                if (account != null && account !is JsonNull) {
                    val dataStr = account.toString()
                    val prevData = lastSeen[pubkey]
                    if (prevData != dataStr) {
                        lastSeen[pubkey] = dataStr
                        // Find matching key and emit as notification
                        val matchingKey = keys.find { it.contains(pubkey) }
                        if (matchingKey != null) {
                            emit(WsEvent.Notification(
                                key = matchingKey,
                                subscriptionId = -1,
                                method = "accountNotification",
                                result = account
                            ))
                        }
                    }
                }
            }
        }
    }

    private suspend fun pollSignatures(keys: List<String>, emit: suspend (WsEvent) -> Unit) {
        val signatures = keys.mapNotNull { key ->
            val parts = key.split(":")
            if (parts.size >= 2) parts[1] else null
        }

        for (batch in signatures.chunked(config.maxBatchSize)) {
            val params = buildJsonArray {
                add(JsonArray(batch.map { JsonPrimitive(it) }))
                add(buildJsonObject { put("searchTransactionHistory", true) })
            }

            val response = rpcCall("getSignatureStatuses", params) ?: continue
            val value = response["value"]?.jsonArray ?: continue

            batch.forEachIndexed { index, sig ->
                val status = value.getOrNull(index)
                if (status != null && status !is JsonNull) {
                    val confirmationStatus = status.jsonObject["confirmationStatus"]?.jsonPrimitive?.content
                    if (confirmationStatus == "confirmed" || confirmationStatus == "finalized") {
                        val matchingKey = keys.find { it.contains(sig) }
                        if (matchingKey != null) {
                            emit(WsEvent.Notification(
                                key = matchingKey,
                                subscriptionId = -1,
                                method = "signatureNotification",
                                result = status
                            ))
                        }
                    }
                }
            }
        }
    }

    private fun rpcCall(method: String, params: JsonElement): JsonObject? {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestId++)
            put("method", method)
            put("params", params)
        }

        return try {
            val body = payload.toString().toRequestBody(mediaType)
            val request = Request.Builder().url(rpcEndpoint).post(body).build()
            val response = http.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            val result = json.parseToJsonElement(responseBody).jsonObject
            result["result"]?.jsonObject
        } catch (_: Throwable) {
            null
        }
    }
}
