/*
 * Drop-in source compatibility with com.solana.programs (web3-solana 0.2.5).
 *
 * web3-solana ships a handful of program helpers that return `TransactionInstruction`
 * ready to append to a `Message.Builder`. Each object below forwards to the
 * Artemis-native program implementations so the bytes emitted on-chain are
 * identical and the caller's existing call sites keep working.
 */
package com.solana.programs

import com.selenus.artemis.programs.AssociatedToken as ArtemisAssociatedToken
import com.selenus.artemis.programs.ProgramIds as ArtemisProgramIds
import com.selenus.artemis.programs.SystemProgram as ArtemisSystemProgram
import com.selenus.artemis.programs.TokenAuthorityType as ArtemisTokenAuthorityType
import com.selenus.artemis.programs.Token2022Program as ArtemisToken2022Program
import com.selenus.artemis.programs.TokenProgram as ArtemisTokenProgram
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.TransactionInstruction

/** Marker interface upstream uses for every program object. */
interface Program {
    val programId: SolanaPublicKey
}

/** SPL Token authority selectors accepted by `TokenProgram.setAuthority`. */
enum class TokenAuthorityType(val value: Int) {
    MintTokens(ArtemisTokenAuthorityType.MintTokens.value),
    FreezeAccount(ArtemisTokenAuthorityType.FreezeAccount.value),
    AccountOwner(ArtemisTokenAuthorityType.AccountOwner.value),
    CloseAccount(ArtemisTokenAuthorityType.CloseAccount.value)
}

/**
 * System program helpers. Matches `com.solana.programs.SystemProgram`.
 */
object SystemProgram : Program {
    override val programId: SolanaPublicKey =
        SolanaPublicKey(ArtemisProgramIds.SYSTEM_PROGRAM.bytes)

    /** The System Program ID constant used by many upstream call sites. */
    val PROGRAM_ID: SolanaPublicKey = programId

    fun transfer(from: SolanaPublicKey, to: SolanaPublicKey, lamports: Long): TransactionInstruction {
        val ix = ArtemisSystemProgram.transfer(
            from = com.selenus.artemis.runtime.Pubkey(from.bytes),
            to = com.selenus.artemis.runtime.Pubkey(to.bytes),
            lamports = lamports
        )
        return TransactionInstruction(
            programId = SolanaPublicKey(ix.programId.bytes),
            accounts = ix.accounts.map { AccountMeta(SolanaPublicKey(it.pubkey.bytes), it.isSigner, it.isWritable) },
            data = ix.data
        )
    }

    fun createAccount(
        from: SolanaPublicKey,
        newAccount: SolanaPublicKey,
        lamports: Long,
        space: Long,
        programId: SolanaPublicKey
    ): TransactionInstruction {
        val ix = ArtemisSystemProgram.createAccount(
            from = com.selenus.artemis.runtime.Pubkey(from.bytes),
            newAccount = com.selenus.artemis.runtime.Pubkey(newAccount.bytes),
            lamports = lamports,
            space = space,
            owner = com.selenus.artemis.runtime.Pubkey(programId.bytes)
        )
        return TransactionInstruction(
            programId = SolanaPublicKey(ix.programId.bytes),
            accounts = ix.accounts.map { AccountMeta(SolanaPublicKey(it.pubkey.bytes), it.isSigner, it.isWritable) },
            data = ix.data
        )
    }
}

/**
 * SPL Token program helpers. Matches `com.solana.programs.TokenProgram`.
 */
object TokenProgram : Program {
    override val programId: SolanaPublicKey =
        SolanaPublicKey(ArtemisProgramIds.TOKEN_PROGRAM.bytes)

    val PROGRAM_ID: SolanaPublicKey = programId
    val TOKEN_PROGRAM_ID: SolanaPublicKey = programId

    /** Rent sysvar used by token account initialization. */
    val SYSVAR_RENT_PUBKEY: SolanaPublicKey =
        SolanaPublicKey(ArtemisProgramIds.RENT_SYSVAR.bytes)

    fun initializeMint2(
        mint: SolanaPublicKey,
        decimals: Int,
        mintAuthority: SolanaPublicKey,
        freezeAuthority: SolanaPublicKey? = null
    ): TransactionInstruction = ArtemisTokenProgram.initializeMint2(
        mint = mint.toArtemisPubkey(),
        decimals = decimals,
        mintAuthority = mintAuthority.toArtemisPubkey(),
        freezeAuthority = freezeAuthority?.toArtemisPubkey()
    ).toCompatInstruction()

    fun initializeMint(
        mint: SolanaPublicKey,
        decimals: Int,
        mintAuthority: SolanaPublicKey,
        freezeAuthority: SolanaPublicKey? = null
    ): TransactionInstruction = initializeMint2(mint, decimals, mintAuthority, freezeAuthority)

    fun transfer(
        source: SolanaPublicKey,
        destination: SolanaPublicKey,
        owner: SolanaPublicKey,
        amount: Long
    ): TransactionInstruction {
        val ix = ArtemisTokenProgram.transfer(
            source = com.selenus.artemis.runtime.Pubkey(source.bytes),
            destination = com.selenus.artemis.runtime.Pubkey(destination.bytes),
            owner = com.selenus.artemis.runtime.Pubkey(owner.bytes),
            amount = amount
        )
        return TransactionInstruction(
            programId = SolanaPublicKey(ix.programId.bytes),
            accounts = ix.accounts.map { AccountMeta(SolanaPublicKey(it.pubkey.bytes), it.isSigner, it.isWritable) },
            data = ix.data
        )
    }

    fun approve(
        source: SolanaPublicKey,
        delegate: SolanaPublicKey,
        owner: SolanaPublicKey,
        amount: Long
    ): TransactionInstruction = ArtemisTokenProgram.approve(
        source = source.toArtemisPubkey(),
        delegate = delegate.toArtemisPubkey(),
        owner = owner.toArtemisPubkey(),
        amount = amount
    ).toCompatInstruction()

    fun revoke(
        source: SolanaPublicKey,
        owner: SolanaPublicKey
    ): TransactionInstruction = ArtemisTokenProgram.revoke(
        source = source.toArtemisPubkey(),
        owner = owner.toArtemisPubkey()
    ).toCompatInstruction()

    fun setAuthority(
        account: SolanaPublicKey,
        currentAuthority: SolanaPublicKey,
        authorityType: TokenAuthorityType,
        newAuthority: SolanaPublicKey?,
        signers: List<SolanaPublicKey> = emptyList()
    ): TransactionInstruction = setAuthority(
        account = account,
        currentAuthority = currentAuthority,
        authorityType = authorityType.value,
        newAuthority = newAuthority,
        signers = signers
    )

    fun setAuthority(
        account: SolanaPublicKey,
        currentAuthority: SolanaPublicKey,
        authorityType: Int,
        newAuthority: SolanaPublicKey?,
        signers: List<SolanaPublicKey> = emptyList()
    ): TransactionInstruction = ArtemisTokenProgram.setAuthority(
        account = account.toArtemisPubkey(),
        currentAuthority = currentAuthority.toArtemisPubkey(),
        authorityType = authorityType,
        newAuthority = newAuthority?.toArtemisPubkey(),
        signers = signers.map { it.toArtemisPubkey() }
    ).toCompatInstruction()

    fun freezeAccount(
        account: SolanaPublicKey,
        mint: SolanaPublicKey,
        authority: SolanaPublicKey,
        signers: List<SolanaPublicKey> = emptyList()
    ): TransactionInstruction = ArtemisTokenProgram.freezeAccount(
        account = account.toArtemisPubkey(),
        mint = mint.toArtemisPubkey(),
        authority = authority.toArtemisPubkey(),
        signers = signers.map { it.toArtemisPubkey() }
    ).toCompatInstruction()

    fun thawAccount(
        account: SolanaPublicKey,
        mint: SolanaPublicKey,
        authority: SolanaPublicKey,
        signers: List<SolanaPublicKey> = emptyList()
    ): TransactionInstruction = ArtemisTokenProgram.thawAccount(
        account = account.toArtemisPubkey(),
        mint = mint.toArtemisPubkey(),
        authority = authority.toArtemisPubkey(),
        signers = signers.map { it.toArtemisPubkey() }
    ).toCompatInstruction()

    fun mintTo(
        mint: SolanaPublicKey,
        destination: SolanaPublicKey,
        mintAuthority: SolanaPublicKey,
        amount: Long
    ): TransactionInstruction {
        val ix = ArtemisTokenProgram.mintTo(
            mint = com.selenus.artemis.runtime.Pubkey(mint.bytes),
            destination = com.selenus.artemis.runtime.Pubkey(destination.bytes),
            mintAuthority = com.selenus.artemis.runtime.Pubkey(mintAuthority.bytes),
            amount = amount
        )
        return TransactionInstruction(
            programId = SolanaPublicKey(ix.programId.bytes),
            accounts = ix.accounts.map { AccountMeta(SolanaPublicKey(it.pubkey.bytes), it.isSigner, it.isWritable) },
            data = ix.data
        )
    }

    fun burn(
        account: SolanaPublicKey,
        mint: SolanaPublicKey,
        owner: SolanaPublicKey,
        amount: Long
    ): TransactionInstruction = ArtemisTokenProgram.burn(
        account = account.toArtemisPubkey(),
        mint = mint.toArtemisPubkey(),
        owner = owner.toArtemisPubkey(),
        amount = amount
    ).toCompatInstruction()

    fun closeAccount(
        account: SolanaPublicKey,
        destination: SolanaPublicKey,
        owner: SolanaPublicKey
    ): TransactionInstruction = ArtemisTokenProgram.closeAccount(
        account = account.toArtemisPubkey(),
        destination = destination.toArtemisPubkey(),
        owner = owner.toArtemisPubkey()
    ).toCompatInstruction()

    fun transferChecked(
        source: SolanaPublicKey,
        mint: SolanaPublicKey,
        destination: SolanaPublicKey,
        owner: SolanaPublicKey,
        amount: Long,
        decimals: Int
    ): TransactionInstruction = ArtemisTokenProgram.transferChecked(
        source = source.toArtemisPubkey(),
        mint = mint.toArtemisPubkey(),
        destination = destination.toArtemisPubkey(),
        owner = owner.toArtemisPubkey(),
        amount = amount,
        decimals = decimals
    ).toCompatInstruction()

    fun syncNative(account: SolanaPublicKey): TransactionInstruction = ArtemisTokenProgram.syncNative(
        account = account.toArtemisPubkey()
    ).toCompatInstruction()
}

/**
 * Associated Token Account program helpers.
 */
object AssociatedTokenProgram : Program {
    override val programId: SolanaPublicKey =
        SolanaPublicKey(ArtemisProgramIds.ASSOCIATED_TOKEN_PROGRAM.bytes)

    val PROGRAM_ID: SolanaPublicKey = programId

    fun createAssociatedTokenAccount(
        payer: SolanaPublicKey,
        owner: SolanaPublicKey,
        mint: SolanaPublicKey
    ): TransactionInstruction = createAssociatedTokenAccount(
        payer = payer,
        owner = owner,
        mint = mint,
        tokenProgram = TokenProgram.PROGRAM_ID
    )

    fun createAssociatedTokenAccount(
        payer: SolanaPublicKey,
        owner: SolanaPublicKey,
        mint: SolanaPublicKey,
        tokenProgram: SolanaPublicKey,
        associatedToken: SolanaPublicKey = deriveAddress(owner, mint, tokenProgram)
    ): TransactionInstruction {
        val ix = ArtemisAssociatedToken.createAssociatedTokenAccount(
            payer = payer.toArtemisPubkey(),
            owner = owner.toArtemisPubkey(),
            mint = mint.toArtemisPubkey(),
            ata = associatedToken.toArtemisPubkey(),
            tokenProgram = tokenProgram.toArtemisPubkey()
        )
        return ix.toCompatInstruction()
    }

    fun createAssociatedTokenAccountIdempotent(
        payer: SolanaPublicKey,
        owner: SolanaPublicKey,
        mint: SolanaPublicKey,
        tokenProgram: SolanaPublicKey = TokenProgram.PROGRAM_ID,
        associatedToken: SolanaPublicKey = deriveAddress(owner, mint, tokenProgram)
    ): TransactionInstruction {
        val ix = ArtemisAssociatedToken.createAssociatedTokenAccountIdempotent(
            payer = payer.toArtemisPubkey(),
            owner = owner.toArtemisPubkey(),
            mint = mint.toArtemisPubkey(),
            ata = associatedToken.toArtemisPubkey(),
            tokenProgram = tokenProgram.toArtemisPubkey()
        )
        return ix.toCompatInstruction()
    }

    fun deriveAddress(
        owner: SolanaPublicKey,
        mint: SolanaPublicKey,
        tokenProgram: SolanaPublicKey = TokenProgram.PROGRAM_ID
    ): SolanaPublicKey = SolanaPublicKey(
        ArtemisAssociatedToken.address(
            owner = owner.toArtemisPubkey(),
            mint = mint.toArtemisPubkey(),
            tokenProgram = tokenProgram.toArtemisPubkey()
        ).bytes
    )
}

/** Token-2022 program helpers added by newer web3-core call sites. */
object Token2022Program : Program {
    override val programId: SolanaPublicKey =
        SolanaPublicKey(ArtemisProgramIds.TOKEN_2022_PROGRAM.bytes)

    val PROGRAM_ID: SolanaPublicKey = programId
    val TOKEN_2022_PROGRAM_ID: SolanaPublicKey = programId

    fun initializeMint2(
        mint: SolanaPublicKey,
        decimals: Int,
        mintAuthority: SolanaPublicKey,
        freezeAuthority: SolanaPublicKey? = null
    ): TransactionInstruction = ArtemisToken2022Program.initializeMint2(
        mint = mint.toArtemisPubkey(),
        decimals = decimals,
        mintAuthority = mintAuthority.toArtemisPubkey(),
        freezeAuthority = freezeAuthority?.toArtemisPubkey()
    ).toCompatInstruction()

    fun transferChecked(
        source: SolanaPublicKey,
        mint: SolanaPublicKey,
        destination: SolanaPublicKey,
        owner: SolanaPublicKey,
        amount: Long,
        decimals: Int
    ): TransactionInstruction = ArtemisToken2022Program.transferChecked(
        source = source.toArtemisPubkey(),
        mint = mint.toArtemisPubkey(),
        destination = destination.toArtemisPubkey(),
        owner = owner.toArtemisPubkey(),
        amount = amount,
        decimals = decimals
    ).toCompatInstruction()

    fun mintToChecked(
        mint: SolanaPublicKey,
        destination: SolanaPublicKey,
        mintAuthority: SolanaPublicKey,
        amount: Long,
        decimals: Int
    ): TransactionInstruction = ArtemisToken2022Program.mintToChecked(
        mint = mint.toArtemisPubkey(),
        destination = destination.toArtemisPubkey(),
        mintAuthority = mintAuthority.toArtemisPubkey(),
        amount = amount,
        decimals = decimals
    ).toCompatInstruction()

    fun closeAccount(
        account: SolanaPublicKey,
        destination: SolanaPublicKey,
        owner: SolanaPublicKey
    ): TransactionInstruction = ArtemisToken2022Program.closeAccount(
        account = account.toArtemisPubkey(),
        destination = destination.toArtemisPubkey(),
        owner = owner.toArtemisPubkey()
    ).toCompatInstruction()

    fun setAuthority(
        account: SolanaPublicKey,
        currentAuthority: SolanaPublicKey,
        authorityType: TokenAuthorityType,
        newAuthority: SolanaPublicKey?,
        signers: List<SolanaPublicKey> = emptyList()
    ): TransactionInstruction = setAuthority(
        account = account,
        currentAuthority = currentAuthority,
        authorityType = authorityType.value,
        newAuthority = newAuthority,
        signers = signers
    )

    fun setAuthority(
        account: SolanaPublicKey,
        currentAuthority: SolanaPublicKey,
        authorityType: Int,
        newAuthority: SolanaPublicKey?,
        signers: List<SolanaPublicKey> = emptyList()
    ): TransactionInstruction = ArtemisToken2022Program.setAuthority(
        account = account.toArtemisPubkey(),
        currentAuthority = currentAuthority.toArtemisPubkey(),
        authorityType = authorityType,
        newAuthority = newAuthority?.toArtemisPubkey(),
        signers = signers.map { it.toArtemisPubkey() }
    ).toCompatInstruction()

    fun freezeAccount(
        account: SolanaPublicKey,
        mint: SolanaPublicKey,
        authority: SolanaPublicKey,
        signers: List<SolanaPublicKey> = emptyList()
    ): TransactionInstruction = ArtemisToken2022Program.freezeAccount(
        account = account.toArtemisPubkey(),
        mint = mint.toArtemisPubkey(),
        authority = authority.toArtemisPubkey(),
        signers = signers.map { it.toArtemisPubkey() }
    ).toCompatInstruction()

    fun thawAccount(
        account: SolanaPublicKey,
        mint: SolanaPublicKey,
        authority: SolanaPublicKey,
        signers: List<SolanaPublicKey> = emptyList()
    ): TransactionInstruction = ArtemisToken2022Program.thawAccount(
        account = account.toArtemisPubkey(),
        mint = mint.toArtemisPubkey(),
        authority = authority.toArtemisPubkey(),
        signers = signers.map { it.toArtemisPubkey() }
    ).toCompatInstruction()

    fun initializeAccount3(
        account: SolanaPublicKey,
        mint: SolanaPublicKey,
        owner: SolanaPublicKey
    ): TransactionInstruction = ArtemisToken2022Program.initializeAccount3(
        account = account.toArtemisPubkey(),
        mint = mint.toArtemisPubkey(),
        owner = owner.toArtemisPubkey()
    ).toCompatInstruction()

    fun initializeImmutableOwner(account: SolanaPublicKey): TransactionInstruction =
        ArtemisToken2022Program.initializeImmutableOwner(account.toArtemisPubkey()).toCompatInstruction()

    fun associatedTokenAddress(owner: SolanaPublicKey, mint: SolanaPublicKey): SolanaPublicKey =
        AssociatedTokenProgram.deriveAddress(owner, mint, programId)

    fun createAssociatedTokenAccount(
        payer: SolanaPublicKey,
        owner: SolanaPublicKey,
        mint: SolanaPublicKey,
        associatedToken: SolanaPublicKey = associatedTokenAddress(owner, mint)
    ): TransactionInstruction = AssociatedTokenProgram.createAssociatedTokenAccount(
        payer = payer,
        owner = owner,
        mint = mint,
        tokenProgram = programId,
        associatedToken = associatedToken
    )

    fun createAssociatedTokenAccountIdempotent(
        payer: SolanaPublicKey,
        owner: SolanaPublicKey,
        mint: SolanaPublicKey,
        associatedToken: SolanaPublicKey = associatedTokenAddress(owner, mint)
    ): TransactionInstruction = AssociatedTokenProgram.createAssociatedTokenAccountIdempotent(
        payer = payer,
        owner = owner,
        mint = mint,
        tokenProgram = programId,
        associatedToken = associatedToken
    )
}

/**
 * Compute Budget program helpers.
 *
 * The on-chain program has four instructions; upstream web3-solana exposes
 * `setComputeUnitLimit` and `setComputeUnitPrice` as the two that mobile apps
 * use. The other two are rarely set from dapp code.
 */
object ComputeBudgetProgram : Program {
    override val programId: SolanaPublicKey =
        SolanaPublicKey(com.selenus.artemis.runtime.Pubkey.fromBase58(
            "ComputeBudget111111111111111111111111111111"
        ).bytes)

    val PROGRAM_ID: SolanaPublicKey = programId

    /** Set the requested compute unit limit for the transaction. */
    fun setComputeUnitLimit(units: Int): TransactionInstruction {
        // Instruction index = 2, payload = u32 little-endian units.
        val data = ByteArray(5)
        data[0] = 2
        data[1] = (units and 0xFF).toByte()
        data[2] = ((units shr 8) and 0xFF).toByte()
        data[3] = ((units shr 16) and 0xFF).toByte()
        data[4] = ((units shr 24) and 0xFF).toByte()
        return TransactionInstruction(programId = programId, accounts = emptyList(), data = data)
    }

    /** Set the priority fee in micro-lamports per compute unit. */
    fun setComputeUnitPrice(microLamports: Long): TransactionInstruction {
        // Instruction index = 3, payload = u64 little-endian micro-lamports.
        val data = ByteArray(9)
        data[0] = 3
        for (i in 0..7) data[i + 1] = ((microLamports shr (i * 8)) and 0xFF).toByte()
        return TransactionInstruction(programId = programId, accounts = emptyList(), data = data)
    }
}

/**
 * Memo program helpers (v2 memo).
 */
object MemoProgram : Program {
    override val programId: SolanaPublicKey =
        SolanaPublicKey(ArtemisProgramIds.MEMO_PROGRAM.bytes)

    val PROGRAM_ID: SolanaPublicKey = programId

    /** Attach an arbitrary UTF-8 memo to a transaction. */
    fun writeMemo(memo: String, signers: List<SolanaPublicKey> = emptyList()): TransactionInstruction {
        return TransactionInstruction(
            programId = programId,
            accounts = signers.map { AccountMeta(it, isSigner = true, isWritable = false) },
            data = memo.encodeToByteArray()
        )
    }
}

private fun SolanaPublicKey.toArtemisPubkey(): com.selenus.artemis.runtime.Pubkey =
    com.selenus.artemis.runtime.Pubkey(bytes)

private fun com.selenus.artemis.tx.Instruction.toCompatInstruction(): TransactionInstruction =
    TransactionInstruction(
        programId = SolanaPublicKey(programId.bytes),
        accounts = accounts.map { AccountMeta(SolanaPublicKey(it.pubkey.bytes), it.isSigner, it.isWritable) },
        data = data
    )
