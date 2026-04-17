package com.selenus.artemis.seedvault

import com.selenus.artemis.runtime.Pubkey

/**
 * SeedVaultKeyStore - [ArtemisKeyStore] backed by the Solana Seed Vault.
 *
 * Keys are held in the device's secure element and never leave hardware.
 * All signing operations are dispatched to the Seed Vault system service via IPC.
 *
 * ```kotlin
 * val manager = SeedVaultManager(context)
 * manager.connect()
 * val auth = manager.resolveAuthorization(tokenResult)
 * val keyStore = SeedVaultKeyStore(manager, auth.authToken)
 *
 * val accounts = keyStore.getAccounts()
 * val signature = keyStore.signTransaction(txBytes)
 * ```
 */
class SeedVaultKeyStore(
    private val manager: SeedVaultManager,
    private val authToken: String
) : ArtemisKeyStore {

    override suspend fun getAccounts(): List<Pubkey> {
        return manager.getAccounts(authToken).map { it.publicKey }
    }

    override suspend fun signTransaction(payload: ByteArray): ByteArray {
        val signatures = manager.signTransactions(authToken, listOf(payload))
        return signatures.firstOrNull()
            ?: throw SeedVaultException.InternalError("Seed Vault returned no signature for signTransaction")
    }

    override suspend fun signMessage(payload: ByteArray): ByteArray {
        val signatures = manager.signMessages(authToken, listOf(payload))
        return signatures.firstOrNull()
            ?: throw SeedVaultException.InternalError("Seed Vault returned no signature for signMessage")
    }
}
