package com.selenus.artemis.seedvault

import com.selenus.artemis.runtime.Pubkey

/**
 * ArtemisKeyStore - key custody abstraction.
 *
 * Contract: this interface represents a key store that signs on the
 * caller's behalf. The guarantee each implementation provides is only as
 * strong as that implementation. In particular:
 *
 * - [SeedVaultKeyStore] delegates to the Solana Seed Vault system service.
 *   On supported devices (Saga, dApp Store) the underlying seed material is
 *   hardware-backed and non-exportable; on other devices the implementation
 *   will refuse to initialise.
 * - Callers that provide their own implementations are responsible for
 *   their own threat model. Artemis makes NO claim that a particular
 *   implementation is hardware-backed or attested beyond what the
 *   implementation documents.
 *
 * For wallet-level operations (connect, send, session management), use
 * [WalletAdapter] and [WalletSession].
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
