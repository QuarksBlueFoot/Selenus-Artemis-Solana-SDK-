package com.selenus.artemis.metaplex

import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

class MetaplexClient(private val rpc: RpcApi) {

  suspend fun getMetadata(mint: Pubkey): MetadataData? {
    val pda = MetadataPdas.metadataPda(mint)
    val info = rpc.getAccountInfo(pda.toString())
    val value = info["value"]?.jsonObject ?: return null
    val dataField = value["data"] ?: return null
    // data = [base64, encoding]
    val arr = dataField.jsonArray
    val b64 = arr[0].jsonPrimitive.content
    val bytes = Base64.getDecoder().decode(b64)
    return TokenMetadata.decodeMetadataAccount(bytes)
  }
}
