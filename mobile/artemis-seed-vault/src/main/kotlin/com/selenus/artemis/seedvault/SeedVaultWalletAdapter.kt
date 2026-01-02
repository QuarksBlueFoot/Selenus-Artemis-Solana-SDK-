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
            supportsReSign = true,
            supportsPartialSign = false,
            supportsFeePayerSwap = false,
            supportsMultipleMessages = true,
            supportsPreAuthorize = true
        )
    }

    override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray {
        // In Seed Vault, signMessage usually means signing a transaction
        val signatures = manager.signTransactions(authToken, listOf(message))
        return signatures.firstOrNull()
            ?: throw IllegalStateException("Seed Vault returned no signatures for signMessage")
    }

    override suspend fun signMessages(messages: List<ByteArray>, request: WalletRequest): List<ByteArray> {
        val result = manager.signTransactions(authToken, messages)
        require(result.size == messages.size) {
            "Seed Vault returned ${result.size} signatures for ${messages.size} messages"
        }
        return result
    }

    override suspend fun signArbitraryMessage(message: ByteArray, request: WalletRequest): ByteArray {
        val signatures = manager.signMessages(authToken, listOf(message))
        return signatures.firstOrNull()
            ?: throw IllegalStateException("Seed Vault returned no signatures for signArbitraryMessage")
    }
}
