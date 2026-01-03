package com.selenus.artemis.wallet

import com.selenus.artemis.runtime.Pubkey

data class WalletIdentity(
  val name: String,
  val publicKey: Pubkey,
  val capabilities: WalletCapabilities
)
