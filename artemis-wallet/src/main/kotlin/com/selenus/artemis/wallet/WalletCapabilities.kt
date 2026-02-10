package com.selenus.artemis.wallet

/**
 * WalletCapabilities
 *
 * A normalized capability contract for wallets and signers.
 * This provides feature detection across different wallet implementations
 * (MWA, Wallet Standard, hardware wallets, etc.).
 * 
 * Full MWA 2.0 parity with additional Artemis enhancements.
 */
data class WalletCapabilities(
  // Basic signing capabilities
  val supportsReSign: Boolean = true,
  val supportsPartialSign: Boolean = false,
  val supportsFeePayerSwap: Boolean = false,
  val supportsMultipleMessages: Boolean = true,
  val supportsPreAuthorize: Boolean = false,
  
  // MWA 2.0 capabilities
  /** Whether wallet supports sign_and_send_transactions (MWA 2.0 mandatory) */
  val supportsSignAndSend: Boolean = true,
  
  /** Whether wallet supports sign_transactions (MWA 2.0 optional) */
  val supportsSignTransactions: Boolean = true,
  
  /** Whether wallet supports clone_authorization (MWA 2.0 optional) */
  val supportsCloneAuthorization: Boolean = false,
  
  /** Whether wallet supports Sign In With Solana (SIWS) */
  val supportsSignIn: Boolean = false,
  
  // Transaction version support
  /** Whether wallet supports legacy transaction format */
  val supportsLegacyTransactions: Boolean = true,
  
  /** Whether wallet supports versioned transactions (v0) */
  val supportsVersionedTransactions: Boolean = false,
  
  // Request limits
  /** Maximum transactions per signing request (null = no limit) */
  val maxTransactionsPerRequest: Int? = null,
  
  /** Maximum messages per signing request (null = no limit) */
  val maxMessagesPerRequest: Int? = null,
  
  // Feature identifiers (MWA 2.0 features array)
  /** Set of all supported feature identifiers */
  val supportedFeatures: Set<String> = emptySet(),
  
  // Artemis enhancements
  /** Whether wallet supports offline/background signing */
  val supportsOfflineSigning: Boolean = false,
  
  /** Whether wallet supports transaction simulation before signing */
  val supportsSimulation: Boolean = false
) {
  companion object {
    /** Default capabilities for mobile wallets */
    fun defaultMobile(): WalletCapabilities = WalletCapabilities(
      supportsReSign = true,
      supportsPartialSign = false,
      supportsFeePayerSwap = false,
      supportsMultipleMessages = true,
      supportsPreAuthorize = false,
      supportsSignAndSend = true,
      supportsSignTransactions = true,
      supportsLegacyTransactions = true,
      supportsVersionedTransactions = true
    )
    
    /** Default capabilities for hardware wallets */
    fun defaultHardware(): WalletCapabilities = WalletCapabilities(
      supportsReSign = false,
      supportsPartialSign = true,
      supportsFeePayerSwap = false,
      supportsMultipleMessages = false,
      supportsPreAuthorize = false,
      supportsSignAndSend = false,
      supportsSignTransactions = true,
      supportsOfflineSigning = true
    )
    
    /** Default capabilities for browser extension wallets */
    fun defaultExtension(): WalletCapabilities = WalletCapabilities(
      supportsReSign = true,
      supportsPartialSign = true,
      supportsFeePayerSwap = false,
      supportsMultipleMessages = true,
      supportsPreAuthorize = true,
      supportsSignAndSend = true,
      supportsSignTransactions = true,
      supportsSimulation = true
    )
  }
  
  /** Check if a specific feature is supported */
  fun hasFeature(featureId: String): Boolean = supportedFeatures.contains(featureId)
}
