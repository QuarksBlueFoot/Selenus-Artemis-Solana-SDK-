/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * ORIGINAL IMPLEMENTATION - Enhanced HD derivation utilities for Solana.
 * Provides BIP32/BIP44 path generation and multi-account management.
 * 
 * Addresses Seed Vault SDK Issue #637: Add m/44'/501' to default pre-derived Solana accounts
 */
package com.selenus.artemis.seedvault

import com.selenus.artemis.runtime.Pubkey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * HD Derivation Path utilities for Solana.
 * 
 * Provides BIP32/BIP44 path generation following Solana standards:
 * - Standard path: m/44'/501'/0'/0'
 * - Change addresses: m/44'/501'/account'/change'
 * - Hardened derivation for security
 * 
 * Pre-derived paths (Issue #637 fix):
 * - m/44'/501' (Solana root for compatibility with older wallets)
 * - m/44'/501'/0' (First account, common Ledger format)
 * - m/44'/501'/0'/0' (Standard BIP44 format)
 * 
 * Features unique to this SDK:
 * - **Path validation**: Ensures correct format
 * - **Named accounts**: Human-readable account labels
 * - **Batch derivation**: Derive multiple accounts efficiently
 * - **Change address generation**: Support for receiving addresses
 */
object DerivationPath {
    
    /** Solana coin type as per SLIP-0044 */
    const val SOLANA_COIN_TYPE = 501
    
    /** BIP44 purpose constant (hardened) */
    const val PURPOSE = 44
    
    /** Hardened indicator for derivation */
    const val HARDENED_OFFSET = 0x80000000.toInt()
    
    /**
     * Default pre-derived paths for Solana.
     * 
     * These should be pre-derived by wallets on seed creation to ensure
     * compatibility with various dApps and wallet implementations.
     * 
     * Issue #637: m/44'/501' should be included for older wallet compatibility.
     */
    object Defaults {
        /** Solana root path - for maximum compatibility with older wallets */
        const val SOLANA_ROOT = "m/44'/501'"
        
        /** First account without change - common Ledger/hardware wallet format */
        const val FIRST_ACCOUNT_NO_CHANGE = "m/44'/501'/0'"
        
        /** Standard BIP44 first account - most common format */
        const val FIRST_ACCOUNT = "m/44'/501'/0'/0'"
        
        /** Second account for separation of concerns */
        const val SECOND_ACCOUNT = "m/44'/501'/1'/0'"
        
        /** Third account */
        const val THIRD_ACCOUNT = "m/44'/501'/2'/0'"
        
        /**
         * Get all default paths that should be pre-derived.
         * This fixes Seed Vault SDK Issue #637.
         */
        fun all(): List<String> = listOf(
            SOLANA_ROOT,           // m/44'/501' - for older wallet compat
            FIRST_ACCOUNT_NO_CHANGE, // m/44'/501'/0' - Ledger format
            FIRST_ACCOUNT,         // m/44'/501'/0'/0' - standard
            SECOND_ACCOUNT,        // m/44'/501'/1'/0' - secondary
            THIRD_ACCOUNT          // m/44'/501'/2'/0' - tertiary
        )
        
        /**
         * Get the recommended set of paths for new seed creation.
         * Includes the first 3 standard accounts plus compatibility paths.
         */
        fun forNewSeed(): List<String> = all()
        
        /**
         * Get extended paths for power users (first 10 accounts).
         */
        fun extended(): List<String> = buildList {
            add(SOLANA_ROOT)
            add(FIRST_ACCOUNT_NO_CHANGE)
            for (i in 0..9) {
                add("m/44'/501'/$i'/0'")
            }
        }
    }
    
    /**
     * Generate a standard Solana derivation path.
     * 
     * Format: m/44'/501'/account'/change'
     * 
     * @param account Account index (0-based)
     * @param change Change index (usually 0 for external, 1 for internal)
     * @return BIP44 derivation path string
     */
    fun solana(account: Int = 0, change: Int = 0): String {
        require(account >= 0) { "Account index must be non-negative" }
        require(change >= 0) { "Change index must be non-negative" }
        return "m/$PURPOSE'/$SOLANA_COIN_TYPE'/$account'/$change'"
    }
    
    /**
     * Generate Solana root path (for legacy compatibility).
     * This is the m/44'/501' path that some older wallets use.
     */
    fun solanaRoot(): String = "m/$PURPOSE'/$SOLANA_COIN_TYPE'"
    
    /**
     * Generate Ledger-style path (without change component).
     * Format: m/44'/501'/account'
     */
    fun solanaLedger(account: Int = 0): String {
        require(account >= 0) { "Account index must be non-negative" }
        return "m/$PURPOSE'/$SOLANA_COIN_TYPE'/$account'"
    }
    
    /**
     * Generate multiple account paths.
     * 
     * @param count Number of accounts to generate
     * @param startIndex Starting account index
     * @return List of derivation paths
     */
    fun solanaAccounts(count: Int, startIndex: Int = 0): List<String> {
        require(count > 0) { "Count must be positive" }
        require(startIndex >= 0) { "Start index must be non-negative" }
        return (startIndex until startIndex + count).map { solana(it) }
    }
    
    /**
     * Generate change addresses for an account.
     * 
     * @param account Account index
     * @param changeCount Number of change addresses
     * @return List of change address paths
     */
    fun changeAddresses(account: Int, changeCount: Int): List<String> {
        require(changeCount > 0) { "Change count must be positive" }
        return (0 until changeCount).map { solana(account, it) }
    }
    
    /**
     * Parse a derivation path into its components.
     * 
     * @param path The derivation path string
     * @return Parsed components or null if invalid
     */
    fun parse(path: String): DerivationComponents? {
        val pattern = Regex("""^m/(\d+)'?/(\d+)'?/(\d+)'?(?:/(\d+)'?)?$""")
        val match = pattern.matchEntire(path) ?: return null
        
        val (purpose, coinType, account, change) = match.destructured
        
        return DerivationComponents(
            purpose = purpose.toIntOrNull() ?: return null,
            coinType = coinType.toIntOrNull() ?: return null,
            account = account.toIntOrNull() ?: return null,
            change = change.toIntOrNull() ?: 0
        )
    }
    
    /**
     * Validate a derivation path.
     * 
     * @param path The derivation path to validate
     * @return true if valid for Solana
     */
    fun isValidSolanaPath(path: String): Boolean {
        val components = parse(path) ?: return false
        return components.purpose == PURPOSE && components.coinType == SOLANA_COIN_TYPE
    }
    
    /**
     * Convert path to index array for low-level derivation.
     * 
     * @param path The derivation path
     * @return Array of indices with hardened flags
     */
    fun toIndices(path: String): IntArray {
        val components = parse(path) ?: throw IllegalArgumentException("Invalid path: $path")
        
        return intArrayOf(
            components.purpose or HARDENED_OFFSET,
            components.coinType or HARDENED_OFFSET,
            components.account or HARDENED_OFFSET,
            components.change or HARDENED_OFFSET
        )
    }
}

/**
 * Parsed derivation path components.
 */
data class DerivationComponents(
    val purpose: Int,
    val coinType: Int,
    val account: Int,
    val change: Int
)

/**
 * Multi-Account Manager for Seed Vault.
 * 
 * Features unique to this SDK:
 * - **Account discovery**: Automatically find used accounts
 * - **Named accounts**: Assign human-readable labels
 * - **Balance tracking**: Monitor account balances
 * - **Account rotation**: Seamlessly switch active account
 * - **Derived key caching**: Efficient key management
 */
class MultiAccountManager private constructor(
    private val seedVault: SeedVaultManager,
    private val authToken: String
) {
    
    private val mutex = Mutex()
    private val accounts = mutableMapOf<Int, DerivedAccount>()
    private val _activeAccountIndex = MutableStateFlow(0)
    
    /**
     * Currently active account index.
     */
    val activeAccountIndex: StateFlow<Int> = _activeAccountIndex.asStateFlow()
    
    /**
     * Get the active account.
     */
    suspend fun getActiveAccount(): DerivedAccount? = mutex.withLock {
        accounts[_activeAccountIndex.value]
    }
    
    /**
     * Set the active account by index.
     */
    suspend fun setActiveAccount(index: Int) = mutex.withLock {
        require(index in accounts.keys) { "Account $index not derived yet" }
        _activeAccountIndex.value = index
    }
    
    /**
     * Derive a single account.
     * 
     * @param index Account index
     * @param name Optional human-readable name
     * @return The derived account
     */
    suspend fun deriveAccount(index: Int, name: String? = null): DerivedAccount = mutex.withLock {
        accounts[index]?.let { return it }
        
        val path = DerivationPath.solana(index)
        val keys = seedVault.requestPublicKeys(authToken, listOf(path))
        
        if (keys.isEmpty()) {
            throw SeedVaultException.Unknown("Failed to derive key for path: $path")
        }
        
        val account = DerivedAccount(
            index = index,
            name = name ?: "Account $index",
            publicKey = keys.first(),
            derivationPath = path
        )
        
        accounts[index] = account
        return account
    }
    
    /**
     * Derive multiple accounts.
     * 
     * @param count Number of accounts to derive
     * @param startIndex Starting index
     * @return List of derived accounts
     */
    suspend fun deriveAccounts(count: Int, startIndex: Int = 0): List<DerivedAccount> = mutex.withLock {
        val paths = DerivationPath.solanaAccounts(count, startIndex)
        val keys = seedVault.requestPublicKeys(authToken, paths)
        
        val result = keys.mapIndexed { idx, pubkey ->
            val accountIndex = startIndex + idx
            val account = DerivedAccount(
                index = accountIndex,
                name = "Account $accountIndex",
                publicKey = pubkey,
                derivationPath = paths[idx]
            )
            accounts[accountIndex] = account
            account
        }
        
        return result
    }
    
    /**
     * Get all derived accounts.
     */
    suspend fun getAllAccounts(): List<DerivedAccount> = mutex.withLock {
        accounts.values.sortedBy { it.index }
    }
    
    /**
     * Sign a message with the active account.
     */
    suspend fun signWithActiveAccount(message: ByteArray): ByteArray {
        val activeAccount = getActiveAccount()
            ?: throw IllegalStateException("No active account")
        
        val signatures = seedVault.signWithDerivationPath(
            authToken,
            activeAccount.derivationPath,
            listOf(message)
        )
        
        return signatures.firstOrNull()
            ?: throw SeedVaultException.Unknown("No signature returned")
    }
    
    /**
     * Sign with a specific account index.
     */
    suspend fun signWithAccount(accountIndex: Int, message: ByteArray): ByteArray = mutex.withLock {
        val account = accounts[accountIndex]
            ?: throw IllegalArgumentException("Account $accountIndex not derived")
        
        val signatures = seedVault.signWithDerivationPath(
            authToken,
            account.derivationPath,
            listOf(message)
        )
        
        return signatures.firstOrNull()
            ?: throw SeedVaultException.Unknown("No signature returned")
    }
    
    /**
     * Stream accounts as they are derived.
     */
    fun accountStream(): Flow<DerivedAccount> = flow {
        val currentAccounts = getAllAccounts()
        for (account in currentAccounts) {
            emit(account)
        }
    }
    
    /**
     * Rename an account.
     */
    suspend fun renameAccount(index: Int, newName: String) = mutex.withLock {
        val account = accounts[index]
            ?: throw IllegalArgumentException("Account $index not found")
        
        accounts[index] = account.copy(name = newName)
    }
    
    /**
     * Account discovery - find accounts that have been used.
     * 
     * This scans through accounts looking for those with transaction history.
     * Uses a gap limit approach - stops after N consecutive unused accounts.
     * 
     * @param gapLimit Number of consecutive unused accounts before stopping
     * @param checkBalance Function to check if an account has been used
     * @return List of discovered accounts with activity
     */
    suspend fun discoverAccounts(
        gapLimit: Int = 20,
        checkBalance: suspend (Pubkey) -> Boolean
    ): List<DerivedAccount> = mutex.withLock {
        val discovered = mutableListOf<DerivedAccount>()
        var consecutiveEmpty = 0
        var index = 0
        
        while (consecutiveEmpty < gapLimit) {
            val account = deriveAccountInternal(index)
            val hasActivity = checkBalance(account.publicKey)
            
            if (hasActivity) {
                discovered.add(account)
                accounts[index] = account
                consecutiveEmpty = 0
            } else {
                consecutiveEmpty++
            }
            
            index++
        }
        
        return discovered
    }
    
    private suspend fun deriveAccountInternal(index: Int): DerivedAccount {
        val path = DerivationPath.solana(index)
        val keys = seedVault.requestPublicKeys(authToken, listOf(path))
        
        return DerivedAccount(
            index = index,
            name = "Account $index",
            publicKey = keys.first(),
            derivationPath = path
        )
    }
    
    companion object {
        /**
         * Create a new multi-account manager.
         * 
         * @param seedVault The seed vault manager
         * @param authToken Authorization token from seed vault
         * @return Multi-account manager instance
         */
        fun create(seedVault: SeedVaultManager, authToken: String): MultiAccountManager {
            return MultiAccountManager(seedVault, authToken)
        }
    }
}

/**
 * A derived account from the seed vault.
 */
data class DerivedAccount(
    val index: Int,
    val name: String,
    val publicKey: Pubkey,
    val derivationPath: String
) {
    /**
     * Get the public key as a base58 string.
     */
    val address: String get() = publicKey.toBase58()
    
    /**
     * Get a short form of the address (first 4...last 4).
     */
    val shortAddress: String get() {
        val full = address
        return if (full.length > 10) {
            "${full.take(4)}...${full.takeLast(4)}"
        } else {
            full
        }
    }
}

/**
 * Account label for human-readable display.
 */
data class AccountLabel(
    val name: String,
    val emoji: String? = null,
    val color: String? = null
) {
    companion object {
        val DEFAULT = AccountLabel("Main Account", "\uD83D\uDCB0")
        val SAVINGS = AccountLabel("Savings", "\uD83C\uDFE6")
        val TRADING = AccountLabel("Trading", "\uD83D\uDCC8")
        val NFT = AccountLabel("NFT Wallet", "\uD83D\uDDBC️")
        val DEFI = AccountLabel("DeFi", "\uD83E\uDD16")
    }
}
