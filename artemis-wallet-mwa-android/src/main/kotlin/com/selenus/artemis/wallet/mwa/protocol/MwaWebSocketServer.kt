package com.selenus.artemis.wallet.mwa.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.Scanner

internal interface MwaTransport {
  fun send(data: ByteArray)
  fun close(code: Int, reason: String)
  val incoming: Channel<ByteArray>
}

internal class MwaWebSocketServer {
  private var serverSocket: ServerSocket? = null

  fun bind(port: Int = 0): Int {
    val s = ServerSocket(port)
    serverSocket = s
    return s.localPort
  }

  suspend fun accept(timeoutMs: Long): MwaTransport = withContext(Dispatchers.IO) {
    val s = serverSocket ?: throw IllegalStateException("Not bound")
    s.soTimeout = timeoutMs.toInt()
    val client = s.accept()
    // Handshake
    doHandshake(client)
    
    // Create transport
    val transport = SocketTransport(client)
    transport.startReader()
    transport
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

    override fun send(data: ByteArray) {
      synchronized(out) {
        out.write(encodeFrame(data))
        out.flush()
      }
    }

    override fun close(code: Int, reason: String) {
      running = false
      try { socket.close() } catch (_: Throwable) {}
      incoming.close()
    }

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
        len = ((l1 shl 8) or l2).toLong()
      } else if (len == 127L) {
        // 8 bytes
        var l = 0L
        for (i in 0 until 8) {
          l = (l shl 8) or inp.read().toLong()
        }
        len = l
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

      if (opcode == 8) { // Close
        close(1000, "Remote close")
        return
      }
      
      if (opcode == 1 || opcode == 2) { // Text or Binary
        incoming.trySend(payload)
      }
    }

    private fun encodeFrame(data: ByteArray): ByteArray {
      val len = data.size
      val headerLen = if (len <= 125) 2 else if (len <= 65535) 4 else 10
      val frame = ByteArray(headerLen + len)
      
      frame[0] = 0x82.toByte() // FIN + Binary
      
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
        // write 8 bytes length
        for (i in 0 until 8) {
          frame[2 + i] = ((len.toLong() shr ((7 - i) * 8)) and 0xFF).toByte()
        }
        System.arraycopy(data, 0, frame, 10, len)
      }
      return frame
    }
  }
}
