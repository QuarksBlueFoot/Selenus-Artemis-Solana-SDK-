package com.selenus.artemis.mplcore

import com.selenus.artemis.runtime.Pubkey

/**
 * MPL Core program ids.
 *
 * If your environment uses a different program id (devnet/testnet/custom),
 * pass it explicitly to instruction builders.
 */
object MplCorePrograms {
  val DEFAULT_PROGRAM_ID: Pubkey = Pubkey.fromBase58("CoREENxT6tW9H9cgb1wP7FvNnL1uQh8xgY7Z2WmN2qN")
}
