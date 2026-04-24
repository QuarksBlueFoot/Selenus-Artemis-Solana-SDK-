/*
 * solana-kmp compatible `Message` / `SolanaMessage`.
 *
 * Upstream exposes a `Message` interface with several introspection helpers
 * (`isAccountSigner`, `isAccountWritable`, `isProgramId`, `programIds`,
 * `nonProgramIds`, `serialize`, `setFeePayer`) and a concrete `SolanaMessage`
 * data class. This shim re-publishes both, backed by Artemis's compiled
 * [com.selenus.artemis.tx.Message].
 */
package foundation.metaplex.solana.transactions

import com.selenus.artemis.tx.Message as ArtemisMessage
import foundation.metaplex.solanapublickeys.PublicKey

/**
 * solana-kmp compatible message interface.
 */
interface Message {
    val header: MessageHeader
    val accountKeys: List<PublicKey>
    val recentBlockhash: String
    val instructions: List<CompiledInstruction>

    fun isAccountSigner(index: Int): Boolean
    fun isAccountWritable(index: Int): Boolean
    fun isProgramId(index: Int): Boolean
    fun programIds(): List<PublicKey>
    fun nonProgramIds(): List<PublicKey>
    fun serialize(): ByteArray

    /**
     * Override the fee payer on a compiled message. Upstream allows this
     * post-compile for durable-nonce paths.
     */
    fun setFeePayer(publicKey: PublicKey)
}

/**
 * Concrete [Message] implementation backed by Artemis's compiled message.
 *
 * Most callers construct this indirectly by calling `Transaction.compileMessage()`;
 * the `wrap` / `from` factories are kept public so drop-in code that builds a
 * message from raw bytes (upstream `SolanaMessage.from(bytes)`) keeps working.
 */
class SolanaMessage internal constructor(
    private var artemis: ArtemisMessage,
) : Message {

    override val header: MessageHeader
        get() = MessageHeader(
            numRequiredSignatures = artemis.header.numRequiredSignatures.toByte(),
            numReadonlySignedAccounts = artemis.header.numReadonlySigned.toByte(),
            numReadonlyUnsignedAccounts = artemis.header.numReadonlyUnsigned.toByte(),
        )

    override val accountKeys: List<PublicKey>
        get() = artemis.accountKeys.map { PublicKey(it.bytes) }

    override val recentBlockhash: String
        get() = artemis.recentBlockhash

    override val instructions: List<CompiledInstruction>
        get() = artemis.instructions.map { ci ->
            CompiledInstruction(
                programIdIndex = ci.programIdIndex,
                accounts = ci.accountIndexes.map { it.toInt() and 0xFF },
                data = com.selenus.artemis.runtime.Base58.encode(ci.data),
            )
        }

    /** Account at [index] is a signer iff it's in the first N slots where N = numRequiredSignatures. */
    override fun isAccountSigner(index: Int): Boolean =
        index in 0 until artemis.header.numRequiredSignatures

    /**
     * Account at [index] is writable according to the Solana layout:
     * - Signers: writable iff `index < (numRequiredSignatures - numReadonlySigned)`.
     * - Non-signers: writable iff `index < (totalAccounts - numReadonlyUnsigned)`.
     */
    override fun isAccountWritable(index: Int): Boolean {
        val h = artemis.header
        val numSignersWritable = h.numRequiredSignatures - h.numReadonlySigned
        val numTotal = artemis.accountKeys.size
        val numUnsignedWritable = numTotal - h.numRequiredSignatures - h.numReadonlyUnsigned
        return if (index < h.numRequiredSignatures) {
            index < numSignersWritable
        } else {
            index - h.numRequiredSignatures < numUnsignedWritable
        }
    }

    /** Account at [index] is a program id iff at least one instruction references it as such. */
    override fun isProgramId(index: Int): Boolean =
        artemis.instructions.any { it.programIdIndex == index }

    override fun programIds(): List<PublicKey> {
        val indices = artemis.instructions.map { it.programIdIndex }.toSet()
        return artemis.accountKeys.withIndex()
            .filter { it.index in indices }
            .map { PublicKey(it.value.bytes) }
    }

    override fun nonProgramIds(): List<PublicKey> {
        val programIdIndices = artemis.instructions.map { it.programIdIndex }.toSet()
        return artemis.accountKeys.withIndex()
            .filter { it.index !in programIdIndices }
            .map { PublicKey(it.value.bytes) }
    }

    override fun serialize(): ByteArray = artemis.serialize()

    override fun setFeePayer(publicKey: PublicKey) {
        // Moving the fee payer to slot 0 on a compiled message is not a safe
        // operation: it breaks every instruction's account index. Upstream
        // effectively recompiles; we do the same by rebuilding the account
        // key list with the new payer at index 0 and re-mapping instruction
        // indices.
        val payer = com.selenus.artemis.runtime.Pubkey(publicKey.toByteArray())
        val oldKeys = artemis.accountKeys
        if (oldKeys.isNotEmpty() && oldKeys[0] == payer) return
        val withoutPayer = oldKeys.filter { it != payer }
        val newKeys = listOf(payer) + withoutPayer
        val oldToNew = oldKeys.mapIndexed { idx, key ->
            idx to newKeys.indexOf(key)
        }.toMap()
        val remappedIxs = artemis.instructions.map { ci ->
            com.selenus.artemis.tx.CompiledInstruction(
                programIdIndex = oldToNew[ci.programIdIndex]!!,
                accountIndexes = ci.accountIndexes.map { (oldToNew[it.toInt() and 0xFF]!!).toByte() }.toByteArray(),
                data = ci.data,
            )
        }
        artemis = ArtemisMessage(
            header = artemis.header,
            accountKeys = newKeys,
            recentBlockhash = artemis.recentBlockhash,
            instructions = remappedIxs,
        )
    }

    companion object {
        /** Wrap an existing Artemis compiled message. Used internally by `Transaction.compileMessage`. */
        internal fun wrap(message: ArtemisMessage): SolanaMessage = SolanaMessage(message)
    }
}
