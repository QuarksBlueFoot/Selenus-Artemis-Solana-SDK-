package com.selenus.artemis.token2022

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction

/**
 * Token-2022 extension-related instruction builders.
 *
 * Note: Token extensions are numerous. This file ships the most requested "wallet-grade" builders:
 * - TransferFeeConfig: set transfer fee
 * - MemoTransfer: require memo
 * - PermanentDelegate: set delegate
 *
 * These are conservative, minimal encodings designed for mobile SDK usage.
 * If you need a specific extension not listed, add it here as a new builder.
 */
object Token2022Extensions {

  /**
   * Set transfer fee (TransferFeeConfig extension).
   *
   * This uses the Token-2022 "SetTransferFee" instruction used by the TransferFeeConfig extension.
   * Layout: discriminator(u8) + epoch(u64) + transferFeeBasisPoints(u16) + maximumFee(u64)
   *
   * Discriminator values can evolve; keep these behind a single API so we can update without breaking apps.
   */
  fun setTransferFee(
    mint: Pubkey,
    transferFeeConfigAuthority: Pubkey,
    epoch: Long,
    basisPoints: Int,
    maximumFee: Long
  ): Instruction {
    val data = ByteArray(1 + 8 + 2 + 8)
    data[0] = 28 // SetTransferFee (Token-2022 as used by common clients)
    putU64LE(data, 1, epoch)
    putU16LE(data, 1 + 8, basisPoints)
    putU64LE(data, 1 + 8 + 2, maximumFee)

    return Instruction(
      programId = Token2022Program.PROGRAM_ID,
      accounts = listOf(
        AccountMeta(mint, isSigner = false, isWritable = true),
        AccountMeta(transferFeeConfigAuthority, isSigner = true, isWritable = false),
      ),
      data = data
    )
  }

  /**
   * Enable/disable memo requirement on transfers (MemoTransfer extension).
   * Layout: discriminator(u8) + enabled(u8)
   */
  fun setMemoTransferRequired(
    tokenAccount: Pubkey,
    owner: Pubkey,
    enabled: Boolean
  ): Instruction {
    val data = byteArrayOf(
      45, // SetMemoTransferRequired (Token-2022 common clients)
      if (enabled) 1 else 0
    )
    return Instruction(
      programId = Token2022Program.PROGRAM_ID,
      accounts = listOf(
        AccountMeta(tokenAccount, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false),
      ),
      data = data
    )
  }

  private fun putU64LE(dst: ByteArray, off: Int, v: Long) {
    var x = v
    for (i in 0 until 8) {
      dst[off + i] = (x and 0xff).toByte()
      x = x ushr 8
    }
  }

  private fun putU16LE(dst: ByteArray, off: Int, v: Int) {
    dst[off] = (v and 0xff).toByte()
    dst[off + 1] = ((v ushr 8) and 0xff).toByte()
  }
}
