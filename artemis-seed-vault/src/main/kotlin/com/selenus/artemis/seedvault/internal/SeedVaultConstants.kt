package com.selenus.artemis.seedvault.internal

/**
 * Defines the contract constants for the Solana Mobile Seed Vault.
 *
 * These values define the external protocol required to communicate with the system-level
 * Seed Vault service on compatible devices (e.g. Saga).
 *
 * **Note:** Do not modify the string values of these constants, as they must match the
 * manifest and service definitions of the OS-level Seed Vault provider.
 */
object SeedVaultConstants {
    // region Service Binding
    /** The package name of the default Seed Vault implementation. */
    const val PACKAGE_SEED_VAULT = "com.solanamobile.seedvaultimpl"
    /** The package used for explicit service binding intent. */
    const val SERVICE_PACKAGE = "com.solanamobile.seedvault"
    /** The class name of the Seed Vault system service. */
    const val SERVICE_CLASS = "com.solanamobile.seedvault.SeedVaultService"
    /** The Intent action used to bind to the Seed Vault service. */
    const val ACTION_BIND_SEED_VAULT = "com.solanamobile.seedvault.BIND_SEED_VAULT"
    // endregion

    // region Authorities and Permissions
    /** Content Provider Authority for the Wallet SDK. */
    const val AUTHORITY_WALLET = "com.solanamobile.seedvault.wallet.v1"
    
    /** Permission required for privileged (system) access. */
    const val PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED = "com.solanamobile.seedvault.ACCESS_SEED_VAULT_PRIVILEGED"
    /** Permission required for standard app access. */
    const val PERMISSION_ACCESS_SEED_VAULT = "com.solanamobile.seedvault.ACCESS_SEED_VAULT"
    /** Permission required to implement a Seed Vault provider. */
    const val PERMISSION_SEED_VAULT_IMPL = "com.solanamobile.seedvault.SEED_VAULT_IMPL"
    // endregion

    // region Intent Actions
    /** Action to request authorization for seed access. */
    const val ACTION_AUTHORIZE_SEED_ACCESS = "$AUTHORITY_WALLET.ACTION_AUTHORIZE_SEED_ACCESS"
    const val ACTION_SIGN_TRANSACTION = "$AUTHORITY_WALLET.ACTION_SIGN_TRANSACTION"
    const val ACTION_SIGN_MESSAGE = "$AUTHORITY_WALLET.ACTION_SIGN_MESSAGE"
    const val ACTION_CREATE_SEED = "$AUTHORITY_WALLET.ACTION_CREATE_SEED"
    const val ACTION_IMPORT_SEED = "$AUTHORITY_WALLET.ACTION_IMPORT_SEED"
    const val ACTION_GET_ACCOUNTS = "$AUTHORITY_WALLET.ACTION_GET_ACCOUNTS"
    // endregion

    // region Bundle Extras
    const val EXTRA_PURPOSE = "purpose"
    const val EXTRA_AUTH_TOKEN = "auth_token"
    const val EXTRA_ACCOUNT_ID = "account_id"
    const val EXTRA_ACCOUNTS = "accounts"
    const val EXTRA_SIGNING_REQUEST = "signing_request"
    const val EXTRA_SIGNING_RESPONSE = "signing_response"
    // endregion

    // region Result Codes
    const val RESULT_INVALID_PURPOSE = 10
    const val RESULT_NO_AVAILABLE_SEEDS = 11
    const val RESULT_AUTHENTICATION_FAILED = 12
    const val RESULT_INVALID_AUTH_TOKEN = 13
    const val RESULT_INVALID_PAYLOAD = 14
    const val RESULT_INVALID_DERIVATION_PATH = 15
    const val RESULT_IMPLEMENTATION_LIMIT_EXCEEDED = 16
    // endregion

    // region URI Schemes
    const val BIP32_URI_SCHEME = "bip32"
    const val BIP44_URI_SCHEME = "bip44"
    // endregion

    const val PURPOSE_SIGN_SOLANA_TRANSACTION = 1

    // region Payload Keys
    const val KEY_PAYLOADS = "payloads"
    const val KEY_SIGNATURES = "signatures"
    // endregion
}
