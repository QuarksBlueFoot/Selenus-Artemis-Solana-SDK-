package com.selenus.artemis.seedvault

import android.os.Bundle
import com.selenus.artemis.runtime.Pubkey

/**
 * Type-safe wrappers for Seed Vault data structures.
 * Eliminates the need to manually manage Bundles and string keys.
 */

data class SeedVaultAccount(
    val id: Long,
    val name: String,
    val publicKey: Pubkey,
    val derivationPath: String? = null
) {
    companion object {
        fun fromBundle(bundle: Bundle): SeedVaultAccount {
            val id = bundle.getLong("account_id")
            val name = bundle.getString("account_name") ?: "Unknown"
            val pkBytes = bundle.getByteArray("public_key") ?: throw IllegalArgumentException("Missing public key")
            return SeedVaultAccount(id, name, Pubkey(pkBytes))
        }
    }
}

data class SeedVaultAuthorization(
    val authToken: String,
    val account: SeedVaultAccount
)

/**
 * Raw result from the authorization Intent. Contains only the auth token
 * and account ID - the actual public key must be resolved via ContentProvider
 * using [SeedVaultManager.resolveAuthorization].
 */
data class SeedVaultTokenResult(
    val authToken: Long,
    val accountId: Long
)

sealed class SeedVaultException(message: String) : Exception(message) {
    class Unauthorized(message: String) : SeedVaultException(message)
    class UserRejected(message: String) : SeedVaultException(message)
    class InvalidRequest(message: String) : SeedVaultException(message)
    class InternalError(message: String) : SeedVaultException(message)
    /**
     * The Seed Vault system service is not reachable — either the binder
     * died mid-call, the service disconnected before responding, or the
     * app was never able to bind to it. Raised immediately for every
     * in-flight call so callers never hang waiting on a dead service.
     */
    class ServiceUnavailable(message: String) : SeedVaultException(message)
    class Unknown(message: String) : SeedVaultException(message)

    companion object {
        fun fromBundle(bundle: Bundle): SeedVaultException {
            val msg = bundle.getString("error_message") ?: "Unknown error"
            return when (val code = bundle.getInt("error_code", -1)) {
                401 -> Unauthorized(msg)
                403 -> UserRejected(msg)
                400 -> InvalidRequest(msg)
                500 -> InternalError(msg)
                else -> Unknown("Code $code: $msg")
            }
        }
    }
}

object SeedVaultKeys {
    const val AUTH_TOKEN = "auth_token"
    const val PAYLOADS = "payloads"
    const val SIGNATURES = "signatures"
    const val ACCOUNTS = "accounts"
}
