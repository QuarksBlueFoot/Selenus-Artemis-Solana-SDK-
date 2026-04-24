/*
 * Drop-in source compatibility with foundation.metaplex.solana.transactions.*
 *
 * Upstream solana-kmp splits transaction construction into several small
 * carrier types (MessageHeader, CompiledInstruction, SignaturePubkeyPair,
 * NonceInformation, SerializeConfig) plus a `Shortvec` encoder. Every one of
 * those is declared here so call sites keep compiling against Artemis.
 *
 * Nothing here talks to the network; these are value objects and thin
 * wire-format helpers.
 */
package foundation.metaplex.solana.transactions

import com.selenus.artemis.runtime.Pubkey as ArtemisPubkey
import foundation.metaplex.solana.TransactionInstruction
import foundation.metaplex.solanapublickeys.PublicKey

/** The raw type of a transaction signature. Matches upstream typealias. */
typealias TransactionSignature = ByteArray

/** Alias upstream uses for the compiled-message blob. */
typealias SerializedTransactionMessage = ByteArray

/** Alias upstream uses for the final on-wire transaction bytes. */
typealias SerializedTransaction = ByteArray

/** Alias upstream uses for decoded error strings. */
typealias TransactionError = String

/** Canonical Solana transaction size ceiling (packet - IP/UDP headers). */
const val TRANSACTION_SIZE_LIMIT: Int = 1232

/** Canonical signature length. */
const val SIGNATURE_LENGTH: Int = 64

/**
 * The three-byte message header that prefixes a compiled Solana transaction.
 * Matches upstream. `toByteArray` emits exactly three bytes in the
 * `[numRequiredSignatures, numReadonlySignedAccounts, numReadonlyUnsignedAccounts]`
 * order, which is the same layout Artemis `artemis-tx` writes.
 */
data class MessageHeader(
    var numRequiredSignatures: Byte = 0,
    var numReadonlySignedAccounts: Byte = 0,
    var numReadonlyUnsignedAccounts: Byte = 0,
) {
    fun toByteArray(): ByteArray = byteArrayOf(
        numRequiredSignatures,
        numReadonlySignedAccounts,
        numReadonlyUnsignedAccounts,
    )

    companion object {
        /** The constant three-byte length of the header. */
        const val HEADER_LENGTH: Int = 3
    }
}

/**
 * Compiled instruction shape carried inside a compiled message.
 *
 * `data` is base58-encoded per the upstream convention.
 */
data class CompiledInstruction(
    val programIdIndex: Int,
    val accounts: List<Int>,
    val data: String,
)

/**
 * Signature paired with the pubkey that produced it. Upstream uses this in
 * `SolanaTransaction.signatures`, so any code that iterates that list needs
 * this exact name / field layout.
 */
data class SignaturePubkeyPair(
    var signature: TransactionSignature?,
    val publicKey: PublicKey,
)

/**
 * Carrier for durable-nonce signing. Upstream sets it on the transaction to
 * tell the compiler to prepend an `advanceNonce` instruction and use the
 * provided nonce as the message's recent blockhash.
 */
data class NonceInformation(
    val nonce: String,
    val nonceInstruction: TransactionInstruction,
)

/**
 * Configuration knobs for `Transaction.serialize`. Upstream defaults match
 * web3.js: both checks on by default, callers opt out for partial signing.
 */
data class SerializeConfig(
    val requireAllSignatures: Boolean = true,
    val verifySignatures: Boolean = true,
)

/**
 * `Shortvec` encoder / decoder. Solana transactions use this compact variable
 * length prefix for account lists, instruction counts, etc. Upstream ships
 * the two methods below - matched bit-for-bit here.
 */
object Shortvec {
    /**
     * Encode a non-negative integer as a little-endian short-vec prefix.
     * Uses the 7-bit-with-continuation encoding the Solana transaction
     * format specifies.
     */
    fun encodeLength(len: Int): ByteArray {
        require(len >= 0) { "length must be non-negative" }
        if (len == 0) return byteArrayOf(0)
        val out = ArrayList<Byte>(4)
        var value = len
        while (true) {
            var b = value and 0x7F
            value = value ushr 7
            if (value == 0) {
                out.add(b.toByte())
                break
            } else {
                b = b or 0x80
                out.add(b.toByte())
            }
        }
        return out.toByteArray()
    }

    /**
     * Inverse of [encodeLength]. Returns `(decodedLength, remainingBytes)`
     * so callers can chain the decode across a longer buffer. Matches the
     * upstream contract exactly.
     */
    fun decodeLength(bytes: ByteArray): Pair<Int, ByteArray> {
        var value = 0
        var shift = 0
        var consumed = 0
        while (consumed < bytes.size) {
            val b = bytes[consumed].toInt() and 0xFF
            consumed++
            value = value or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return value to bytes.copyOfRange(consumed, bytes.size)
    }
}

// ---------------------------------------------------------------------------
// Internal helpers (not part of the upstream public surface).
// ---------------------------------------------------------------------------

internal fun PublicKey.toArtemis(): ArtemisPubkey = ArtemisPubkey(this.toByteArray())
