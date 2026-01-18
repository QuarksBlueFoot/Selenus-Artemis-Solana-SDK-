package com.selenus.artemis.nft

import com.selenus.artemis.runtime.Pubkey

data class NftMetadata(
  val mint: Pubkey,
  val name: String,
  val symbol: String,
  val uri: String,
  val sellerFeeBasisPoints: Int,
  val updateAuthority: Pubkey?,
  val creators: List<Creator> = emptyList(),
  val collection: CollectionRef? = null,
  val uses: Uses? = null,
  val collectionDetails: CollectionDetails? = null,
  val primarySaleHappened: Boolean? = null,
  val isMutable: Boolean? = null
)

data class Creator(
  val address: Pubkey,
  val verified: Boolean,
  val share: Int
)

data class CollectionRef(
  val verified: Boolean,
  val key: Pubkey
)

sealed class CollectionDetails {
  data class Sized(val size: Long) : CollectionDetails()
}

data class Uses(
  val useMethod: Int,
  val remaining: Long,
  val total: Long
)

data class MasterEdition(
  val mint: Pubkey,
  val supply: Long,
  val maxSupply: Long?
)

/**
 * TokenRecord is used by programmable NFTs (pNFTs).
 * Layouts can evolve; parser is tolerant and exposes common fields.
 */
data class TokenRecord(
  val mint: Pubkey,
  val tokenAccount: Pubkey,
  val state: Int,
  val delegate: Pubkey? = null,
  val delegateRole: Int? = null,
  val lockedBy: Pubkey? = null,
  val ruleSet: Pubkey? = null
)

data class CollectionAuthorityRecord(
  val collectionMint: Pubkey,
  val authority: Pubkey,
  val bump: Int
)

data class WalletNft(
  val mint: Pubkey,
  val tokenAccount: Pubkey,
  val amount: Long,
  val metadata: NftMetadata?
)
