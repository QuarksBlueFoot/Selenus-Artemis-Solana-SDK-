/*
 * Drop-in source compatibility for sol4k instruction and account-meta types.
 *
 * sol4k exposes a small set of concrete `Instruction` implementations plus a
 * generic `BaseInstruction` escape hatch. Artemis has its own richer
 * `Instruction` / `AccountMeta` types under com.selenus.artemis.tx; this file
 * provides thin sol4k-named wrappers that round-trip to the Artemis types.
 *
 * Innovation: the converters are zero-copy at the byte-array level. Each
 * wrapper holds a single reference to the underlying Artemis instruction and
 * only computes sol4k-shaped fields on demand.
 */
package org.sol4k

import com.selenus.artemis.programs.AssociatedToken as ArtemisAta
import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.programs.SystemProgram as ArtemisSystemProgram
import com.selenus.artemis.programs.Token2022Program as ArtemisToken2022Program
import com.selenus.artemis.programs.TokenProgram as ArtemisTokenProgram
import com.selenus.artemis.runtime.Pda
import com.selenus.artemis.runtime.Pubkey as ArtemisPubkey
import com.selenus.artemis.tx.AccountMeta as ArtemisAccountMeta
import com.selenus.artemis.tx.Instruction as ArtemisInstruction

/**
 * sol4k compatible `AccountMeta`.
 *
 * Matches the sol4k 0.7.0 shape: a `publicKey`, plus `signer` and `writable`
 * flags. The wrapper can be produced from or converted to the Artemis
 * [ArtemisAccountMeta] in constant time.
 */
data class AccountMeta @JvmOverloads constructor(
    val publicKey: PublicKey,
    val signer: Boolean = false,
    val writable: Boolean = false
) {
    internal fun toArtemis(): ArtemisAccountMeta = ArtemisAccountMeta(
        pubkey = publicKey.toArtemis(),
        isSigner = signer,
        isWritable = writable
    )

    companion object {
        internal fun fromArtemis(meta: ArtemisAccountMeta): AccountMeta = AccountMeta(
            publicKey = PublicKey(meta.pubkey.bytes),
            signer = meta.isSigner,
            writable = meta.isWritable
        )

        /**
         * Convenience factory for an account that signs but is not
         * writable. Mirrors upstream sol4k's
         * `AccountMeta.signerAndWritable` companion factory naming.
         */
        @JvmStatic
        fun signerAndWritable(publicKey: PublicKey): AccountMeta =
            AccountMeta(publicKey, signer = true, writable = true)

        /** Convenience factory for a writable, non-signing account. */
        @JvmStatic
        fun writable(publicKey: PublicKey): AccountMeta =
            AccountMeta(publicKey, signer = false, writable = true)

        /** Convenience factory for a read-only signer (multisig co-signer). */
        @JvmStatic
        fun signer(publicKey: PublicKey): AccountMeta =
            AccountMeta(publicKey, signer = true, writable = false)

        /** Convenience factory for a plain read-only account. */
        @JvmStatic
        fun readonly(publicKey: PublicKey): AccountMeta =
            AccountMeta(publicKey, signer = false, writable = false)
    }
}

/** Marker interface for everything that can be turned into a sol4k-shape instruction. */
interface Instruction {
    val programId: PublicKey
    val keys: List<AccountMeta>
    val data: ByteArray
    fun toArtemis(): ArtemisInstruction
}

/**
 * Generic raw instruction container. sol4k users call this `BaseInstruction`.
 *
 * Construct one directly when none of the typed helpers fit, or when building
 * against a program that has no Artemis surface yet.
 */
data class BaseInstruction(
    override val programId: PublicKey,
    override val keys: List<AccountMeta>,
    override val data: ByteArray
) : Instruction {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseInstruction) return false
        return programId == other.programId &&
            keys == other.keys &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = programId.hashCode()
        result = 31 * result + keys.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    override fun toArtemis(): ArtemisInstruction = ArtemisInstruction(
        programId = programId.toArtemis(),
        accounts = keys.map { it.toArtemis() },
        data = data
    )

    companion object {
        internal fun fromArtemis(ix: ArtemisInstruction): BaseInstruction = BaseInstruction(
            programId = PublicKey(ix.programId.bytes),
            keys = ix.accounts.map { AccountMeta.fromArtemis(it) },
            data = ix.data
        )
    }
}

/**
 * sol4k compatible `TransferInstruction` for a SOL system transfer.
 */
class TransferInstruction(
    val from: PublicKey,
    val to: PublicKey,
    val lamports: Long
) : Instruction {

    private val compiled: ArtemisInstruction = ArtemisSystemProgram.transfer(
        from = from.toArtemis(),
        to = to.toArtemis(),
        lamports = lamports
    )

    // Cache the converted public-key, account-meta list, and data array so
    // repeat reads during signing (compileMessage walks keys/accounts N
    // times) don't reallocate. Each wrapper instance is immutable, so a
    // single conversion per field is safe and lock-free.
    override val programId: PublicKey by lazy { PublicKey(compiled.programId.bytes) }
    override val keys: List<AccountMeta> by lazy {
        compiled.accounts.map { AccountMeta.fromArtemis(it) }
    }
    override val data: ByteArray get() = compiled.data
    override fun toArtemis(): ArtemisInstruction = compiled
}

/**
 * sol4k compatible `SplTransferInstruction` for an SPL Token transfer.
 *
 * Matches upstream 7-arg constructor: (from, to, mint, owner, amount, decimals, signers).
 * When `decimals` is provided (>= 0) the instruction is built with
 * `transferChecked` (index 12). Callers that only have the legacy
 * `transfer` (index 3) layout pass `decimals = -1` to opt out.
 */
class SplTransferInstruction @JvmOverloads constructor(
    val from: PublicKey,
    val to: PublicKey,
    val mint: PublicKey,
    val owner: PublicKey,
    val amount: Long,
    val decimals: Int,
    val signers: List<PublicKey> = emptyList()
) : Instruction {

    private val compiled: ArtemisInstruction = run {
        val base = if (decimals >= 0) {
            ArtemisTokenProgram.transferChecked(
                source = from.toArtemis(),
                mint = mint.toArtemis(),
                destination = to.toArtemis(),
                owner = owner.toArtemis(),
                amount = amount,
                decimals = decimals
            )
        } else {
            ArtemisTokenProgram.transfer(
                source = from.toArtemis(),
                destination = to.toArtemis(),
                owner = owner.toArtemis(),
                amount = amount
            )
        }
        if (signers.isEmpty()) {
            base
        } else {
            // Append each multisig co-signer as an additional signer account,
            // mirroring the SPL Token transfer layout when the owner is a multisig.
            val extraSigners = signers.map { pk ->
                ArtemisAccountMeta(pk.toArtemis(), isSigner = true, isWritable = false)
            }
            ArtemisInstruction(
                programId = base.programId,
                accounts = base.accounts + extraSigners,
                data = base.data
            )
        }
    }

    // Cache the converted public-key, account-meta list, and data array so
    // repeat reads during signing (compileMessage walks keys/accounts N
    // times) don't reallocate. Each wrapper instance is immutable, so a
    // single conversion per field is safe and lock-free.
    override val programId: PublicKey by lazy { PublicKey(compiled.programId.bytes) }
    override val keys: List<AccountMeta> by lazy {
        compiled.accounts.map { AccountMeta.fromArtemis(it) }
    }
    override val data: ByteArray get() = compiled.data
    override fun toArtemis(): ArtemisInstruction = compiled
}

/**
 * sol4k compatible `CreateAssociatedTokenAccountInstruction`.
 *
 * Matches upstream 4-arg constructor: (payer, associatedToken, owner, mint).
 */
class CreateAssociatedTokenAccountInstruction(
    val payer: PublicKey,
    val associatedToken: PublicKey,
    val owner: PublicKey,
    val mint: PublicKey
) : Instruction {

    private val compiled: ArtemisInstruction = ArtemisAta.createAssociatedTokenAccount(
        payer = payer.toArtemis(),
        owner = owner.toArtemis(),
        mint = mint.toArtemis(),
        ata = associatedToken.toArtemis()
    )

    // Cache the converted public-key, account-meta list, and data array so
    // repeat reads during signing (compileMessage walks keys/accounts N
    // times) don't reallocate. Each wrapper instance is immutable, so a
    // single conversion per field is safe and lock-free.
    override val programId: PublicKey by lazy { PublicKey(compiled.programId.bytes) }
    override val keys: List<AccountMeta> by lazy {
        compiled.accounts.map { AccountMeta.fromArtemis(it) }
    }
    override val data: ByteArray get() = compiled.data
    override fun toArtemis(): ArtemisInstruction = compiled
}

/**
 * Open base class for SPL Token / Token-2022 transfer instructions.
 * Mirrors upstream sol4k's `TokenTransferInstruction` so callers that
 * type their parameters as the open base keep compiling even when the
 * concrete subclass swaps between standard SPL and Token-2022.
 */
abstract class TokenTransferInstruction(
    @JvmField val from: PublicKey,
    @JvmField val to: PublicKey,
    @JvmField val mint: PublicKey,
    @JvmField val owner: PublicKey,
    @JvmField val amount: Long,
    @JvmField val decimals: Int,
    @JvmField val signers: List<PublicKey> = emptyList()
) : Instruction

/**
 * sol4k compatible `Token2022TransferInstruction` for a Token-2022
 * `transferChecked` (always; Token-2022 enforces decimals checking).
 *
 * Matches upstream signature:
 *   `(from, to, mint, owner, amount, decimals, signers)`.
 */
class Token2022TransferInstruction @JvmOverloads constructor(
    from: PublicKey,
    to: PublicKey,
    mint: PublicKey,
    owner: PublicKey,
    amount: Long,
    decimals: Int,
    signers: List<PublicKey> = emptyList()
) : TokenTransferInstruction(from, to, mint, owner, amount, decimals, signers) {

    private val compiled: ArtemisInstruction = run {
        val base = ArtemisToken2022Program.transferChecked(
            source = from.toArtemis(),
            mint = mint.toArtemis(),
            destination = to.toArtemis(),
            owner = owner.toArtemis(),
            amount = amount,
            decimals = decimals
        )
        if (signers.isEmpty()) base
        else {
            val extraSigners = signers.map { pk ->
                ArtemisAccountMeta(pk.toArtemis(), isSigner = true, isWritable = false)
            }
            ArtemisInstruction(
                programId = base.programId,
                accounts = base.accounts + extraSigners,
                data = base.data
            )
        }
    }

    override val programId: PublicKey by lazy { PublicKey(compiled.programId.bytes) }
    override val keys: List<AccountMeta> by lazy {
        compiled.accounts.map { AccountMeta.fromArtemis(it) }
    }
    override val data: ByteArray get() = compiled.data
    override fun toArtemis(): ArtemisInstruction = compiled
}

/**
 * sol4k compatible `CreateAssociatedToken2022AccountInstruction`.
 *
 * Matches upstream 4-arg constructor: (payer, associatedToken, owner,
 * mint), but pins the inner token program to TOKEN_2022 so the ATA
 * is created under the Token-2022 program. Account derivation also
 * uses TOKEN_2022 as a seed so the ATA address matches what
 * Token-2022's transferChecked expects.
 */
class CreateAssociatedToken2022AccountInstruction(
    val payer: PublicKey,
    val associatedToken: PublicKey,
    val owner: PublicKey,
    val mint: PublicKey
) : Instruction {

    private val compiled: ArtemisInstruction = run {
        val accounts = listOf(
            ArtemisAccountMeta(payer.toArtemis(), isSigner = true, isWritable = true),
            ArtemisAccountMeta(associatedToken.toArtemis(), isSigner = false, isWritable = true),
            ArtemisAccountMeta(owner.toArtemis(), isSigner = false, isWritable = false),
            ArtemisAccountMeta(mint.toArtemis(), isSigner = false, isWritable = false),
            ArtemisAccountMeta(ProgramIds.SYSTEM_PROGRAM, isSigner = false, isWritable = false),
            ArtemisAccountMeta(ProgramIds.TOKEN_2022_PROGRAM, isSigner = false, isWritable = false)
        )
        ArtemisInstruction(
            programId = ProgramIds.ASSOCIATED_TOKEN_PROGRAM,
            accounts = accounts,
            data = ByteArray(0)
        )
    }

    override val programId: PublicKey by lazy { PublicKey(compiled.programId.bytes) }
    override val keys: List<AccountMeta> by lazy {
        compiled.accounts.map { AccountMeta.fromArtemis(it) }
    }
    override val data: ByteArray get() = compiled.data
    override fun toArtemis(): ArtemisInstruction = compiled

    companion object {
        /**
         * Derive the Token-2022 ATA for `(owner, mint)`. Returns the
         * `(address, bump)` pair upstream callers use. The Token-2022
         * ATA seed differs from standard SPL only by the token program
         * id used in the seed; everything else is identical.
         */
        @JvmStatic
        fun deriveAddress(owner: PublicKey, mint: PublicKey): PublicKey {
            val seeds = listOf(
                owner.toArtemis().bytes,
                ProgramIds.TOKEN_2022_PROGRAM.bytes,
                mint.toArtemis().bytes
            )
            val pda = Pda.findProgramAddress(seeds, ProgramIds.ASSOCIATED_TOKEN_PROGRAM)
            return PublicKey(pda.address.bytes)
        }
    }
}

/**
 * Well-known program ids exposed as sol4k `PublicKey` values for convenience.
 */
object ProgramIdsCompat {
    @JvmField val SYSTEM_PROGRAM: PublicKey = PublicKey(ProgramIds.SYSTEM_PROGRAM.bytes)
    @JvmField val TOKEN_PROGRAM: PublicKey = PublicKey(ProgramIds.TOKEN_PROGRAM.bytes)
    @JvmField val TOKEN_2022_PROGRAM: PublicKey = PublicKey(ProgramIds.TOKEN_2022_PROGRAM.bytes)
    @JvmField val ASSOCIATED_TOKEN_PROGRAM: PublicKey = PublicKey(ProgramIds.ASSOCIATED_TOKEN_PROGRAM.bytes)
    @JvmField val MEMO_PROGRAM: PublicKey = PublicKey(ProgramIds.MEMO_PROGRAM.bytes)
    @JvmField val RENT_SYSVAR: PublicKey = PublicKey(ProgramIds.RENT_SYSVAR.bytes)
}
