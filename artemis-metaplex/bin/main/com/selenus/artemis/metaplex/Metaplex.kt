package com.selenus.artemis.metaplex

import com.selenus.artemis.candymachine.presets.CandyMachineMintPresets
import com.selenus.artemis.cnft.CnftMarketplaceFlows
import com.selenus.artemis.nft.NftClient
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.ExperimentalArtemisApi
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.wallet.WalletAdapter

/**
 * One-stop entry point for the Metaplex program features Artemis supports.
 *
 * This intentionally focuses on mobile-first, indexer-free workflows:
 * - Token Metadata reads (NFT, pNFT)
 * - Candy Machine v3 mint presets
 * - Bubblegum/cNFT marketplace flows
 */
@ExperimentalArtemisApi
class Metaplex(private val rpc: RpcApi) {

  /** Token Metadata read helpers (Metadata, MasterEdition, TokenRecord, etc.). */
  val nfts: NftClient = NftClient(rpc)

  /** Token Metadata write helpers (Create, Update, Verify). */
  val metadata: TokenMetadataInstructions = TokenMetadataInstructions

  /** Token Metadata read helpers (Lightweight). */
  val client: MetaplexClient = MetaplexClient(rpc)

  /** Bubblegum/cNFT helpers (instruction composition flows). */
  val cnft: CnftApi = CnftApi

  /** Candy Machine v3 presets (safe plan + tx presets). */
  val candyMachine: CandyMachineApi = CandyMachineApi(rpc)

  object CnftApi {
    val flows = CnftMarketplaceFlows
  }

  class CandyMachineApi internal constructor(private val rpc: RpcApi) {
    suspend fun mintNewWithSeed(
      adapter: WalletAdapter,
      candyGuard: Pubkey,
      candyMachine: Pubkey,
      seed: String,
      group: String? = null,
      forcePnft: Boolean = false,
    ): CandyMachineMintPresets.MintResult {
      return CandyMachineMintPresets.mintNewWithSeed(
        rpc = rpc,
        adapter = adapter,
        candyGuard = candyGuard,
        candyMachine = candyMachine,
        seed = seed,
        group = group,
        guardArgs = null,
        forcePnft = forcePnft,
      )
    }
  }
}
