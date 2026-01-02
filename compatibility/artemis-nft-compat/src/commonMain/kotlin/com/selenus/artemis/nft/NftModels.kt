package com.selenus.artemis.nft

import com.selenus.artemis.runtime.Pubkey

/**
 * A minimal, mobile-friendly NFT model aligned to common Metaplex SDK use-cases.
 *
 * This intentionally avoids any indexer assumptions.
 */
data class Nft(
  val mint: Pubkey,
  val metadata: NftMetadata,
  val masterEdition: MasterEdition? = null,
)

/**
 * Wallet-owned NFT view that retains the token account (useful for pNFT token records).
 */
data class WalletOwnedNft(
  val mint: Pubkey,
  val tokenAccount: Pubkey,
  val metadata: NftMetadata?,
  val masterEdition: MasterEdition? = null,
)
