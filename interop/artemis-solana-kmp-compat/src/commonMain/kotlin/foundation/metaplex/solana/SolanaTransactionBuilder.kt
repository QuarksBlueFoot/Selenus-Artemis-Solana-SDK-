/*
 * Drop-in source compatibility with foundation.metaplex.solana.
 *
 * solana-kmp exposes a tiny `SolanaTransactionBuilder` that mirrors the classic
 * solana-web3.js fluent API. This shim wraps Artemis `Transaction` to expose
 * the same methods.
 */
package foundation.metaplex.solana

import com.selenus.artemis.runtime.Signer as ArtemisSigner
import com.selenus.artemis.tx.AccountMeta as ArtemisAccountMeta
import com.selenus.artemis.tx.Instruction as ArtemisInstruction
import com.selenus.artemis.tx.Transaction as ArtemisTransaction
import foundation.metaplex.signer.Signer
import foundation.metaplex.solanapublickeys.PublicKey

/**
 * solana-kmp compatible `AccountMeta`.
 */
data class AccountMeta(
    val publicKey: PublicKey,
    val isSigner: Boolean,
    val isWritable: Boolean
)

/**
 * solana-kmp compatible `TransactionInstruction`.
 */
data class TransactionInstruction(
    val programId: PublicKey,
    val keys: List<AccountMeta>,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransactionInstruction) return false
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
}

/**
 * solana-kmp compatible `Transaction`.
 *
 * Holds an Artemis [ArtemisTransaction] internally and delegates compilation
 * and signing to it. The only thing this class adds is the solana-kmp-named
 * properties and the `serialize` that returns the upstream `SerializedTransaction`
 * wrapper type.
 */
class Transaction internal constructor(internal val artemis: ArtemisTransaction) {
    fun serialize(): SerializedTransaction = SerializedTransaction(artemis.serialize())
}

/**
 * solana-kmp compatible `SerializedTransaction`.
 *
 * Upstream uses a value-class-ish wrapper around the byte array. The shim
 * exposes the same shape so destructuring and `.bytes` lookups keep working.
 */
@JvmInline
value class SerializedTransaction(val bytes: ByteArray)

/**
 * solana-kmp compatible `SolanaTransactionBuilder`.
 *
 * Usage matches upstream:
 *
 * ```kotlin
 * val tx = SolanaTransactionBuilder()
 *     .addInstruction(instruction)
 *     .setRecentBlockHash(blockhash)
 *     .setSigners(signers)
 *     .build()
 * ```
 */
class SolanaTransactionBuilder {

    private val artemis = ArtemisTransaction()
    private var signers: List<Signer> = emptyList()

    fun addInstruction(instruction: TransactionInstruction): SolanaTransactionBuilder {
        val ix = ArtemisInstruction(
            programId = com.selenus.artemis.runtime.Pubkey(instruction.programId.toByteArray()),
            accounts = instruction.keys.map { meta ->
                ArtemisAccountMeta(
                    pubkey = com.selenus.artemis.runtime.Pubkey(meta.publicKey.toByteArray()),
                    isSigner = meta.isSigner,
                    isWritable = meta.isWritable
                )
            },
            data = instruction.data
        )
        artemis.addInstruction(ix)
        return this
    }

    fun setRecentBlockHash(blockhash: String): SolanaTransactionBuilder {
        artemis.recentBlockhash = blockhash
        return this
    }

    fun setSigners(signers: List<Signer>): SolanaTransactionBuilder {
        this.signers = signers
        if (signers.isNotEmpty()) {
            artemis.feePayer = com.selenus.artemis.runtime.Pubkey(signers.first().publicKey.toByteArray())
        }
        return this
    }

    /**
     * Build and sign the transaction.
     *
     * Upstream signs inside `build`, so we do the same. Signing is suspend-safe
     * because [Signer.sign] is `suspend`. Callers that cannot block should wrap
     * the build in a coroutine scope.
     */
    suspend fun build(): Transaction {
        require(artemis.recentBlockhash != null) { "recentBlockhash must be set" }
        require(artemis.feePayer != null) { "feePayer must be set (call setSigners first)" }

        val msg = artemis.compileMessage()
        val msgBytes = msg.serialize()
        signers.forEach { signer ->
            val signature = signer.sign(msgBytes)
            artemis.addSignature(
                com.selenus.artemis.runtime.Pubkey(signer.publicKey.toByteArray()),
                signature
            )
        }
        return Transaction(artemis)
    }
}
