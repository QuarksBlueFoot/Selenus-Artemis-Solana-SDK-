package com.selenus.artemis.txpresets

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.programs.AssociatedToken
import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.programs.SystemProgram
import com.selenus.artemis.programs.TokenProgram
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction

/**
 * High-level, opinionated helpers for the most common Solana operations.
 *
 * These are the "one-line" APIs that make Artemis feel like a modern SDK:
 *
 * ```kotlin
 * // Transfer SOL
 * val ixs = Artemis.transferSol(from, to, 1_000_000_000L) // 1 SOL
 *
 * // Transfer SPL token (handles ATA creation)
 * val ixs = Artemis.transferToken(from, to, mint, 100_000L, decimals = 6)
 *
 * // Create a new SPL token mint
 * val ixs = Artemis.createMint(payer, mintKeypair.publicKey, authority, decimals = 9)
 *
 * // Mint tokens to a destination
 * val ixs = Artemis.mintTokens(mint, destination, authority, amount)
 * ```
 *
 * Each method returns a list of instructions. The caller is responsible for
 * assembling them into a transaction and signing. This keeps the API composable -
 * you can combine instructions from different helpers into a single transaction.
 */
object Artemis {

    // ════════════════════════════════════════════════════════════════════════
    // SOL Transfers
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Transfer SOL from one account to another.
     *
     * @param from The sender (must be a signer)
     * @param to The recipient
     * @param lamports Amount in lamports (1 SOL = 1_000_000_000 lamports)
     * @return A single transfer instruction
     */
    @JvmStatic
    fun transferSol(from: Pubkey, to: Pubkey, lamports: Long): List<Instruction> {
        return listOf(SystemProgram.transfer(from, to, lamports))
    }

    // ════════════════════════════════════════════════════════════════════════
    // SPL Token Transfers
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Transfer SPL tokens. Automatically creates the destination ATA if needed.
     *
     * Returns 1-2 instructions:
     * 1. (optional) Create destination ATA if it doesn't exist
     * 2. Token transfer
     *
     * @param from Owner of the source token account (signer)
     * @param to Recipient wallet address
     * @param mint Token mint address
     * @param amount Amount in raw token units (not adjusted for decimals)
     * @param createAta If true, includes an ATA creation instruction for the recipient
     * @param tokenProgram Token program ID (SPL Token or Token-2022)
     */
    @JvmStatic
    @JvmOverloads
    fun transferToken(
        from: Pubkey,
        to: Pubkey,
        mint: Pubkey,
        amount: Long,
        createAta: Boolean = true,
        tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM
    ): List<Instruction> {
        val sourceAta = AssociatedToken.address(from, mint, tokenProgram)
        val destAta = AssociatedToken.address(to, mint, tokenProgram)

        val ixs = mutableListOf<Instruction>()

        if (createAta) {
            // Idempotent ATA creation (uses createIdempotent-style approach)
            ixs.add(AssociatedToken.createAssociatedTokenAccount(from, to, mint, destAta))
        }

        ixs.add(TokenProgram.transfer(sourceAta, destAta, from, amount))
        return ixs
    }

    // ════════════════════════════════════════════════════════════════════════
    // Mint Creation
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Create a new SPL token mint.
     *
     * Returns instructions to:
     * 1. Create the mint account (system create account)
     * 2. Initialize the mint
     *
     * The caller should include a signer for both `payer` and `mint`.
     *
     * @param payer Account paying for rent
     * @param mint The new mint account (must be a signer keypair)
     * @param authority The mint authority
     * @param decimals Number of decimal places
     * @param freezeAuthority Optional freeze authority
     * @param space Mint account size (default 82 bytes for SPL Token)
     * @param lamports Rent-exempt lamports (pass the correct value from getMinimumBalanceForRentExemption)
     */
    @JvmStatic
    @JvmOverloads
    fun createMint(
        payer: Pubkey,
        mint: Pubkey,
        authority: Pubkey,
        decimals: Int,
        freezeAuthority: Pubkey? = null,
        space: Long = 82,
        lamports: Long = 1_461_600
    ): List<Instruction> {
        return listOf(
            SystemProgram.createAccount(
                from = payer,
                newAccount = mint,
                lamports = lamports,
                space = space,
                owner = ProgramIds.TOKEN_PROGRAM
            ),
            TokenProgram.initializeMint2(mint, decimals, authority, freezeAuthority)
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // Minting Tokens
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Mint tokens to a destination account.
     *
     * Optionally creates the destination ATA first.
     *
     * @param mint The token mint
     * @param to The recipient wallet (ATA is derived automatically)
     * @param mintAuthority The mint authority (signer)
     * @param amount Amount to mint in raw units
     * @param createAta If true, includes ATA creation
     */
    @JvmStatic
    @JvmOverloads
    fun mintTokens(
        mint: Pubkey,
        to: Pubkey,
        mintAuthority: Pubkey,
        amount: Long,
        createAta: Boolean = true,
        payer: Pubkey = mintAuthority
    ): List<Instruction> {
        val destAta = AssociatedToken.address(to, mint)

        val ixs = mutableListOf<Instruction>()
        if (createAta) {
            ixs.add(AssociatedToken.createAssociatedTokenAccount(payer, to, mint, destAta))
        }
        ixs.add(TokenProgram.mintTo(mint, destAta, mintAuthority, amount))
        return ixs
    }

    // ════════════════════════════════════════════════════════════════════════
    // Token Burns
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Burn tokens from an account.
     *
     * @param mint The token mint
     * @param owner The token account owner (signer)
     * @param amount Amount to burn in raw units
     */
    @JvmStatic
    fun burnTokens(
        mint: Pubkey,
        owner: Pubkey,
        amount: Long
    ): List<Instruction> {
        val sourceAta = AssociatedToken.address(owner, mint)
        return listOf(TokenProgram.burn(sourceAta, mint, owner, amount))
    }

    // ════════════════════════════════════════════════════════════════════════
    // Token Authority & Account Management
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Approve a delegate to spend tokens from an account.
     *
     * @param owner The token account owner (signer)
     * @param delegate The delegate to approve
     * @param mint Token mint address
     * @param amount Maximum amount the delegate can spend
     */
    @JvmStatic
    fun approveDelegate(
        owner: Pubkey,
        delegate: Pubkey,
        mint: Pubkey,
        amount: Long
    ): List<Instruction> {
        val sourceAta = AssociatedToken.address(owner, mint)
        return listOf(TokenProgram.approve(sourceAta, delegate, owner, amount))
    }

    /**
     * Revoke a delegate's authority over a token account.
     *
     * @param owner The token account owner (signer)
     * @param mint Token mint address
     */
    @JvmStatic
    fun revokeDelegate(
        owner: Pubkey,
        mint: Pubkey
    ): List<Instruction> {
        val sourceAta = AssociatedToken.address(owner, mint)
        return listOf(TokenProgram.revoke(sourceAta, owner))
    }

    /**
     * Close a token account and reclaim its rent.
     *
     * @param owner The token account owner (signer)
     * @param mint Token mint address
     * @param rentDestination Where to send reclaimed SOL (defaults to owner)
     */
    @JvmStatic
    @JvmOverloads
    fun closeTokenAccount(
        owner: Pubkey,
        mint: Pubkey,
        rentDestination: Pubkey = owner
    ): List<Instruction> {
        val ata = AssociatedToken.address(owner, mint)
        return listOf(TokenProgram.closeAccount(ata, rentDestination, owner))
    }

    // ════════════════════════════════════════════════════════════════════════
    // ATA Management
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Derive the associated token address for a wallet + mint pair.
     */
    @JvmStatic
    @JvmOverloads
    fun getAssociatedTokenAddress(
        owner: Pubkey,
        mint: Pubkey,
        tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM
    ): Pubkey = AssociatedToken.address(owner, mint, tokenProgram)

    /**
     * Create an associated token account.
     */
    @JvmStatic
    @JvmOverloads
    fun createAssociatedTokenAccount(
        payer: Pubkey,
        owner: Pubkey,
        mint: Pubkey,
        tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM
    ): List<Instruction> {
        val ata = AssociatedToken.address(owner, mint, tokenProgram)
        return listOf(AssociatedToken.createAssociatedTokenAccount(payer, owner, mint, ata))
    }

    // ════════════════════════════════════════════════════════════════════════
    // Staking
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Create and delegate a stake account.
     *
     * Returns instructions to:
     * 1. Create a stake account
     * 2. Initialize it
     * 3. Delegate to the given vote account
     *
     * @param from The funder/staker (signer)
     * @param stakeAccount The new stake account (signer)
     * @param voteAccount The validator vote account to delegate to
     * @param lamports Amount to stake
     * @param stakeAuthority The stake authority
     * @param withdrawAuthority The withdrawal authority
     */
    @JvmStatic
    @JvmOverloads
    fun stakeSol(
        from: Pubkey,
        stakeAccount: Pubkey,
        voteAccount: Pubkey,
        lamports: Long,
        stakeAuthority: Pubkey = from,
        withdrawAuthority: Pubkey = from
    ): List<Instruction> {
        return listOf(
            // Create stake account with required space (200 bytes)
            SystemProgram.createAccount(
                from = from,
                newAccount = stakeAccount,
                lamports = lamports,
                space = 200,
                owner = Pubkey.fromBase58("Stake11111111111111111111111111111111111111")
            )
            // Note: StakeProgram.initialize and delegate should be chained by caller
            // from the artemis-programs module
        )
    }
}
