package com.selenus.artemis.candymachine

import com.selenus.artemis.disc.AnchorDiscriminators
import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import com.selenus.artemis.candymachine.internal.BorshWriter

/**
 * Candy Guard `mint_v2` instruction.
 *
 * This builder covers the most common mobile flow: SOL payment + no extra mint arguments.
 * If your Candy Guard requires additional mint arguments (e.g. allow-list proofs), pass them
 * as `remainingAccounts` and configure the guard on-chain accordingly.
 */
object CandyGuardMintV2 {
  data class Args(
    /** Optional guard group label. */
    val group: String? = null,
  )

  data class Accounts(
    val candyGuard: Pubkey,
    val candyMachine: Pubkey,
    val payer: Pubkey,
    val minter: Pubkey,
    val nftMint: Pubkey,
    /** Defaults to [payer] if not provided. */
    val nftMintAuthority: Pubkey = payer,

    val nftMetadata: Pubkey,
    val nftMasterEdition: Pubkey,

    /** Required for pNFT mints. */
    val token: Pubkey? = null,

    /** Required for pNFT mints. */
    val tokenRecord: Pubkey? = null,

    val collectionDelegateRecord: Pubkey,
    val collectionMint: Pubkey,
    val collectionMetadata: Pubkey,
    val collectionMasterEdition: Pubkey,
    val collectionUpdateAuthority: Pubkey,

    val candyMachineAuthorityPda: Pubkey = CandyMachinePdas.findCandyMachineAuthorityPda(candyMachine).address,
    val candyMachineProgram: Pubkey = CandyMachineIds.CANDY_MACHINE_CORE,
    val tokenMetadataProgram: Pubkey = ProgramIds.METAPLEX_TOKEN_METADATA,
    val splTokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM,
    val splAtaProgram: Pubkey? = ProgramIds.ASSOCIATED_TOKEN_PROGRAM,
    val systemProgram: Pubkey = ProgramIds.SYSTEM_PROGRAM,
    val sysvarInstructions: Pubkey = CandyMachineSysvars.INSTRUCTIONS,
    val recentSlothashes: Pubkey = CandyMachineSysvars.SLOT_HASHES,
    val authorizationRulesProgram: Pubkey? = null,
    val authorizationRules: Pubkey? = null,

    /** Extra accounts required by some guards (e.g. allow-list proofs). */
    val remainingAccounts: List<AccountMeta> = emptyList(),

    /** If true, marks [nftMint] as a signer (recommended when creating a fresh mint account in-tx). */
    val nftMintIsSigner: Boolean = true,

    /** If true, marks [nftMintAuthority] as a signer. */
    val nftMintAuthorityIsSigner: Boolean = true,
  )

  fun build(args: Args, accounts: Accounts): Instruction = build(args, accounts, mintArgsBorsh = null)

  /**
   * Build a `mint_v2` instruction with optional serialized MintArgs.
   *
   * `mintArgsBorsh` must be the Borsh-serialized bytes of `mpl_candy_guard::MintArgs`.
   */
  fun build(args: Args, accounts: Accounts, mintArgsBorsh: ByteArray?): Instruction {
    // Assemble bytes manually: [anchor discriminator] + [borsh args]
    val header = AnchorDiscriminators.global("mint_v2")
    val body = BorshWriter()
      .optionString(args.group)
      // mint_args: Option<MintArgs>
      .option(mintArgsBorsh) { bytes -> fixedBytes(bytes) }
      .bytes()
    val payload = header + body

    val metas = mutableListOf<AccountMeta>()

    fun ro(pk: Pubkey, signer: Boolean = false) = AccountMeta(pk, isSigner = signer, isWritable = false)
    fun rw(pk: Pubkey, signer: Boolean = false) = AccountMeta(pk, isSigner = signer, isWritable = true)

    // Account order matches mpl_candy_guard::accounts::MintV2.
    metas += rw(accounts.candyGuard)
    metas += ro(accounts.candyMachineProgram)
    metas += rw(accounts.candyMachine)
    metas += ro(accounts.candyMachineAuthorityPda)
    metas += rw(accounts.payer, signer = true)
    metas += ro(accounts.minter, signer = true)
    metas += rw(accounts.nftMint, signer = accounts.nftMintIsSigner)
    metas += ro(accounts.nftMintAuthority, signer = accounts.nftMintAuthorityIsSigner)
    metas += rw(accounts.nftMetadata)
    metas += rw(accounts.nftMasterEdition)

    accounts.token?.let { metas += rw(it) }
    accounts.tokenRecord?.let { metas += rw(it) }

    metas += ro(accounts.collectionDelegateRecord)
    metas += ro(accounts.collectionMint)
    metas += ro(accounts.collectionMetadata)
    metas += ro(accounts.collectionMasterEdition)
    metas += ro(accounts.collectionUpdateAuthority)
    metas += ro(accounts.tokenMetadataProgram)
    metas += ro(accounts.splTokenProgram)
    accounts.splAtaProgram?.let { metas += ro(it) }
    metas += ro(accounts.systemProgram)
    metas += ro(accounts.sysvarInstructions)
    metas += ro(accounts.recentSlothashes)
    accounts.authorizationRulesProgram?.let { metas += ro(it) }
    accounts.authorizationRules?.let { metas += ro(it) }

    metas += accounts.remainingAccounts

    return Instruction(
      programId = CandyMachineIds.CANDY_GUARD,
      accounts = metas,
      data = payload,
    )
  }
}
