package com.selenus.artemis.cnft

/**
 * Bubblegum v1 builder payloads.
 *
 * Discriminators are computed using AnchorDiscriminator.global(methodName) at the callsite.
 */
object BubblegumV1Builders {

  data class CreateTreeConfigArgs(
    val maxDepth: Int,
    val maxBufferSize: Int,
    val public: Boolean
  ) {
    fun serialize(): ByteArray {
      return CnftCodec.concat(
        listOf(
          CnftCodec.u32le(maxDepth.toLong()),
          CnftCodec.u32le(maxBufferSize.toLong()),
          CnftCodec.u8(if (public) 1 else 0)
        )
      )
    }
  }

  data class MintV1Args(val metadata: MetadataArgs) {
    fun serialize(): ByteArray = metadata.serialize()
  }

  data class MintToCollectionV1Args(val metadata: MetadataArgs) {
    fun serialize(): ByteArray = metadata.serialize()
  }
}
