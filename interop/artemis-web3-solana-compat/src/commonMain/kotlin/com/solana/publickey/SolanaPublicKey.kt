/*
 * Drop-in source compatibility with com.solana.publickey (web3-solana 0.2.5).
 *
 * web3-solana is the Solana Mobile team's Kotlin-first transaction SDK. Its
 * `SolanaPublicKey` is the canonical public key type used across mobile-wallet-
 * adapter-clientlib-ktx, rpc-core, and the transaction builder. This shim
 * re-publishes the upstream package with Artemis internals: every decode,
 * encode, PDA, and equality check delegates to com.selenus.artemis.runtime.Pubkey.
 */
package com.solana.publickey

import com.selenus.artemis.runtime.Pubkey as ArtemisPubkey

/**
 * Marker interface used by web3-solana. Other SDK layers (`Signer`,
 * `TransactionInstruction`) accept the supertype so callers can implement
 * their own public key types if they have exotic key schemes. Artemis keeps
 * the supertype as an empty marker so `is PublicKey` checks continue to work.
 */
interface PublicKey {
    val bytes: ByteArray
}

/**
 * web3-solana compatible `SolanaPublicKey`.
 *
 * Field parity with upstream: 32-byte `bytes`, `length`, `address` (base58),
 * `base58()`, DEPRECATED `string()`, `toString()` returns base58. Construction
 * through [from] matches the upstream static factory.
 */
open class SolanaPublicKey(override val bytes: ByteArray) : PublicKey {

    init {
        require(bytes.size == PUBLIC_KEY_LENGTH) {
            "SolanaPublicKey must be exactly $PUBLIC_KEY_LENGTH bytes, got ${bytes.size}"
        }
    }

    private val inner: ArtemisPubkey = ArtemisPubkey(bytes)

    /** Number of bytes in the key. Always 32. */
    val length: Int get() = bytes.size

    /** Base58 representation. */
    val address: String get() = inner.toBase58()

    /** Base58 representation. Matches [toString]. */
    fun base58(): String = inner.toBase58()

    /** Legacy accessor kept for binary compatibility with pre-0.2.5 users. */
    @Deprecated("Use base58() or address", ReplaceWith("base58()"))
    fun string(): String = base58()

    override fun toString(): String = inner.toBase58()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SolanaPublicKey) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    companion object {
        /** Standard 32-byte length of a Solana public key. */
        const val PUBLIC_KEY_LENGTH: Int = 32

        /** Parse a base58-encoded public key. */
        @JvmStatic
        fun from(base58: String): SolanaPublicKey = SolanaPublicKey(ArtemisPubkey(base58).bytes)
    }
}

/**
 * web3-solana compatible `ProgramDerivedAddress`.
 *
 * Holds a PDA plus its bump nonce. The upstream API exposes `find(...)` as a
 * suspend function because the derivation loop is CPU-bound and the designers
 * wanted to keep it off the main thread. The shim mirrors the suspend shape
 * even though Artemis's underlying Pda.findProgramAddress is synchronous, so
 * call sites that `await` on the result continue to compile.
 */
class ProgramDerivedAddress(bytes: ByteArray, val nonce: UByte) : SolanaPublicKey(bytes) {

    companion object {
        /**
         * Derive a PDA for [seeds] under [programId]. Returns a wrapped result
         * matching upstream semantics.
         */
        @JvmStatic
        suspend fun find(seeds: List<ByteArray>, programId: PublicKey): Result<ProgramDerivedAddress> {
            return runCatching {
                val artemisResult = com.selenus.artemis.runtime.Pda.findProgramAddress(
                    seeds = seeds,
                    programId = ArtemisPubkey(programId.bytes)
                )
                ProgramDerivedAddress(artemisResult.address.bytes, artemisResult.bump.toUByte())
            }
        }

        /**
         * Construct a [ProgramDerivedAddress] from known bytes plus nonce.
         * Matches upstream's `create(bytes, nonce)` companion factory.
         */
        @JvmStatic
        fun create(bytes: ByteArray, nonce: UByte): ProgramDerivedAddress =
            ProgramDerivedAddress(bytes, nonce)

        /** Same as [create] but takes an existing [SolanaPublicKey]. */
        @JvmStatic
        fun create(publicKey: SolanaPublicKey, nonce: UByte): ProgramDerivedAddress =
            ProgramDerivedAddress(publicKey.bytes, nonce)
    }
}

/** Artemis-side helper: convert to the native Pubkey type. */
fun SolanaPublicKey.toArtemis(): ArtemisPubkey = ArtemisPubkey(bytes)

/** Artemis-side helper: convert an Artemis Pubkey to web3-solana's SolanaPublicKey. */
fun ArtemisPubkey.asWeb3Solana(): SolanaPublicKey = SolanaPublicKey(bytes)
