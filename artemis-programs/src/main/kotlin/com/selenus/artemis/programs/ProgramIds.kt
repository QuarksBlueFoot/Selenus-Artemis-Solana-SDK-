package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey

object ProgramIds {
  val SYSTEM_PROGRAM = Pubkey.fromBase58("11111111111111111111111111111111")
  val TOKEN_PROGRAM = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
  val ASSOCIATED_TOKEN_PROGRAM = Pubkey.fromBase58("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
  val MEMO_PROGRAM = Pubkey.fromBase58("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr")
  val TOKEN_2022_PROGRAM = Pubkey.fromBase58("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")
  val METAPLEX_TOKEN_METADATA = Pubkey.fromBase58("metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s")
  val RENT_SYSVAR = Pubkey.fromBase58("SysvarRent111111111111111111111111111111111")
}
