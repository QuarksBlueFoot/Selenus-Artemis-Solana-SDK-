package com.selenus.artemis.candymachine.presets

import com.selenus.artemis.candymachine.CandyGuardMintV2Safe
import com.selenus.artemis.candymachine.guards.CandyGuardManifestReader
import com.selenus.artemis.candymachine.guards.GuardArgs
import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.programs.SystemProgram
import com.selenus.artemis.programs.TokenProgram
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.txpresets.TxComposerPresets
import com.selenus.artemis.wallet.SendPipeline
import com.selenus.artemis.wallet.WalletAdapter

/**
 * CandyMachineMintPresets
 *
 * v60 goal: one-call "mint" experience for mobile apps:
 * - uses v58 Candy Guard planning + safe builder
 * - uses v59 transaction composer (ATA + priority + resend)
 *
 * This module is optional and drop-in.
 */
object CandyMachineMintPresets {

  private const val SPL_MINT_SIZE: Long = 82

  data class MintResult(
    val signature: String,
    val notes: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val mintedMint: Pubkey? = null,
  )

  data class NewMintSeed(
    val seed: String,
    val mint: Pubkey,
  )

  /**
   * Derive a new mint pubkey using `createWithSeed` so only the wallet signs.
   *
   * This is mobile-first: no local keypair, no extra signer plumbing.
   */
  fun deriveNewMintWithSeed(
    wallet: Pubkey,
    seed: String,
    tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM,
  ): NewMintSeed {
    val mint = Pubkey.createWithSeed(base = wallet, seed = seed, owner = tokenProgram)
    return NewMintSeed(seed = seed, mint = mint)
  }

  /**
   * Preset: plan + validate + mint with optional ATA creation and mobile-first send.
   *
   * This does not use any indexer or paid services.
   */
  suspend fun mintWithPriorityAndResend(
    rpc: RpcApi,
    adapter: WalletAdapter,
    candyGuard: Pubkey,
    candyMachine: Pubkey,
    mint: Pubkey,
    group: String? = null,
    guardArgs: GuardArgs? = null,
    forcePnft: Boolean = false,
    sendConfig: SendPipeline.Config = SendPipeline.Config(),
    resendConfig: TxComposerPresets.ResendConfig = TxComposerPresets.ResendConfig(),
  ): MintResult {
    val wallet = adapter.publicKey

    // Read manifest for ATA intents (token payment, etc). Fail-closed for unknown guards.
    val guardData = rpc.getAccountInfoBase64(candyGuard.toString())
      ?: throw IllegalArgumentException("Candy Guard account not found")
    val manifest = CandyGuardManifestReader.read(guardData)
    if (manifest.unknownGuards.isNotEmpty()) {
      throw IllegalArgumentException(
        "Candy Guard has unsupported/unknown guards: " + manifest.unknownGuards.joinToString(", ")
      )
    }

    // Build a safe mint instruction using the planner.
    val safe = CandyGuardMintV2Safe.buildSafe(
      rpc = rpc,
      wallet = wallet,
      candyGuard = candyGuard,
      candyMachine = candyMachine,
      mint = mint,
      group = group,
      guardArgs = guardArgs,
      forcePnft = forcePnft,
    )

    // ATA intents:
    // - pNFT minting: ensure the NFT ATA exists.
    // - token payment: ensure payer has an ATA for the payment mint.
    val ataIntents = mutableListOf<TxComposerPresets.AtaIntent>()
    if (forcePnft) {
      ataIntents += TxComposerPresets.AtaIntent(owner = wallet, mint = mint)
    }
    val tokenMint = manifest.tokenPaymentMint
    if (manifest.requirements.requiresTokenPayment && tokenMint != null) {
      ataIntents += TxComposerPresets.AtaIntent(owner = wallet, mint = tokenMint)
    }

    val composed = TxComposerPresets.sendWithAtaAndPriority(
      rpc = rpc,
      adapter = adapter,
      instructions = listOf(safe.instruction),
      ataIntents = ataIntents,
      sendConfig = sendConfig,
      resendConfig = resendConfig,
      feePayer = wallet,
    )

    return MintResult(
      signature = composed.signature,
      notes = composed.notes,
      warnings = safe.warnings + composed.warnings,
      mintedMint = mint,
    )
  }

  /**
   * Preset: create a fresh SPL mint (with seed) + initializeMint2 + mint_v2.
   *
   * Returns the derived mint pubkey.
   */
  suspend fun mintNewWithSeed(
    rpc: RpcApi,
    adapter: WalletAdapter,
    candyGuard: Pubkey,
    candyMachine: Pubkey,
    seed: String,
    group: String? = null,
    guardArgs: GuardArgs? = null,
    forcePnft: Boolean = false,
    sendConfig: SendPipeline.Config = SendPipeline.Config(),
    resendConfig: TxComposerPresets.ResendConfig = TxComposerPresets.ResendConfig(),
  ): MintResult {
    val wallet = adapter.publicKey
    val mint = deriveNewMintWithSeed(wallet = wallet, seed = seed).mint

    // Rent exempt lamports for SPL mint.
    val lamports = rpc.getMinimumBalanceForRentExemption(SPL_MINT_SIZE)

    val createMintIx = SystemProgram.createAccountWithSeed(
      from = wallet,
      newAccount = mint,
      base = wallet,
      seed = seed,
      lamports = lamports,
      space = SPL_MINT_SIZE,
      owner = ProgramIds.TOKEN_PROGRAM
    )

    val initMintIx = TokenProgram.initializeMint2(
      mint = mint,
      decimals = 0,
      mintAuthority = wallet,
      freezeAuthority = null
    )

    // Then run the standard preset using the derived mint.
    val walletPub = adapter.publicKey
    val guardData = rpc.getAccountInfoBase64(candyGuard.toString())
      ?: throw IllegalArgumentException("Candy Guard account not found")
    val manifest = CandyGuardManifestReader.read(guardData)
    if (manifest.unknownGuards.isNotEmpty()) {
      throw IllegalArgumentException(
        "Candy Guard has unsupported/unknown guards: " + manifest.unknownGuards.joinToString(", ")
      )
    }

    val safe = CandyGuardMintV2Safe.buildSafe(
      rpc = rpc,
      wallet = walletPub,
      candyGuard = candyGuard,
      candyMachine = candyMachine,
      mint = mint,
      group = group,
      guardArgs = guardArgs,
      forcePnft = forcePnft,
      mintIsSigner = false,
    )

    val ataIntents = mutableListOf<TxComposerPresets.AtaIntent>()
    if (forcePnft) {
      ataIntents += TxComposerPresets.AtaIntent(owner = walletPub, mint = mint)
    }
    val tokenMint = manifest.tokenPaymentMint
    if (manifest.requirements.requiresTokenPayment && tokenMint != null) {
      ataIntents += TxComposerPresets.AtaIntent(owner = walletPub, mint = tokenMint)
    }

    val composed = TxComposerPresets.sendWithAtaAndPriority(
      rpc = rpc,
      adapter = adapter,
      instructions = listOf(createMintIx, initMintIx, safe.instruction),
      ataIntents = ataIntents,
      sendConfig = sendConfig,
      resendConfig = resendConfig,
      feePayer = walletPub,
    )

    return MintResult(
      signature = composed.signature,
      notes = composed.notes,
      warnings = safe.warnings + composed.warnings,
      mintedMint = mint,
    )
  }
}
