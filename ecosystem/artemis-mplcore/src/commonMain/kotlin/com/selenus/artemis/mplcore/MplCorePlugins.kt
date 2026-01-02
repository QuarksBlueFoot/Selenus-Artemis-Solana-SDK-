package com.selenus.artemis.mplcore

import com.selenus.artemis.runtime.Pubkey

/**
 * Minimal "plugin-like" metadata support for MPL Core.
 *
 * MPL Core supports extensible plugins; exact layouts can differ by program build.
 * This SDK provides pragmatic, mobile-friendly encoding of common fields:
 * - royalties (bps + optional creators)
 * - attributes (key/value pairs)
 *
 * If your build expects a different schema, you can use the raw builder variants
 * in MplCoreInstructions that accept a `data: ByteArray`.
 */
object MplCorePlugins {

  data class RoyaltyCreator(
    val address: Pubkey,
    val share: Int
  )

  data class Royalties(
    val basisPoints: Int,
    val creators: List<RoyaltyCreator> = emptyList()
  ) {
    fun serialize(): ByteArray {
      require(basisPoints in 0..10_000) { "basisPoints must be 0..10000" }
      if (creators.isNotEmpty()) {
        val sum = creators.sumOf { it.share }
        require(sum == 100) { "creator shares must sum to 100" }
      }
      val creatorsBytes = MplCoreCodec.concat(
        listOf(
          MplCoreCodec.u32le(creators.size.toLong()),
          *creators.map {
            MplCoreCodec.concat(listOf(it.address.bytes, MplCoreCodec.u8(it.share)))
          }.toTypedArray()
        )
      )
      return MplCoreCodec.concat(
        listOf(
          MplCoreCodec.u16le(basisPoints),
          creatorsBytes
        )
      )
    }
  }

  data class Attribute(val key: String, val value: String)

  data class Attributes(val items: List<Attribute>) {
    fun serialize(): ByteArray {
      val parts = ArrayList<ByteArray>()
      parts.add(MplCoreCodec.u32le(items.size.toLong()))
      for (a in items) {
        parts.add(MplCoreCodec.borshString(a.key))
        parts.add(MplCoreCodec.borshString(a.value))
      }
      return MplCoreCodec.concat(parts)
    }
  }
}
