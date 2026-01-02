package com.selenus.artemis.cnft.das

import com.selenus.artemis.cnft.ProofArgs
import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.Pubkey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parse DAS responses into strongly-typed proof args for Bubblegum instructions.
 *
 * This supports common provider shapes (Helius/QuickNode). Unknown fields are ignored.
 */
object DasProofParser {

  data class AssetHashes(
    val dataHash: ByteArray,
    val creatorHash: ByteArray
  )

  fun parseProofArgs(asset: JsonObject, proof: JsonObject): ProofArgs {
    val assetResult = asset["result"]?.jsonObject ?: asset
    val proofResult = proof["result"]?.jsonObject ?: proof

    val compression = assetResult["compression"]?.jsonObject
      ?: assetResult["result"]?.jsonObject?.get("compression")?.jsonObject

    val dataHashB58 = compression?.get("data_hash")?.jsonPrimitive?.content
      ?: compression?.get("dataHash")?.jsonPrimitive?.content
      ?: error("missing compression.data_hash")

    val creatorHashB58 = compression?.get("creator_hash")?.jsonPrimitive?.content
      ?: compression?.get("creatorHash")?.jsonPrimitive?.content
      ?: error("missing compression.creator_hash")

    val dataHash = Base58.decode(dataHashB58)
    val creatorHash = Base58.decode(creatorHashB58)

    val rootB58 = proofResult["root"]?.jsonPrimitive?.content ?: error("missing proof.root")
    val root = Base58.decode(rootB58)

    val idx = (proofResult["node_index"]?.jsonPrimitive?.content
      ?: proofResult["nodeIndex"]?.jsonPrimitive?.content
      ?: proofResult["leaf_index"]?.jsonPrimitive?.content
      ?: proofResult["leafIndex"]?.jsonPrimitive?.content
      ?: error("missing proof.node_index")).toLong().toInt()

    val proofArr = proofResult["proof"] as? JsonArray ?: error("missing proof.proof")
    val proofNodes = proofArr.map { Pubkey(Base58.decode(it.jsonPrimitive.content)) }

    val nonce = (compression?.get("leaf_id")?.jsonPrimitive?.content
      ?: compression?.get("leafId")?.jsonPrimitive?.content
      ?: compression?.get("nonce")?.jsonPrimitive?.content
      ?: "0").toLong()

    return ProofArgs(
      root = root,
      dataHash = dataHash,
      creatorHash = creatorHash,
      nonce = nonce,
      index = idx,
      proof = proofNodes
    )
  }

  fun proofAccountsFromProof(proof: JsonObject): List<Pubkey> {
    val proofResult = proof["result"]?.jsonObject ?: proof
    val proofArr = proofResult["proof"] as? JsonArray ?: return emptyList()
    return proofArr.map { Pubkey.fromBase58(it.jsonPrimitive.content) }
  }
}
