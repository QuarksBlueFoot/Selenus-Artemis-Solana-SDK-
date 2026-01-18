package com.selenus.artemis.nft

import com.selenus.artemis.runtime.Pubkey

/**
 * MasterEditionParser
 *
 * Parses MasterEditionV2 accounts (common for NFTs).
 */
object MasterEditionParser {

  fun parse(mint: Pubkey, accountData: ByteArray): MasterEdition {
    val r = BorshReader(accountData)

    // key: u8
    r.u8()

    // supply: u64
    val supply = r.u64()

    // maxSupply: option<u64>
    val tag = r.u8()
    val maxSupply = if (tag == 0) null else r.u64()

    return MasterEdition(
      mint = mint,
      supply = supply,
      maxSupply = maxSupply
    )
  }
}
