package com.selenus.artemis.mplcore

import com.selenus.artemis.runtime.Pubkey

/**
 * Minimal args for MPL Core create/update flows.
 *
 * Core data models can be richer; this is a practical baseline for mobile SDKs.
 */
object MplCoreArgs {

  data class CreateCollectionArgs(
    val name: String,
    val uri: String,
    val updateAuthority: Pubkey,
    val isMutable: Boolean = true
  ) {
    fun serialize(): ByteArray {
      return MplCoreCodec.concat(
        listOf(
          MplCoreCodec.borshString(name),
          MplCoreCodec.borshString(uri),
          updateAuthority.bytes,
          MplCoreCodec.u8(if (isMutable) 1 else 0)
        )
      )
    }
  }

  data class CreateAssetArgs(
    val name: String,
    val uri: String,
    val owner: Pubkey,
    val updateAuthority: Pubkey,
    val collection: Pubkey? = null,
    val isMutable: Boolean = true
  ) {
    fun serialize(): ByteArray {
      val hasCollection = collection != null
      val colBytes = if (!hasCollection) {
        MplCoreCodec.u8(0)
      } else {
        MplCoreCodec.u8(1) + collection!!.bytes
      }
      return MplCoreCodec.concat(
        listOf(
          MplCoreCodec.borshString(name),
          MplCoreCodec.borshString(uri),
          owner.bytes,
          updateAuthority.bytes,
          colBytes,
          MplCoreCodec.u8(if (isMutable) 1 else 0)
        )
      )
    }
  }

  data class UpdateAuthorityArgs(
    val newAuthority: Pubkey
  ) {
    fun serialize(): ByteArray = newAuthority.bytes
  }
}
