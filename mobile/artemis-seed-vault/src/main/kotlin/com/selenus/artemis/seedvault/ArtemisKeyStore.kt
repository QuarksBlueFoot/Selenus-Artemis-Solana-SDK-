package com.selenus.artemis.seedvault

import com.selenus.artemis.runtime.Pubkey

/**
 * ArtemisKeyStore - secure key custody abstraction.
 *
 * This is the low-level interface for hardware-backed key storage.
 * Keys never leave the secure element - all signing happens on-device.
 *
 * Implementations:
 * - [SeedVaultKeyStore]: Solana Seed Vault (Saga, dApp Store devices)
 * - Apps can provide their own implementations for custom HSMs or enclaves.
 *
 * For wallet-level operations (connect, send, session management),
 * use [WalletAdapter] and [WalletSession] instead.
 */
interface ArtemisKeyStore {

    /**
     * List all accounts available in this key store.
     *
     * @return Public keys for all authorized accounts
     */
    suspend fun getAccounts(): List<Pubkey>

    /**
     * Sign a serialized transaction using the key store's secure signing.
     *
     * The payload is a fully-serialized transaction message. The key store
     * signs it with the authorized key and returns the raw Ed25519 signature.
     *
     * @param payload Serialized transaction bytes
     * @return Ed25519 signature bytes (64 bytes)
     */
    suspend fun signTransaction(payload: ByteArray): ByteArray

    /**
     * Sign an arbitrary off-chain message.
     *
     * @param payload Raw message bytes to sign
     * @return Ed25519 signature bytes (64 bytes)
     */
    suspend fun signMessage(payload: ByteArray): ByteArray
}
