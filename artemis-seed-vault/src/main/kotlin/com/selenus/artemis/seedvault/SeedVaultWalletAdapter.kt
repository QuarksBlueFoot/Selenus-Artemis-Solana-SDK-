package com.selenus.artemis.seedvault

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.wallet.WalletAdapter
import com.selenus.artemis.wallet.WalletCapabilities
import com.selenus.artemis.wallet.WalletRequest

/**
 * SeedVaultWalletAdapter
 *
 * Adapts the Seed Vault to the standard WalletAdapter interface.
 * Allows Seed Vault to be used interchangeably with MWA or other signers.
 */
class SeedVaultWalletAdapter(
    private val manager: SeedVaultManager,
    private val authToken: String,
    private val account: SeedVaultAccount
) : WalletAdapter {

    override val publicKey: Pubkey = account.publicKey

    override suspend fun getCapabilities(): WalletCapabilities {
        return WalletCapabilities(
            supportsSignAndSend = false, // Seed Vault is a signer, not an RPC provider
            supportsSignTransactions = true,
            supportsSignMessages = true,
            maxTransactionsPerRequest = 20,
            maxMessagesPerRequest = 20,
            supportsPreAuthorize = true,
            supportsMultipleMessages = true
        )
    }

    override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray {
        // In Seed Vault, signMessage usually means signing a transaction
        val signatures = manager.signTransactions(authToken, listOf(message))
        return signatures.first()
    }

    override suspend fun signMessages(messages: List<ByteArray>, request: WalletRequest): List<ByteArray> {
        return manager.signTransactions(authToken, messages)
    }

    override suspend fun signArbitraryMessage(message: ByteArray, request: WalletRequest): ByteArray {
        val signatures = manager.signMessages(authToken, listOf(message))
        return signatures.first()
    }
}
