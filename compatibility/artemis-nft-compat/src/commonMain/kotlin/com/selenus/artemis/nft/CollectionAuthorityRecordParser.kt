package com.selenus.artemis.nft

import com.selenus.artemis.runtime.Pubkey

object CollectionAuthorityRecordParser {

  fun parse(collectionMint: Pubkey, authority: Pubkey, accountData: ByteArray): CollectionAuthorityRecord {
    val r = BorshReader(accountData)

    r.u8() // key
    val bump = r.u8()
    r.bytes(32) // update authority

    return CollectionAuthorityRecord(collectionMint = collectionMint, authority = authority, bump = bump)
  }
}
