package com.selenus.artemis.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.Closeable

/**
 * RealtimeEngine — high-level Solana account and program subscription manager.
 *
 * Wraps [SolanaWsClient] to expose a declarative, callback-based subscription API.
 * Typed callbacks survive reconnects. [reconnect] rotates through [endpoints] so a
 * failed primary node does not break the subscription surface.
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
 * // Rotate to the next endpoint and replay all subscriptions
 * realtime.reconnect()
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

    // Endpoint rotation index — incremented by reconnect() for failover.
    private var endpointIndex = 0

    // Parsed event callbacks keyed by pubkey / signature.
    private val accountCallbacks = mutableMapOf<String, (AccountNotification) -> Unit>()
    private val signatureCallbacks = mutableMapOf<String, (confirmed: Boolean) -> Unit>()

    // Raw subscription handles from SolanaWsClient, kept for clean unsubscribe.
    private val accountHandles = mutableMapOf<String, SubscriptionHandle>()
    private val signatureHandles = mutableMapOf<String, SubscriptionHandle>()

    // Spec registry — lets reconnect() replay all typed subscriptions on a new client.
    private data class AccountSpec(
        val pubkey: String,
        val commitment: String,
        val callback: (AccountNotification) -> Unit
    )
    private data class SignatureSpec(
        val signature: String,
        val commitment: String,
        val callback: (Boolean) -> Unit
    )
    private val accountSpecs = LinkedHashMap<String, AccountSpec>()
    private val signatureSpecs = LinkedHashMap<String, SignatureSpec>()

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
     * Connect to the first available endpoint and start the event loop.
     * Use [reconnect] to rotate to the next endpoint on failure.
     */
    fun connect() {
        val url = currentEndpointUrl()
        val wsClient = SolanaWsClient(url = url, scope = scope)
        client = wsClient
        attachEventDispatcher(wsClient)
        wsClient.connect()
    }

    /**
     * Rotate to the next endpoint and rebuild the subscription surface.
     *
     * Closes the current [SolanaWsClient], picks the next URL from [endpoints],
     * opens a fresh client, and replays every registered typed subscription so
     * callers never need to re-subscribe manually after a node failure.
     */
    fun reconnect() {
        client?.close()
        client = null
        endpointIndex = (endpointIndex + 1) % endpoints.size.coerceAtLeast(1)
        val url = currentEndpointUrl()
        val wsClient = SolanaWsClient(url = url, scope = scope)
        client = wsClient
        attachEventDispatcher(wsClient)
        wsClient.connect()

        // Replay all registered typed subscriptions on the new client.
        scope.launch {
            for ((pubkey, spec) in accountSpecs.toMap()) {
                try {
                    val rawHandle = wsClient.accountSubscribe(spec.pubkey, spec.commitment, "jsonParsed")
                    accountHandles[pubkey] = rawHandle
                    accountCallbacks[pubkey] = spec.callback
                } catch (_: Exception) { /* best-effort */ }
            }
            for ((sig, spec) in signatureSpecs.toMap()) {
                try {
                    val rawHandle = wsClient.signatureSubscribe(spec.signature, spec.commitment)
                    signatureHandles[sig] = rawHandle
                    signatureCallbacks[sig] = spec.callback
                } catch (_: Exception) { /* best-effort */ }
            }
        }
    }

    /**
     * Subscribe to account changes for [pubkey].
     *
     * The [callback] is invoked on every notification with a parsed [AccountNotification].
     * The subscription is recorded in the spec registry and replayed automatically on [reconnect].
     * Returns a [SubscriptionHandle] that can be [SubscriptionHandle.close]d to unsubscribe.
     */
    suspend fun subscribeAccount(
        pubkey: String,
        commitment: String = "confirmed",
        callback: (AccountNotification) -> Unit
    ): SubscriptionHandle {
        val ws = requireConnected()
        val spec = AccountSpec(pubkey, commitment, callback)
        accountSpecs[pubkey] = spec
        accountCallbacks[pubkey] = callback
        val rawHandle = ws.accountSubscribe(pubkey, commitment, "jsonParsed")
        accountHandles[pubkey] = rawHandle
        return SubscriptionHandle(rawHandle.key) { _ ->
            accountSpecs.remove(pubkey)
            accountCallbacks.remove(pubkey)
            accountHandles.remove(pubkey)
            rawHandle.close()
        }
    }

    /**
     * Subscribe to a specific transaction signature confirmation.
     *
     * [callback] is invoked with `true` once the transaction is confirmed.
     * The subscription is auto-removed after the first notification.
     * It is also recorded in the spec registry and replayed on [reconnect].
     */
    suspend fun subscribeSignature(
        signature: String,
        commitment: String = "confirmed",
        callback: (confirmed: Boolean) -> Unit
    ): SubscriptionHandle {
        val ws = requireConnected()
        val spec = SignatureSpec(signature, commitment, callback)
        signatureSpecs[signature] = spec
        signatureCallbacks[signature] = callback
        val rawHandle = ws.signatureSubscribe(signature, commitment)
        signatureHandles[signature] = rawHandle
        return SubscriptionHandle(rawHandle.key) { _ ->
            signatureSpecs.remove(signature)
            signatureCallbacks.remove(signature)
            signatureHandles.remove(signature)
            rawHandle.close()
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
        accountSpecs.clear()
        signatureSpecs.clear()
        accountHandles.clear()
        signatureHandles.clear()
    }

    // ─── internal ────────────────────────────────────────────────────────────

    private fun currentEndpointUrl(): String {
        if (endpoints.isEmpty()) throw IllegalArgumentException("RealtimeEngine requires at least one endpoint")
        return endpoints[endpointIndex % endpoints.size]
    }

    private fun attachEventDispatcher(wsClient: SolanaWsClient) {
        wsClient.events
            .onEach { event -> dispatchEvent(event) }
            .launchIn(scope)
    }

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
