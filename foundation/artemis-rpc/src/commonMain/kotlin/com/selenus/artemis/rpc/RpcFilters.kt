package com.selenus.artemis.rpc

import kotlinx.serialization.json.*

/**
 * Filter builders for getProgramAccounts and token account queries.
 */
object RpcFilters {

  fun dataSize(bytes: Int): JsonObject = buildJsonObject {
    put("dataSize", bytes)
  }

  fun memcmp(offset: Int, base58: String): JsonObject = buildJsonObject {
    put("memcmp", buildJsonObject {
      put("offset", offset)
      put("bytes", base58)
    })
  }

  fun filters(vararg f: JsonObject): JsonArray = JsonArray(f.toList())

  fun encodingBase64(commitment: String): JsonObject = buildJsonObject {
    put("encoding", "base64")
    put("commitment", commitment)
  }

  fun encodingJsonParsed(commitment: String): JsonObject = buildJsonObject {
    put("encoding", "jsonParsed")
    put("commitment", commitment)
  }
}
