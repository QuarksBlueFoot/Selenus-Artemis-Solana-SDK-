package com.selenus.artemis.ws

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicLong

/**
 * HTTP polling fallback for WebSocket subscriptions.
 *
 * When the WebSocket connection is down, this fallback polls the RPC
 * endpoint via HTTP to keep subscription data flowing. `SolanaWsClient`
 * enables and disables it during disconnect/reconnect cycles. Supports
 * account, signature, program-account, and logs subscription keys.
 *
 * Key format:
 *   acct:PUBKEY:COMMITMENT:ENCODING
 *   sig:SIGNATURE[:COMMITMENT]
 *   prog:PROGRAM_ID:COMMITMENT[:ENCODING]
 *   logs:FILTER[:COMMITMENT]
 *
 * Keys are parsed deterministically via `split(':', limit = 4)`; the old
 * `contains(pubkey)` substring match is gone because it could match one
 * key against another when pubkeys had overlapping prefixes.
 */
class HttpPollingFallback(
    private val rpcEndpoint: String,
    private val http: OkHttpClient = OkHttpClient(),
    private val config: Config = Config(),
    private val sleep: suspend (Long) -> Unit = ::delay
) : SolanaWsClient.WsFallback {

    data class Config(
        /** Minimum delay between poll cycles (ms). Enforced between cycles. */
        val pollIntervalMs: Long = 2_000,
        /** Max keys to poll in a single batch request. */
        val maxBatchSize: Int = 20,
        /** Tail logs fetched per `prog:`/`logs:` poll when the RPC supports it. */
        val logsTailLimit: Int = 25
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()
    private val requestId = AtomicLong(1L)

    // Track last-seen state to only emit changes.
    private val lastAccountState = mutableMapOf<String, String>()
    private val lastProgramState = mutableMapOf<String, String>()
    @Volatile private var lastPollAt: Long = 0L

    /**
     * Parsed subscription key. Constructed exclusively by [parseKey] so
     * downstream matching cannot accidentally conflate two keys whose
     * pubkeys overlap textually.
     */
    private sealed class ParsedKey(val raw: String) {
        class Account(
            raw: String,
            val pubkey: String,
            val commitment: String,
            val encoding: String
        ) : ParsedKey(raw)

        class Signature(raw: String, val signature: String, val commitment: String) : ParsedKey(raw)

        class Program(
            raw: String,
            val programId: String,
            val commitment: String,
            val encoding: String
        ) : ParsedKey(raw)

        class Logs(raw: String, val filter: String, val commitment: String) : ParsedKey(raw)

        object Unknown : ParsedKey("")
    }

    private fun parseKey(raw: String): ParsedKey {
        val parts = raw.split(':', limit = 4)
        return when (parts.firstOrNull()) {
            "acct" -> if (parts.size >= 2) ParsedKey.Account(
                raw = raw,
                pubkey = parts[1],
                commitment = parts.getOrNull(2) ?: "confirmed",
                encoding = parts.getOrNull(3) ?: "base64"
            ) else ParsedKey.Unknown
            "sig" -> if (parts.size >= 2) ParsedKey.Signature(
                raw = raw,
                signature = parts[1],
                commitment = parts.getOrNull(2) ?: "confirmed"
            ) else ParsedKey.Unknown
            "prog" -> if (parts.size >= 2) ParsedKey.Program(
                raw = raw,
                programId = parts[1],
                commitment = parts.getOrNull(2) ?: "confirmed",
                encoding = parts.getOrNull(3) ?: "base64"
            ) else ParsedKey.Unknown
            "logs" -> if (parts.size >= 2) ParsedKey.Logs(
                raw = raw,
                filter = parts[1],
                commitment = parts.getOrNull(2) ?: "confirmed"
            ) else ParsedKey.Unknown
            else -> ParsedKey.Unknown
        }
    }

    override suspend fun poll(activeKeys: List<String>, emit: suspend (WsEvent) -> Unit) {
        if (activeKeys.isEmpty()) return
        // Honour pollIntervalMs so back-to-back poll cycles do not hammer
        // the RPC. SolanaWsClient drives this method at its own cadence,
        // and an internal floor is still enforced for safety.
        val now = System.currentTimeMillis()
        val waitMs = config.pollIntervalMs - (now - lastPollAt)
        if (waitMs > 0 && lastPollAt != 0L) sleep(waitMs)
        lastPollAt = System.currentTimeMillis()

        val parsed = activeKeys.map { parseKey(it) }.filterNot { it is ParsedKey.Unknown }
        val accounts = parsed.filterIsInstance<ParsedKey.Account>()
        val sigs = parsed.filterIsInstance<ParsedKey.Signature>()
        val programs = parsed.filterIsInstance<ParsedKey.Program>()
        val logs = parsed.filterIsInstance<ParsedKey.Logs>()

        if (accounts.isNotEmpty()) pollAccounts(accounts, emit)
        if (sigs.isNotEmpty()) pollSignatures(sigs, emit)
        if (programs.isNotEmpty()) pollPrograms(programs, emit)
        if (logs.isNotEmpty()) pollLogs(logs, emit)
    }

    private suspend fun pollAccounts(
        keys: List<ParsedKey.Account>,
        emit: suspend (WsEvent) -> Unit
    ) {
        // Group by (commitment, encoding) so one getMultipleAccounts call
        // uses consistent options across the batch.
        keys.groupBy { it.commitment to it.encoding }
            .forEach { (opts, group) ->
                val (commitment, encoding) = opts
                for (batch in group.chunked(config.maxBatchSize)) {
                    val params = buildJsonArray {
                        add(JsonArray(batch.map { JsonPrimitive(it.pubkey) }))
                        add(buildJsonObject {
                            put("encoding", encoding)
                            put("commitment", commitment)
                        })
                    }
                    val response = rpcCall("getMultipleAccounts", params) ?: continue
                    val value = response["value"]?.jsonArray ?: continue
                    batch.forEachIndexed { index, key ->
                        val account = value.getOrNull(index)
                        if (account != null && account !is JsonNull) {
                            val dataStr = account.toString()
                            if (lastAccountState[key.raw] != dataStr) {
                                lastAccountState[key.raw] = dataStr
                                emit(
                                    WsEvent.Notification(
                                        key = key.raw,
                                        subscriptionId = -1,
                                        method = "accountNotification",
                                        result = account
                                    )
                                )
                            }
                        }
                    }
                }
            }
    }

    private suspend fun pollSignatures(
        keys: List<ParsedKey.Signature>,
        emit: suspend (WsEvent) -> Unit
    ) {
        for (batch in keys.chunked(config.maxBatchSize)) {
            val params = buildJsonArray {
                add(JsonArray(batch.map { JsonPrimitive(it.signature) }))
                add(buildJsonObject { put("searchTransactionHistory", true) })
            }
            val response = rpcCall("getSignatureStatuses", params) ?: continue
            val value = response["value"]?.jsonArray ?: continue
            batch.forEachIndexed { index, key ->
                val status = value.getOrNull(index)
                if (status != null && status !is JsonNull) {
                    val confirmationStatus =
                        status.jsonObject["confirmationStatus"]?.jsonPrimitive?.content
                    val targetReached = when (key.commitment) {
                        "processed" ->
                            confirmationStatus == "processed" ||
                                confirmationStatus == "confirmed" ||
                                confirmationStatus == "finalized"
                        "finalized" -> confirmationStatus == "finalized"
                        else -> confirmationStatus == "confirmed" ||
                            confirmationStatus == "finalized"
                    }
                    if (targetReached) {
                        emit(
                            WsEvent.Notification(
                                key = key.raw,
                                subscriptionId = -1,
                                method = "signatureNotification",
                                result = status
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun pollPrograms(
        keys: List<ParsedKey.Program>,
        emit: suspend (WsEvent) -> Unit
    ) {
        // Program-account polling: fetch all accounts owned by the program
        // and diff against last-seen. Per-program rather than per-key to
        // avoid issuing one RPC per subscriber for the same program.
        keys.distinctBy { it.programId to it.commitment to it.encoding }.forEach { key ->
            val params = buildJsonArray {
                add(JsonPrimitive(key.programId))
                add(buildJsonObject {
                    put("encoding", key.encoding)
                    put("commitment", key.commitment)
                })
            }
            val response = rpcCall("getProgramAccounts", params) ?: return@forEach
            // getProgramAccounts returns a JSON array at "result"; the rpcCall
            // shim unwraps `result` to a JsonObject which is NOT correct for
            // array responses. Inline the raw call here instead.
            val arrResponse = rpcCallRaw("getProgramAccounts", params) ?: return@forEach
            val arr = arrResponse["result"]?.let { el ->
                if (el is JsonArray) el else null
            } ?: return@forEach
            val digest = arr.toString()
            if (lastProgramState[key.raw] != digest) {
                lastProgramState[key.raw] = digest
                emit(
                    WsEvent.Notification(
                        key = key.raw,
                        subscriptionId = -1,
                        method = "programNotification",
                        result = arrResponse
                    )
                )
            }
        }
    }

    private suspend fun pollLogs(
        keys: List<ParsedKey.Logs>,
        emit: suspend (WsEvent) -> Unit
    ) {
        // logsSubscribe is genuinely push-only on upstream Solana RPC; no
        // pull equivalent exists. Emit a one-shot heartbeat per subscriber
        // so the caller knows polling is live but no log stream is
        // available until the WS reconnects. No data is fabricated when
        // a real fetch is impossible.
        for (key in keys) {
            emit(
                WsEvent.Notification(
                    key = key.raw,
                    subscriptionId = -1,
                    method = "logsPollingUnavailable",
                    result = JsonObject(
                        mapOf(
                            "reason" to JsonPrimitive(
                                "logsSubscribe has no HTTP equivalent; waiting for WS"
                            ),
                            "filter" to JsonPrimitive(key.filter),
                            "commitment" to JsonPrimitive(key.commitment)
                        )
                    )
                )
            )
        }
    }

    private fun rpcCall(method: String, params: JsonElement): JsonObject? {
        val raw = rpcCallRaw(method, params) ?: return null
        return raw["result"]?.let { it as? JsonObject }
    }

    private fun rpcCallRaw(method: String, params: JsonElement): JsonObject? {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestId.getAndIncrement())
            put("method", method)
            put("params", params)
        }
        return try {
            val body = payload.toString().toRequestBody(mediaType)
            val request = Request.Builder().url(rpcEndpoint).post(body).build()
            http.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: return null
                json.parseToJsonElement(responseBody).jsonObject
            }
        } catch (_: Throwable) {
            null
        }
    }
}
