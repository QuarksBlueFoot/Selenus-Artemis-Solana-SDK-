package com.selenus.artemis.runtime

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Artemis SLIP-0010 / BIP-32 Ed25519 Key Derivation
 * 
 * Implements hierarchical deterministic (HD) key derivation for Ed25519 keys,
 * following SLIP-0010 specification which adapts BIP-32 for Ed25519 curves.
 * 
 * This enables deriving multiple keypairs from a single seed using derivation
 * paths like "m/44'/501'/0'/0'" (standard Solana path).
 * 
 * Standards:
 * - SLIP-0010: Universal private key derivation from master seed
 * - BIP-32: Hierarchical Deterministic Wallets  
 * - BIP-44: Multi-Account Hierarchy for Deterministic Wallets
 */
object Bip32 {
    
    private const val ED25519_CURVE = "ed25519 seed"
    private const val HMAC_SHA512 = "HmacSHA512"
    private const val HARDENED_OFFSET = 0x80000000.toInt()
    
    /**
     * Represents a level in a BIP-32 derivation path.
     */
    data class PathLevel(
        val index: Int,
        val hardened: Boolean
    ) {
        init {
            require(index >= 0) { "Index must be non-negative" }
        }
        
        val fullIndex: Int get() = if (hardened) index or HARDENED_OFFSET else index
        
        override fun toString(): String = "$index${if (hardened) "'" else ""}"
    }
    
    /**
     * Represents a complete BIP-32/BIP-44 derivation path.
     */
    data class DerivationPath(
        val levels: List<PathLevel>
    ) {
        /**
         * String representation like "m/44'/501'/0'/0'"
         */
        override fun toString(): String = "m/" + levels.joinToString("/")
        
        companion object {
            /**
             * Parse a derivation path string.
             * 
             * @param path Path string like "m/44'/501'/0'/0'" or "44'/501'/0'/0'"
             * @return Parsed DerivationPath
             */
            fun parse(path: String): DerivationPath {
                val normalized = path.trim().removePrefix("m/").removePrefix("M/")
                if (normalized.isEmpty()) return DerivationPath(emptyList())
                
                val levels = normalized.split("/").map { segment ->
                    val hardened = segment.endsWith("'") || segment.endsWith("h") || segment.endsWith("H")
                    val indexStr = segment.removeSuffix("'").removeSuffix("h").removeSuffix("H")
                    val index = indexStr.toIntOrNull() 
                        ?: throw IllegalArgumentException("Invalid path segment: $segment")
                    PathLevel(index, hardened)
                }
                
                return DerivationPath(levels)
            }
            
            /**
             * Standard Solana derivation path: m/44'/501'/account'/change'
             */
            fun solana(account: Int = 0, change: Int = 0): DerivationPath {
                return DerivationPath(listOf(
                    PathLevel(44, true),   // BIP-44 purpose
                    PathLevel(501, true),  // Solana coin type
                    PathLevel(account, true),
                    PathLevel(change, true)
                ))
            }
            
            /**
             * Short Solana path commonly used: m/44'/501'/0'
             * This is what Phantom and some other wallets use by default.
             */
            fun solanaShort(account: Int = 0): DerivationPath {
                return DerivationPath(listOf(
                    PathLevel(44, true),
                    PathLevel(501, true),
                    PathLevel(account, true)
                ))
            }
        }
        
        /**
         * Derive a child path by appending more levels.
         */
        fun child(vararg childLevels: PathLevel): DerivationPath {
            return DerivationPath(levels + childLevels.toList())
        }
        
        /**
         * Derive a hardened child at the given index.
         */
        fun hardened(index: Int): DerivationPath {
            return child(PathLevel(index, true))
        }
        
        /**
         * Derive a non-hardened child at the given index.
         * Note: Ed25519 (SLIP-0010) only supports hardened derivation.
         */
        fun normal(index: Int): DerivationPath {
            return child(PathLevel(index, false))
        }
    }
    
    /**
     * Internal representation of derived key material.
     */
    private data class KeyMaterial(
        val key: ByteArray,      // 32-byte private key
        val chainCode: ByteArray // 32-byte chain code
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is KeyMaterial) return false
            return key.contentEquals(other.key) && chainCode.contentEquals(other.chainCode)
        }
        
        override fun hashCode(): Int = 31 * key.contentHashCode() + chainCode.contentHashCode()
    }
    
    /**
     * Derives a keypair from a BIP-39 seed using a derivation path.
     * 
     * @param seed The 64-byte BIP-39 seed (from Bip39.toSeed())
     * @param path The derivation path (e.g., "m/44'/501'/0'/0'")
     * @return The derived Keypair
     */
    fun deriveKeypair(seed: ByteArray, path: String): Keypair {
        return deriveKeypair(seed, DerivationPath.parse(path))
    }
    
    /**
     * Derives a keypair from a BIP-39 seed using a DerivationPath.
     * 
     * @param seed The 64-byte BIP-39 seed
     * @param path The DerivationPath
     * @return The derived Keypair
     */
    fun deriveKeypair(seed: ByteArray, path: DerivationPath): Keypair {
        require(seed.size == 64) { "Seed must be 64 bytes (got ${seed.size})" }
        
        val keyMaterial = deriveKeyMaterial(seed, path)
        return Keypair.fromSeed(keyMaterial.key)
    }
    
    /**
     * Derives multiple keypairs from a single seed at sequential account indices.
     * Uses the standard Solana path: m/44'/501'/{account}'/0'
     * 
     * @param seed The 64-byte BIP-39 seed
     * @param count Number of keypairs to derive
     * @param startAccount Starting account index (default 0)
     * @return List of derived Keypairs
     */
    fun deriveMultiple(seed: ByteArray, count: Int, startAccount: Int = 0): List<Keypair> {
        require(count > 0) { "Count must be positive" }
        require(startAccount >= 0) { "Start account must be non-negative" }
        
        return (startAccount until startAccount + count).map { account ->
            deriveKeypair(seed, DerivationPath.solana(account, 0))
        }
    }
    
    /**
     * Convenience function to derive a Solana keypair at a specific account index.
     * Uses path: m/44'/501'/{account}'/0'
     */
    fun deriveSolanaKeypair(seed: ByteArray, account: Int = 0, change: Int = 0): Keypair {
        return deriveKeypair(seed, DerivationPath.solana(account, change))
    }
    
    /**
     * Derives the public key for a given path without exposing the private key.
     * Useful for generating watch-only addresses.
     */
    fun derivePublicKey(seed: ByteArray, path: DerivationPath): Pubkey {
        return deriveKeypair(seed, path).publicKey
    }
    
    /**
     * Derives a keypair directly from a mnemonic phrase.
     * This is a convenience method combining Bip39.toSeed() and deriveKeypair().
     * 
     * @param mnemonic The BIP-39 mnemonic phrase
     * @param path The derivation path
     * @param passphrase Optional BIP-39 passphrase
     * @return The derived Keypair
     */
    fun fromMnemonic(mnemonic: String, path: String, passphrase: String = ""): Keypair {
        val seed = Bip39.toSeed(mnemonic, passphrase)
        return deriveKeypair(seed, path)
    }
    
    /**
     * Derives the master key from a BIP-39 seed.
     */
    private fun deriveMasterKey(seed: ByteArray): KeyMaterial {
        val mac = Mac.getInstance(HMAC_SHA512)
        mac.init(SecretKeySpec(ED25519_CURVE.toByteArray(Charsets.UTF_8), HMAC_SHA512))
        val result = mac.doFinal(seed)
        
        return KeyMaterial(
            key = result.copyOfRange(0, 32),
            chainCode = result.copyOfRange(32, 64)
        )
    }
    
    /**
     * Derives a child key from a parent key using SLIP-0010.
     * For Ed25519, only hardened derivation is supported.
     */
    private fun deriveChildKey(parent: KeyMaterial, level: PathLevel): KeyMaterial {
        // Ed25519 SLIP-0010 only supports hardened derivation
        if (!level.hardened) {
            throw IllegalArgumentException(
                "Ed25519 (SLIP-0010) only supports hardened derivation. " +
                "Use hardened path level (${level.index}' instead of ${level.index})"
            )
        }
        
        val mac = Mac.getInstance(HMAC_SHA512)
        mac.init(SecretKeySpec(parent.chainCode, HMAC_SHA512))
        
        // For hardened derivation: 0x00 || private_key || index
        val data = ByteArray(37)
        data[0] = 0x00
        System.arraycopy(parent.key, 0, data, 1, 32)
        
        // Add the full index (with hardened bit set)
        val fullIndex = level.fullIndex
        data[33] = (fullIndex shr 24).toByte()
        data[34] = (fullIndex shr 16).toByte()
        data[35] = (fullIndex shr 8).toByte()
        data[36] = fullIndex.toByte()
        
        val result = mac.doFinal(data)
        
        return KeyMaterial(
            key = result.copyOfRange(0, 32),
            chainCode = result.copyOfRange(32, 64)
        )
    }
    
    /**
     * Derives key material for a complete path.
     */
    private fun deriveKeyMaterial(seed: ByteArray, path: DerivationPath): KeyMaterial {
        var current = deriveMasterKey(seed)
        
        for (level in path.levels) {
            current = deriveChildKey(current, level)
        }
        
        return current
    }
    
    /**
     * Validates that a path string is syntactically correct.
     */
    fun isValidPath(path: String): Boolean {
        return try {
            DerivationPath.parse(path)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates that a path is usable with Ed25519 (all levels must be hardened).
     */
    fun isValidEd25519Path(path: String): Boolean {
        return try {
            val parsed = DerivationPath.parse(path)
            parsed.levels.all { it.hardened }
        } catch (e: Exception) {
            false
        }
    }
}
