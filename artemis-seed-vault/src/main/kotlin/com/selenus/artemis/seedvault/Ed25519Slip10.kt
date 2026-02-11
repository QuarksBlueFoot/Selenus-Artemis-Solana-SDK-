/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * Ed25519Slip10 - SLIP-0010 Ed25519 key derivation for Solana.
 * 
 * Addresses Seed Vault Issue #4: Support Ed25519-BIP32 derivation scheme
 * 
 * This implements the SLIP-0010 specification for Ed25519 hierarchical deterministic
 * key derivation. This is the derivation scheme used internally by the Solana Seed Vault.
 * 
 * SLIP-0010 differs from standard BIP32 in that:
 * - Only hardened derivation is supported for Ed25519
 * - Uses "ed25519 seed" as the HMAC key for master secret generation
 * - Child key derivation uses HMAC-SHA512
 * 
 * This implementation is useful for:
 * - Testing and simulation without a real Seed Vault device
 * - Client-side key derivation for non-Saga devices
 * - Verification of derived public keys
 * 
 * @see <a href="https://github.com/satoshilabs/slips/blob/master/slip-0010.md">SLIP-0010</a>
 */
package com.selenus.artemis.seedvault

import java.security.InvalidKeyException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.math.ec.rfc8032.Ed25519

/**
 * SLIP-0010 Ed25519 key derivation.
 * 
 * Usage:
 * ```kotlin
 * // Derive from a BIP39 seed
 * val seed = ... // 64-byte BIP39 seed
 * val path = Bip32DerivationPath.fromPathString("m/44'/501'/0'/0'")
 * 
 * // Get private key material
 * val material = Ed25519Slip10.deriveKey(seed, path)
 * 
 * // Get Ed25519 keypair (requires an Ed25519 library like Bouncy Castle)
 * // val keyPair = Ed25519.generateKeyPair(material.privateKey)
 * ```
 */
object Ed25519Slip10 {
    
    private const val MASTER_SECRET_KEY = "ed25519 seed"
    private const val HMAC_ALGORITHM = "HmacSHA512"
    const val PRIVATE_KEY_SIZE = 32
    const val CHAIN_CODE_SIZE = 32
    
    /**
     * Key derivation material at a given path level.
     * Contains the 32-byte private key and 32-byte chain code.
     */
    data class KeyDerivationMaterial(
        val privateKey: ByteArray,
        val chainCode: ByteArray
    ) {
        init {
            require(privateKey.size == PRIVATE_KEY_SIZE) { 
                "Private key must be $PRIVATE_KEY_SIZE bytes, got ${privateKey.size}" 
            }
            require(chainCode.size == CHAIN_CODE_SIZE) { 
                "Chain code must be $CHAIN_CODE_SIZE bytes, got ${chainCode.size}" 
            }
        }
        
        /**
         * Securely clear the key material from memory.
         */
        fun clear() {
            privateKey.fill(0)
            chainCode.fill(0)
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is KeyDerivationMaterial) return false
            return privateKey.contentEquals(other.privateKey) &&
                   chainCode.contentEquals(other.chainCode)
        }
        
        override fun hashCode(): Int {
            var result = privateKey.contentHashCode()
            result = 31 * result + chainCode.contentHashCode()
            return result
        }
    }
    
    /**
     * Derive the master secret from a BIP39 seed.
     * 
     * @param seed The BIP39 seed (typically 64 bytes)
     * @return The master key derivation material
     */
    fun deriveMasterSecret(seed: ByteArray): KeyDerivationMaterial {
        val hmac = Mac.getInstance(HMAC_ALGORITHM)
        hmac.init(SecretKeySpec(MASTER_SECRET_KEY.toByteArray(), HMAC_ALGORITHM))
        val data = hmac.doFinal(seed)
        return KeyDerivationMaterial(
            privateKey = data.copyOf(32),
            chainCode = data.copyOfRange(32, 64)
        )
    }
    
    /**
     * Derive a child key from a parent key.
     * 
     * For Ed25519, only hardened derivation is supported.
     * The index must have the hardened bit set (>= 0x80000000).
     * 
     * @param parent The parent key material
     * @param index The child index (must be hardened)
     * @param hardened Whether this is a hardened derivation
     * @return The child key derivation material
     * @throws InvalidKeyException if non-hardened derivation is attempted
     */
    fun deriveChild(
        parent: KeyDerivationMaterial,
        index: Int,
        hardened: Boolean = true
    ): KeyDerivationMaterial {
        if (!hardened) {
            throw InvalidKeyException(
                "Ed25519 SLIP-0010 only supports hardened derivation"
            )
        }
        
        // For hardened keys: HMAC-SHA512(Key = parent.chainCode, Data = 0x00 || parent.privateKey || ser32(index))
        val hardenedIndex = index or 0x80000000.toInt()
        
        val data = ByteArray(1 + 32 + 4)
        data[0] = 0x00
        System.arraycopy(parent.privateKey, 0, data, 1, 32)
        data[33] = ((hardenedIndex shr 24) and 0xFF).toByte()
        data[34] = ((hardenedIndex shr 16) and 0xFF).toByte()
        data[35] = ((hardenedIndex shr 8) and 0xFF).toByte()
        data[36] = (hardenedIndex and 0xFF).toByte()
        
        val hmac = Mac.getInstance(HMAC_ALGORITHM)
        hmac.init(SecretKeySpec(parent.chainCode, HMAC_ALGORITHM))
        val result = hmac.doFinal(data)
        
        return KeyDerivationMaterial(
            privateKey = result.copyOf(32),
            chainCode = result.copyOfRange(32, 64)
        )
    }
    
    /**
     * Derive a key at a full BIP32 derivation path.
     * 
     * @param seed The BIP39 seed
     * @param path The derivation path
     * @return The derived key material
     */
    fun deriveKey(seed: ByteArray, path: Bip32DerivationPath): KeyDerivationMaterial {
        var node = deriveMasterSecret(seed)
        
        for (level in path.levels) {
            if (!level.hardened) {
                throw InvalidKeyException(
                    "Ed25519 SLIP-0010 requires all levels to be hardened. " +
                    "Level ${level.index} is not hardened."
                )
            }
            node = deriveChild(node, level.index, level.hardened)
        }
        
        return node
    }
    
    /**
     * Derive a key using a string path.
     * 
     * @param seed The BIP39 seed
     * @param pathString Path string like "m/44'/501'/0'/0'"
     * @return The derived key material
     */
    fun deriveKey(seed: ByteArray, pathString: String): KeyDerivationMaterial {
        val path = BipDerivationPath.fromPathString(pathString)
        return deriveKey(seed, path)
    }
    
    /**
     * Derive the Ed25519 public key at a full BIP32 derivation path.
     * 
     * This is a convenience method matching the upstream Seed Vault SDK's
     * Ed25519Slip10UseCase.derivePublicKey(). It derives the private key
     * material and computes the corresponding Ed25519 public key.
     * 
     * @param seed The BIP39 seed
     * @param path The derivation path
     * @return The 32-byte Ed25519 public key
     */
    fun derivePublicKey(seed: ByteArray, path: Bip32DerivationPath): ByteArray {
        val material = deriveKey(seed, path)
        return publicKeyFromPrivate(material.privateKey)
    }
    
    /**
     * Derive the Ed25519 public key using a string path.
     * 
     * @param seed The BIP39 seed
     * @param pathString Path string like "m/44'/501'/0'/0'"
     * @return The 32-byte Ed25519 public key
     */
    fun derivePublicKey(seed: ByteArray, pathString: String): ByteArray {
        val path = BipDerivationPath.fromPathString(pathString)
        return derivePublicKey(seed, path)
    }
    
    /**
     * Derive the Ed25519 public key from a partial derivation root.
     * 
     * Matches upstream Ed25519Slip10UseCase.derivePublicKey(seed, path, rootKeyPair).
     * Useful for bulk derivation with a cached root.
     * 
     * @param root Pre-derived root key material (from derivePartial)
     * @param childPath Additional derivation levels from the root
     * @return The 32-byte Ed25519 public key
     */
    fun derivePublicKey(root: KeyDerivationMaterial, childPath: Bip32DerivationPath): ByteArray {
        val material = deriveFromPartial(root, childPath)
        return publicKeyFromPrivate(material.privateKey)
    }
    
    /**
     * Compute the Ed25519 public key from a 32-byte private key.
     * 
     * @param privateKey The 32-byte Ed25519 private key (seed)
     * @return The 32-byte Ed25519 public key
     */
    fun publicKeyFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == PRIVATE_KEY_SIZE) {
            "Private key must be $PRIVATE_KEY_SIZE bytes"
        }
        val publicKey = ByteArray(32)
        Ed25519.generatePublicKey(privateKey, 0, publicKey, 0)
        return publicKey
    }
    
    /**
     * Derive a key with partial derivation support.
     * 
     * This is an optimization for deriving multiple keys that share a common
     * ancestor path. First derive to the common ancestor, then derive each
     * child from there.
     * 
     * @param seed The BIP39 seed
     * @param basePath The common ancestor path
     * @return The key material at the base path, for further derivation
     */
    fun derivePartial(seed: ByteArray, basePath: Bip32DerivationPath): KeyDerivationMaterial {
        return deriveKey(seed, basePath)
    }
    
    /**
     * Continue derivation from a partial derivation.
     * 
     * @param base The base key material from derivePartial
     * @param childPath Additional derivation levels
     * @return The derived key material
     */
    fun deriveFromPartial(
        base: KeyDerivationMaterial,
        childPath: Bip32DerivationPath
    ): KeyDerivationMaterial {
        var node = base
        for (level in childPath.levels) {
            node = deriveChild(node, level.index, level.hardened)
        }
        return node
    }
    
    /**
     * Derive multiple keys efficiently by caching the common derivation root.
     * 
     * All paths must share a common prefix. The common prefix is derived once,
     * then each unique suffix is derived from there.
     * 
     * @param seed The BIP39 seed
     * @param paths List of full derivation paths
     * @return Map of path to derived key material
     */
    fun deriveMultiple(
        seed: ByteArray,
        paths: List<Bip32DerivationPath>
    ): Map<Bip32DerivationPath, KeyDerivationMaterial> {
        if (paths.isEmpty()) return emptyMap()
        if (paths.size == 1) return mapOf(paths[0] to deriveKey(seed, paths[0]))
        
        // Find common prefix length
        val minLength = paths.minOf { it.levels.size }
        var commonLength = 0
        outer@ for (i in 0 until minLength) {
            val level = paths[0].levels[i]
            for (path in paths) {
                if (path.levels[i] != level) break@outer
            }
            commonLength = i + 1
        }
        
        // Derive to common ancestor
        val commonPath = Bip32DerivationPath(paths[0].levels.take(commonLength))
        val commonNode = if (commonLength > 0) deriveKey(seed, commonPath) else deriveMasterSecret(seed)
        
        // Derive each remaining suffix
        return paths.associateWith { path ->
            var node = commonNode
            for (i in commonLength until path.levels.size) {
                val level = path.levels[i]
                node = deriveChild(node, level.index, level.hardened)
            }
            node
        }
    }
}
