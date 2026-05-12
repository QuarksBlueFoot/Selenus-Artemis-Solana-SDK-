package com.selenus.artemis.streaming

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.Socket
import java.util.Base64
import java.util.concurrent.TimeUnit

class MwaReflectorServerTest {
    private val servers = mutableListOf<MwaReflectorServer>()
    private val clients = mutableListOf<OkHttpClient>()

    @AfterEach
    fun tearDown() {
        servers.forEach { it.close() }
        clients.forEach { it.dispatcher.executorService.shutdownNow() }
    }

    @Test
    fun `pairs binary websocket peers and relays opaque frames`() = runBlocking {
        val server = startServer()
        val id = reflectorId(byteArrayOf(1, 2, 3, 4))
        val left = connect(server.localPort, id, MwaReflectorServer.SUBPROTOCOL_BINARY)
        val right = connect(server.localPort, id, MwaReflectorServer.SUBPROTOCOL_BINARY)

        withTimeout(5_000) {
            left.reflected.await()
            right.reflected.await()
        }
        assertEquals(MwaReflectorServer.SUBPROTOCOL_BINARY, left.selectedProtocol.await())
        assertEquals(MwaReflectorServer.SUBPROTOCOL_BINARY, right.selectedProtocol.await())

        val payload = byteArrayOf(9, 8, 7, 6)
        left.socket.await().send(payload.toByteString())
        assertArrayEquals(payload, withTimeout(5_000) { right.logicalMessages.receive() })

        val response = byteArrayOf(4, 3, 2, 1)
        right.socket.await().send(response.toByteString())
        assertArrayEquals(response, withTimeout(5_000) { left.logicalMessages.receive() })
        assertEquals(2, server.stats().totalFramesReflected)
    }

    @Test
    fun `converts between binary and base64 text peers`() = runBlocking {
        val server = startServer()
        val id = reflectorId(byteArrayOf(5, 6, 7, 8))
        val binary = connect(server.localPort, id, MwaReflectorServer.SUBPROTOCOL_BINARY)
        val text = connect(server.localPort, id, MwaReflectorServer.SUBPROTOCOL_BASE64)

        withTimeout(5_000) {
            binary.reflected.await()
            text.reflected.await()
        }
        assertEquals(MwaReflectorServer.SUBPROTOCOL_BASE64, text.selectedProtocol.await())

        val toText = byteArrayOf(1, 3, 5, 7)
        binary.socket.await().send(toText.toByteString())
        assertArrayEquals(toText, withTimeout(5_000) { text.logicalMessages.receive() })
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(toText), text.rawTextMessages.receive())

        val toBinary = byteArrayOf(2, 4, 6, 8)
        text.socket.await().send(Base64.getUrlEncoder().withoutPadding().encodeToString(toBinary))
        assertArrayEquals(toBinary, withTimeout(5_000) { binary.logicalMessages.receive() })
    }

    @Test
    fun `rejects reflect websocket without id`() {
        val server = startServer()
        Socket("127.0.0.1", server.localPort).use { socket ->
            socket.getOutputStream().write(
                "GET /reflect HTTP/1.1\r\n".toByteArray() +
                    "Host: 127.0.0.1\r\n".toByteArray() +
                    "Upgrade: websocket\r\n".toByteArray() +
                    "Connection: Upgrade\r\n".toByteArray() +
                    "Sec-WebSocket-Version: 13\r\n".toByteArray() +
                    "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n".toByteArray() +
                    "Sec-WebSocket-Protocol: ${MwaReflectorServer.SUBPROTOCOL_BINARY}\r\n\r\n".toByteArray()
            )
            socket.getOutputStream().flush()
            val response = socket.getInputStream().bufferedReader().readLine()
            assertTrue(response.contains("400 Bad Request"))
        }
    }

    private fun startServer(): MwaReflectorServer {
        val server = MwaReflectorServer(
            MwaReflectorServer.Config(
                bindAddress = InetAddress.getByName("127.0.0.1"),
                port = 0,
                pendingTtlMs = 5_000L
            )
        )
        server.start()
        servers += server
        return server
    }

    private fun connect(port: Int, id: String, protocol: String): TestSocket {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        clients += client
        val listener = TestSocket()
        val request = Request.Builder()
            .url("ws://127.0.0.1:$port/reflect?id=$id")
            .header("Sec-WebSocket-Protocol", protocol)
            .build()
        client.newWebSocket(request, listener)
        return listener
    }

    private fun reflectorId(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private class TestSocket : WebSocketListener() {
        val socket = CompletableDeferred<WebSocket>()
        val selectedProtocol = CompletableDeferred<String>()
        val reflected = CompletableDeferred<Unit>()
        val logicalMessages = Channel<ByteArray>(Channel.BUFFERED)
        val rawTextMessages = Channel<String>(Channel.BUFFERED)

        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket.complete(webSocket)
            selectedProtocol.complete(response.header("Sec-WebSocket-Protocol").orEmpty())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.isEmpty()) {
                reflected.complete(Unit)
                return
            }
            rawTextMessages.trySend(text)
            logicalMessages.trySend(Base64.getUrlDecoder().decode(text))
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val data = bytes.toByteArray()
            if (data.isEmpty()) {
                reflected.complete(Unit)
                return
            }
            logicalMessages.trySend(data)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!socket.isCompleted) socket.completeExceptionally(t)
            if (!reflected.isCompleted) reflected.completeExceptionally(t)
            logicalMessages.close(t)
            rawTextMessages.close(t)
        }
    }
}