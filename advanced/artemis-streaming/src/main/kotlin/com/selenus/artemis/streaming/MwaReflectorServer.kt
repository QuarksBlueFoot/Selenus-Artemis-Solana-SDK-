package com.selenus.artemis.streaming

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Standalone Mobile Wallet Adapter remote-association reflector.
 *
 * The service accepts WebSocket upgrades at `/reflect?id=<base64url-id>`, pairs
 * two peers with the same id, sends an empty APP_PING to both sides, and relays
 * opaque MWA frames until either peer closes. It never decrypts or interprets
 * the MWA HELLO / JSON-RPC payloads.
 */
class MwaReflectorServer(
    private val config: Config = Config()
) : AutoCloseable {

    data class Config(
        val bindAddress: InetAddress = InetAddress.getByName("0.0.0.0"),
        val port: Int = 0,
        val maxFrameSizeBytes: Int = 4 * 1024,
        val maxReflectorIdBytes: Int = 64,
        val maxPendingAssociations: Int = 2_048,
        val pendingTtlMs: Long = 120_000L,
        val socketReadTimeoutMs: Int = 60_000,
        val pingIntervalMs: Long = 15_000L,
        val pongTimeoutMs: Long = 45_000L,
        val allowedOrigins: Set<String> = emptySet()
    )

    data class Stats(
        val pendingAssociations: Int,
        val activePeers: Int,
        val activePairs: Long,
        val totalConnections: Long,
        val totalPairs: Long,
        val totalFramesReflected: Long,
        val rejectedConnections: Long
    )

    private val closed = AtomicBoolean(false)
    private val pendingLock = Any()
    private val pending = LinkedHashMap<String, Peer>()
    private val peers = ConcurrentHashMap.newKeySet<Peer>()
    private val totalConnections = AtomicLong(0)
    private val totalPairs = AtomicLong(0)
    private val totalFramesReflected = AtomicLong(0)
    private val rejectedConnections = AtomicLong(0)

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    val localPort: Int
        get() = serverSocket?.localPort ?: -1

    fun start(): Int {
        check(serverSocket == null) { "reflector already started" }
        require(config.maxFrameSizeBytes > 0) { "maxFrameSizeBytes must be positive" }
        require(config.maxReflectorIdBytes > 0) { "maxReflectorIdBytes must be positive" }
        val socket = ServerSocket(config.port, 128, config.bindAddress)
        serverSocket = socket
        acceptThread = thread(
            start = true,
            isDaemon = true,
            name = "artemis-mwa-reflector-accept"
        ) {
            acceptLoop(socket)
        }
        return socket.localPort
    }

    fun stats(): Stats = Stats(
        pendingAssociations = synchronized(pendingLock) { pending.size },
        activePeers = peers.count { !it.isClosed },
        activePairs = peers.count { !it.isClosed && it.partner != null }.toLong() / 2,
        totalConnections = totalConnections.get(),
        totalPairs = totalPairs.get(),
        totalFramesReflected = totalFramesReflected.get(),
        rejectedConnections = rejectedConnections.get()
    )

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try { serverSocket?.close() } catch (_: Throwable) {}
        synchronized(pendingLock) {
            pending.values.forEach { it.close(1001, "server shutting down", cascade = false) }
            pending.clear()
        }
        peers.forEach { it.close(1001, "server shutting down", cascade = false) }
        peers.clear()
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!closed.get()) {
            val socket = try {
                server.accept()
            } catch (_: Throwable) {
                if (!closed.get()) rejectedConnections.incrementAndGet()
                return
            }
            totalConnections.incrementAndGet()
            thread(
                start = true,
                isDaemon = true,
                name = "artemis-mwa-reflector-client"
            ) {
                handleClient(socket)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = config.socketReadTimeoutMs
            val request = readHttpRequest(socket)
            if (request.path == "/healthz") {
                writeHttpResponse(socket, 200, "OK", "ok\n")
                return
            }
            val associationId = validateAssociationId(request)
            val protocol = negotiateProtocol(request)
            writeUpgradeResponse(socket, request.webSocketKey, protocol)

            val peer = Peer(socket, associationId, protocol)
            peers += peer
            val partner = register(peer)
            if (peer.isClosed) return
            if (partner == null) {
                if (!peer.awaitPair(config.pendingTtlMs)) {
                    removePending(peer)
                    peer.close(1001, "association timed out", cascade = false)
                }
            } else {
                pair(peer, partner)
            }
        } catch (e: HttpReject) {
            rejectedConnections.incrementAndGet()
            writeHttpResponse(socket, e.status, e.reason, e.body)
        } catch (_: Throwable) {
            rejectedConnections.incrementAndGet()
            try { socket.close() } catch (_: Throwable) {}
        }
    }

    private fun register(peer: Peer): Peer? = synchronized(pendingLock) {
        cleanupExpiredPendingLocked()
        val existing = pending.remove(peer.associationId)
        if (existing == null || existing.isClosed) {
            if (pending.size >= config.maxPendingAssociations) {
                rejectedConnections.incrementAndGet()
                peer.close(1013, "too many pending associations", cascade = false)
                return null
            }
            pending[peer.associationId] = peer
            null
        } else {
            existing
        }
    }

    private fun pair(a: Peer, b: Peer) {
        a.partner = b
        b.partner = a
        a.markPaired()
        b.markPaired()
        totalPairs.incrementAndGet()
        a.sendAppPing()
        b.sendAppPing()
        a.startReader()
        b.startReader()
        a.startKeepalive()
        b.startKeepalive()
    }

    private fun removePending(peer: Peer) = synchronized(pendingLock) {
        if (pending[peer.associationId] === peer) pending.remove(peer.associationId)
    }

    private fun cleanupExpiredPendingLocked() {
        val now = System.currentTimeMillis()
        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            val peer = iterator.next().value
            if (peer.isClosed || now - peer.createdAtMs > config.pendingTtlMs) {
                iterator.remove()
                peer.close(1001, "association timed out", cascade = false)
            }
        }
    }

    private fun validateAssociationId(request: HttpRequest): String {
        if (request.path != "/reflect") {
            throw HttpReject(404, "Not Found", "expected /reflect\n")
        }
        val rawId = request.query["id"]?.takeIf { it.isNotBlank() }
            ?: throw HttpReject(400, "Bad Request", "missing id\n")
        val decoded = try {
            Base64.getUrlDecoder().decode(rawId)
        } catch (_: IllegalArgumentException) {
            throw HttpReject(400, "Bad Request", "id must be base64url\n")
        }
        if (decoded.isEmpty() || decoded.size > config.maxReflectorIdBytes) {
            throw HttpReject(400, "Bad Request", "invalid id length\n")
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(decoded)
    }

    private fun negotiateProtocol(request: HttpRequest): ReflectorProtocol {
        val requested = request.headers["sec-websocket-protocol"].orEmpty()
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (token in requested) {
            when (token) {
                SUBPROTOCOL_BINARY -> return ReflectorProtocol.Binary
                SUBPROTOCOL_BASE64 -> return ReflectorProtocol.Base64Text
            }
        }
        throw HttpReject(400, "Bad Request", "missing supported MWA reflector subprotocol\n")
    }

    private fun readHttpRequest(socket: Socket): HttpRequest {
        val raw = readHeaders(socket)
        val lines = raw.split("\r\n")
        val requestLine = lines.firstOrNull().orEmpty().split(' ')
        if (requestLine.size < 3 || !requestLine[0].equals("GET", ignoreCase = true)) {
            throw HttpReject(405, "Method Not Allowed", "GET required\n")
        }

        val headers = LinkedHashMap<String, MutableList<String>>()
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase(Locale.US)
            val value = line.substring(colon + 1).trim()
            headers.getOrPut(name) { mutableListOf() } += value
        }

        val target = requestLine[1]
        val uri = try { URI(target) } catch (_: Throwable) {
            throw HttpReject(400, "Bad Request", "invalid request target\n")
        }
        val path = uri.path ?: target.substringBefore('?')
        val query = parseQuery(uri.rawQuery ?: target.substringAfter('?', missingDelimiterValue = ""))
        val key = headers["sec-websocket-key"]?.lastOrNull()?.trim().orEmpty()
        val version = headers["sec-websocket-version"]?.lastOrNull()?.trim().orEmpty()
        val upgrade = headers["upgrade"]?.lastOrNull()?.trim().orEmpty()
        if (path != "/healthz") {
            if (!upgrade.equals("websocket", ignoreCase = true)) {
                throw HttpReject(400, "Bad Request", "websocket upgrade required\n")
            }
            if (version != "13" || key.isBlank()) {
                throw HttpReject(400, "Bad Request", "invalid websocket handshake\n")
            }
            validateOrigin(headers)
        }
        return HttpRequest(path, query, headers, key)
    }

    private fun validateOrigin(headers: Map<String, List<String>>) {
        if (config.allowedOrigins.isEmpty()) return
        val origin = headers["origin"]?.lastOrNull()?.trim().orEmpty()
        if (origin.isBlank()) return
        if (origin !in config.allowedOrigins) {
            throw HttpReject(403, "Forbidden", "origin not allowed\n")
        }
    }

    private fun readHeaders(socket: Socket): String {
        val input = socket.getInputStream()
        val out = ByteArrayOutputStream()
        var matched = 0
        val marker = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        while (out.size() < MAX_HEADER_BYTES) {
            val b = input.read()
            if (b == -1) throw EOFException("socket closed before headers")
            out.write(b)
            matched = if (b.toByte() == marker[matched]) matched + 1 else if (b == '\r'.code) 1 else 0
            if (matched == marker.size) {
                return out.toString(StandardCharsets.UTF_8.name())
            }
        }
        throw HttpReject(431, "Request Header Fields Too Large", "headers too large\n")
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val eq = part.indexOf('=')
            val name = if (eq >= 0) part.substring(0, eq) else part
            val value = if (eq >= 0) part.substring(eq + 1) else ""
            URLDecoder.decode(name, StandardCharsets.UTF_8.name()) to
                URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.toMap()
    }

    private fun writeUpgradeResponse(socket: Socket, key: String, protocol: ReflectorProtocol) {
        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
            "Connection: Upgrade\r\n" +
            "Upgrade: websocket\r\n" +
            "Sec-WebSocket-Accept: ${makeAcceptKey(key)}\r\n" +
            "Sec-WebSocket-Protocol: ${protocol.wireValue}\r\n" +
            "\r\n"
        socket.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
        socket.getOutputStream().flush()
    }

    private fun writeHttpResponse(socket: Socket, status: Int, reason: String, body: String) {
        try {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val response = "HTTP/1.1 $status $reason\r\n" +
                "Connection: close\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "\r\n"
            socket.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
            socket.getOutputStream().write(bytes)
            socket.getOutputStream().flush()
        } catch (_: Throwable) {
        } finally {
            try { socket.close() } catch (_: Throwable) {}
        }
    }

    private fun makeAcceptKey(key: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
        return Base64.getEncoder().encodeToString(
            sha1.digest((key + WEBSOCKET_GUID).toByteArray(StandardCharsets.US_ASCII))
        )
    }

    private inner class Peer(
        private val socket: Socket,
        val associationId: String,
        private val protocol: ReflectorProtocol
    ) {
        val createdAtMs: Long = System.currentTimeMillis()
        private val closed = AtomicBoolean(false)
        private val readerStarted = AtomicBoolean(false)
        private val keepaliveStarted = AtomicBoolean(false)
        private val pairedLatch = CountDownLatch(1)
        private val output = socket.getOutputStream()
        private val input = socket.getInputStream()
        private var fragmentOpcode = 0
        private val fragmentBuffer = ByteArrayOutputStream()

        @Volatile
        var partner: Peer? = null

        @Volatile
        private var lastPongAtMs: Long = System.currentTimeMillis()

        val isClosed: Boolean get() = closed.get() || socket.isClosed

        fun awaitPair(timeoutMs: Long): Boolean = pairedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

        fun markPaired() = pairedLatch.countDown()

        fun sendAppPing() {
            val opcode = if (protocol == ReflectorProtocol.Base64Text) OPCODE_TEXT else OPCODE_BINARY
            sendFrame(ByteArray(0), opcode)
        }

        fun startReader() {
            if (!readerStarted.compareAndSet(false, true)) return
            thread(start = true, isDaemon = true, name = "artemis-mwa-reflector-relay") {
                try {
                    while (!isClosed) {
                        when (val frame = readFrame()) {
                            is WsFrame.Data -> relay(frame)
                            is WsFrame.Close -> {
                                close(1000, "peer closed")
                                return@thread
                            }
                            is WsFrame.Ping -> sendControl(OPCODE_PONG, frame.payload)
                            is WsFrame.Pong -> lastPongAtMs = System.currentTimeMillis()
                        }
                    }
                } catch (_: Throwable) {
                    close(1001, "read error")
                }
            }
        }

        fun startKeepalive() {
            if (!keepaliveStarted.compareAndSet(false, true)) return
            thread(start = true, isDaemon = true, name = "artemis-mwa-reflector-keepalive") {
                while (!isClosed) {
                    try {
                        Thread.sleep(config.pingIntervalMs)
                        if (isClosed) return@thread
                        sendControl(OPCODE_PING, "ping".toByteArray(StandardCharsets.UTF_8))
                        if (System.currentTimeMillis() - lastPongAtMs > config.pongTimeoutMs) {
                            close(1011, "pong timeout")
                            return@thread
                        }
                    } catch (_: InterruptedException) {
                        return@thread
                    } catch (_: Throwable) {
                        close(1001, "keepalive error")
                        return@thread
                    }
                }
            }
        }

        fun close(code: Int, reason: String, cascade: Boolean = true) {
            if (!closed.compareAndSet(false, true)) return
            removePending(this)
            runCatching { sendClose(code, reason) }
            runCatching { socket.close() }
            peers.remove(this)
            pairedLatch.countDown()
            if (cascade) partner?.close(1001, "paired peer closed", cascade = false)
        }

        private fun relay(frame: WsFrame.Data) {
            val logicalPayload = when (protocol) {
                ReflectorProtocol.Binary -> {
                    if (frame.opcode != OPCODE_BINARY) {
                        close(1003, "binary subprotocol requires binary frames")
                        return
                    }
                    frame.payload
                }
                ReflectorProtocol.Base64Text -> {
                    if (frame.opcode != OPCODE_TEXT) {
                        close(1003, "base64 subprotocol requires text frames")
                        return
                    }
                    val text = frame.payload.toString(StandardCharsets.UTF_8)
                    try {
                        Base64.getUrlDecoder().decode(text)
                    } catch (_: IllegalArgumentException) {
                        close(1007, "invalid base64 payload")
                        return
                    }
                }
            }
            if (logicalPayload.isEmpty() || logicalPayload.size > config.maxFrameSizeBytes) {
                close(1009, "frame too large")
                return
            }
            val target = partner ?: run {
                close(1001, "no paired peer")
                return
            }
            target.sendLogical(logicalPayload)
            totalFramesReflected.incrementAndGet()
        }

        private fun sendLogical(payload: ByteArray) {
            when (protocol) {
                ReflectorProtocol.Binary -> sendFrame(payload, OPCODE_BINARY)
                ReflectorProtocol.Base64Text -> {
                    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
                    sendFrame(encoded.toByteArray(StandardCharsets.UTF_8), OPCODE_TEXT)
                }
            }
        }

        private fun sendControl(opcode: Int, payload: ByteArray) {
            require(payload.size <= 125) { "control frames cannot exceed 125 bytes" }
            sendFrame(payload, opcode)
        }

        private fun sendClose(code: Int, reason: String) {
            val reasonBytes = reason.toByteArray(StandardCharsets.UTF_8).take(123).toByteArray()
            val payload = ByteArray(2 + reasonBytes.size)
            payload[0] = ((code ushr 8) and 0xff).toByte()
            payload[1] = (code and 0xff).toByte()
            reasonBytes.copyInto(payload, 2)
            sendFrame(payload, OPCODE_CLOSE)
        }

        private fun sendFrame(payload: ByteArray, opcode: Int) {
            synchronized(output) {
                output.write(encodeFrame(payload, opcode))
                output.flush()
            }
        }

        private fun readFrame(): WsFrame {
            val first = input.read()
            if (first == -1) throw EOFException()
            val fin = (first and 0x80) != 0
            val opcode = first and 0x0f

            val second = input.read()
            if (second == -1) throw EOFException()
            val masked = (second and 0x80) != 0
            var length = (second and 0x7f).toLong()
            if (length == 126L) {
                val b1 = input.read()
                val b2 = input.read()
                if (b1 == -1 || b2 == -1) throw EOFException()
                length = ((b1 shl 8) or b2).toLong()
            } else if (length == 127L) {
                var longLength = 0L
                repeat(8) {
                    val b = input.read()
                    if (b == -1) throw EOFException()
                    longLength = (longLength shl 8) or b.toLong()
                }
                length = longLength
            }

            if (length < 0 || length > maxWireFrameSize()) {
                close(1009, "frame too large")
                throw IllegalStateException("frame size $length exceeds cap")
            }

            val maskKey = ByteArray(4)
            if (masked && input.read(maskKey) != 4) throw EOFException()
            val payload = ByteArray(length.toInt())
            var offset = 0
            while (offset < payload.size) {
                val read = input.read(payload, offset, payload.size - offset)
                if (read == -1) throw EOFException()
                offset += read
            }
            if (masked) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
                }
            }

            return when (opcode) {
                OPCODE_CONTINUATION -> readContinuation(fin, payload)
                OPCODE_TEXT, OPCODE_BINARY -> readDataStart(fin, opcode, payload)
                OPCODE_CLOSE -> WsFrame.Close
                OPCODE_PING -> WsFrame.Ping(payload)
                OPCODE_PONG -> WsFrame.Pong
                else -> {
                    close(1002, "unknown opcode")
                    throw IllegalStateException("unknown opcode $opcode")
                }
            }
        }

        private fun readDataStart(fin: Boolean, opcode: Int, payload: ByteArray): WsFrame {
            if (fin) return WsFrame.Data(opcode, payload)
            if (fragmentOpcode != 0) {
                close(1002, "interleaved fragments")
                throw IllegalStateException("interleaved fragments")
            }
            fragmentOpcode = opcode
            fragmentBuffer.reset()
            fragmentBuffer.write(payload)
            return readFrame()
        }

        private fun readContinuation(fin: Boolean, payload: ByteArray): WsFrame {
            if (fragmentOpcode == 0) {
                close(1002, "continuation without starter")
                throw IllegalStateException("continuation without starter")
            }
            if (fragmentBuffer.size() + payload.size > maxWireFrameSize()) {
                close(1009, "reassembled message too large")
                throw IllegalStateException("reassembled message too large")
            }
            fragmentBuffer.write(payload)
            if (!fin) return readFrame()
            val opcode = fragmentOpcode
            val data = fragmentBuffer.toByteArray()
            fragmentOpcode = 0
            fragmentBuffer.reset()
            return WsFrame.Data(opcode, data)
        }

        private fun maxWireFrameSize(): Int = when (protocol) {
            ReflectorProtocol.Binary -> config.maxFrameSizeBytes
            ReflectorProtocol.Base64Text -> ((config.maxFrameSizeBytes + 2) / 3) * 4
        }
    }

    private enum class ReflectorProtocol(val wireValue: String) {
        Binary(SUBPROTOCOL_BINARY),
        Base64Text(SUBPROTOCOL_BASE64)
    }

    private sealed class WsFrame {
        data class Data(val opcode: Int, val payload: ByteArray) : WsFrame()
        data class Ping(val payload: ByteArray) : WsFrame()
        object Pong : WsFrame()
        object Close : WsFrame()
    }

    private data class HttpRequest(
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, List<String>>,
        val webSocketKey: String
    )

    private class HttpReject(
        val status: Int,
        val reason: String,
        val body: String
    ) : Exception(body)

    companion object {
        const val SUBPROTOCOL_BINARY: String = "com.solana.mobilewalletadapter.v1"
        const val SUBPROTOCOL_BASE64: String = "com.solana.mobilewalletadapter.v1.base64"

        private const val MAX_HEADER_BYTES = 8 * 1024
        private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private const val OPCODE_CONTINUATION = 0x0
        private const val OPCODE_TEXT = 0x1
        private const val OPCODE_BINARY = 0x2
        private const val OPCODE_CLOSE = 0x8
        private const val OPCODE_PING = 0x9
        private const val OPCODE_PONG = 0xA

        private fun encodeFrame(payload: ByteArray, opcode: Int): ByteArray {
            val headerLength = when {
                payload.size <= 125 -> 2
                payload.size <= 65_535 -> 4
                else -> 10
            }
            val frame = ByteArray(headerLength + payload.size)
            frame[0] = (0x80 or (opcode and 0x0f)).toByte()
            when {
                payload.size <= 125 -> {
                    frame[1] = payload.size.toByte()
                    payload.copyInto(frame, 2)
                }
                payload.size <= 65_535 -> {
                    frame[1] = 126.toByte()
                    frame[2] = ((payload.size ushr 8) and 0xff).toByte()
                    frame[3] = (payload.size and 0xff).toByte()
                    payload.copyInto(frame, 4)
                }
                else -> {
                    frame[1] = 127.toByte()
                    val length = payload.size.toLong()
                    for (i in 0 until 8) {
                        frame[2 + i] = ((length ushr ((7 - i) * 8)) and 0xff).toByte()
                    }
                    payload.copyInto(frame, 10)
                }
            }
            return frame
        }
    }
}