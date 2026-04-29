package com.selenus.artemis.solanapay

import com.selenus.artemis.runtime.Pubkey

/**
 * Solana Pay URI Builder/Parser.
 *
 * Supports the Solana Pay specification:
 * solana:<recipient>?amount=<amount>&spl-token=<mint>&reference=<ref>&label=<label>&message=<msg>&memo=<memo>
 */
object SolanaPayUri {

  data class Request(
    val recipient: Pubkey,
    val amount: String? = null,
    val splToken: Pubkey? = null,
    val reference: List<Pubkey> = emptyList(),
    val label: String? = null,
    val message: String? = null,
    val memo: String? = null
  )

  fun parse(uri: String): Request {
    if (!uri.startsWith("solana:")) throw IllegalArgumentException("Invalid scheme")
    
    val parts = uri.substring(7).split("?")
    val recipient = Pubkey.fromBase58(parts[0])
    
    if (parts.size == 1) return Request(recipient)
    
    val params = parts[1].split("&").associate {
      val kv = it.split("=", limit = 2)
      kv[0] to percentDecode(kv[1])
    }

    val refs = ArrayList<Pubkey>()
    // Safe invariant: `containsKey("reference")` guarantees `params["reference"]` is non-null here.
    params["reference"]?.let { refs.add(Pubkey.fromBase58(it)) }

    return Request(
      recipient = recipient,
      amount = params["amount"],
      splToken = params["spl-token"]?.let { Pubkey.fromBase58(it) },
      reference = refs,
      label = params["label"],
      message = params["message"],
      memo = params["memo"]
    )
  }

  fun build(req: Request): String {
    val sb = StringBuilder("solana:${req.recipient}")
    val params = ArrayList<String>()
    
    req.amount?.let { params.add("amount=$it") }
    req.splToken?.let { params.add("spl-token=$it") }
    req.reference.forEach { params.add("reference=$it") }
    req.label?.let { params.add("label=${percentEncode(it)}") }
    req.message?.let { params.add("message=${percentEncode(it)}") }
    req.memo?.let { params.add("memo=${percentEncode(it)}") }
    
    if (params.isNotEmpty()) {
      sb.append("?")
      sb.append(params.joinToString("&"))
    }
    
    return sb.toString()
  }
}

internal fun percentEncode(s: String): String {
    val bytes = s.encodeToByteArray()
    val sb = StringBuilder(bytes.size)
    for (b in bytes) {
        val c = b.toInt() and 0xFF
        if (c.toChar().isLetterOrDigit() || c.toChar() in "-_.~") {
            sb.append(c.toChar())
        } else {
            sb.append('%')
            sb.append("0123456789ABCDEF"[c ushr 4])
            sb.append("0123456789ABCDEF"[c and 0x0F])
        }
    }
    return sb.toString()
}

internal fun percentDecode(s: String): String {
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < s.length) {
        if (s[i] == '%' && i + 2 < s.length) {
            val hi = s[i + 1].digitToIntOrNull(16)
            val lo = s[i + 2].digitToIntOrNull(16)
            if (hi != null && lo != null) {
                bytes.add(((hi shl 4) or lo).toByte())
                i += 3
                continue
            }
        }
        if (s[i] == '+') {
            bytes.add(' '.code.toByte())
        } else {
            bytes.add(s[i].code.toByte())
        }
        i++
    }
    return bytes.toByteArray().decodeToString()
}
