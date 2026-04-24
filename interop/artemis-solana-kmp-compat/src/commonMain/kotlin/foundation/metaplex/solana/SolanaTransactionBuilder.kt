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
 * and signing to it. Mirrors the upstream method surface end-to-end so call
 * sites using any of `addInstruction`, `add`, `setRecentBlockHash`, `sign`,
 * `partialSign`, `addSignature`, `verifySignatures`, `compileMessage`,
 * `serializeMessage`, and `serialize` keep compiling without changes.
 */
class Transaction internal constructor(internal val artemis: ArtemisTransaction) {

    constructor() : this(ArtemisTransaction())

    companion object {
        /**
         * Reconstruct a [Transaction] from a serialized buffer. Matches upstream
         * `Transaction.from(buffer)`. Callers typically use this when they
         * receive a base64 transaction over the wire (SIWS responses, MWA
         * sign-and-send fallbacks, saved QR payloads) and need to inspect it
         * before re-signing or re-broadcasting.
         */
        @JvmStatic
        fun from(buffer: ByteArray): Transaction = Transaction(ArtemisTransaction.from(buffer))

        /**
         * Build a fully-signed [Transaction] from a compiled message and a
         * list of pre-computed signatures. Upstream exposes this as
         * `Transaction.populate(message, signatures)` for relay services and
         * cosigner flows; Artemis honours the same shape.
         *
         * Signature strings are base58-encoded. An empty or blank string at
         * index [i] leaves that signer slot unsigned (the upstream convention
         * for partial-sign scenarios).
         *
         * Implementation: we reconstruct the full wire-format transaction by
         * prefixing a short-vec signature count, each 64-byte signature slot
         * (zeroed when absent), and the serialized message - then feed the
         * whole thing into the core `Transaction.from(bytes)` parser so the
         * resulting object goes back through the same validation path the
         * wire bytes would.
         */
        @JvmStatic
        fun populate(
            message: foundation.metaplex.solana.transactions.SolanaMessage,
            signatures: List<String>,
        ): Transaction {
            val msgBytes = message.serialize()
            // Short-vec prefix for the signature count.
            val sigCountBytes = shortVecEncode(signatures.size)
            // 64 zero bytes per absent signature, real bytes for present ones.
            val sigSection = ByteArray(signatures.size * 64)
            signatures.forEachIndexed { i, sigB58 ->
                if (sigB58.isNotBlank()) {
                    val decoded = com.selenus.artemis.runtime.Base58.decode(sigB58)
                    require(decoded.size == 64) {
                        "populate: signature $i must be 64 bytes, got ${decoded.size}"
                    }
                    decoded.copyInto(sigSection, destinationOffset = i * 64)
                }
            }
            val wire = sigCountBytes + sigSection + msgBytes
            return Transaction(ArtemisTransaction.from(wire))
        }

        /**
         * Encode [n] as Solana's compact short-vec length prefix. Kept
         * private to the companion; callers that need the public helper
         * should import [foundation.metaplex.solana.transactions.Shortvec].
         */
        private fun shortVecEncode(n: Int): ByteArray {
            if (n == 0) return byteArrayOf(0)
            val out = ArrayList<Byte>(4)
            var v = n
            while (true) {
                var b = v and 0x7F
                v = v ushr 7
                if (v == 0) {
                    out.add(b.toByte()); break
                } else {
                    b = b or 0x80
                    out.add(b.toByte())
                }
            }
            return out.toByteArray()
        }
    }

    /** Append one or more instructions. */
    fun addInstruction(vararg instruction: TransactionInstruction): Transaction {
        instruction.forEach { artemis.addInstruction(it.toArtemis()) }
        return this
    }

    /** Alias for [addInstruction] to match upstream's shorter name. */
    fun add(vararg instruction: TransactionInstruction): Transaction = addInstruction(*instruction)

    /** Set the recent blockhash the message will commit to. */
    fun setRecentBlockHash(recentBlockhash: String) {
        artemis.recentBlockhash = recentBlockhash
    }

    /** Sign with every supplied signer, replacing any prior signatures. */
    suspend fun sign(vararg signer: foundation.metaplex.signer.Signer): Unit = sign(signer.toList())

    /** List overload of [sign]. */
    suspend fun sign(signers: List<foundation.metaplex.signer.Signer>) {
        require(signers.isNotEmpty()) { "at least one signer required" }
        if (artemis.feePayer == null) {
            artemis.feePayer = com.selenus.artemis.runtime.Pubkey(signers.first().publicKey.toByteArray())
        }
        val msg = artemis.compileMessage()
        val msgBytes = msg.serialize()
        signers.forEach { s ->
            val sig = s.sign(msgBytes)
            artemis.addSignature(com.selenus.artemis.runtime.Pubkey(s.publicKey.toByteArray()), sig)
        }
    }

    /** Sign with a subset of signers without resetting others. Matches upstream. */
    suspend fun partialSign(vararg signers: foundation.metaplex.signer.Signer) {
        require(signers.isNotEmpty()) { "at least one signer required for partialSign" }
        val msg = artemis.compileMessage()
        val msgBytes = msg.serialize()
        signers.forEach { s ->
            val sig = s.sign(msgBytes)
            artemis.addSignature(com.selenus.artemis.runtime.Pubkey(s.publicKey.toByteArray()), sig)
        }
    }

    /** Manually attach a pre-computed signature. */
    fun addSignature(pubkey: PublicKey, signature: ByteArray) {
        artemis.addSignature(com.selenus.artemis.runtime.Pubkey(pubkey.toByteArray()), signature)
    }

    /**
     * Verify every signature on this transaction against the compiled message.
     * Returns `true` when every attached signature passes ed25519 verification.
     * Matches upstream `verifySignatures()` semantics: empty maps return true,
     * a single failed verification short-circuits to false.
     */
    suspend fun verifySignatures(): Boolean {
        if (artemis.signatures.isEmpty()) return true
        val msg = artemis.compileMessage().serialize()
        return artemis.signatures.all { (pubkey, signature) ->
            pubkey.verify(signature, msg)
        }
    }

    /** Compile the current transaction to a solana-kmp-shaped message wrapper. */
    fun compileMessage(): foundation.metaplex.solana.transactions.SolanaMessage =
        foundation.metaplex.solana.transactions.SolanaMessage.wrap(artemis.compileMessage())

    /** Serialize just the compiled message (without signatures). */
    fun serializeMessage(): ByteArray = artemis.compileMessage().serialize()

    /** Serialize the complete signed transaction. */
    fun serialize(config: foundation.metaplex.solana.transactions.SerializeConfig = foundation.metaplex.solana.transactions.SerializeConfig()): SerializedTransaction {
        // SerializeConfig mirrors upstream: when `requireAllSignatures` is set,
        // every required-signer account in the compiled message must have a
        // signature attached. The Artemis signatures map stores pubkey -> bytes
        // so a missing slot shows up as a pubkey that is in the message's
        // signer list but not in the signatures map.
        if (config.requireAllSignatures) {
            val compiled = artemis.compileMessage()
            val requiredSigners = compiled.accountKeys.take(compiled.header.numRequiredSignatures)
            val present = artemis.signatures.keys
            val missing = requiredSigners.filterNot { it in present }
            require(missing.isEmpty()) {
                "requireAllSignatures=true but missing signatures for: ${missing.joinToString { it.toBase58() }}"
            }
        }
        return SerializedTransaction(artemis.serialize())
    }
}

/** Internal conversion from the solana-kmp shape back to Artemis instructions. */
internal fun TransactionInstruction.toArtemis(): ArtemisInstruction = ArtemisInstruction(
    programId = com.selenus.artemis.runtime.Pubkey(programId.toByteArray()),
    accounts = keys.map { meta ->
        ArtemisAccountMeta(
            pubkey = com.selenus.artemis.runtime.Pubkey(meta.publicKey.toByteArray()),
            isSigner = meta.isSigner,
            isWritable = meta.isWritable,
        )
    },
    data = data,
)

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
