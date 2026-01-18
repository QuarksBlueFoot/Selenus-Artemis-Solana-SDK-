package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Pubkey

data class AccountMeta(val pubkey: Pubkey, val isSigner: Boolean, val isWritable: Boolean)

data class Instruction(val programId: Pubkey, val accounts: List<AccountMeta>, val data: ByteArray)
