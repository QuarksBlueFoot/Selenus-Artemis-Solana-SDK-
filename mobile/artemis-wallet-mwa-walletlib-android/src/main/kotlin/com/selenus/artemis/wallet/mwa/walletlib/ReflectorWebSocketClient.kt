package com.selenus.artemis.wallet.mwa.walletlib

import com.selenus.artemis.wallet.mwa.protocol.Base64Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WSS reflector client for remote MWA wallet associations.
 *
 * The wallet connects to `wss://<reflector>/reflect?id=<id>`, waits for the
 * reflector's empty APP_PING, then hands the transport to the same HELLO and
 * JSON-RPC server used by local associations.
 */
internal class ReflectorWebSocketClient(
    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {
    suspend fun connect(
        associationUri: AssociationUri.Remote,
        connectTimeoutMs: Long = CONNECT_TIMEOUT_MS
    ): WalletTransport {
        val incoming = Channel<ByteArray>(Channel.BUFFERED)
        val reflected = CompletableDeferred<WalletTransport>()
        val useBase64 = AtomicBoolean(false)
        val didReflect = AtomicBoolean(false)

        val request = Request.Builder()
            .url(reflectorUrlFor(associationUri))
            .header("Sec-WebSocket-Protocol", SUBPROTOCOL_BINARY + ", " + SUBPROTOCOL_BASE64)
            .build()
        val client = okHttp.newBuilder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val selected = response.header("Sec-WebSocket-Protocol").orEmpty()
                useBase64.set(selected.equals(SUBPROTOCOL_BASE64, ignoreCase = true))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!didReflect.get()) {
                    if (text.isEmpty()) {
                        establishReflection(webSocket)
                    } else {
                        val error = MwaAssociationException("reflector sent data before APP_PING")
                        reflected.completeExceptionally(error)
                        webSocket.close(1002, "protocol error")
                    }
                    return
                }
                if (text.isEmpty()) return
                val decoded = try {
                    Base64Url.decode(text)
                } catch (e: IllegalArgumentException) {
                    reflected.completeExceptionally(MwaAssociationException("invalid base64 reflector frame", e))
                    webSocket.close(1002, "invalid base64")
                    return
                }
                deliver(decoded, webSocket)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (!didReflect.get()) {
                    if (data.isEmpty()) {
                        establishReflection(webSocket)
                    } else {
                        val error = MwaAssociationException("reflector sent data before APP_PING")
                        reflected.completeExceptionally(error)
                        webSocket.close(1002, "protocol error")
                    }
                    return
                }
                deliver(data, webSocket)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!reflected.isCompleted) reflected.completeExceptionally(t)
                incoming.close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!reflected.isCompleted) {
                    reflected.completeExceptionally(MwaAssociationException("reflector closed before APP_PING: $reason"))
                }
                incoming.close()
            }

            private fun establishReflection(webSocket: WebSocket) {
                if (!didReflect.compareAndSet(false, true)) return
                reflected.complete(ReflectorTransport(webSocket, incoming, useBase64::get))
            }

            private fun deliver(data: ByteArray, webSocket: WebSocket) {
                if (data.size > MAX_FRAME_SIZE_BYTES) {
                    webSocket.close(1009, "frame too large")
                    incoming.close(MwaAssociationException("reflector frame exceeds ${MAX_FRAME_SIZE_BYTES} bytes"))
                    return
                }
                incoming.trySend(data)
            }
        }

        val socket = client.newWebSocket(request, listener)
        return try {
            withTimeout(connectTimeoutMs) { reflected.await() }
        } catch (e: Throwable) {
            socket.cancel()
            incoming.close(e)
            throw e
        }
    }

    private class ReflectorTransport(
        private val webSocket: WebSocket,
        override val incoming: Channel<ByteArray>,
        private val useBase64: () -> Boolean
    ) : WalletTransport {
        override fun send(data: ByteArray) {
            require(data.size <= MAX_FRAME_SIZE_BYTES) {
                "reflector frame exceeds ${MAX_FRAME_SIZE_BYTES} bytes"
            }
            val accepted = if (useBase64()) {
                webSocket.send(Base64Url.encode(data))
            } else {
                webSocket.send(data.toByteString())
            }
            if (!accepted) throw IllegalStateException("reflector websocket rejected frame")
        }

        override fun close(code: Int, reason: String) {
            webSocket.close(code, reason)
            incoming.close()
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS: Long = 30_000L
        const val MAX_FRAME_SIZE_BYTES: Int = 4 * 1024
        const val SUBPROTOCOL_BINARY: String = "com.solana.mobilewalletadapter.v1"
        const val SUBPROTOCOL_BASE64: String = "com.solana.mobilewalletadapter.v1.base64"

        internal fun reflectorUrlFor(uri: AssociationUri.Remote): String =
            "wss://${uri.reflectorAuthority}/reflect?id=${Base64Url.encode(uri.reflectorId)}"
    }
}