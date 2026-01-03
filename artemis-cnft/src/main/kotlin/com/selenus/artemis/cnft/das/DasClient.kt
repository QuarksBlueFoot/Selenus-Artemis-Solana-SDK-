package com.selenus.artemis.cnft.das

import com.selenus.artemis.rpc.JsonRpcClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Minimal DAS client for compressed NFT proofs.
 *
 * Works with DAS-compatible RPC endpoints (e.g., Helius DAS, QuickNode DAS, etc).
 */
class DasClient(private val rpc: JsonRpcClient) {

  fun getAsset(id: String): JsonObject {
    val req = buildJsonObject {
      put("id", "1")
      put("jsonrpc", "2.0")
      put("method", "getAsset")
      putJsonArray("params") { add(buildJsonObject { put("id", id) }) }
    }
    return rpc.call(req)
  }

  fun getAssetProof(id: String): JsonObject {
    val req = buildJsonObject {
      put("id", "1")
      put("jsonrpc", "2.0")
      put("method", "getAssetProof")
      putJsonArray("params") { add(buildJsonObject { put("id", id) }) }
    }
    return rpc.call(req)
  }

  /**
   * Helper: getAsset + getAssetProof and return both results.
   */
  fun getAssetWithProof(id: String, truncateCanopy: Boolean = true): JsonObject {
    val req = buildJsonObject {
      put("id", "1")
      put("jsonrpc", "2.0")
      put("method", "getAsset")
      putJsonArray("params") {
        add(buildJsonObject {
          put("id", id)
          // Some providers accept options; others ignore them. Safe to include.
          putJsonArray("options") { }
        })
      }
    }
    val asset = rpc.call(req)

    val proofReq = buildJsonObject {
      put("id", "1")
      put("jsonrpc", "2.0")
      put("method", "getAssetProof")
      putJsonArray("params") { add(buildJsonObject { put("id", id) }) }
    }
    val proof = rpc.call(proofReq)

    return buildJsonObject {
      put("asset", asset)
      put("proof", proof)
      put("truncateCanopy", truncateCanopy)
    }
  }
}
