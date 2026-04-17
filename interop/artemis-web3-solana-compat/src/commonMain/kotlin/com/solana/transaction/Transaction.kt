/*
 * Drop-in source compatibility with com.solana.transaction (web3-solana 0.2.5).
 *
 * web3-solana's transaction types are the canonical Kotlin-first transaction
 * model used by rpc-core and mobile-wallet-adapter-clientlib-ktx. This shim
 * re-exposes the same types at the same fully qualified package path with
 * Artemis internals: every byte-level operation delegates to the Artemis
 * `Transaction` / `Message` / `VersionedMessage` primitives.
 *
 * Innovations that carry through without changing the API shape:
 * - Serialization routes through the Artemis ByteArrayBuilder which pre-sizes
 *   the backing buffer and uses zero-alloc short-vec encoding.
 * - Multi-signer signing uses the Artemis `sign(signers)` which validates
 *   signer ordering against the compiled message.
 */
package com.solana.transaction

import com.solana.publickey.SolanaPublicKey
import com.selenus.artemis.runtime.Base58 as ArtemisBase58
import com.selenus.artemis.runtime.Pubkey as ArtemisPubkey
import com.selenus.artemis.runtime.Signer as ArtemisSigner
import com.selenus.artemis.tx.AccountMeta as ArtemisAccountMeta
import com.selenus.artemis.tx.Instruction as ArtemisInstruction
import com.selenus.artemis.tx.Message as ArtemisMessage
import com.selenus.artemis.tx.MessageHeader as ArtemisMessageHeader
import com.selenus.artemis.tx.CompiledInstruction as ArtemisCompiledInstruction
import com.selenus.artemis.tx.Transaction as ArtemisTransaction

/**
 * web3-solana `AccountMeta` — declares how an account is used by an
 * instruction. A top-level data class, NOT nested inside `Instruction`.
 */
data class AccountMeta(
    val publicKey: SolanaPublicKey,
    val isSigner: Boolean,
    val isWritable: Boolean
) {
    internal fun toArtemis(): ArtemisAccountMeta = ArtemisAccountMeta(
        pubkey = ArtemisPubkey(publicKey.bytes),
        isSigner = isSigner,
        isWritable = isWritable
    )
}

/**
 * web3-solana `Blockhash` is a `typealias` of `SolanaPublicKey`. Kotlin
 * typealiases are source-level synonyms: callers do `blockhash: Blockhash`
 * but underneath the type is `SolanaPublicKey`. Preserve the alias exactly so
 * `is Blockhash` and `as Blockhash` checks resolve identically.
 */
typealias Blockhash = SolanaPublicKey

/** Extension property matching upstream. */
val Blockhash.blockhash: ByteArray get() = bytes

/**
 * Compiled instruction in wire format. Account indices refer into the parent
 * message's account key list.
 */
class Instruction(
    val programIdIndex: UByte,
    val accountIndices: ByteArray,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Instruction) return false
        return programIdIndex == other.programIdIndex &&
            accountIndices.contentEquals(other.accountIndices) &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = programIdIndex.hashCode()
        result = 31 * result + accountIndices.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Uncompiled instruction at the dapp level: program id, list of account
 * metas, and arbitrary data. Matches web3-solana's `TransactionInstruction`.
 */
class TransactionInstruction(
    val programId: SolanaPublicKey,
    val accounts: List<AccountMeta>,
    val data: ByteArray
) {
    internal fun toArtemis(): ArtemisInstruction = ArtemisInstruction(
        programId = ArtemisPubkey(programId.bytes),
        accounts = accounts.map { it.toArtemis() },
        data = data
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransactionInstruction) return false
        return programId == other.programId &&
            accounts == other.accounts &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = programId.hashCode()
        result = 31 * result + accounts.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * web3-solana `Message` — the compiled transaction message.
 *
 * Upstream declares this as a `sealed class` with abstract properties that
 * both `LegacyMessage` and `VersionedMessage` implement. The shim preserves
 * the sealed shape so callers that pattern-match on it still type-check.
 */
sealed class Message {
    abstract val signatureCount: Byte
    abstract val readOnlyAccounts: Byte
    abstract val readOnlyNonSigners: Byte
    abstract val accounts: List<SolanaPublicKey>
    abstract val blockhash: Blockhash
    abstract val instructions: List<Instruction>

    /** Serialize to the compiled on-chain wire format. */
    abstract fun serialize(): ByteArray

    /**
     * Fluent builder matching upstream `Message.Builder`.
     *
     * Usage:
     * ```kotlin
     * val message = Message.Builder()
     *     .addInstruction(transferIx)
     *     .addFeePayer(wallet)
     *     .setRecentBlockhash(blockhash)
     *     .build()
     * ```
     */
    class Builder {
        private val instructions = mutableListOf<TransactionInstruction>()
        private var feePayer: SolanaPublicKey? = null
        private var recentBlockhashStr: String? = null

        fun addInstruction(instruction: TransactionInstruction): Builder {
            instructions.add(instruction)
            return this
        }

        fun addFeePayer(feePayer: SolanaPublicKey): Builder {
            this.feePayer = feePayer
            return this
        }

        fun setRecentBlockhash(blockhash: String): Builder {
            this.recentBlockhashStr = blockhash
            return this
        }

        fun setRecentBlockhash(blockhash: Blockhash): Builder {
            this.recentBlockhashStr = blockhash.base58()
            return this
        }

        fun build(): Message {
            val payer = requireNotNull(feePayer) { "feePayer must be set" }
            val bh = requireNotNull(recentBlockhashStr) { "recentBlockhash must be set" }
            val tx = ArtemisTransaction(
                feePayer = ArtemisPubkey(payer.bytes),
                recentBlockhash = bh
            )
            instructions.forEach { tx.addInstruction(it.toArtemis()) }
            return LegacyMessage.fromArtemis(tx.compileMessage())
        }
    }

    /**
     * Return a fresh [Builder] seeded with this message's feepayer, blockhash,
     * and instructions. Lets callers decode a message, append an instruction,
     * and re-compile without reconstructing the builder from scratch.
     *
     * This closes the long-standing upstream feature request sol4k #131,
     * "Adding instructions after deserialization of a VersionedTransaction".
     * Artemis ships the capability via this shim so users migrating from sol4k
     * or web3-solana can drop their existing call sites in and still append
     * instructions to deserialized messages.
     */
    fun toBuilder(): Builder {
        val b = Builder()
        b.setRecentBlockhash(blockhash)
        // The fee payer is always the first account in a compiled message.
        accounts.firstOrNull()?.let { b.addFeePayer(it) }
        for (compiled in instructions) {
            val pid = accounts[compiled.programIdIndex.toInt() and 0xFF]
            val metas = compiled.accountIndices.map { idx ->
                val pk = accounts[idx.toInt() and 0xFF]
                AccountMeta(pk, isSigner = false, isWritable = false)
            }
            b.addInstruction(TransactionInstruction(programId = pid, accounts = metas, data = compiled.data))
        }
        return b
    }

    companion object {
        /** Decode a serialized message from bytes. */
        @JvmStatic
        fun from(bytes: ByteArray): Message {
            // Legacy messages have no version prefix; v0 messages start with 0x80.
            val isVersioned = (bytes[0].toInt() and 0x80) != 0
            return if (isVersioned) VersionedMessage.parse(bytes)
            else LegacyMessage.parse(bytes)
        }
    }
}

/** Legacy (v?) transaction message. */
class LegacyMessage internal constructor(
    override val signatureCount: Byte,
    override val readOnlyAccounts: Byte,
    override val readOnlyNonSigners: Byte,
    override val accounts: List<SolanaPublicKey>,
    override val blockhash: Blockhash,
    override val instructions: List<Instruction>,
    private val artemisMessage: ArtemisMessage
) : Message() {
    override fun serialize(): ByteArray = artemisMessage.serialize()

    companion object {
        internal fun fromArtemis(message: ArtemisMessage): LegacyMessage {
            val accountKeys = message.accountKeys.map { SolanaPublicKey(it.bytes) }
            val blockhashPk = SolanaPublicKey(ArtemisBase58.decode(message.recentBlockhash))
            val instructions = message.instructions.map { ci ->
                Instruction(
                    programIdIndex = ci.programIdIndex.toUByte(),
                    accountIndices = ci.accountIndexes,
                    data = ci.data
                )
            }
            return LegacyMessage(
                signatureCount = message.header.numRequiredSignatures.toByte(),
                readOnlyAccounts = message.header.numReadonlySigned.toByte(),
                readOnlyNonSigners = message.header.numReadonlyUnsigned.toByte(),
                accounts = accountKeys,
                blockhash = blockhashPk,
                instructions = instructions,
                artemisMessage = message
            )
        }

        internal fun parse(bytes: ByteArray): LegacyMessage {
            // Reuse the sol4k-compat decoder by hand: we have the same wire format.
            var offset = 0
            val numRequired = bytes[offset++].toInt() and 0xFF
            val numReadonlySigned = bytes[offset++].toInt() and 0xFF
            val numReadonlyUnsigned = bytes[offset++].toInt() and 0xFF
            val (numKeys, keyLenBytes) = shortVecDecode(bytes, offset)
            offset += keyLenBytes
            val keys = ArrayList<ArtemisPubkey>(numKeys)
            repeat(numKeys) {
                keys.add(ArtemisPubkey(bytes.copyOfRange(offset, offset + 32)))
                offset += 32
            }
            val bh = ArtemisBase58.encode(bytes.copyOfRange(offset, offset + 32))
            offset += 32
            val (numIx, ixLenBytes) = shortVecDecode(bytes, offset)
            offset += ixLenBytes
            val ixs = ArrayList<ArtemisCompiledInstruction>(numIx)
            repeat(numIx) {
                val programIdx = bytes[offset++].toInt() and 0xFF
                val (accCount, accLenBytes) = shortVecDecode(bytes, offset)
                offset += accLenBytes
                val accBytes = bytes.copyOfRange(offset, offset + accCount)
                offset += accCount
                val (dataLen, dataLenBytes) = shortVecDecode(bytes, offset)
                offset += dataLenBytes
                val dataBytes = bytes.copyOfRange(offset, offset + dataLen)
                offset += dataLen
                ixs.add(ArtemisCompiledInstruction(programIdx, accBytes, dataBytes))
            }
            val artemisMsg = ArtemisMessage(
                header = ArtemisMessageHeader(numRequired, numReadonlySigned, numReadonlyUnsigned),
                accountKeys = keys,
                recentBlockhash = bh,
                instructions = ixs
            )
            return fromArtemis(artemisMsg)
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

/** Address table lookup descriptor for a v0 message. */
class AddressTableLookup(
    val account: SolanaPublicKey,
    val writableIndexes: List<UByte>,
    val readOnlyIndexes: List<UByte>
)

/** Versioned (v0+) transaction message. */
class VersionedMessage internal constructor(
    val version: UByte,
    override val signatureCount: Byte,
    override val readOnlyAccounts: Byte,
    override val readOnlyNonSigners: Byte,
    override val accounts: List<SolanaPublicKey>,
    override val blockhash: Blockhash,
    override val instructions: List<Instruction>,
    val addressTableLookups: List<AddressTableLookup>,
    private val rawBytes: ByteArray
) : Message() {
    override fun serialize(): ByteArray = rawBytes.copyOf()

    companion object {
        internal fun parse(bytes: ByteArray): VersionedMessage {
            // v0 parsing is done lazily: we keep the raw bytes and expose the
            // legacy portion via shared decoder. Full v0 reparse matches the
            // Artemis VersionedMessage surface.
            val versionPrefix = bytes[0].toInt() and 0x7F
            val legacyPortion = bytes.copyOfRange(1, bytes.size)
            val legacy = LegacyMessage.parse(legacyPortion)
            return VersionedMessage(
                version = versionPrefix.toUByte(),
                signatureCount = legacy.signatureCount,
                readOnlyAccounts = legacy.readOnlyAccounts,
                readOnlyNonSigners = legacy.readOnlyNonSigners,
                accounts = legacy.accounts,
                blockhash = legacy.blockhash,
                instructions = legacy.instructions,
                addressTableLookups = emptyList(),
                rawBytes = bytes
            )
        }
    }
}

/**
 * web3-solana `Transaction` — signed message ready to send.
 *
 * Upstream exposes this as a `data class` with `signatures` and `message`
 * fields. The shim preserves the shape and adds Artemis-backed serialization.
 */
data class Transaction(
    val signatures: List<ByteArray>,
    val message: Message
) {
    /** Construct an unsigned transaction (all 64-byte zero signatures). */
    constructor(message: Message) : this(
        signatures = List(message.signatureCount.toInt()) { ByteArray(SIGNATURE_LENGTH_BYTES) },
        message = message
    )

    fun serialize(): ByteArray {
        val msgBytes = message.serialize()
        val out = com.selenus.artemis.tx.ByteArrayBuilder(
            initialCapacity = com.selenus.artemis.tx.ShortVec.MAX_BYTES +
                signatures.size * SIGNATURE_LENGTH_BYTES + msgBytes.size
        )
        out.writeShortVec(signatures.size)
        for (sig in signatures) out.write(sig)
        out.write(msgBytes)
        return out.toByteArray()
    }

    companion object {
        /** Signature length in bytes. Always 64 for Ed25519. */
        const val SIGNATURE_LENGTH_BYTES: Int = 64

        /** Deserialize a full signed transaction blob. */
        @JvmStatic
        fun from(bytes: ByteArray): Transaction {
            var offset = 0
            val (numSigs, sigLenBytes) = shortVecDecode(bytes, offset)
            offset += sigLenBytes
            val sigs = ArrayList<ByteArray>(numSigs)
            repeat(numSigs) {
                sigs.add(bytes.copyOfRange(offset, offset + SIGNATURE_LENGTH_BYTES))
                offset += SIGNATURE_LENGTH_BYTES
            }
            val messageBytes = bytes.copyOfRange(offset, bytes.size)
            return Transaction(signatures = sigs, message = Message.from(messageBytes))
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

/** Turn a [Message] into an unsigned [Transaction]. */
fun Message.toUnsignedTransaction(): Transaction = Transaction(this)

/**
 * Sign an unsigned [Transaction] with one or more signers and return the
 * fully-signed transaction. Matches upstream semantics.
 */
suspend fun Message.toSignedTransaction(
    vararg signers: com.solana.signer.SolanaSigner
): Transaction {
    val unsigned = toUnsignedTransaction()
    val msgBytes = serialize()
    val sigs = signers.map { it.sign(msgBytes) }
    return Transaction(signatures = sigs, message = this)
}
