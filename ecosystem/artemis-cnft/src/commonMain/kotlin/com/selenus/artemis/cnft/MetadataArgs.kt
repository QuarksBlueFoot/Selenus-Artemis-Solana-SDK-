package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Pubkey

/**
 * Bubblegum v1 MetadataArgs (borsh-like).
 *
 * This is the payload used by mintV1 and mintToCollectionV1 on Bubblegum v1.
 * We keep it intentionally "wallet-grade": enough to build real mints.
 */
data class MetadataArgs(
  val name: String,
  val symbol: String = "",
  val uri: String,
  val sellerFeeBasisPoints: Int,
  val primarySaleHappened: Boolean = false,
  val isMutable: Boolean = true,
  val editionNonce: Int? = null,
  val tokenStandard: Int? = null,
  val collection: Collection? = null,
  val uses: Uses? = null,
  val creators: List<Creator> = emptyList()
) {
  data class Creator(
    val address: Pubkey,
    val verified: Boolean,
    val share: Int
  )

  data class Collection(
    val key: Pubkey,
    val verified: Boolean
  )

  data class Uses(
    val useMethod: Int,
    val remaining: Long,
    val total: Long
  )

  fun serialize(): ByteArray {
    require(sellerFeeBasisPoints in 0..10_000) { "sellerFeeBasisPoints must be 0..10000" }
    creators.forEach { require(it.share in 0..100) { "creator share must be 0..100" } }
    if (creators.isNotEmpty()) {
      val sum = creators.sumOf { it.share }
      require(sum == 100) { "creator shares must sum to 100" }
    }

    val creatorsBytes = CnftCodec.vec(
      creators.map {
        CnftCodec.concat(
          listOf(
            it.address.bytes,
            CnftCodec.u8(if (it.verified) 1 else 0),
            CnftCodec.u8(it.share)
          )
        )
      }
    )

    val collectionBytes = if (collection == null) {
      CnftCodec.u8(0)
    } else {
      CnftCodec.u8(1) + CnftCodec.concat(
        listOf(
          collection.key.bytes,
          CnftCodec.u8(if (collection.verified) 1 else 0)
        )
      )
    }

    val usesBytes = if (uses == null) {
      CnftCodec.u8(0)
    } else {
      CnftCodec.u8(1) + CnftCodec.concat(
        listOf(
          CnftCodec.u8(uses.useMethod),
          CnftCodec.u64le(uses.remaining),
          CnftCodec.u64le(uses.total)
        )
      )
    }

    fun optU8(v: Int?): ByteArray = if (v == null) CnftCodec.u8(0) else CnftCodec.u8(1) + CnftCodec.u8(v)
    fun optU16(v: Int?): ByteArray = if (v == null) CnftCodec.u8(0) else CnftCodec.u8(1) + CnftCodec.u16le(v)

    return CnftCodec.concat(
      listOf(
        CnftCodec.borshString(name),
        CnftCodec.borshString(symbol),
        CnftCodec.borshString(uri),
        CnftCodec.u16le(sellerFeeBasisPoints),
        CnftCodec.u8(if (primarySaleHappened) 1 else 0),
        CnftCodec.u8(if (isMutable) 1 else 0),
        optU8(editionNonce),
        optU8(tokenStandard),
        collectionBytes,
        usesBytes,
        creatorsBytes
      )
    )
  }
}
