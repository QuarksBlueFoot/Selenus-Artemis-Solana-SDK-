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
import com.selenus.artemis.programs.TokenProgram as ArtemisTokenProgram
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.TransactionInstruction

/** Marker interface upstream uses for every program object. */
interface Program {
    val programId: SolanaPublicKey
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

    /** Rent sysvar used by token account initialization. */
    val SYSVAR_RENT_PUBKEY: SolanaPublicKey =
        SolanaPublicKey(ArtemisProgramIds.RENT_SYSVAR.bytes)

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
    ): TransactionInstruction {
        val ix = ArtemisAssociatedToken.createAssociatedTokenAccount(
            payer = com.selenus.artemis.runtime.Pubkey(payer.bytes),
            owner = com.selenus.artemis.runtime.Pubkey(owner.bytes),
            mint = com.selenus.artemis.runtime.Pubkey(mint.bytes)
        )
        return TransactionInstruction(
            programId = SolanaPublicKey(ix.programId.bytes),
            accounts = ix.accounts.map { AccountMeta(SolanaPublicKey(it.pubkey.bytes), it.isSigner, it.isWritable) },
            data = ix.data
        )
    }
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
