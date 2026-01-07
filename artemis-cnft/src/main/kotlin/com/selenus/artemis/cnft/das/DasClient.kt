package com.selenus.artemis.cnft.das

import com.selenus.artemis.rpc.JsonRpcClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Minimal DAS client for compressed NFT proofs.
 *
 * Works with DAS-compatible RPC endpoints (e.g., Helius DAS, QuickNode DAS, etc).
 */
class DasClient(private val rpc: JsonRpcClient) {

  suspend fun getAsset(id: String): JsonObject {
    val params = buildJsonObject {
      put("id", id)
    }
    return rpc.call("getAsset", params)
  }

  suspend fun getAssetProof(id: String): JsonObject {
    val params = buildJsonObject {
      put("id", id)
    }
    return rpc.call("getAssetProof", params)
  }

  /**
   * Helper: getAsset + getAssetProof and return both results.
   */
  suspend fun getAssetWithProof(id: String, truncateCanopy: Boolean = true): JsonObject {
    val assetParams = buildJsonObject {
      put("id", id)
      // Some providers accept options; others ignore them. Safe to include.
      putJsonArray("options") { }
    }
    val asset = rpc.call("getAsset", assetParams)

    val proofParams = buildJsonObject {
      put("id", id)
    }
    val proof = rpc.call("getAssetProof", proofParams)

    return buildJsonObject {
      put("asset", asset)
      put("proof", proof)
      put("truncateCanopy", truncateCanopy)
    }
  }
}
