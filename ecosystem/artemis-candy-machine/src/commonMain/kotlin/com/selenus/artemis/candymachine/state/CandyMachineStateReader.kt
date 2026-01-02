package com.selenus.artemis.candymachine.state

import com.selenus.artemis.candymachine.guards.CandyMachineState
import com.selenus.artemis.candymachine.guards.Price
import com.selenus.artemis.candymachine.guards.GuardSummaryItem
import com.selenus.artemis.candymachine.internal.BorshReader

/**
 * Best-effort Candy Machine Core account parser.
 *
 * This reader focuses on mobile UX-critical fields: itemsAvailable and itemsRedeemed.
 *
 * Note: mpl-candy-machine-core is an Anchor program. We skip the 8-byte discriminator and parse
 * a stable prefix of the account.
 */
object CandyMachineStateReader {

  private const val ANCHOR_DISCRIMINATOR_LEN = 8

  data class CoreFields(
    val authority: ByteArray,
    val collectionMint: ByteArray,
    val itemsRedeemed: Long,
    val itemsAvailable: Long,
  )

  fun parseCoreFields(accountData: ByteArray): CoreFields {
    require(accountData.size > ANCHOR_DISCRIMINATOR_LEN) { "CandyMachine account data too short" }
    val r = BorshReader(accountData, ANCHOR_DISCRIMINATOR_LEN)

    // CandyMachine prefix (as of mpl-candy-machine-core v3):
    // authority: Pubkey
    // collection_mint: Pubkey
    // items_redeemed: u64
    // data: CandyMachineData { items_available: u64, ... }
    val authority = r.pubkey32()
    val collectionMint = r.pubkey32()
    val itemsRedeemed = r.u64()

    // CandyMachineData begins. We only need itemsAvailable.
    val itemsAvailable = r.u64()

    return CoreFields(authority, collectionMint, itemsRedeemed, itemsAvailable)
  }

  fun toState(
    accountData: ByteArray,
    currentPrice: Price?,
    guardSummary: List<GuardSummaryItem>,
  ): CandyMachineState {
    val f = parseCoreFields(accountData)
    val soldOut = f.itemsRedeemed >= f.itemsAvailable
    return CandyMachineState(
      itemsAvailable = f.itemsAvailable,
      itemsRedeemed = f.itemsRedeemed,
      isSoldOut = soldOut,
      currentPrice = currentPrice,
      guardSummary = guardSummary,
    )
  }
}
