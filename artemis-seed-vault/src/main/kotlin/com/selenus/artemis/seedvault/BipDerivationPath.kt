/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * BIP Derivation Path types - Full parity with Solana Mobile Seed Vault SDK v0.4.0.
 * 
 * Provides:
 * - BipLevel: Single level in a derivation path
 * - BipDerivationPath: Base class for BIP derivation URIs
 * - Bip32DerivationPath: Full BIP32 path (m/44'/501'/0'/0')
 * - Bip44DerivationPath: Compact BIP44 path (account/change/addressIndex)
 * 
 * These types use Android Uri-based representation matching the upstream SDK format:
 * - bip32:/m/44'/501'/0'/0'
 * - bip44:/0'/0'
 * 
 * Drop-in compatible with the Solana Mobile Seed Vault service IPC protocol.
 */
package com.selenus.artemis.seedvault

import android.net.Uri
import com.selenus.artemis.seedvault.internal.SeedVaultConstants

/**
 * A single level in a BIP derivation path.
 * 
 * @param index The numeric index of this level
 * @param hardened Whether this level uses hardened derivation
 */
data class BipLevel(
    val index: Int,
    val hardened: Boolean
) {
    override fun toString(): String = if (hardened) "$index'" else "$index"
}

/**
 * Base class for BIP derivation path URIs.
 * 
 * Supports both bip32:// and bip44:// URI schemes as defined by WalletContractV1.
 */
sealed class BipDerivationPath {
    
    /**
     * Convert this derivation path to an Android Uri.
     */
    abstract fun toUri(): Uri
    
    /**
     * Get the levels of this derivation path.
     */
    abstract val levels: List<BipLevel>
    
    companion object {
        /**
         * Parse a BIP derivation path from a Uri.
         * 
         * @param bipUri A Uri with scheme "bip32" or "bip44"
         * @return The parsed derivation path
         * @throws UnsupportedOperationException if the URI scheme is unknown
         */
        fun fromUri(bipUri: Uri): BipDerivationPath {
            return when (bipUri.scheme) {
                SeedVaultConstants.BIP32_URI_SCHEME -> Bip32DerivationPath.fromUri(bipUri)
                SeedVaultConstants.BIP44_URI_SCHEME -> Bip44DerivationPath.fromUri(bipUri)
                else -> throw UnsupportedOperationException(
                    "Unknown BIP derivation URI scheme '${bipUri.scheme}'"
                )
            }
        }
        
        /**
         * Parse from a standard path string like "m/44'/501'/0'/0'".
         * Returns a Bip32DerivationPath.
         */
        fun fromPathString(path: String): Bip32DerivationPath {
            val normalized = path.trim()
            require(normalized.startsWith("m/")) { "Path must start with 'm/'" }
            
            val parts = normalized.removePrefix("m/").split("/")
            val levels = parts.map { part ->
                val isHardened = part.endsWith("'") || part.endsWith("h")
                val value = part.removeSuffix("'").removeSuffix("h").toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid path component: $part")
                BipLevel(value, isHardened)
            }
            
            return Bip32DerivationPath(levels)
        }
    }
}

/**
 * An immutable BIP32 derivation path.
 * 
 * Full path format: bip32:/m/44'/501'/0'/0'
 * 
 * Compatible with the Solana Mobile Seed Vault SDK Bip32DerivationPath class.
 * 
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP-0032</a>
 */
class Bip32DerivationPath(
    override val levels: List<BipLevel>
) : BipDerivationPath() {
    
    init {
        require(levels.size <= MAX_DEPTH) {
            "BIP32 max supported depth ($MAX_DEPTH) exceeded"
        }
    }
    
    override fun toUri(): Uri {
        val builder = Uri.Builder()
            .scheme(SeedVaultConstants.BIP32_URI_SCHEME)
            .appendPath(MASTER_KEY_INDICATOR)
        
        for (level in levels) {
            val pathElement = if (level.hardened) {
                "${level.index}${HARDENED_INDICATOR}"
            } else {
                level.index.toString()
            }
            builder.appendPath(pathElement)
        }
        
        return builder.build()
    }
    
    /**
     * Convert to standard path string (e.g., "m/44'/501'/0'/0'").
     */
    fun toPathString(): String {
        return "m/" + levels.joinToString("/") { level ->
            if (level.hardened) "${level.index}'" else level.index.toString()
        }
    }
    
    /**
     * Test if this derivation path is an ancestor of another.
     * An ancestor starts with the same levels as the descendant.
     */
    fun isAncestorOf(descendant: Bip32DerivationPath): Boolean {
        if (levels.size >= descendant.levels.size) return false
        return levels.indices.all { i -> levels[i] == descendant.levels[i] }
    }
    
    /**
     * Normalize this path for a given purpose.
     * For Solana (PURPOSE_SIGN_SOLANA_TRANSACTION), all levels must be hardened.
     */
    fun normalize(purpose: Int = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION): Bip32DerivationPath {
        return when (purpose) {
            SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION -> {
                if (levels.all { it.hardened }) this
                else Bip32DerivationPath(levels.map { 
                    if (it.hardened) it else BipLevel(it.index, true) 
                })
            }
            else -> this
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bip32DerivationPath) return false
        return levels == other.levels
    }
    
    override fun hashCode(): Int = levels.hashCode()
    
    override fun toString(): String = "Bip32DerivationPath{levels=$levels}"
    
    class Builder {
        private val mLevels = mutableListOf<BipLevel>()
        
        fun appendLevel(level: BipLevel): Builder {
            mLevels.add(level)
            return this
        }
        
        fun appendLevels(levels: Collection<BipLevel>): Builder {
            mLevels.addAll(levels)
            return this
        }
        
        fun build(): Bip32DerivationPath = Bip32DerivationPath(mLevels.toList())
    }
    
    companion object {
        const val MAX_DEPTH = 8
        private const val MASTER_KEY_INDICATOR = "m"
        private const val HARDENED_INDICATOR = "'"
        
        fun newBuilder(): Builder = Builder()
        
        /**
         * Parse from a bip32 Uri.
         * Format: bip32:/m/44'/501'/0'/0'
         */
        fun fromUri(bip32Uri: Uri): Bip32DerivationPath {
            require(bip32Uri.isHierarchical) { "BIP32 URI must be hierarchical" }
            
            if (bip32Uri.isAbsolute && bip32Uri.scheme != SeedVaultConstants.BIP32_URI_SCHEME) {
                throw UnsupportedOperationException(
                    "BIP32 URI absolute scheme must be ${SeedVaultConstants.BIP32_URI_SCHEME}"
                )
            }
            
            require(bip32Uri.authority == null) { "BIP32 URI authority must be null" }
            require(bip32Uri.query == null) { "BIP32 URI query must be null" }
            require(bip32Uri.fragment == null) { "BIP32 URI fragment must be null" }
            
            val path = bip32Uri.pathSegments
            require(path.isNotEmpty() && path[0] == MASTER_KEY_INDICATOR) {
                "BIP32 URI path must start with a master key indicator"
            }
            
            val builder = newBuilder()
            for (i in 1 until path.size) {
                val element = path[i]
                val hardened = element.endsWith(HARDENED_INDICATOR)
                val indexStr = if (hardened) {
                    element.substring(0, element.length - HARDENED_INDICATOR.length)
                } else {
                    element
                }
                val index = indexStr.toIntOrNull()
                    ?: throw UnsupportedOperationException(
                        "Path element [$i]($element) could not be parsed as a BIP32 level"
                    )
                builder.appendLevel(BipLevel(index, hardened))
            }
            
            return builder.build()
        }
    }
}

/**
 * An immutable BIP44 derivation path.
 * 
 * Compact format: bip44:/account'/change'/addressIndex'
 * 
 * BIP44 omits purpose and coin_type (they are implicit based on the signing purpose).
 * Only contains: account / change / address_index
 * 
 * Compatible with the Solana Mobile Seed Vault SDK Bip44DerivationPath class.
 * 
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki">BIP-0044</a>
 */
class Bip44DerivationPath(
    val account: BipLevel,
    val change: BipLevel? = null,
    val addressIndex: BipLevel? = null
) : BipDerivationPath() {
    
    init {
        require(account.hardened) { "account must be hardened" }
        require(!(change == null && addressIndex != null)) {
            "addressIndex must be null when change is null"
        }
    }
    
    override val levels: List<BipLevel>
        get() = buildList {
            add(account)
            if (change != null) {
                add(change)
                if (addressIndex != null) {
                    add(addressIndex)
                }
            }
        }
    
    val hasChange: Boolean get() = change != null
    val hasAddressIndex: Boolean get() = addressIndex != null
    
    override fun toUri(): Uri {
        val builder = Uri.Builder()
            .scheme(SeedVaultConstants.BIP44_URI_SCHEME)
        
        for (level in levels) {
            val pathElement = if (level.hardened) {
                "${level.index}'"
            } else {
                level.index.toString()
            }
            builder.appendPath(pathElement)
        }
        
        return builder.build()
    }
    
    /**
     * Convert to a BIP32 derivation path by prepending purpose and coin type.
     * For Solana: m/44'/501'/account'/change'/addressIndex'
     */
    fun toBip32DerivationPath(
        purpose: Int = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION
    ): Bip32DerivationPath {
        val bip32Levels = mutableListOf<BipLevel>()
        
        when (purpose) {
            SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION -> {
                bip32Levels.add(BipLevel(44, true))  // purpose
                bip32Levels.add(BipLevel(501, true))  // coin type
                // All levels hardened for Solana
                for (level in levels) {
                    bip32Levels.add(if (level.hardened) level else BipLevel(level.index, true))
                }
            }
        }
        
        return Bip32DerivationPath(bip32Levels)
    }
    
    /**
     * Normalize this path for a given purpose.
     * For Solana, all levels must be hardened.
     */
    fun normalize(purpose: Int = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION): Bip44DerivationPath {
        return when (purpose) {
            SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION -> {
                if (levels.all { it.hardened }) this
                else {
                    val hardened = levels.map { 
                        if (it.hardened) it else BipLevel(it.index, true) 
                    }
                    Bip44DerivationPath(
                        hardened[0],
                        hardened.getOrNull(1),
                        hardened.getOrNull(2)
                    )
                }
            }
            else -> this
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bip44DerivationPath) return false
        return levels == other.levels
    }
    
    override fun hashCode(): Int = levels.hashCode()
    
    override fun toString(): String = "Bip44DerivationPath{levels=$levels}"
    
    class Builder {
        private var mAccount: BipLevel? = null
        private var mChange: BipLevel? = null
        private var mAddressIndex: BipLevel? = null
        
        fun setAccount(account: BipLevel): Builder {
            mAccount = account
            return this
        }
        
        fun setChange(change: BipLevel?): Builder {
            mChange = change
            return this
        }
        
        fun setAddressIndex(addressIndex: BipLevel?): Builder {
            mAddressIndex = addressIndex
            return this
        }
        
        fun build(): Bip44DerivationPath {
            val account = requireNotNull(mAccount) { "account is required" }
            return Bip44DerivationPath(account, mChange, mAddressIndex)
        }
    }
    
    companion object {
        fun newBuilder(): Builder = Builder()
        
        /**
         * Parse from a bip44 Uri.
         * Format: bip44:/account'/change'/addressIndex'
         */
        fun fromUri(bip44Uri: Uri): Bip44DerivationPath {
            require(bip44Uri.isHierarchical) { "BIP44 URI must be hierarchical" }
            require(bip44Uri.isAbsolute && bip44Uri.scheme == SeedVaultConstants.BIP44_URI_SCHEME) {
                "BIP44 URI must be absolute with scheme ${SeedVaultConstants.BIP44_URI_SCHEME}"
            }
            require(bip44Uri.authority == null) { "BIP44 URI authority must be null" }
            require(bip44Uri.query == null) { "BIP44 URI query must be null" }
            require(bip44Uri.fragment == null) { "BIP44 URI fragment must be null" }
            
            val path = bip44Uri.pathSegments
            require(path.size in 1..3) { 
                "BIP44 URI path must contain between 1 and 3 elements" 
            }
            
            val builder = newBuilder()
            
            for (i in path.indices) {
                val element = path[i]
                val hardened = element.endsWith("'")
                val indexStr = if (hardened) element.dropLast(1) else element
                val index = indexStr.toIntOrNull()
                    ?: throw UnsupportedOperationException(
                        "Path element $i could not be parsed as a BIP level"
                    )
                val level = BipLevel(index, hardened)
                
                when (i) {
                    0 -> builder.setAccount(level)
                    1 -> builder.setChange(level)
                    2 -> builder.setAddressIndex(level)
                }
            }
            
            return builder.build()
        }
    }
}

/**
 * Helper for creating Solana derivation paths quickly.
 */
object SolanaBipPaths {
    
    /** Standard Solana BIP44 account path */
    fun account(index: Int): Bip44DerivationPath {
        return Bip44DerivationPath.newBuilder()
            .setAccount(BipLevel(index, true))
            .build()
    }
    
    /** Standard Solana BIP44 account with change */
    fun accountWithChange(accountIndex: Int, changeIndex: Int = 0): Bip44DerivationPath {
        return Bip44DerivationPath.newBuilder()
            .setAccount(BipLevel(accountIndex, true))
            .setChange(BipLevel(changeIndex, true))
            .build()
    }
    
    /** Full BIP32 Solana path */
    fun bip32(accountIndex: Int = 0, changeIndex: Int = 0): Bip32DerivationPath {
        return Bip32DerivationPath.newBuilder()
            .appendLevel(BipLevel(44, true))
            .appendLevel(BipLevel(501, true))
            .appendLevel(BipLevel(accountIndex, true))
            .appendLevel(BipLevel(changeIndex, true))
            .build()
    }
    
    /** Ledger-style BIP32 Solana path (no change level) */
    fun ledger(accountIndex: Int = 0): Bip32DerivationPath {
        return Bip32DerivationPath.newBuilder()
            .appendLevel(BipLevel(44, true))
            .appendLevel(BipLevel(501, true))
            .appendLevel(BipLevel(accountIndex, true))
            .build()
    }
    
    /** Solana root path for compatibility */
    fun root(): Bip32DerivationPath {
        return Bip32DerivationPath.newBuilder()
            .appendLevel(BipLevel(44, true))
            .appendLevel(BipLevel(501, true))
            .build()
    }
}
