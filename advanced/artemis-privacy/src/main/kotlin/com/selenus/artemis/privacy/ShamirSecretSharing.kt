/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * ShamirSecretSharing - Client-side seed splitting for recovery.
 * 
 * Addresses Seed Vault Issue #130: Shamir's secret sharing for seed
 * 
 * Implements Shamir's Secret Sharing Scheme (SSSS) for splitting
 * sensitive data (like BIP-39 entropy) into multiple shares where
 * only a threshold number of shares are needed to reconstruct.
 * 
 * Example: Split a seed into 5 shares where any 3 can recover it.
 * 
 * This implementation:
 * - Uses GF(256) finite field arithmetic
 * - Is fully deterministic given the same random bytes
 * - Works with any byte array (not just seeds)
 * - Provides secure serialization for share storage
 */
package com.selenus.artemis.privacy

import java.security.SecureRandom

/**
 * A single share of a split secret.
 */
data class ShamirShare(
    /** Share index (1-255, never 0) */
    val index: Int,
    /** The share data bytes */
    val data: ByteArray,
    /** Total shares created */
    val totalShares: Int,
    /** Required shares for recovery */
    val threshold: Int
) {
    init {
        require(index in 1..255) { "Share index must be between 1 and 255" }
        require(threshold >= 2) { "Threshold must be at least 2" }
        require(totalShares >= threshold) { "Total shares must be >= threshold" }
    }
    
    /**
     * Serialize share for storage.
     * Format: [version:1][index:1][threshold:1][total:1][data_len:2][data:N][checksum:4]
     */
    fun serialize(): ByteArray {
        val result = ByteArray(1 + 1 + 1 + 1 + 2 + data.size + 4)
        var offset = 0
        
        // Version
        result[offset++] = 0x01
        
        // Metadata
        result[offset++] = index.toByte()
        result[offset++] = threshold.toByte()
        result[offset++] = totalShares.toByte()
        
        // Data length (big-endian)
        result[offset++] = ((data.size shr 8) and 0xFF).toByte()
        result[offset++] = (data.size and 0xFF).toByte()
        
        // Data
        System.arraycopy(data, 0, result, offset, data.size)
        offset += data.size
        
        // Simple checksum (CRC32-style)
        val checksum = computeChecksum(result, 0, offset)
        result[offset++] = ((checksum shr 24) and 0xFF).toByte()
        result[offset++] = ((checksum shr 16) and 0xFF).toByte()
        result[offset++] = ((checksum shr 8) and 0xFF).toByte()
        result[offset] = (checksum and 0xFF).toByte()
        
        return result
    }
    
    /**
     * Serialize to a human-readable format.
     */
    fun toMnemonic(): String {
        // Encode as hex with prefix
        val hex = data.joinToString("") { "%02x".format(it) }
        return "share-$index-$threshold-$totalShares-$hex"
    }
    
    companion object {
        fun deserialize(bytes: ByteArray): ShamirShare {
            require(bytes.size >= 10) { "Invalid share data: too short" }
            
            val version = bytes[0].toInt() and 0xFF
            require(version == 0x01) { "Unsupported share version: $version" }
            
            val index = bytes[1].toInt() and 0xFF
            val threshold = bytes[2].toInt() and 0xFF
            val totalShares = bytes[3].toInt() and 0xFF
            
            val dataLen = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
            require(bytes.size >= 6 + dataLen + 4) { "Invalid share data: length mismatch" }
            
            val data = bytes.copyOfRange(6, 6 + dataLen)
            
            // Verify checksum
            val expectedChecksum = computeChecksum(bytes, 0, 6 + dataLen)
            val actualChecksum = ((bytes[6 + dataLen].toInt() and 0xFF) shl 24) or
                                 ((bytes[6 + dataLen + 1].toInt() and 0xFF) shl 16) or
                                 ((bytes[6 + dataLen + 2].toInt() and 0xFF) shl 8) or
                                 (bytes[6 + dataLen + 3].toInt() and 0xFF)
            
            require(expectedChecksum == actualChecksum) { "Share checksum mismatch" }
            
            return ShamirShare(index, data, totalShares, threshold)
        }
        
        fun fromMnemonic(mnemonic: String): ShamirShare {
            val parts = mnemonic.split("-")
            require(parts.size == 5 && parts[0] == "share") { "Invalid share mnemonic" }
            
            val index = parts[1].toInt()
            val threshold = parts[2].toInt()
            val totalShares = parts[3].toInt()
            val hex = parts[4]
            
            val data = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            
            return ShamirShare(index, data, totalShares, threshold)
        }
        
        private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
            var crc = 0xFFFFFFFF.toInt()
            for (i in offset until offset + length) {
                crc = crc xor (data[i].toInt() and 0xFF)
                for (j in 0 until 8) {
                    crc = if ((crc and 1) != 0) {
                        (crc ushr 1) xor 0xEDB88320.toInt()
                    } else {
                        crc ushr 1
                    }
                }
            }
            return crc xor 0xFFFFFFFF.toInt()
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShamirShare) return false
        return index == other.index && 
               data.contentEquals(other.data) &&
               totalShares == other.totalShares &&
               threshold == other.threshold
    }
    
    override fun hashCode(): Int {
        var result = index
        result = 31 * result + data.contentHashCode()
        result = 31 * result + totalShares
        result = 31 * result + threshold
        return result
    }
}

/**
 * Result of secret recovery.
 */
sealed class RecoveryResult {
    data class Success(val secret: ByteArray) : RecoveryResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return secret.contentEquals(other.secret)
        }
        
        override fun hashCode(): Int = secret.contentHashCode()
    }
    
    data class NeedMoreShares(val have: Int, val need: Int) : RecoveryResult()
    data class InvalidShares(val message: String) : RecoveryResult()
}

/**
 * Shamir's Secret Sharing implementation.
 * 
 * Splits a secret into N shares where any K can reconstruct it.
 * 
 * Usage:
 * ```kotlin
 * val shamir = ShamirSecretSharing()
 * 
 * // Split a secret into 5 shares, requiring 3 to recover
 * val secret = "my-seed-entropy".toByteArray()
 * val shares = shamir.split(secret, threshold = 3, totalShares = 5)
 * 
 * // Give each share to a different person/location
 * shares.forEach { share ->
 *     println("Share ${share.index}: ${share.toMnemonic()}")
 * }
 * 
 * // Later, recover with any 3 shares
 * val recovered = shamir.recover(listOf(shares[0], shares[2], shares[4]))
 * when (recovered) {
 *     is RecoveryResult.Success -> println("Recovered: ${recovered.secret}")
 *     is RecoveryResult.NeedMoreShares -> println("Need ${recovered.need} shares")
 *     is RecoveryResult.InvalidShares -> println("Error: ${recovered.message}")
 * }
 * ```
 */
class ShamirSecretSharing(
    private val random: SecureRandom = SecureRandom()
) {
    
    /**
     * Split a secret into shares.
     * 
     * @param secret The secret bytes to split
     * @param threshold Minimum shares needed to recover (K)
     * @param totalShares Total number of shares to create (N)
     * @return List of shares
     */
    fun split(secret: ByteArray, threshold: Int, totalShares: Int): List<ShamirShare> {
        require(threshold >= 2) { "Threshold must be at least 2" }
        require(totalShares >= threshold) { "Total shares must be >= threshold" }
        require(totalShares <= 255) { "Maximum 255 shares supported" }
        require(secret.isNotEmpty()) { "Secret cannot be empty" }
        
        val shares = Array(totalShares) { ByteArray(secret.size) }
        
        // Process each byte of the secret independently
        for (byteIndex in secret.indices) {
            // Create random polynomial coefficients
            // f(x) = secret[i] + a1*x + a2*x^2 + ... + a_{k-1}*x^{k-1}
            val coefficients = ByteArray(threshold)
            coefficients[0] = secret[byteIndex]
            random.nextBytes(coefficients.sliceArray(1 until threshold).also { 
                System.arraycopy(it, 0, coefficients, 1, it.size)
            })
            
            // Evaluate polynomial at each x = 1, 2, ..., n
            for (shareIndex in 0 until totalShares) {
                val x = (shareIndex + 1).toByte() // x values are 1 to n
                shares[shareIndex][byteIndex] = evaluatePolynomial(coefficients, x)
            }
        }
        
        return shares.mapIndexed { index, data ->
            ShamirShare(
                index = index + 1,
                data = data,
                totalShares = totalShares,
                threshold = threshold
            )
        }
    }
    
    /**
     * Recover the secret from shares.
     * 
     * @param shares List of shares (must be >= threshold)
     * @return Recovery result
     */
    fun recover(shares: List<ShamirShare>): RecoveryResult {
        if (shares.isEmpty()) {
            return RecoveryResult.InvalidShares("No shares provided")
        }
        
        val threshold = shares.first().threshold
        
        // Check we have enough shares
        if (shares.size < threshold) {
            return RecoveryResult.NeedMoreShares(have = shares.size, need = threshold)
        }
        
        // Verify shares are compatible
        val dataSize = shares.first().data.size
        val total = shares.first().totalShares
        for (share in shares) {
            if (share.threshold != threshold || share.totalShares != total) {
                return RecoveryResult.InvalidShares("Incompatible share parameters")
            }
            if (share.data.size != dataSize) {
                return RecoveryResult.InvalidShares("Incompatible share sizes")
            }
        }
        
        // Check for duplicate indices
        val indices = shares.map { it.index }.toSet()
        if (indices.size != shares.size) {
            return RecoveryResult.InvalidShares("Duplicate share indices")
        }
        
        // Use exactly threshold shares
        val usedShares = shares.take(threshold)
        val xValues = usedShares.map { it.index.toByte() }
        
        // Recover each byte using Lagrange interpolation
        val secret = ByteArray(dataSize)
        for (byteIndex in 0 until dataSize) {
            val yValues = usedShares.map { it.data[byteIndex] }
            secret[byteIndex] = lagrangeInterpolate(xValues, yValues)
        }
        
        return RecoveryResult.Success(secret)
    }
    
    /**
     * Verify that a set of shares are valid and compatible.
     */
    fun verifyShares(shares: List<ShamirShare>): Boolean {
        if (shares.isEmpty()) return false
        
        val first = shares.first()
        return shares.all { 
            it.threshold == first.threshold &&
            it.totalShares == first.totalShares &&
            it.data.size == first.data.size
        } && shares.map { it.index }.toSet().size == shares.size
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GF(256) FINITE FIELD ARITHMETIC
    // ═══════════════════════════════════════════════════════════════════════════
    
    // GF(256) with irreducible polynomial x^8 + x^4 + x^3 + x + 1 (0x11B)
    
    private fun gfAdd(a: Byte, b: Byte): Byte {
        return (a.toInt() xor b.toInt()).toByte()
    }
    
    private fun gfMul(a: Byte, b: Byte): Byte {
        var result = 0
        var aa = a.toInt() and 0xFF
        var bb = b.toInt() and 0xFF
        
        while (bb != 0) {
            if ((bb and 1) != 0) {
                result = result xor aa
            }
            aa = aa shl 1
            if ((aa and 0x100) != 0) {
                aa = aa xor 0x11B // Reduce by irreducible polynomial
            }
            bb = bb shr 1
        }
        
        return result.toByte()
    }
    
    private fun gfDiv(a: Byte, b: Byte): Byte {
        require(b.toInt() != 0) { "Division by zero in GF(256)" }
        return gfMul(a, gfInverse(b))
    }
    
    private fun gfInverse(a: Byte): Byte {
        // Extended Euclidean algorithm for GF(256)
        val aa = a.toInt() and 0xFF
        require(aa != 0) { "No inverse for zero" }
        
        // a^254 = a^(-1) in GF(256) (Fermat's little theorem)
        var result = aa
        repeat(6) { // a^(2^7 - 2) = a^254
            result = gfMul(result.toByte(), result.toByte()).toInt() and 0xFF
            result = gfMul(result.toByte(), aa.toByte()).toInt() and 0xFF
        }
        result = gfMul(result.toByte(), result.toByte()).toInt() and 0xFF
        
        return result.toByte()
    }
    
    private fun evaluatePolynomial(coefficients: ByteArray, x: Byte): Byte {
        var result: Byte = 0
        var power: Byte = 1
        
        for (coeff in coefficients) {
            result = gfAdd(result, gfMul(coeff, power))
            power = gfMul(power, x)
        }
        
        return result
    }
    
    private fun lagrangeInterpolate(xValues: List<Byte>, yValues: List<Byte>): Byte {
        var result: Byte = 0
        
        for (i in xValues.indices) {
            var numerator: Byte = 1
            var denominator: Byte = 1
            
            for (j in xValues.indices) {
                if (i != j) {
                    // numerator *= (0 - xj) = -xj = xj in GF(256)
                    numerator = gfMul(numerator, xValues[j])
                    // denominator *= (xi - xj)
                    denominator = gfMul(denominator, gfAdd(xValues[i], xValues[j]))
                }
            }
            
            val lagrangeCoeff = gfDiv(numerator, denominator)
            result = gfAdd(result, gfMul(yValues[i], lagrangeCoeff))
        }
        
        return result
    }
}
