/*
 * Drop-in source compatibility with org.sol4k.Transaction,
 * org.sol4k.VersionedTransaction, and org.sol4k.TransactionMessage.
 *
 * Implementation note: these wrappers delegate all byte-level work to the
 * Artemis `Transaction` and `Message` classes. Using the Artemis native types
 * means every sol4k call site picks up Artemis's wire-format correctness
 * (signer ordering, readonly-vs-writable account partitioning, deterministic
 * sort) without any changes.
 */
package org.sol4k

import com.selenus.artemis.tx.Message as ArtemisMessage
import com.selenus.artemis.tx.Transaction as ArtemisTransaction
import com.selenus.artemis.runtime.Signer as ArtemisSigner

/**
 * sol4k compatible `TransactionMessage`.
 *
 * sol4k uses a builder-style static factory to assemble an immutable
 * message that can be serialized for `getFeeForMessage` or wrapped into a
 * [Transaction] / [VersionedTransaction]. This shim mirrors the exact
 * constructor shape with Artemis internals.
 */
class TransactionMessage private constructor(
    val feePayer: PublicKey,
    val recentBlockhash: String,
    val instructions: List<Instruction>,
    val addressLookupTableAccounts: List<AddressLookupTableAccount>
) {

    /**
     * Return a new [TransactionMessage] with the same feepayer and instructions
     * but a fresh blockhash. Matches sol4k's `withNewBlockhash` method.
     */
    fun withNewBlockhash(newBlockhash: String): TransactionMessage = TransactionMessage(
        feePayer = feePayer,
        recentBlockhash = newBlockhash,
        instructions = instructions,
        addressLookupTableAccounts = addressLookupTableAccounts
    )

    /**
     * Serialize the message into the compiled wire format. The caller can feed
     * this directly into `Connection.getFeeForMessage` or wrap it in a
     * [Transaction] for signing.
     */
    fun serialize(): ByteArray {
        val artemisTx = toArtemisTransaction()
        return artemisTx.compileMessage().serialize()
    }

    /** Build the equivalent Artemis [ArtemisTransaction]. */
    internal fun toArtemisTransaction(): ArtemisTransaction {
        val tx = ArtemisTransaction(
            feePayer = feePayer.toArtemis(),
            recentBlockhash = recentBlockhash
        )
        instructions.forEach { tx.addInstruction(it.toArtemis()) }
        return tx
    }

    companion object {
        /**
         * Create a message from a single instruction. Matches sol4k 0.7.0's
         * primary factory.
         */
        @JvmStatic
        @JvmOverloads
        fun newMessage(
            feePayer: PublicKey,
            recentBlockhash: String,
            instruction: Instruction,
            addressLookupTableAccounts: List<AddressLookupTableAccount> = emptyList()
        ): TransactionMessage = TransactionMessage(
            feePayer = feePayer,
            recentBlockhash = recentBlockhash,
            instructions = listOf(instruction),
            addressLookupTableAccounts = addressLookupTableAccounts
        )

        /** Same as [newMessage] but accepts a list of instructions. */
        @JvmStatic
        @JvmOverloads
        fun newMessage(
            feePayer: PublicKey,
            recentBlockhash: String,
            instructions: List<Instruction>,
            addressLookupTableAccounts: List<AddressLookupTableAccount> = emptyList()
        ): TransactionMessage = TransactionMessage(
            feePayer = feePayer,
            recentBlockhash = recentBlockhash,
            instructions = instructions,
            addressLookupTableAccounts = addressLookupTableAccounts
        )

        /**
         * Deserialize a compiled message blob back into a [TransactionMessage].
         *
         * This implementation walks the Solana wire format directly and
         * reconstructs the account-meta flags from the header partitioning.
         * sol4k apps that round-trip serialized messages through a backend
         * service continue to work unchanged.
         */
        @JvmStatic
        fun deserialize(serialized: ByteArray): TransactionMessage {
            var offset = 0
            val numRequired = serialized[offset++].toInt() and 0xFF
            val numReadonlySigned = serialized[offset++].toInt() and 0xFF
            val numReadonlyUnsigned = serialized[offset++].toInt() and 0xFF
            val (numAccounts, accountLenBytes) = shortVecDecode(serialized, offset)
            offset += accountLenBytes
            val accountKeys = ArrayList<PublicKey>(numAccounts)
            repeat(numAccounts) {
                val pkBytes = serialized.copyOfRange(offset, offset + 32)
                accountKeys.add(PublicKey(pkBytes))
                offset += 32
            }
            val blockhashBytes = serialized.copyOfRange(offset, offset + 32)
            offset += 32
            val recentBlockhash = com.selenus.artemis.runtime.Base58.encode(blockhashBytes)
            val (numInstructions, ixLenBytes) = shortVecDecode(serialized, offset)
            offset += ixLenBytes

            val writableSignedCount = numRequired - numReadonlySigned
            val writableUnsignedCount = numAccounts - numRequired - numReadonlyUnsigned

            fun metaForIndex(index: Int): AccountMeta {
                val isSigner = index < numRequired
                val isWritable = when {
                    index < writableSignedCount -> true
                    index < numRequired -> false
                    index - numRequired < writableUnsignedCount -> true
                    else -> false
                }
                return AccountMeta(publicKey = accountKeys[index], signer = isSigner, writable = isWritable)
            }

            val instructions = ArrayList<Instruction>(numInstructions)
            repeat(numInstructions) {
                val programIdIndex = serialized[offset++].toInt() and 0xFF
                val (numKeys, keyLenBytes) = shortVecDecode(serialized, offset)
                offset += keyLenBytes
                val keys = ArrayList<AccountMeta>(numKeys)
                repeat(numKeys) {
                    val accIdx = serialized[offset++].toInt() and 0xFF
                    keys.add(metaForIndex(accIdx))
                }
                val (dataLen, dataLenBytes) = shortVecDecode(serialized, offset)
                offset += dataLenBytes
                val data = serialized.copyOfRange(offset, offset + dataLen)
                offset += dataLen
                instructions.add(
                    BaseInstruction(
                        programId = accountKeys[programIdIndex],
                        keys = keys,
                        data = data
                    )
                )
            }

            return TransactionMessage(
                feePayer = accountKeys.first(),
                recentBlockhash = recentBlockhash,
                instructions = instructions,
                addressLookupTableAccounts = emptyList()
            )
        }

        private fun shortVecDecode(bytes: ByteArray, offset: Int): Pair<Int, Int> {
            var value = 0
            var shift = 0
            var read = 0
            while (true) {
                val byte = bytes[offset + read].toInt() and 0xFF
                read++
                value = value or ((byte and 0x7F) shl shift)
                if ((byte and 0x80) == 0) break
                shift += 7
                if (shift > 21) error("short-vec length overflow")
            }
            return value to read
        }
    }
}

/**
 * sol4k compatible `Transaction` (legacy).
 *
 * A [Transaction] is a [TransactionMessage] plus a set of signatures. Call
 * [addSignature] with a [Keypair] to produce a signed blob ready for
 * `Connection.sendTransaction`.
 */
class Transaction(private val message: TransactionMessage) {

    private val artemis: ArtemisTransaction = message.toArtemisTransaction()

    /** Sign with [keypair] and append the signature. */
    fun addSignature(keypair: Keypair) {
        val signer = object : ArtemisSigner {
            override val publicKey = keypair.publicKey.toArtemis()
            override fun sign(message: ByteArray): ByteArray = keypair.sign(message)
        }
        artemis.partialSign(listOf(signer))
    }

    /** Alias that matches sol4k convenience. */
    fun sign(keypair: Keypair) = addSignature(keypair)

    /** Serialize into the full wire format (signatures + message). */
    fun serialize(): ByteArray = artemis.serialize()
}

/**
 * sol4k compatible `VersionedTransaction` (v0).
 *
 * For now this is a thin wrapper that behaves identically to [Transaction]
 * at the serialization boundary because Artemis represents v0 transactions
 * with the same `Transaction` type when lookup tables are absent. ALT-aware
 * callers should use the native Artemis `VersionedTransactionBuilder`.
 */
class VersionedTransaction(private val message: TransactionMessage) {

    private val artemis: ArtemisTransaction = message.toArtemisTransaction()

    fun addSignature(keypair: Keypair) {
        val signer = object : ArtemisSigner {
            override val publicKey = keypair.publicKey.toArtemis()
            override fun sign(message: ByteArray): ByteArray = keypair.sign(message)
        }
        artemis.partialSign(listOf(signer))
    }

    fun sign(keypair: Keypair) = addSignature(keypair)

    fun serialize(): ByteArray = artemis.serialize()
}

/**
 * sol4k compatible `AddressLookupTableAccount`.
 *
 * Holds the ATL address plus the list of addresses resolved from it.
 */
data class AddressLookupTableAccount(
    val address: PublicKey,
    val addresses: List<PublicKey>
)
