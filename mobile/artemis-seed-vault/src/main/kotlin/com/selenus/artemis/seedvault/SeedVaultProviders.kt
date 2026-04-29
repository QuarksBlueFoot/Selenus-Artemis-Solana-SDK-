/*
 * Concrete provider implementations layered on top of [SeedVaultContractClient].
 *
 * `SeedVaultAccountProviderImpl` and `SeedVaultSigningProviderImpl` are the
 * production implementations used by [SeedVaultManager]. They take a
 * contract client, feed its raw Bundle results through
 * [SeedVaultResponseValidator], and return strongly-typed artefacts.
 *
 * Splitting the providers from the manager lets:
 *   - tests substitute a fake contract client without touching real binder
 *     IPC
 *   - apps add signing or account lookup backed by something other than
 *     the system Seed Vault (MPC signer, hardware wallet, emulator)
 *     without rewriting every caller
 */
package com.selenus.artemis.seedvault

import android.net.Uri
import android.os.Bundle
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.seedvault.internal.SeedVaultConstants

class SeedVaultAccountProviderImpl(
    private val contract: SeedVaultContractClient
) : SeedVaultAccountProvider {

    override suspend fun getAccounts(authToken: String): List<SeedVaultAccount> {
        val params = Bundle().apply {
            putLong(
                SeedVaultConstants.EXTRA_AUTH_TOKEN,
                SeedVaultManager.parseAuthTokenStrict(authToken)
            )
        }
        val response = contract.getAccounts(params)
        val list = response.getParcelableArrayList<Bundle>(SeedVaultConstants.EXTRA_ACCOUNTS)
            ?: return emptyList()
        return list.map { SeedVaultAccount.fromBundle(it) }
    }

    override suspend fun requestPublicKeys(
        authToken: String,
        derivationPaths: List<Uri>
    ): List<Pubkey> {
        derivationPaths.forEach {
            SeedVaultResponseValidator.requireDerivationPath(it, "requestPublicKeys")
        }
        val params = Bundle().apply {
            putLong(
                SeedVaultConstants.EXTRA_AUTH_TOKEN,
                SeedVaultManager.parseAuthTokenStrict(authToken)
            )
            putParcelableArrayList(
                SeedVaultConstants.EXTRA_DERIVATION_PATH,
                ArrayList(derivationPaths)
            )
        }
        // Multiple paths map to a single binder call: the AIDL only has one
        // resolveDerivationPath / signMessages verb, so requests are batched
        // through the same path the manager uses. The contract client owns the
        // IPC; the provider validates.
        val response = contract.resolveDerivationPath(params)
        val keys = response.getParcelableArrayList<Bundle>(SeedVaultConstants.EXTRA_PUBLIC_KEY)
            ?: throw SeedVaultException.Unknown(
                "Seed Vault requestPublicKeys returned no keys"
            )
        return keys.mapIndexed { index, bundle ->
            val raw = bundle.getByteArray("public_key_raw")
            SeedVaultResponseValidator.requirePublicKey(raw, "requestPublicKeys[$index]")
        }
    }

    override suspend fun resolveDerivationPath(
        authToken: String,
        derivationPath: Uri
    ): Pubkey {
        SeedVaultResponseValidator.requireDerivationPath(derivationPath, "resolveDerivationPath")
        val params = Bundle().apply {
            putLong(
                SeedVaultConstants.EXTRA_AUTH_TOKEN,
                SeedVaultManager.parseAuthTokenStrict(authToken)
            )
            putParcelable(SeedVaultConstants.EXTRA_DERIVATION_PATH, derivationPath)
        }
        val response = contract.resolveDerivationPath(params)
        val keyBytes = response.getByteArray(SeedVaultConstants.EXTRA_PUBLIC_KEY)
        return SeedVaultResponseValidator.requirePublicKey(keyBytes, "resolveDerivationPath")
    }
}

class SeedVaultSigningProviderImpl(
    private val contract: SeedVaultContractClient
) : SeedVaultSigningProvider {

    override suspend fun signTransactions(
        authToken: String,
        transactions: List<ByteArray>
    ): List<ByteArray> {
        require(transactions.isNotEmpty()) { "signTransactions: transactions list must not be empty" }
        val params = Bundle().apply {
            putLong(
                SeedVaultConstants.EXTRA_AUTH_TOKEN,
                SeedVaultManager.parseAuthTokenStrict(authToken)
            )
            putSerializable(SeedVaultConstants.KEY_PAYLOADS, ArrayList(transactions))
        }
        val response = contract.signTransactions(params)
        return extractSignatures(response, transactions.size, "signTransactions")
    }

    override suspend fun signMessages(
        authToken: String,
        messages: List<ByteArray>
    ): List<ByteArray> {
        require(messages.isNotEmpty()) { "signMessages: messages list must not be empty" }
        val params = Bundle().apply {
            putLong(
                SeedVaultConstants.EXTRA_AUTH_TOKEN,
                SeedVaultManager.parseAuthTokenStrict(authToken)
            )
            putSerializable(SeedVaultConstants.KEY_PAYLOADS, ArrayList(messages))
        }
        val response = contract.signMessages(params)
        return extractSignatures(response, messages.size, "signMessages")
    }

    override suspend fun signWithDerivationPath(
        authToken: String,
        derivationPath: Uri,
        payloads: List<ByteArray>
    ): List<ByteArray> {
        require(payloads.isNotEmpty()) { "signWithDerivationPath: payloads list must not be empty" }
        SeedVaultResponseValidator.requireDerivationPath(derivationPath, "signWithDerivationPath")
        val params = Bundle().apply {
            putLong(
                SeedVaultConstants.EXTRA_AUTH_TOKEN,
                SeedVaultManager.parseAuthTokenStrict(authToken)
            )
            putParcelable(SeedVaultConstants.EXTRA_DERIVATION_PATH, derivationPath)
            putSerializable(SeedVaultConstants.KEY_PAYLOADS, ArrayList(payloads))
        }
        val response = contract.signTransactions(params)
        return extractSignatures(response, payloads.size, "signWithDerivationPath")
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractSignatures(
        response: Bundle,
        expected: Int,
        method: String
    ): List<ByteArray> {
        SeedVaultResponseValidator.requireBundleKey(response, SeedVaultConstants.KEY_SIGNATURES, method)
        val sigs = response.getSerializable(SeedVaultConstants.KEY_SIGNATURES) as? ArrayList<ByteArray>
            ?: throw SeedVaultException.Unknown(
                "Seed Vault $method returned non-list signatures container"
            )
        SeedVaultResponseValidator.requireSignatureCount(sigs.size, expected, method)
        return sigs.mapIndexed { index, bytes ->
            SeedVaultResponseValidator.requireSignature(bytes, index, method)
        }
    }
}
