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
class TransactionMessage internal constructor(
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
     * Return a mutable [Builder] primed with this message's fields. Closes the
     * upstream sol4k gap (sol4k#131) where a round-tripped, deserialized
     * message could not be safely edited because every field was private.
     */
    fun toBuilder(): Builder = Builder()
        .setFeePayer(feePayer)
        .setRecentBlockhash(recentBlockhash)
        .apply { instructions.forEach { addInstruction(it) } }
        .apply { addressLookupTableAccounts.forEach { addAddressLookupTableAccount(it) } }

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

    /** Mutable builder used by [toBuilder] and direct construction. */
    class Builder {
        private var feePayer: PublicKey? = null
        private var recentBlockhash: String? = null
        private val instructions: MutableList<Instruction> = mutableListOf()
        private val addressLookupTableAccounts: MutableList<AddressLookupTableAccount> =
            mutableListOf()

        fun setFeePayer(feePayer: PublicKey): Builder = apply { this.feePayer = feePayer }
        fun setRecentBlockhash(blockhash: String): Builder = apply { this.recentBlockhash = blockhash }
        fun addInstruction(instruction: Instruction): Builder = apply { instructions.add(instruction) }
        fun addAddressLookupTableAccount(account: AddressLookupTableAccount): Builder =
            apply { addressLookupTableAccounts.add(account) }

        fun build(): TransactionMessage = TransactionMessage(
            feePayer = requireNotNull(feePayer) { "feePayer not set" },
            recentBlockhash = requireNotNull(recentBlockhash) { "recentBlockhash not set" },
            instructions = instructions.toList(),
            addressLookupTableAccounts = addressLookupTableAccounts.toList()
        )
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
            val numRequired = readByte(serialized, offset).also { offset++ }
            val numReadonlySigned = readByte(serialized, offset).also { offset++ }
            val numReadonlyUnsigned = readByte(serialized, offset).also { offset++ }
            val (numAccounts, accountLenBytes) = shortVecDecode(serialized, offset)
            offset += accountLenBytes
            require(offset + 32L * numAccounts + 32 <= serialized.size) {
                "message truncated: declared $numAccounts account keys"
            }
            val accountKeys = ArrayList<PublicKey>(numAccounts)
            repeat(numAccounts) {
                accountKeys.add(PublicKey(serialized.copyOfRange(offset, offset + 32)))
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
                require(offset < serialized.size) { "message truncated in instruction header" }
                val programIdIndex = serialized[offset++].toInt() and 0xFF
                val (numKeys, keyLenBytes) = shortVecDecode(serialized, offset)
                offset += keyLenBytes
                require(offset + numKeys <= serialized.size) { "message truncated in key list" }
                val keys = ArrayList<AccountMeta>(numKeys)
                repeat(numKeys) {
                    val accIdx = serialized[offset++].toInt() and 0xFF
                    keys.add(metaForIndex(accIdx))
                }
                val (dataLen, dataLenBytes) = shortVecDecode(serialized, offset)
                offset += dataLenBytes
                require(offset + dataLen <= serialized.size) { "message truncated in instruction data" }
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

        private fun readByte(bytes: ByteArray, offset: Int): Int {
            require(offset < bytes.size) { "message truncated at byte $offset" }
            return bytes[offset].toInt() and 0xFF
        }

        private fun shortVecDecode(bytes: ByteArray, offset: Int): Pair<Int, Int> {
            var value = 0
            var shift = 0
            var read = 0
            while (true) {
                require(offset + read < bytes.size) {
                    "short-vec length truncated at offset ${offset + read}"
                }
                val byte = bytes[offset + read].toInt() and 0xFF
                read++
                value = value or ((byte and 0x7F) shl shift)
                if ((byte and 0x80) == 0) break
                shift += 7
                require(shift <= 21) { "short-vec length overflow" }
            }
            return value to read
        }
    }
}

/**
 * sol4k compatible `Transaction` (legacy).
 *
 * Upstream sol4k exposes two primary constructors plus `Transaction.from(base64)`.
 * Mutating the [signatures] list directly matches upstream's internal mechanism;
 * normal callers use [sign] or [addSignature] instead.
 */
class Transaction private constructor(
    val message: TransactionMessage,
    internal val _signatures: MutableList<String>
) {

    constructor(
        recentBlockhash: String,
        instructions: List<Instruction>,
        feePayer: PublicKey
    ) : this(
        message = TransactionMessage(
            feePayer = feePayer,
            recentBlockhash = recentBlockhash,
            instructions = instructions,
            addressLookupTableAccounts = emptyList()
        ),
        _signatures = mutableListOf()
    )

    constructor(
        recentBlockhash: String,
        instruction: Instruction,
        feePayer: PublicKey
    ) : this(recentBlockhash, listOf(instruction), feePayer)

    constructor(message: TransactionMessage) : this(message, mutableListOf())

    /** Live view of the signature list, in upstream-compatible base58 form. */
    val signatures: MutableList<String> get() = _signatures

    /** Sign with [keypair] and append the resulting signature. */
    fun sign(keypair: Keypair) {
        val compiled = message.toArtemisTransaction().compileMessage().serialize()
        val sig = keypair.sign(compiled)
        _signatures.add(com.selenus.artemis.runtime.Base58.encode(sig))
    }

    /** Append a pre-computed base58 signature. Matches upstream sol4k exactly. */
    fun addSignature(signature: String) {
        _signatures.add(signature)
    }

    /** Serialize into the full wire format (signatures + message). */
    fun serialize(): ByteArray {
        val messageBytes = message.serialize()
        val sigCountBytes = shortVecEncode(_signatures.size)
        val sigsBytes = ByteArray(_signatures.size * 64)
        _signatures.forEachIndexed { i, sig ->
            val raw = com.selenus.artemis.runtime.Base58.decode(sig)
            require(raw.size == 64) { "signature $i must be 64 bytes, got ${raw.size}" }
            raw.copyInto(sigsBytes, i * 64)
        }
        return sigCountBytes + sigsBytes + messageBytes
    }

    companion object {
        /**
         * Decode a base64-encoded transaction (signatures + message) back into
         * a [Transaction]. Matches `org.sol4k.Transaction.from` byte-for-byte.
         */
        @JvmStatic
        fun from(encodedTransaction: String): Transaction {
            val bytes = com.selenus.artemis.runtime.PlatformBase64.decode(encodedTransaction)
            var offset = 0
            val (sigCount, sigLenBytes) = TransactionMessage.decodeShortVec(bytes, offset)
            offset += sigLenBytes
            require(offset + sigCount.toLong() * 64 <= bytes.size) {
                "transaction truncated: declared $sigCount signatures"
            }
            val sigs = mutableListOf<String>()
            repeat(sigCount) {
                val raw = bytes.copyOfRange(offset, offset + 64)
                sigs.add(com.selenus.artemis.runtime.Base58.encode(raw))
                offset += 64
            }
            val messageBytes = bytes.copyOfRange(offset, bytes.size)
            val message = TransactionMessage.deserialize(messageBytes)
            return Transaction(message = message, _signatures = sigs)
        }
    }
}

/**
 * sol4k compatible `VersionedTransaction` (v0).
 *
 * For now this is a thin wrapper that behaves identically to [Transaction]
 * at the serialization boundary because Artemis represents v0 transactions
 * with the same `Transaction` type when lookup tables are absent. ALT-aware
 * callers should use the native Artemis `VersionedTransactionBuilder`.
 */
class VersionedTransaction private constructor(
    val message: TransactionMessage,
    internal val _signatures: MutableList<String?>
) {

    constructor(message: TransactionMessage) : this(message, MutableList(1) { null })

    val signatures: MutableList<String?> get() = _signatures

    fun sign(keypair: Keypair) {
        val compiled = message.toArtemisTransaction().compileMessage().serialize()
        val sig = keypair.sign(compiled)
        if (_signatures.isEmpty() || _signatures[0] == null) {
            if (_signatures.isEmpty()) _signatures.add(null)
            _signatures[0] = com.selenus.artemis.runtime.Base58.encode(sig)
        } else {
            _signatures.add(com.selenus.artemis.runtime.Base58.encode(sig))
        }
    }

    fun addSignature(signature: String) {
        if (_signatures.isNotEmpty() && _signatures[0] == null) {
            _signatures[0] = signature
        } else {
            _signatures.add(signature)
        }
    }

    fun serialize(): ByteArray {
        val messageBytes = message.serialize()
        val filled = _signatures.map { it ?: "" }
        val sigCountBytes = shortVecEncode(filled.size)
        val sigsBytes = ByteArray(filled.size * 64)
        filled.forEachIndexed { i, sig ->
            if (sig.isEmpty()) return@forEachIndexed
            val raw = com.selenus.artemis.runtime.Base58.decode(sig)
            require(raw.size == 64) { "signature $i must be 64 bytes, got ${raw.size}" }
            raw.copyInto(sigsBytes, i * 64)
        }
        return sigCountBytes + sigsBytes + messageBytes
    }

    /**
     * Approximate the network fee the validator will charge for this
     * transaction at [lamportsPerSignature]. Matches the sol4k upstream shape;
     * returns the product of the signature slot count (bounded to at least 1)
     * and the per-signature price.
     *
     * This is a client-side hint only: actual fees depend on compute-budget
     * instructions, priority fees, and network conditions. For a precise
     * number, call `Connection.getFeeForMessage(message)` instead.
     */
    fun calculateFee(lamportsPerSignature: Int): java.math.BigDecimal {
        val sigs = _signatures.size.coerceAtLeast(1)
        return java.math.BigDecimal.valueOf(sigs.toLong() * lamportsPerSignature.toLong())
    }

    companion object {
        @JvmStatic
        fun from(encodedTransaction: String): VersionedTransaction {
            val bytes = com.selenus.artemis.runtime.PlatformBase64.decode(encodedTransaction)
            var offset = 0
            val (sigCount, sigLenBytes) = TransactionMessage.decodeShortVec(bytes, offset)
            offset += sigLenBytes
            require(offset + sigCount.toLong() * 64 <= bytes.size) {
                "transaction truncated: declared $sigCount signatures"
            }
            val sigs = mutableListOf<String?>()
            repeat(sigCount) {
                val raw = bytes.copyOfRange(offset, offset + 64)
                val isEmpty = raw.all { it == 0.toByte() }
                sigs.add(if (isEmpty) null else com.selenus.artemis.runtime.Base58.encode(raw))
                offset += 64
            }
            val messageBytes = bytes.copyOfRange(offset, bytes.size)
            return VersionedTransaction(
                message = TransactionMessage.deserialize(messageBytes),
                _signatures = sigs
            )
        }
    }
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

private fun shortVecEncode(value: Int): ByteArray {
    require(value >= 0) { "short-vec value must be non-negative" }
    val out = mutableListOf<Byte>()
    var v = value
    while (true) {
        var elem = v and 0x7F
        v = v ushr 7
        if (v == 0) {
            out.add(elem.toByte())
            break
        }
        elem = elem or 0x80
        out.add(elem.toByte())
    }
    return out.toByteArray()
}

/** Exposed to the file-level companion objects above. */
internal fun TransactionMessage.Companion.decodeShortVec(bytes: ByteArray, offset: Int): Pair<Int, Int> {
    var value = 0
    var shift = 0
    var read = 0
    while (true) {
        require(offset + read < bytes.size) {
            "short-vec length truncated at offset ${offset + read}"
        }
        val byte = bytes[offset + read].toInt() and 0xFF
        read++
        value = value or ((byte and 0x7F) shl shift)
        if ((byte and 0x80) == 0) break
        shift += 7
        require(shift <= 21) { "short-vec length overflow" }
    }
    return value to read
}
