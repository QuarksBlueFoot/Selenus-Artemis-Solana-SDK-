/*
 * Drop-in source compatibility with org.sol4k.PublicKey.
 *
 * This file exposes the sol4k public API (class name, package, method signatures)
 * so applications built against sol4k compile without any source changes. The
 * internal implementation is Artemis code: every operation delegates to the
 * Artemis core types under com.selenus.artemis.runtime, which means users
 * automatically inherit Artemis improvements (faster Base58, typed errors, PDA
 * derivation backed by the hardened Artemis implementation) without touching
 * their call sites.
 */
package org.sol4k

import com.selenus.artemis.runtime.Pubkey as ArtemisPubkey

/**
 * sol4k compatible `PublicKey` wrapper.
 *
 * Wraps an Artemis [ArtemisPubkey] while exposing the same constructor and
 * method shape sol4k users expect. The two-way conversion with [ArtemisPubkey]
 * is zero-copy: the underlying `ByteArray` reference is shared.
 */
class PublicKey private constructor(internal val inner: ArtemisPubkey) {

    /** Construct from the 32-byte compressed form. */
    constructor(bytes: ByteArray) : this(ArtemisPubkey(bytes))

    /** Construct from a base58-encoded string. */
    constructor(base58: String) : this(ArtemisPubkey(base58))

    /** Return the 32-byte compressed form. */
    fun bytes(): ByteArray = inner.bytes

    /** Alias matching sol4k 0.7.0 property-style access. */
    val bytes: ByteArray get() = inner.bytes

    /** Base58 representation. Equivalent to `toString()`. */
    fun toBase58(): String = inner.toBase58()

    override fun toString(): String = inner.toBase58()

    /**
     * Verify an Ed25519 signature against [message] using this public key.
     * Returns `true` on a valid signature, `false` otherwise.
     */
    fun verify(signature: ByteArray, message: ByteArray): Boolean =
        inner.verify(signature, message)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicKey) return false
        return inner == other.inner
    }

    override fun hashCode(): Int = inner.hashCode()

    companion object {
        /**
         * Derive a program-derived address from [seeds] and [programId]. Matches
         * `sol4k.PublicKey.findProgramAddress`.
         */
        @JvmStatic
        fun findProgramAddress(seeds: List<PublicKey>, programId: PublicKey): ProgramDerivedAddress {
            val artemisResult = com.selenus.artemis.runtime.Pda.findProgramAddress(
                seeds = seeds.map { it.inner.bytes },
                programId = programId.inner
            )
            return ProgramDerivedAddress(
                address = PublicKey(artemisResult.address),
                nonce = artemisResult.bump.toInt()
            )
        }

        /**
         * Derive the associated token address for [holderAddress], [tokenMintAddress],
         * and [programId]. Parameter names match upstream sol4k's
         * `PublicKey.findProgramDerivedAddress` signature.
         */
        @JvmStatic
        @JvmOverloads
        fun findProgramDerivedAddress(
            holderAddress: PublicKey,
            tokenMintAddress: PublicKey,
            programId: PublicKey = TOKEN_PROGRAM_ID
        ): ProgramDerivedAddress {
            val seeds = listOf(
                holderAddress.inner.bytes,
                programId.inner.bytes,
                tokenMintAddress.inner.bytes
            )
            val result = com.selenus.artemis.runtime.Pda.findProgramAddress(
                seeds = seeds,
                programId = ASSOCIATED_TOKEN_PROGRAM_ID.inner
            )
            return ProgramDerivedAddress(
                address = PublicKey(result.address),
                nonce = result.bump.toInt()
            )
        }

        /**
         * Read 32 bytes of public key starting at [offset] from
         * [bytes]. Mirrors upstream sol4k `PublicKey.readPubkey` /
         * `solana-kmp` static helper of the same name. Returns the
         * decoded [PublicKey] without copying when the input is exactly
         * 32 bytes long; otherwise allocates a 32-byte slice.
         */
        @JvmStatic
        @JvmOverloads
        fun readPubkey(bytes: ByteArray, offset: Int = 0): PublicKey {
            require(offset >= 0 && offset + 32 <= bytes.size) {
                "readPubkey($offset, ${bytes.size}): out of range"
            }
            val slice = if (offset == 0 && bytes.size == 32) bytes
            else bytes.copyOfRange(offset, offset + 32)
            return PublicKey(slice)
        }

        /**
         * Decode a base58-encoded public key. Equivalent to
         * `PublicKey(string)`; matches upstream sol4k's `valueOf`
         * companion factory naming.
         */
        @JvmStatic
        fun valueOf(base58: String): PublicKey = PublicKey(base58)

        /** The standard SPL Token program ID. */
        @JvmField
        val TOKEN_PROGRAM_ID: PublicKey = PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")

        /** The Associated Token Account program ID. */
        @JvmField
        val ASSOCIATED_TOKEN_PROGRAM_ID: PublicKey = PublicKey("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")

        /** The System program ID. */
        @JvmField
        val SYSTEM_PROGRAM_ID: PublicKey = PublicKey("11111111111111111111111111111111")
    }
}

/** Convert an Artemis [ArtemisPubkey] to a sol4k [PublicKey] without copying. */
fun ArtemisPubkey.asSol4kPublicKey(): PublicKey = PublicKey(this.bytes)

/** Convert a sol4k [PublicKey] to an Artemis [ArtemisPubkey] without copying. */
fun PublicKey.toArtemis(): ArtemisPubkey = inner

