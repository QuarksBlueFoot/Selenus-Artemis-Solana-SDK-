/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * SolanaDerivation - Enhanced derivation path support for Solana.
 * 
 * Addresses Seed Vault Issues:
 * - #637: Add m/44'/501' to default accounts
 * - #4: Support Ed25519-BIP32 derivation scheme
 * 
 * Provides comprehensive derivation path utilities for all Solana wallet types.
 */
package com.selenus.artemis.seedvault

/**
 * Solana key derivation path utilities.
 * 
 * Supports all common derivation schemes used by Solana wallets:
 * - BIP44 Standard (Phantom, Solflare)
 * - Ledger Live scheme
 * - Ed25519-BIP32 (alternative scheme)
 * 
 * Usage:
 * ```kotlin
 * // Get path for account 0
 * val path = SolanaDerivation.STANDARD
 * 
 * // Get path for account 5
 * val path5 = SolanaDerivation.forAccount(5)
 * 
 * // Get Ledger-compatible path
 * val ledgerPath = SolanaDerivation.LEDGER_LIVE
 * 
 * // Parse and validate paths
 * val parsed = SolanaDerivation.parse("m/44'/501'/0'/0'")
 * ```
 */
object SolanaDerivation {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STANDARD PATHS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Standard Solana derivation path (BIP44).
     * Used by: Phantom, Solflare, Backpack, most mobile wallets
     * 
     * Format: m/44'/501'/account'/change'
     */
    const val STANDARD = "m/44'/501'/0'/0'"
    
    /**
     * Ledger Live derivation scheme.
     * Used by: Ledger hardware wallets
     * 
     * Format: m/44'/501'/account'
     * Note: No change level, accounts are directly derived
     */
    const val LEDGER_LIVE = "m/44'/501'/0'"
    
    /**
     * Phantom wallet default (same as standard but explicit).
     */
    const val PHANTOM = STANDARD
    
    /**
     * Solflare wallet default.
     */
    const val SOLFLARE = STANDARD
    
    /**
     * Ed25519-BIP32 alternative scheme.
     * Used by: Some specialized wallets
     * 
     * Uses coin type 1022 instead of 501
     */
    const val ED25519_BIP32 = "m/44'/1022'/0'/0'"
    
    /**
     * Solana coin type in BIP44.
     */
    const val SOLANA_COIN_TYPE = 501
    
    /**
     * Ed25519-BIP32 coin type.
     */
    const val ED25519_COIN_TYPE = 1022
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PATH GENERATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Generate path for a specific account index.
     * 
     * @param accountIndex The account number (0-based)
     * @param scheme The derivation scheme to use
     * @return The derivation path string
     */
    fun forAccount(
        accountIndex: Int,
        scheme: DerivationScheme = DerivationScheme.STANDARD
    ): String {
        require(accountIndex >= 0) { "Account index must be non-negative" }
        
        return when (scheme) {
            DerivationScheme.STANDARD -> "m/44'/501'/$accountIndex'/0'"
            DerivationScheme.LEDGER_LIVE -> "m/44'/501'/$accountIndex'"
            DerivationScheme.ED25519_BIP32 -> "m/44'/1022'/$accountIndex'/0'"
        }
    }
    
    /**
     * Generate path with custom change index.
     * 
     * @param accountIndex The account number
     * @param changeIndex The change number (0 = external, 1 = internal)
     * @return The derivation path string
     */
    fun forAccountAndChange(accountIndex: Int, changeIndex: Int): String {
        require(accountIndex >= 0) { "Account index must be non-negative" }
        require(changeIndex >= 0) { "Change index must be non-negative" }
        return "m/44'/501'/$accountIndex'/$changeIndex'"
    }
    
    /**
     * Generate multiple account paths.
     * 
     * @param count Number of accounts to generate
     * @param scheme The derivation scheme to use
     * @return List of derivation paths
     */
    fun multipleAccounts(
        count: Int,
        scheme: DerivationScheme = DerivationScheme.STANDARD
    ): List<String> {
        require(count > 0) { "Count must be positive" }
        return (0 until count).map { forAccount(it, scheme) }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PATH PARSING & VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Parse a derivation path string.
     * 
     * @param path The path string (e.g., "m/44'/501'/0'/0'")
     * @return Parsed path components
     * @throws IllegalArgumentException if path is invalid
     */
    fun parse(path: String): ParsedDerivationPath {
        val normalized = path.trim().lowercase()
        
        require(normalized.startsWith("m/")) { "Path must start with 'm/'" }
        
        val parts = normalized.removePrefix("m/").split("/")
        val components = parts.map { part ->
            val isHardened = part.endsWith("'") || part.endsWith("h")
            val value = part.removeSuffix("'").removeSuffix("h").toIntOrNull()
                ?: throw IllegalArgumentException("Invalid path component: $part")
            
            PathComponent(value, isHardened)
        }
        
        return ParsedDerivationPath(components)
    }
    
    /**
     * Validate a derivation path.
     * 
     * @param path The path string to validate
     * @return True if valid, false otherwise
     */
    fun isValid(path: String): Boolean {
        return try {
            parse(path)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if a path is a valid Solana path.
     * 
     * @param path The path to check
     * @return True if it's a valid Solana derivation path
     */
    fun isSolanaPath(path: String): Boolean {
        return try {
            val parsed = parse(path)
            parsed.components.size >= 2 &&
                parsed.components[0].value == 44 &&
                parsed.components[0].isHardened &&
                (parsed.components[1].value == SOLANA_COIN_TYPE || 
                 parsed.components[1].value == ED25519_COIN_TYPE) &&
                parsed.components[1].isHardened
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Detect which scheme a path uses.
     */
    fun detectScheme(path: String): DerivationScheme? {
        return try {
            val parsed = parse(path)
            when {
                parsed.components.size == 3 && 
                    parsed.components[1].value == SOLANA_COIN_TYPE -> DerivationScheme.LEDGER_LIVE
                parsed.components.size >= 4 && 
                    parsed.components[1].value == ED25519_COIN_TYPE -> DerivationScheme.ED25519_BIP32
                parsed.components.size >= 4 && 
                    parsed.components[1].value == SOLANA_COIN_TYPE -> DerivationScheme.STANDARD
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Extract account index from a path.
     */
    fun extractAccountIndex(path: String): Int? {
        return try {
            val parsed = parse(path)
            if (parsed.components.size >= 3) {
                parsed.components[2].value
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PATH CONVERSION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Convert a path to a different scheme while preserving account index.
     */
    fun convertScheme(path: String, targetScheme: DerivationScheme): String {
        val accountIndex = extractAccountIndex(path) ?: 0
        return forAccount(accountIndex, targetScheme)
    }
    
    /**
     * Get the next account path.
     */
    fun nextAccount(currentPath: String): String {
        val currentIndex = extractAccountIndex(currentPath) ?: 0
        val scheme = detectScheme(currentPath) ?: DerivationScheme.STANDARD
        return forAccount(currentIndex + 1, scheme)
    }
}

/**
 * Supported derivation schemes.
 */
enum class DerivationScheme {
    /** BIP44 standard: m/44'/501'/account'/change' */
    STANDARD,
    
    /** Ledger Live: m/44'/501'/account' */
    LEDGER_LIVE,
    
    /** Ed25519-BIP32: m/44'/1022'/account'/change' */
    ED25519_BIP32
}

/**
 * A component of a derivation path.
 */
data class PathComponent(
    val value: Int,
    val isHardened: Boolean
) {
    override fun toString(): String = if (isHardened) "$value'" else "$value"
}

/**
 * Parsed derivation path.
 */
data class ParsedDerivationPath(
    val components: List<PathComponent>
) {
    val purpose: Int get() = components.getOrNull(0)?.value ?: 44
    val coinType: Int get() = components.getOrNull(1)?.value ?: 501
    val account: Int get() = components.getOrNull(2)?.value ?: 0
    val change: Int? get() = components.getOrNull(3)?.value
    
    override fun toString(): String = "m/" + components.joinToString("/")
    
    /**
     * Convert to BIP32 path array for native implementations.
     */
    fun toPathArray(): IntArray {
        return components.map { c ->
            if (c.isHardened) c.value or 0x80000000.toInt() else c.value
        }.toIntArray()
    }
}

/**
 * Preset account configurations for common wallets.
 */
object WalletPresets {
    
    /**
     * Get default paths for a wallet type.
     */
    fun defaultPaths(wallet: WalletType, numAccounts: Int = 5): List<String> {
        return when (wallet) {
            WalletType.PHANTOM -> SolanaDerivation.multipleAccounts(numAccounts, DerivationScheme.STANDARD)
            WalletType.SOLFLARE -> SolanaDerivation.multipleAccounts(numAccounts, DerivationScheme.STANDARD)
            WalletType.LEDGER -> SolanaDerivation.multipleAccounts(numAccounts, DerivationScheme.LEDGER_LIVE)
            WalletType.BACKPACK -> SolanaDerivation.multipleAccounts(numAccounts, DerivationScheme.STANDARD)
            WalletType.GENERIC -> SolanaDerivation.multipleAccounts(numAccounts, DerivationScheme.STANDARD)
        }
    }
    
    enum class WalletType {
        PHANTOM,
        SOLFLARE,
        LEDGER,
        BACKPACK,
        GENERIC
    }
}
