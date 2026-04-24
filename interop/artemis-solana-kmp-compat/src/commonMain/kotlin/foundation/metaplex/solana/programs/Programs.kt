/*
 * solana-kmp compatible program helpers.
 *
 * Upstream exposes `SystemProgram` and `MemoProgram` as singletons under
 * `foundation.metaplex.solana.programs`. The shim re-publishes both so existing
 * `program.methods.xxx` / `transfer(...)` / `writeUtf8(...)` call sites compile
 * unchanged against Artemis.
 *
 * Implementation notes:
 * - The builders delegate to `artemis-programs` for wire behavior (discriminators,
 *   account ordering). Only the surface is re-shaped here.
 * - `AccountMeta` and `TransactionInstruction` use the shim's solana-kmp-shaped
 *   types from `foundation.metaplex.solana`, not Artemis's core types, so
 *   downstream callers stay on the solana-kmp namespace end-to-end.
 */
package foundation.metaplex.solana.programs

import com.selenus.artemis.programs.SystemProgram as ArtemisSystemProgram
import com.selenus.artemis.programs.MemoProgram as ArtemisMemoProgram
import com.selenus.artemis.runtime.Pubkey as ArtemisPubkey
import com.selenus.artemis.tx.Instruction as ArtemisInstruction
import foundation.metaplex.solana.AccountMeta
import foundation.metaplex.solana.TransactionInstruction
import foundation.metaplex.solanapublickeys.PublicKey

/**
 * Base class upstream uses for every static program helper. Exposed so callers
 * that extend or reference `Program.createTransactionInstruction` can resolve it.
 */
abstract class Program {
    companion object {
        /**
         * Build a [TransactionInstruction] directly. Useful when you have an
         * already-serialized data blob and need to wrap it in a solana-kmp
         * compatible instruction type.
         */
        fun createTransactionInstruction(
            programId: PublicKey,
            keys: List<AccountMeta>,
            data: ByteArray,
        ): TransactionInstruction = TransactionInstruction(programId, keys, data)
    }
}

/**
 * Solana System Program helpers. Matches upstream `SystemProgram` on
 * solana-kmp at the builder level; the wire bytes come from Artemis.
 */
object SystemProgram : Program() {
    val PROGRAM_ID: PublicKey = PublicKey(ArtemisSystemProgram.PROGRAM_ID.bytes)

    const val PROGRAM_INDEX_CREATE_ACCOUNT: Int = 0
    const val PROGRAM_INDEX_TRANSFER: Int = 2

    /** Transfer [lamports] from one account to another. */
    fun transfer(fromPublicKey: PublicKey, toPublickKey: PublicKey, lamports: Long): TransactionInstruction {
        val ix = ArtemisSystemProgram.transfer(
            from = ArtemisPubkey(fromPublicKey.toByteArray()),
            to = ArtemisPubkey(toPublickKey.toByteArray()),
            lamports = lamports,
        )
        return ix.toKmpInstruction()
    }

    /** Create a fresh account owned by [programId] with [space] bytes of data. */
    fun createAccount(
        fromPublicKey: PublicKey,
        newAccountPublickey: PublicKey,
        lamports: Long,
        space: Long,
        programId: PublicKey,
    ): TransactionInstruction {
        val ix = ArtemisSystemProgram.createAccount(
            from = ArtemisPubkey(fromPublicKey.toByteArray()),
            newAccount = ArtemisPubkey(newAccountPublickey.toByteArray()),
            lamports = lamports,
            space = space,
            owner = ArtemisPubkey(programId.toByteArray()),
        )
        return ix.toKmpInstruction()
    }
}

/**
 * Solana Memo Program helper. Matches upstream `MemoProgram.writeUtf8` which
 * emits a memo instruction signed by [account].
 */
object MemoProgram : Program() {
    val PROGRAM_ID: PublicKey = PublicKey(ArtemisMemoProgram.PROGRAM_ID.bytes)

    /**
     * Write a UTF-8 memo. The memo program requires the signer as an account,
     * matching upstream semantics and the on-chain layout.
     */
    fun writeUtf8(account: PublicKey, memo: String): TransactionInstruction {
        val ix = ArtemisMemoProgram.memo(
            text = memo,
            signers = listOf(ArtemisPubkey(account.toByteArray())),
        )
        return ix.toKmpInstruction()
    }
}

// ---------------------------------------------------------------------------
// Internal conversion from Artemis's core `Instruction` to the solana-kmp-
// shaped `TransactionInstruction`. Kept private to this file so it does not
// leak into the compat namespace.
// ---------------------------------------------------------------------------

private fun ArtemisInstruction.toKmpInstruction(): TransactionInstruction =
    TransactionInstruction(
        programId = PublicKey(programId.bytes),
        keys = accounts.map { meta ->
            AccountMeta(
                publicKey = PublicKey(meta.pubkey.bytes),
                isSigner = meta.isSigner,
                isWritable = meta.isWritable,
            )
        },
        data = data,
    )
