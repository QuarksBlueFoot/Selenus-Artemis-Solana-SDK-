package com.selenus.artemis.wallet.mwa.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.Scanner

/**
 * Public so the wallet-side walletlib (a separate Gradle module)
 * round-trip test can drive the dApp end. Production callers go
 * through [MwaSession] which hides this seam.
 */
interface MwaTransport {
    fun send(data: ByteArray)
    fun close(code: Int, reason: String)
    val incoming: Channel<ByteArray>
}

/**
 * Local WebSocket server used by the MWA local-association flow.
 *
 * Hardened against three common failure modes:
 * - Binds to the loopback interface only so other apps on the device cannot
 *   inject connections into the association port.
 * - Rejects WebSocket upgrade requests whose `Origin` header is not
 *   `null` / `solana-wallet:` - MWA wallets don't send browser origins, so
 *   a non-empty origin almost always signals a wrong-protocol peer.
 * - Sends periodic ping frames and enforces a pong timeout so idle stale
 *   sockets tear down instead of hanging the dapp indefinitely.
 */
class MwaWebSocketServer {
    private var serverSocket: ServerSocket? = null

    fun bind(port: Int = 0): Int {
        // Loopback-only bind: the MWA association URI is consumed on-device,
        // so binding 0.0.0.0 would only widen the attack surface without
        // serving any legitimate caller.
        val loopback = InetAddress.getByName("127.0.0.1")
        val s = ServerSocket(port, /* backlog */ 1, loopback)
        serverSocket = s
        return s.localPort
    }

    suspend fun accept(timeoutMs: Long): MwaTransport = withContext(Dispatchers.IO) {
        val s = serverSocket ?: throw IllegalStateException("Not bound")
        s.soTimeout = timeoutMs.toInt()
        val client = s.accept()
        // Post-accept read timeout: the connected wallet is expected to
        // keep the channel lively (via PONGs + RPC replies). An accepted
        // socket with no read timeout could hang the reader thread
        // indefinitely if the peer silently vanished. 60s strikes a
        // balance with the 45s PING/PONG window.
        client.soTimeout = 60_000
        // Read the upgrade request and validate the Origin before completing
        // the 101 Switching Protocols handshake.
        doHandshake(client)
        val transport = SocketTransport(client)
        transport.startReader()
        transport.startKeepalive()
        transport
    }

    companion object {
        /**
         * Maximum decoded payload bytes per frame (or per reassembled message
         * when the peer fragments). The MWA wire message is an AES-GCM-wrapped
         * JSON-RPC envelope: real messages stay well under 64 KiB. 1 MiB is a
         * generous upper bound that still prevents a malicious peer from
         * claiming a 64-bit length and exhausting heap before we even read
         * any bytes.
         */
        const val MAX_FRAME_SIZE_BYTES: Int = 1 shl 20
    }

    fun close() {
        try { serverSocket?.close() } catch (_: Throwable) {}
    }

    private fun doHandshake(socket: Socket) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        val scanner = Scanner(input, "UTF-8")
        val data = scanner.useDelimiter("\\r\\n\\r\\n").next()

        val get = java.util.regex.Pattern.compile("^GET").matcher(data)
        if (!get.find()) throw IllegalStateException("Not a GET request")

        val match = java.util.regex.Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data)
        if (!match.find()) throw IllegalStateException("Missing Sec-WebSocket-Key")
        val key = match.group(1).trim()

        // Origin policy: MWA wallets running on-device do not advertise a
        // browser origin. Accept requests that either omit the header or
        // present the MWA scheme. Any other origin (e.g. `Origin: https://
        // attacker.com`) is rejected to prevent DNS-rebinding / cross-origin
        // browser-initiated attachments.
        val originMatch = java.util.regex.Pattern
            .compile("(?im)^Origin:\\s*(.*?)$").matcher(data)
        if (originMatch.find()) {
            val origin = originMatch.group(1).trim().lowercase()
            val allowed = origin.isEmpty() ||
                origin.startsWith("solana-wallet:") ||
                origin == "null"
            if (!allowed) {
                // 403 Forbidden, NO upgrade. Close the socket.
                val err = "HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n"
                output.write(err.toByteArray(Charsets.UTF_8))
                output.flush()
                socket.close()
                throw IllegalStateException("rejected non-MWA Origin: $origin")
            }
        }

        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
            "Connection: Upgrade\r\n" +
            "Upgrade: websocket\r\n" +
            "Sec-WebSocket-Accept: ${makeAcceptKey(key)}\r\n\r\n"

        output.write(response.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun makeAcceptKey(key: String): String {
        val guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val sha1 = MessageDigest.getInstance("SHA-1")
        return Base64.getEncoder().encodeToString(sha1.digest((key + guid).toByteArray()))
    }

    private class SocketTransport(private val socket: Socket) : MwaTransport {
        override val incoming = Channel<ByteArray>(Channel.BUFFERED)
        private val out = socket.getOutputStream()
        private val inp = socket.getInputStream()
        @Volatile private var running = true
        @Volatile private var lastPongAt: Long = System.currentTimeMillis()

        fun startReader() {
            Thread {
                try {
                    while (running && !socket.isClosed) {
                        readFrame()
                    }
                } catch (e: Exception) {
                    close(1001, "Error")
                }
            }.start()
        }

        /**
         * Periodic ping + pong deadline. Sends a ping every 15s and closes
         * the socket if no pong arrives within 45s. Prevents stale sockets
         * from wedging the dapp when the wallet app is force-killed without
         * a clean WebSocket close frame.
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
            }.apply {
                isDaemon = true
                start()
            }
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

        // Buffer for reassembling a fragmented data message. Continuation
        // frames (opcode 0x0) accumulate here; terminal frame (FIN=1)
        // emits the full payload and clears the buffer.
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

            // Reject outsized frames before allocating. An attacker could
            // claim len = 2^63-1 to DoS the reader; cap against a real
            // upper bound that still accommodates honest MWA messages.
            if (len < 0L || len > MAX_FRAME_SIZE_BYTES) {
                close(1009, "frame too large: $len")
                throw IllegalStateException("frame size $len exceeds cap $MAX_FRAME_SIZE_BYTES")
            }

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
                0x0 -> { // Continuation
                    if (fragmentOpcode == 0) {
                        close(1002, "continuation without starter")
                        throw IllegalStateException("continuation frame without leading data frame")
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
                0x1, 0x2 -> { // Text or Binary
                    if (fin) {
                        incoming.trySend(payload)
                    } else {
                        // Start of a fragmented message. Reset any partial
                        // state from a malformed prior fragment and begin
                        // accumulating. RFC 6455 forbids interleaving
                        // data frames from different messages so we do not
                        // need to track more than one in-flight stream.
                        if (fragmentOpcode != 0) {
                            close(1002, "interleaved fragments")
                            throw IllegalStateException("interleaved fragmented messages")
                        }
                        fragmentOpcode = opcode
                        fragmentBuffer.reset()
                        fragmentBuffer.write(payload)
                    }
                }
                0x8 -> { // Close
                    close(1000, "Remote close"); return
                }
                0x9 -> { // Ping -> respond Pong.
                    sendControl(opcode = 0xA, payload = payload)
                }
                0xA -> { // Pong
                    lastPongAt = System.currentTimeMillis()
                }
                else -> {
                    close(1002, "unknown opcode: $opcode")
                    throw IllegalStateException("unknown opcode $opcode")
                }
            }
        }

        private fun encodeFrame(data: ByteArray, opcode: Int): ByteArray {
            val len = data.size
            val headerLen = if (len <= 125) 2 else if (len <= 65535) 4 else 10
            val frame = ByteArray(headerLen + len)

            frame[0] = (0x80 or (opcode and 0x0F)).toByte() // FIN + opcode

            if (len <= 125) {
                frame[1] = len.toByte()
                System.arraycopy(data, 0, frame, 2, len)
            } else if (len <= 65535) {
                frame[1] = 126.toByte()
                frame[2] = ((len shr 8) and 0xFF).toByte()
                frame[3] = (len and 0xFF).toByte()
                System.arraycopy(data, 0, frame, 4, len)
            } else {
                frame[1] = 127.toByte()
                for (i in 0 until 8) {
                    frame[2 + i] = ((len.toLong() shr ((7 - i) * 8)) and 0xFF).toByte()
                }
                System.arraycopy(data, 0, frame, 10, len)
            }
            return frame
        }
    }
}
