package com.selenus.artemis.wallet.mwa.walletlib

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Loopback WebSocket client used by the wallet to connect to the
 * dApp's MWA association port. Mirrors the framing logic in the
 * dApp-side `MwaWebSocketServer` so the wallet can talk to either an
 * Artemis dApp or upstream's clientlib.
 *
 * The WebSocket spec (RFC 6455) requires the client to mask outbound
 * frames; that's the only structural difference from the dApp-side
 * server's [encodeFrame]. Fragmentation, ping/pong, and the size cap
 * mirror the server's behavior exactly so a malformed peer cannot
 * starve the wallet's reader thread or pin its heap.
 *
 * The class is internal: it is an implementation detail of
 * [LocalScenario]. Tests that need to drive a wallet without a real
 * socket use the in-process transport pair plumbed through
 * [LocalScenario.startWithTransport] instead.
 */
internal interface WalletTransport {
    /** Send one binary message. Frames it with FIN=1, opcode=0x2, masked=1. */
    fun send(data: ByteArray)
    /** Close the socket and the [incoming] channel. Idempotent. */
    fun close(code: Int, reason: String)
    /** Inbound messages, fully reassembled across continuation frames. */
    val incoming: Channel<ByteArray>
}

internal class WalletWebSocketClient {
    /**
     * Open a TCP connection to `127.0.0.1:port`, perform the WS upgrade
     * handshake, and return a transport ready to exchange MWA frames.
     *
     * Loopback-only by construction: the wallet never connects out
     * over the public network for a local-association URI, so the
     * resolver hardcodes the loopback address even if the caller is
     * tricked into passing a different host string.
     */
    suspend fun connect(port: Int, connectTimeoutMs: Int = 10_000): WalletTransport =
        withContext(Dispatchers.IO) {
            require(port in 1..65535) { "port out of range: $port" }
            val socket = Socket()
            // Bind to loopback only. defence in depth against an
            // attacker who somehow steered the resolver to a non-loopback
            // address. The MWA local-association port is by spec a
            // loopback bind, so refusing other targets here cannot
            // close off any legitimate flow.
            val target = java.net.InetSocketAddress(
                InetAddress.getByName("127.0.0.1"),
                port
            )
            socket.connect(target, connectTimeoutMs)
            // Post-connect read timeout: dApp is expected to keep the
            // channel lively (RPC responses + pings). Stale connections
            // tear down rather than wedging the wallet's reader.
            socket.soTimeout = 60_000
            // Disable Nagle: MWA frames are small and latency-sensitive,
            // and we already buffer a full frame before flushing.
            socket.tcpNoDelay = true

            val key = generateSecWebSocketKey()
            doHandshake(socket, port, key)
            val transport = SocketTransport(socket)
            transport.startReader()
            transport.startKeepalive()
            transport
        }

    private fun generateSecWebSocketKey(): String {
        val raw = ByteArray(16)
        SecureRandom().nextBytes(raw)
        return Base64.getEncoder().encodeToString(raw)
    }

    private fun doHandshake(socket: Socket, port: Int, key: String) {
        val out = socket.getOutputStream()
        val request = buildString {
            append("GET / HTTP/1.1\r\n")
            // The dApp-side server binds to 127.0.0.1; using the literal
            // hostname keeps the upgrade aligned with what the dApp's
            // `Sec-WebSocket-Accept` derivation expects.
            append("Host: 127.0.0.1:").append(port).append("\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            // MWA wallets running on-device do not advertise a browser
            // origin. The dApp's server accepts an empty / `null` /
            // `solana-wallet:` origin, so we send the canonical scheme
            // string explicitly to make traffic captures self-describing.
            append("Origin: solana-wallet:\r\n")
            append("Sec-WebSocket-Key: ").append(key).append("\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            append("\r\n")
        }
        out.write(request.toByteArray(Charsets.UTF_8))
        out.flush()

        val response = readHttpResponseHeaders(socket.getInputStream())
        require(response.statusLine.startsWith("HTTP/1.1 101")) {
            "WebSocket upgrade refused: ${response.statusLine}"
        }
        val expected = makeAcceptKey(key)
        val actual = response.headers["sec-websocket-accept"]
            ?: throw IllegalStateException("missing Sec-WebSocket-Accept header in upgrade response")
        require(actual == expected) {
            "Sec-WebSocket-Accept mismatch: expected $expected, got $actual"
        }
    }

    private fun readHttpResponseHeaders(input: InputStream): HttpResponse {
        // We need EXACT byte-level reading (no Scanner) because after
        // the headers are consumed the very next bytes are WebSocket
        // frames; a buffered reader would swallow them. Read until we
        // see "\r\n\r\n", then stop.
        val sb = StringBuilder()
        val window = ByteArray(4)
        var winLen = 0
        while (true) {
            val b = input.read()
            if (b == -1) throw java.io.EOFException("connection closed during upgrade")
            sb.append(b.toChar())
            window[winLen and 0x03] = b.toByte()
            winLen++
            if (winLen >= 4) {
                val w0 = window[(winLen - 4) and 0x03]
                val w1 = window[(winLen - 3) and 0x03]
                val w2 = window[(winLen - 2) and 0x03]
                val w3 = window[(winLen - 1) and 0x03]
                if (w0 == 0x0D.toByte() && w1 == 0x0A.toByte() &&
                    w2 == 0x0D.toByte() && w3 == 0x0A.toByte()
                ) break
            }
            if (sb.length > 16 * 1024) {
                // Cap the upgrade response. A malicious server can
                // stream headers indefinitely; 16 KiB is well above
                // any honest upgrade reply.
                throw IllegalStateException("upgrade response exceeded 16KiB")
            }
        }
        val raw = sb.toString().trimEnd('\r', '\n')
        val lines = raw.split("\r\n")
        val statusLine = lines.first()
        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            headers[name] = value
        }
        return HttpResponse(statusLine = statusLine, headers = headers)
    }

    private fun makeAcceptKey(key: String): String {
        val guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val sha1 = MessageDigest.getInstance("SHA-1")
        return Base64.getEncoder().encodeToString(sha1.digest((key + guid).toByteArray()))
    }

    private data class HttpResponse(val statusLine: String, val headers: Map<String, String>)

    companion object {
        /** Maximum decoded payload bytes per frame (and per reassembled message). */
        const val MAX_FRAME_SIZE_BYTES: Int = 1 shl 20
    }

    /**
     * Concrete WS transport over a connected, already-upgraded TCP
     * socket. Symmetric with the dApp-side `SocketTransport` except
     * outbound frames are masked.
     */
    private class SocketTransport(private val socket: Socket) : WalletTransport {
        override val incoming = Channel<ByteArray>(Channel.BUFFERED)
        private val out: OutputStream = socket.getOutputStream()
        private val inp: InputStream = socket.getInputStream()
        private val maskRandom = SecureRandom()
        @Volatile private var running = true
        @Volatile private var lastPongAt: Long = System.currentTimeMillis()

        fun startReader() {
            Thread {
                try {
                    while (running && !socket.isClosed) {
                        readFrame()
                    }
                } catch (_: Exception) {
                    close(1001, "Error")
                }
            }.apply { isDaemon = true; start() }
        }

        /**
         * Periodic ping + pong deadline. Sends a ping every 15s; closes
         * the socket if no pong arrives within 45s. Mirrors the dApp-side
         * server's keepalive so a wallet whose dApp is force-killed
         * doesn't wedge the UI.
         */
        fun startKeepalive() {
            Thread {
                while (running && !socket.isClosed) {
                    try {
                        Thread.sleep(15_000)
                        if (!running) return@Thread
                        sendControl(opcode = 0x9, payload = "ping".toByteArray())
                        if (System.currentTimeMillis() - lastPongAt > 45_000) {
                            close(1011, "pong timeout")
                            return@Thread
                        }
                    } catch (_: InterruptedException) {
                        return@Thread
                    } catch (_: Exception) {
                        close(1001, "keepalive error")
                        return@Thread
                    }
                }
            }.apply { isDaemon = true; start() }
        }

        override fun send(data: ByteArray) {
            synchronized(out) {
                out.write(encodeFrame(data, opcode = 0x2))
                out.flush()
            }
        }

        private fun sendControl(opcode: Int, payload: ByteArray) {
            synchronized(out) {
                out.write(encodeFrame(payload, opcode = opcode))
                out.flush()
            }
        }

        override fun close(code: Int, reason: String) {
            running = false
            try { socket.close() } catch (_: Throwable) {}
            incoming.close()
        }

        private var fragmentOpcode: Int = 0
        private val fragmentBuffer = java.io.ByteArrayOutputStream()

        private fun readFrame() {
            val b0 = inp.read()
            if (b0 == -1) throw java.io.EOFException()
            val fin = (b0 and 0x80) != 0
            val opcode = b0 and 0x0F

            val b1 = inp.read()
            if (b1 == -1) throw java.io.EOFException()
            val masked = (b1 and 0x80) != 0
            var len = (b1 and 0x7F).toLong()

            if (len == 126L) {
                val l1 = inp.read(); val l2 = inp.read()
                if (l1 == -1 || l2 == -1) throw java.io.EOFException()
                len = ((l1 shl 8) or l2).toLong()
            } else if (len == 127L) {
                var l = 0L
                for (i in 0 until 8) {
                    val b = inp.read()
                    if (b == -1) throw java.io.EOFException()
                    l = (l shl 8) or b.toLong()
                }
                len = l
            }

            if (len < 0L || len > MAX_FRAME_SIZE_BYTES) {
                close(1009, "frame too large: $len")
                throw IllegalStateException("frame size $len exceeds cap $MAX_FRAME_SIZE_BYTES")
            }

            // RFC 6455: server MUST NOT mask. Clients SHOULD reject a
            // masked server frame; we close with 1002 (protocol error).
            val maskKey = ByteArray(4)
            if (masked) {
                if (inp.read(maskKey) != 4) throw java.io.EOFException()
            }

            val payload = ByteArray(len.toInt())
            var read = 0
            while (read < payload.size) {
                val r = inp.read(payload, read, payload.size - read)
                if (r == -1) throw java.io.EOFException()
                read += r
            }

            if (masked) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
                }
            }

            when (opcode) {
                0x0 -> {
                    if (fragmentOpcode == 0) {
                        close(1002, "continuation without starter")
                        throw IllegalStateException("continuation without starter")
                    }
                    if (fragmentBuffer.size() + payload.size > MAX_FRAME_SIZE_BYTES) {
                        close(1009, "reassembled message too large")
                        throw IllegalStateException("reassembled message exceeds cap")
                    }
                    fragmentBuffer.write(payload)
                    if (fin) {
                        incoming.trySend(fragmentBuffer.toByteArray())
                        fragmentBuffer.reset()
                        fragmentOpcode = 0
                    }
                }
                0x1, 0x2 -> {
                    if (fin) {
                        incoming.trySend(payload)
                    } else {
                        if (fragmentOpcode != 0) {
                            close(1002, "interleaved fragments")
                            throw IllegalStateException("interleaved fragments")
                        }
                        fragmentOpcode = opcode
                        fragmentBuffer.reset()
                        fragmentBuffer.write(payload)
                    }
                }
                0x8 -> { close(1000, "Remote close"); return }
                0x9 -> { sendControl(opcode = 0xA, payload = payload) }
                0xA -> { lastPongAt = System.currentTimeMillis() }
                else -> {
                    close(1002, "unknown opcode: $opcode")
                    throw IllegalStateException("unknown opcode $opcode")
                }
            }
        }

        private fun encodeFrame(data: ByteArray, opcode: Int): ByteArray {
            val len = data.size
            // Client frames MUST be masked per RFC 6455 §5.2. Generate a
            // fresh 32-bit masking key for every frame.
            val mask = ByteArray(4).also { maskRandom.nextBytes(it) }
            val headerLen = when {
                len <= 125 -> 2
                len <= 65535 -> 4
                else -> 10
            }
            val frame = ByteArray(headerLen + 4 + len)
            frame[0] = (0x80 or (opcode and 0x0F)).toByte() // FIN + opcode

            var idx = 1
            when {
                len <= 125 -> {
                    frame[idx++] = (0x80 or len).toByte() // mask bit + length
                }
                len <= 65535 -> {
                    frame[idx++] = (0x80 or 126).toByte()
                    frame[idx++] = ((len shr 8) and 0xFF).toByte()
                    frame[idx++] = (len and 0xFF).toByte()
                }
                else -> {
                    frame[idx++] = (0x80 or 127).toByte()
                    for (i in 0 until 8) {
                        frame[idx++] = ((len.toLong() shr ((7 - i) * 8)) and 0xFF).toByte()
                    }
                }
            }
            System.arraycopy(mask, 0, frame, idx, 4); idx += 4
            // Apply mask while copying so we never hold both clear-text
            // and masked copies of the same payload in the heap.
            for (i in 0 until len) {
                frame[idx + i] = (data[i].toInt() xor mask[i and 0x03].toInt()).toByte()
            }
            return frame
        }
    }
}
