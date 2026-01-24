package com.selenus.artemis.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Artemis Account Discovery
 * 
 * Implements BIP-44 gap limit account discovery for finding all
 * active accounts from a single seed phrase.
 * 
 * This fills a gap in the Solana Mobile SDK which lacks:
 * - Automatic account discovery
 * - Gap limit handling
 * - Balance-based account detection
 * - Multiple wallet path support
 */
object AccountDiscovery {
    
    /** Standard gap limit per BIP-44 */
    const val DEFAULT_GAP_LIMIT = 20
    
    /** Common derivation path patterns used by Solana wallets */
    enum class DerivationPattern(val description: String) {
        /** Solana standard: m/44'/501'/x'/0' */
        SOLANA_STANDARD("Solana Standard (Phantom, Solflare)"),
        
        /** Solana short: m/44'/501'/x' (some older wallets) */
        SOLANA_SHORT("Solana Short Path"),
        
        /** Ledger Live: m/44'/501'/x'/0'/0' */
        LEDGER_LIVE("Ledger Live"),
        
        /** Trust Wallet: m/44'/501'/0'/0'/x' */
        TRUST_WALLET("Trust Wallet");
        
        fun derivePath(accountIndex: Int): String {
            return when (this) {
                SOLANA_STANDARD -> "m/44'/501'/${accountIndex}'/0'"
                SOLANA_SHORT -> "m/44'/501'/${accountIndex}'"
                LEDGER_LIVE -> "m/44'/501'/${accountIndex}'/0'/0'"
                TRUST_WALLET -> "m/44'/501'/0'/0'/${accountIndex}'"
            }
        }
    }
    
    /**
     * Result of account discovery.
     */
    data class DiscoveredAccount(
        val publicKey: Pubkey,
        val keypair: Keypair,
        val derivationPath: String,
        val accountIndex: Int,
        val pattern: DerivationPattern,
        val hasActivity: Boolean,
        val balance: Long? = null
    )
    
    /**
     * Discovery result containing all found accounts.
     */
    data class DiscoveryResult(
        val accounts: List<DiscoveredAccount>,
        val scannedCount: Int,
        val patternsChecked: List<DerivationPattern>,
        val gapLimitReached: Boolean
    ) {
        /** Primary accounts with activity */
        val activeAccounts: List<DiscoveredAccount>
            get() = accounts.filter { it.hasActivity }
        
        /** The first (default) account */
        val primaryAccount: DiscoveredAccount?
            get() = accounts.firstOrNull()
        
        /** Total balance across all accounts */
        val totalBalance: Long
            get() = accounts.mapNotNull { it.balance }.sum()
    }
    
    /**
     * Activity checker interface - implement to define how accounts are checked.
     */
    fun interface ActivityChecker {
        /**
         * Check if an account has activity (transactions or balance).
         * @return Pair of (hasActivity, balance in lamports or null)
         */
        suspend fun checkActivity(publicKey: Pubkey): Pair<Boolean, Long?>
    }
    
    /**
     * Discovers accounts from a mnemonic phrase using BIP-44 gap limit.
     * 
     * @param mnemonic The BIP-39 mnemonic phrase
     * @param passphrase Optional BIP-39 passphrase
     * @param patterns Which derivation patterns to check
     * @param gapLimit Stop after this many consecutive empty accounts
     * @param maxAccounts Maximum accounts to discover per pattern
     * @param activityChecker Function to check if account has activity
     */
    suspend fun discover(
        mnemonic: String,
        passphrase: String = "",
        patterns: List<DerivationPattern> = listOf(DerivationPattern.SOLANA_STANDARD),
        gapLimit: Int = DEFAULT_GAP_LIMIT,
        maxAccounts: Int = 100,
        activityChecker: ActivityChecker
    ): DiscoveryResult = withContext(Dispatchers.Default) {
        require(Bip39.isValid(mnemonic)) { "Invalid mnemonic phrase" }
        require(gapLimit > 0) { "Gap limit must be positive" }
        
        val seed = Bip39.toSeed(mnemonic, passphrase)
        val discoveredAccounts = mutableListOf<DiscoveredAccount>()
        var totalScanned = 0
        var gapLimitReached = false
        
        for (pattern in patterns) {
            var consecutiveEmpty = 0
            var accountIndex = 0
            
            while (consecutiveEmpty < gapLimit && accountIndex < maxAccounts) {
                val path = pattern.derivePath(accountIndex)
                val keypair = Bip32.deriveKeypair(seed, path)
                
                val (hasActivity, balance) = activityChecker.checkActivity(keypair.publicKey)
                totalScanned++
                
                if (hasActivity) {
                    consecutiveEmpty = 0
                    discoveredAccounts.add(
                        DiscoveredAccount(
                            publicKey = keypair.publicKey,
                            keypair = keypair,
                            derivationPath = path,
                            accountIndex = accountIndex,
                            pattern = pattern,
                            hasActivity = true,
                            balance = balance
                        )
                    )
                } else {
                    consecutiveEmpty++
                    // Still include first account even if empty
                    if (accountIndex == 0) {
                        discoveredAccounts.add(
                            DiscoveredAccount(
                                publicKey = keypair.publicKey,
                                keypair = keypair,
                                derivationPath = path,
                                accountIndex = accountIndex,
                                pattern = pattern,
                                hasActivity = false,
                                balance = balance
                            )
                        )
                    }
                }
                
                accountIndex++
            }
            
            if (consecutiveEmpty >= gapLimit) {
                gapLimitReached = true
            }
        }
        
        DiscoveryResult(
            accounts = discoveredAccounts,
            scannedCount = totalScanned,
            patternsChecked = patterns,
            gapLimitReached = gapLimitReached
        )
    }
    
    /**
     * Quick discovery of just the first N accounts without activity checking.
     * Useful for generating accounts before checking activity.
     */
    fun generateAccounts(
        mnemonic: String,
        passphrase: String = "",
        count: Int = 5,
        pattern: DerivationPattern = DerivationPattern.SOLANA_STANDARD
    ): List<DiscoveredAccount> {
        require(Bip39.isValid(mnemonic)) { "Invalid mnemonic phrase" }
        require(count > 0) { "Count must be positive" }
        
        val seed = Bip39.toSeed(mnemonic, passphrase)
        
        return (0 until count).map { index ->
            val path = pattern.derivePath(index)
            val keypair = Bip32.deriveKeypair(seed, path)
            
            DiscoveredAccount(
                publicKey = keypair.publicKey,
                keypair = keypair,
                derivationPath = path,
                accountIndex = index,
                pattern = pattern,
                hasActivity = false, // Unknown until checked
                balance = null
            )
        }
    }
    
    /**
     * Derives a specific account without discovery.
     */
    fun deriveAccount(
        mnemonic: String,
        accountIndex: Int,
        passphrase: String = "",
        pattern: DerivationPattern = DerivationPattern.SOLANA_STANDARD
    ): DiscoveredAccount {
        require(Bip39.isValid(mnemonic)) { "Invalid mnemonic phrase" }
        require(accountIndex >= 0) { "Account index must be non-negative" }
        
        val seed = Bip39.toSeed(mnemonic, passphrase)
        val path = pattern.derivePath(accountIndex)
        val keypair = Bip32.deriveKeypair(seed, path)
        
        return DiscoveredAccount(
            publicKey = keypair.publicKey,
            keypair = keypair,
            derivationPath = path,
            accountIndex = accountIndex,
            pattern = pattern,
            hasActivity = false,
            balance = null
        )
    }
    
    /**
     * Attempts to find which derivation pattern was used for a given public key.
     * Useful for wallet recovery when the pattern is unknown.
     */
    fun findDerivationPattern(
        mnemonic: String,
        targetPublicKey: Pubkey,
        passphrase: String = "",
        patterns: List<DerivationPattern> = DerivationPattern.entries,
        maxAccountIndex: Int = 10
    ): Pair<DerivationPattern, Int>? {
        require(Bip39.isValid(mnemonic)) { "Invalid mnemonic phrase" }
        
        val seed = Bip39.toSeed(mnemonic, passphrase)
        
        for (pattern in patterns) {
            for (index in 0 until maxAccountIndex) {
                val path = pattern.derivePath(index)
                val keypair = Bip32.deriveKeypair(seed, path)
                
                if (keypair.publicKey == targetPublicKey) {
                    return pattern to index
                }
            }
        }
        
        return null
    }
}
