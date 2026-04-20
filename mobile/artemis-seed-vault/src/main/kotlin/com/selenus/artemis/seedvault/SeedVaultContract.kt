/*
 * Seed Vault provider contract split.
 *
 * The audit flagged that [SeedVaultManager] was doing three unrelated
 * jobs at once: raw binder IPC, account retrieval, and signing. That made
 * the layer hard to test (every test needed a live binder) and hard to
 * extend (a custom provider like a hardware signer emulator could not be
 * plugged in). The three interfaces here split those concerns cleanly:
 *
 *   SeedVaultContractClient  - raw IPC verbs; 1:1 with the binder AIDL.
 *                              Throws typed exceptions on failure, no
 *                              convenience wrapping.
 *   SeedVaultAccountProvider - typed account lookup built on top of the
 *                              contract client.
 *   SeedVaultSigningProvider - typed signing (transactions + arbitrary
 *                              messages + derivation-path signing) built
 *                              on top of the contract client.
 *
 * `SeedVaultManager` keeps its public surface but now delegates to these
 * interfaces. Tests pass a [FakeSeedVaultContractClient] (not yet shipped
 * but easy to write) without touching the real Android binder.
 */
package com.selenus.artemis.seedvault

import android.net.Uri
import android.os.Bundle
import com.selenus.artemis.runtime.Pubkey

/**
 * Raw Seed Vault binder verbs. Every method maps 1:1 to a method on the
 * system service's AIDL interface (see
 * `src/main/aidl/com/solanamobile/seedvault/ISeedVaultService.aidl`).
 *
 * Implementations throw [SeedVaultException] on failure; no method returns
 * an error bundle silently.
 */
interface SeedVaultContractClient {
    suspend fun authorize(params: Bundle): Bundle
    suspend fun createSeed(params: Bundle): Bundle
    suspend fun importSeed(params: Bundle): Bundle
    suspend fun updateSeed(params: Bundle): Bundle
    suspend fun getAccounts(params: Bundle): Bundle
    suspend fun resolveDerivationPath(params: Bundle): Bundle
    suspend fun signTransactions(params: Bundle): Bundle
    suspend fun signMessages(params: Bundle): Bundle
    suspend fun deauthorize(params: Bundle): Bundle
}

/**
 * Typed account retrieval. Layered on top of [SeedVaultContractClient] so
 * callers get back strongly-typed [SeedVaultAccount] records instead of
 * raw bundles.
 */
interface SeedVaultAccountProvider {
    suspend fun getAccounts(authToken: String): List<SeedVaultAccount>

    suspend fun requestPublicKeys(
        authToken: String,
        derivationPaths: List<Uri>
    ): List<Pubkey>

    suspend fun resolveDerivationPath(
        authToken: String,
        derivationPath: Uri
    ): Pubkey
}

/**
 * Typed signing. Takes validated inputs and returns [ByteArray] signatures,
 * validating every response against the expected length + count. A
 * wallet/app never sees a malformed signature sneak through to on-chain
 * submission.
 */
interface SeedVaultSigningProvider {
    suspend fun signTransactions(
        authToken: String,
        transactions: List<ByteArray>
    ): List<ByteArray>

    suspend fun signMessages(
        authToken: String,
        messages: List<ByteArray>
    ): List<ByteArray>

    suspend fun signWithDerivationPath(
        authToken: String,
        derivationPath: Uri,
        payloads: List<ByteArray>
    ): List<ByteArray>
}
