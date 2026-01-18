package com.selenus.artemis.nft

import com.selenus.artemis.runtime.Pubkey

object TokenRecordParser {

  fun parse(mint: Pubkey, tokenAccount: Pubkey, accountData: ByteArray): TokenRecord {
    val r = BorshReader(accountData)

    r.u8() // key
    r.u8() // bump
    val state = r.u8()

    var delegate: Pubkey? = null
    var delegateRole: Int? = null
    var lockedBy: Pubkey? = null
    var ruleSet: Pubkey? = null

    try {
      val dTag = r.u8()
      if (dTag != 0) delegate = Pubkey(r.bytes(32))

      val roleTag = r.u8()
      if (roleTag != 0) delegateRole = r.u8()

      val lockTag = r.u8()
      if (lockTag != 0) lockedBy = Pubkey(r.bytes(32))

      val ruleTag = r.u8()
      if (ruleTag != 0) ruleSet = Pubkey(r.bytes(32))
    } catch (_: Throwable) {
      // tolerate missing tail
    }

    return TokenRecord(
      mint = mint,
      tokenAccount = tokenAccount,
      state = state,
      delegate = delegate,
      delegateRole = delegateRole,
      lockedBy = lockedBy,
      ruleSet = ruleSet
    )
  }
}
