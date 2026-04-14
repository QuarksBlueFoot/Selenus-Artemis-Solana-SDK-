package com.selenus.artemis.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.Closeable

/**
 * RealtimeEngine — high-level Solana account and program subscription manager.
 *
 * Wraps [SolanaWsClient] to expose a declarative, callback-based subscription API that
 * matches the v68 Artemis developer experience. Auto-reconnects with jittered backoff;
 * re-subscribes all active subscriptions on reconnect; sends heartbeat pings every 60 s.
 *
 * ```kotlin
 * val realtime = RealtimeEngine(
 *     endpoints = listOf(
 *         "wss://atlas-mainnet.helius-rpc.com/?api-key=...",
 *         "wss://api.mainnet-beta.solana.com"
 *     )
 * )
 * realtime.connect()
 *
 * val handle = realtime.subscribeAccount("EPjFWddqJkwNX...") { info ->
 *     println("lamports: ${info.lamports}")
 * }
 *
 * // later
 * handle.close()
 * realtime.close()
 * ```
 */
class RealtimeEngine(
    private val endpoints: List<String>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : Closeable {

    private var client: SolanaWsClient? = null

    // Registered callbacks, kept so we can deliver parsed events.
    private val accountCallbacks = mutableMapOf<String, (AccountNotification) -> Unit>()
    private val signatureCallbacks = mutableMapOf<String, (confirmed: Boolean) -> Unit>()

    /**
     * Lightweight view of an account notification from the WebSocket subscription.
     */
    data class AccountNotification(
        val pubkey: String,
        val lamports: Long,
        val data: String?,
        val owner: String?,
        val slot: Long
    )

    /**
     * Connect to the first endpoint and start the event loop.
     * Falls back through [endpoints] on reconnect (handled by [SolanaWsClient] internally
     * via its configured reconnect policy; we expose only the primary endpoint here since
     * the client reconnects to the same URL).
     */
    fun connect() {
        val url = endpoints.firstOrNull()
            ?: throw IllegalArgumentException("RealtimeEngine requires at least one endpoint")
        val wsClient = SolanaWsClient(url = url, scope = scope)
        client = wsClient

        // Dispatch parsed events to registered callbacks
        wsClient.events
            .onEach { event -> dispatchEvent(event) }
            .launchIn(scope)

        wsClient.connect()
    }

    /**
     * Subscribe to account changes for [pubkey].
     *
     * The [callback] is invoked on every notification with a parsed [AccountNotification].
     * Returns a [SubscriptionHandle] that can be [SubscriptionHandle.close]d to unsubscribe.
     */
    suspend fun subscribeAccount(
        pubkey: String,
        commitment: String = "confirmed",
        callback: (AccountNotification) -> Unit
    ): SubscriptionHandle {
        val ws = requireConnected()
        accountCallbacks[pubkey] = callback
        val handle = ws.accountSubscribe(pubkey, commitment, "jsonParsed")
        // Wrap to also remove our callback on close
        return SubscriptionHandle(handle.key) { h ->
            accountCallbacks.remove(pubkey)
            handle.close()
        }
    }

    /**
     * Subscribe to a specific transaction signature confirmation.
     *
     * [callback] is invoked with `true` once the transaction is confirmed,
     * and the subscription is automatically removed.
     */
    suspend fun subscribeSignature(
        signature: String,
        commitment: String = "confirmed",
        callback: (confirmed: Boolean) -> Unit
    ): SubscriptionHandle {
        val ws = requireConnected()
        signatureCallbacks[signature] = callback
        val handle = ws.signatureSubscribe(signature, commitment)
        return SubscriptionHandle(handle.key) { h ->
            signatureCallbacks.remove(signature)
            handle.close()
        }
    }

    /**
     * Subscribe to all account changes made by [programId].
     * Returns the raw [SubscriptionHandle] — events are delivered via [SolanaWsClient.events].
     */
    suspend fun subscribeProgram(
        programId: String,
        commitment: String = "confirmed"
    ): SubscriptionHandle {
        return requireConnected().programSubscribe(programId, commitment)
    }

    /**
     * Expose the underlying event flow for callers that want raw [WsEvent]s.
     */
    val events get() = client?.events
        ?: throw IllegalStateException("RealtimeEngine not connected. Call connect() first.")

    override fun close() {
        client?.close()
        client = null
        accountCallbacks.clear()
        signatureCallbacks.clear()
    }

    // ─── internal ────────────────────────────────────────────────────────────

    private fun requireConnected(): SolanaWsClient =
        client ?: throw IllegalStateException("RealtimeEngine not connected. Call connect() first.")

    private fun dispatchEvent(event: WsEvent) {
        if (event !is WsEvent.Notification) return

        when {
            event.method == "accountNotification" -> {
                val result = event.result?.jsonObject ?: return
                val value = result["value"]?.jsonObject ?: return
                val context = result["context"]?.jsonObject
                val slot = context?.get("slot")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

                val lamports = value["lamports"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val owner = value["owner"]?.jsonPrimitive?.content
                val dataArr = value["data"]
                val data = when {
                    dataArr?.jsonObject != null -> dataArr.jsonObject["parsed"]?.toString()
                    else -> dataArr?.jsonPrimitive?.content
                }

                // Key format: "acct:<pubkey>:..." — extract pubkey
                val pubkey = event.key?.removePrefix("acct:")?.substringBefore(":") ?: return
                val notification = AccountNotification(pubkey, lamports, data, owner, slot)
                accountCallbacks[pubkey]?.invoke(notification)
            }
            event.method == "signatureNotification" -> {
                val sig = event.key?.removePrefix("sig:")?.substringBefore(":") ?: return
                val result = event.result?.jsonObject
                val err = result?.get("value")?.jsonObject?.get("err")
                val confirmed = err == null || err.toString() == "null"
                signatureCallbacks[sig]?.invoke(confirmed)
                signatureCallbacks.remove(sig) // auto-remove after first notification
            }
        }
    }
}
