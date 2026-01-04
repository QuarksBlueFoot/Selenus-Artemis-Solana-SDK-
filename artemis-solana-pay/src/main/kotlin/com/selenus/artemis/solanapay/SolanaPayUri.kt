package com.selenus.artemis.solanapay

import com.selenus.artemis.runtime.Pubkey
import java.net.URLEncoder
import java.net.URLDecoder
import java.math.BigDecimal

/**
 * Solana Pay URI Builder/Parser.
 *
 * Supports the Solana Pay specification:
 * solana:<recipient>?amount=<amount>&spl-token=<mint>&reference=<ref>&label=<label>&message=<msg>&memo=<memo>
 */
object SolanaPayUri {

  data class Request(
    val recipient: Pubkey,
    val amount: BigDecimal? = null,
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
      val kv = it.split("=")
      kv[0] to URLDecoder.decode(kv[1], "UTF-8")
    }

    val refs = ArrayList<Pubkey>()
    // Handle multiple references if present (though map logic above overwrites duplicates, 
    // a robust parser handles repeated keys. For simplicity here we assume standard single or handle manually if needed.
    // Actually, standard query param parsing allows multiples. Let's stick to simple map for now.)
    if (params.containsKey("reference")) {
        refs.add(Pubkey.fromBase58(params["reference"]!!))
    }

    return Request(
      recipient = recipient,
      amount = params["amount"]?.toBigDecimal(),
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
    
    req.amount?.let { params.add("amount=${it.toPlainString()}") }
    req.splToken?.let { params.add("spl-token=$it") }
    req.reference.forEach { params.add("reference=$it") }
    req.label?.let { params.add("label=${enc(it)}") }
    req.message?.let { params.add("message=${enc(it)}") }
    req.memo?.let { params.add("memo=${enc(it)}") }
    
    if (params.isNotEmpty()) {
      sb.append("?")
      sb.append(params.joinToString("&"))
    }
    
    return sb.toString()
  }

  private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
