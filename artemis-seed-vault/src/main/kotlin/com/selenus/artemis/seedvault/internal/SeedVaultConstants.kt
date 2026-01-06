package com.selenus.artemis.seedvault.internal

object SeedVaultConstants {
    const val PACKAGE_SEED_VAULT = "com.solanamobile.seedvaultimpl"
    const val SERVICE_PACKAGE = "com.solanamobile.seedvault"
    const val SERVICE_CLASS = "com.solanamobile.seedvault.SeedVaultService"
    const val ACTION_BIND_SEED_VAULT = "com.solanamobile.seedvault.BIND_SEED_VAULT"
    
    // Authorities and Permissions
    const val AUTHORITY_WALLET = "com.solanamobile.seedvault.wallet.v1"
    const val PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED = "com.solanamobile.seedvault.ACCESS_SEED_VAULT_PRIVILEGED"
    const val PERMISSION_ACCESS_SEED_VAULT = "com.solanamobile.seedvault.ACCESS_SEED_VAULT"
    const val PERMISSION_SEED_VAULT_IMPL = "com.solanamobile.seedvault.SEED_VAULT_IMPL"

    // Intent Actions
    const val ACTION_AUTHORIZE_SEED_ACCESS = "$AUTHORITY_WALLET.ACTION_AUTHORIZE_SEED_ACCESS"
    const val ACTION_SIGN_TRANSACTION = "$AUTHORITY_WALLET.ACTION_SIGN_TRANSACTION"
    const val ACTION_SIGN_MESSAGE = "$AUTHORITY_WALLET.ACTION_SIGN_MESSAGE"
    const val ACTION_CREATE_SEED = "$AUTHORITY_WALLET.ACTION_CREATE_SEED"
    const val ACTION_IMPORT_SEED = "$AUTHORITY_WALLET.ACTION_IMPORT_SEED"
    const val ACTION_GET_ACCOUNTS = "$AUTHORITY_WALLET.ACTION_GET_ACCOUNTS"

    // Extras
    const val EXTRA_PURPOSE = "purpose"
    const val EXTRA_AUTH_TOKEN = "auth_token"
    const val EXTRA_ACCOUNT_ID = "account_id"
    const val EXTRA_ACCOUNTS = "accounts"
    const val EXTRA_SIGNING_REQUEST = "signing_request"
    const val EXTRA_SIGNING_RESPONSE = "signing_response"
    
     // Result Codes
    const val RESULT_INVALID_PURPOSE = 10
    const val RESULT_NO_AVAILABLE_SEEDS = 11
    const val RESULT_AUTHENTICATION_FAILED = 12
    const val RESULT_INVALID_AUTH_TOKEN = 13
    const val RESULT_INVALID_PAYLOAD = 14
    const val RESULT_INVALID_DERIVATION_PATH = 15
    const val RESULT_IMPLEMENTATION_LIMIT_EXCEEDED = 16

    // Schemes
    const val BIP32_URI_SCHEME = "bip32"
    const val BIP44_URI_SCHEME = "bip44"

    const val PURPOSE_SIGN_SOLANA_TRANSACTION = 1
}
