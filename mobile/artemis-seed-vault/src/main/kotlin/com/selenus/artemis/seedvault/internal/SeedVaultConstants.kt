package com.selenus.artemis.seedvault.internal

import android.net.Uri

/**
 * Defines the contract constants for the Solana Mobile Seed Vault.
 * 
 * Full parity with com.solanamobile.seedvault.WalletContractV1 v0.4.0.
 *
 * These values define the external protocol required to communicate with the system-level
 * Seed Vault service on compatible devices (e.g. Saga).
 *
 * **Note:** Do not modify the string values of these constants, as they must match the
 * manifest and service definitions of the OS-level Seed Vault provider.
 */
object SeedVaultConstants {
    // ═══════════════════════════════════════════════════════════════════════════
    // SERVICE BINDING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** The package name of the default Seed Vault implementation. */
    const val PACKAGE_SEED_VAULT = "com.solanamobile.seedvaultimpl"
    /** The package used for explicit service binding intent. */
    const val SERVICE_PACKAGE = "com.solanamobile.seedvault"
    /** The class name of the Seed Vault system service. */
    const val SERVICE_CLASS = "com.solanamobile.seedvault.SeedVaultService"
    /** The Intent action used to bind to the Seed Vault service. */
    const val ACTION_BIND_SEED_VAULT = "com.solanamobile.seedvault.BIND_SEED_VAULT"

    // ═══════════════════════════════════════════════════════════════════════════
    // AUTHORITIES AND PERMISSIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Content Provider Authority for the Wallet SDK. */
    const val AUTHORITY_WALLET = "com.solanamobile.seedvault.wallet.v1"
    
    /** Permission required for privileged (system) access. */
    const val PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED = "com.solanamobile.seedvault.ACCESS_SEED_VAULT_PRIVILEGED"
    /** Permission required for standard app access. */
    const val PERMISSION_ACCESS_SEED_VAULT = "com.solanamobile.seedvault.ACCESS_SEED_VAULT"
    /** Permission required to implement a Seed Vault provider. */
    const val PERMISSION_SEED_VAULT_IMPL = "com.solanamobile.seedvault.SEED_VAULT_IMPL"

    // ═══════════════════════════════════════════════════════════════════════════
    // INTENT ACTIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Action to request authorization for seed access. */
    const val ACTION_AUTHORIZE_SEED_ACCESS = "$AUTHORITY_WALLET.ACTION_AUTHORIZE_SEED_ACCESS"
    /** Action to sign a set of transactions. */
    const val ACTION_SIGN_TRANSACTION = "$AUTHORITY_WALLET.ACTION_SIGN_TRANSACTION"
    /** Action to sign a set of messages. */
    const val ACTION_SIGN_MESSAGE = "$AUTHORITY_WALLET.ACTION_SIGN_MESSAGE"
    /** Action to request public keys. */
    const val ACTION_GET_PUBLIC_KEY = "$AUTHORITY_WALLET.ACTION_GET_PUBLIC_KEY"
    /** Action to create a new seed. NOTE: Use implicit Intent (no package). */
    const val ACTION_CREATE_SEED = "$AUTHORITY_WALLET.ACTION_CREATE_SEED"
    /** Action to import an existing seed. NOTE: Use implicit Intent (no package). */
    const val ACTION_IMPORT_SEED = "$AUTHORITY_WALLET.ACTION_IMPORT_SEED"
    /** Action to get accounts. */
    const val ACTION_GET_ACCOUNTS = "$AUTHORITY_WALLET.ACTION_GET_ACCOUNTS"
    /** Action to show seed settings. Privileged only. */
    const val ACTION_SEED_SETTINGS = "$AUTHORITY_WALLET.ACTION_SEED_SETTINGS"

    // ═══════════════════════════════════════════════════════════════════════════
    // BUNDLE EXTRAS (matching upstream WalletContractV1 exactly)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Purpose for this action (int, one of PURPOSE_* constants). */
    const val EXTRA_PURPOSE = "Purpose"
    /** Auth token (long). */
    const val EXTRA_AUTH_TOKEN = "AuthToken"
    /** Account ID (long). */
    const val EXTRA_ACCOUNT_ID = "account_id"
    /** Accounts bundle list. */
    const val EXTRA_ACCOUNTS = "accounts"
    /** Signing request(s) - ArrayList<SigningRequest>. */
    const val EXTRA_SIGNING_REQUEST = "SigningRequest"
    /** Signing response(s) - ArrayList<SigningResponse>. */
    const val EXTRA_SIGNING_RESPONSE = "SigningResponse"
    /** Derivation path(s) - ArrayList<Uri>. */
    const val EXTRA_DERIVATION_PATH = "DerivationPath"
    /** Public key response(s) - ArrayList<PublicKeyResponse>. */
    const val EXTRA_PUBLIC_KEY = "PublicKey"

    // ═══════════════════════════════════════════════════════════════════════════
    // PURPOSE CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Sign Solana transactions. */
    const val PURPOSE_SIGN_SOLANA_TRANSACTION = 0

    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT CODES (RESULT_FIRST_USER + offset)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** An unspecified error occurred. */
    const val RESULT_UNSPECIFIED_ERROR = 1000  // RESULT_FIRST_USER + 999
    /** Invalid or unknown auth token. */
    const val RESULT_INVALID_AUTH_TOKEN = 1001  // RESULT_FIRST_USER + 1000
    /** Invalid transaction payload for signing purpose. */
    const val RESULT_INVALID_PAYLOAD = 1002  // RESULT_FIRST_USER + 1001
    /** Legacy alias for RESULT_INVALID_PAYLOAD. */
    const val RESULT_INVALID_TRANSACTION = RESULT_INVALID_PAYLOAD
    /** User failed or declined authentication. */
    const val RESULT_AUTHENTICATION_FAILED = 1003  // RESULT_FIRST_USER + 1002
    /** No seeds available to authorize. */
    const val RESULT_NO_AVAILABLE_SEEDS = 1004  // RESULT_FIRST_USER + 1003
    /** Invalid purpose value. */
    const val RESULT_INVALID_PURPOSE = 1005  // RESULT_FIRST_USER + 1004
    /** Invalid BIP32 or BIP44 derivation path URI. */
    const val RESULT_INVALID_DERIVATION_PATH = 1006  // RESULT_FIRST_USER + 1005
    /** Implementation limit exceeded. */
    const val RESULT_IMPLEMENTATION_LIMIT_EXCEEDED = 1007  // RESULT_FIRST_USER + 1006

    // ═══════════════════════════════════════════════════════════════════════════
    // BIP URI CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** URI scheme for BIP32 derivation paths. Format: bip32:/m/44'/501'/0' */
    const val BIP32_URI_SCHEME = "bip32"
    /** URI scheme for BIP44 derivation paths. Format: bip44:/0' */
    const val BIP44_URI_SCHEME = "bip44"
    /** Master key indicator in BIP32 URIs. */
    const val BIP32_URI_MASTER_KEY_INDICATOR = "m"
    /** Hardened index identifier in BIP URIs. */
    const val BIP_URI_HARDENED_INDEX_IDENTIFIER = "'"
    /** Maximum BIP32 derivation path depth. */
    const val BIP32_URI_MAX_DEPTH = 20

    // ═══════════════════════════════════════════════════════════════════════════
    // PERMISSIONED ACCOUNTS (PRIVILEGED ACCESS)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** BIP44 account index for permissioned accounts. */
    const val PERMISSIONED_BIP44_ACCOUNT = 10000
    /** BIP44 change index for permissioned accounts. */
    const val PERMISSIONED_BIP44_CHANGE = 0

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPLEMENTATION LIMITS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Minimum number of signing requests all implementations must support. */
    const val MIN_SUPPORTED_SIGNING_REQUESTS = 3
    /** Minimum number of requested signatures per signing request. */
    const val MIN_SUPPORTED_REQUESTED_SIGNATURES = 3
    /** Minimum number of public key requests per ACTION_GET_PUBLIC_KEY. */
    const val MIN_SUPPORTED_REQUESTED_PUBLIC_KEYS = 10

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTENT PROVIDER - WALLET PROVIDER
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Authority of the Seed Vault Wallet content provider. */
    const val AUTHORITY_WALLET_PROVIDER = "$AUTHORITY_WALLET.walletprovider"
    /** Base content URI for the wallet provider. */
    val WALLET_PROVIDER_CONTENT_URI_BASE: Uri = Uri.parse("content://$AUTHORITY_WALLET_PROVIDER")
    
    /** Method to resolve BIP32 derivation paths. */
    const val RESOLVE_BIP32_DERIVATION_PATH_METHOD = "resolve_bip32_derivation_path"

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTENT PROVIDER - AUTHORIZED SEEDS TABLE
    // ═══════════════════════════════════════════════════════════════════════════
    
    const val AUTHORIZED_SEEDS_TABLE = "authorizedseeds"
    val AUTHORIZED_SEEDS_CONTENT_URI: Uri = Uri.withAppendedPath(WALLET_PROVIDER_CONTENT_URI_BASE, AUTHORIZED_SEEDS_TABLE)
    const val AUTHORIZED_SEEDS_AUTH_TOKEN = "_id"  // BaseColumns._ID
    const val AUTHORIZED_SEEDS_AUTH_PURPOSE = "AuthorizedSeeds_AuthPurpose"
    const val AUTHORIZED_SEEDS_SEED_NAME = "AuthorizedSeeds_SeedName"
    val AUTHORIZED_SEEDS_ALL_COLUMNS = arrayOf(
        AUTHORIZED_SEEDS_AUTH_TOKEN,
        AUTHORIZED_SEEDS_AUTH_PURPOSE,
        AUTHORIZED_SEEDS_SEED_NAME
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTENT PROVIDER - UNAUTHORIZED SEEDS TABLE
    // ═══════════════════════════════════════════════════════════════════════════
    
    const val UNAUTHORIZED_SEEDS_TABLE = "unauthorizedseeds"
    val UNAUTHORIZED_SEEDS_CONTENT_URI: Uri = Uri.withAppendedPath(WALLET_PROVIDER_CONTENT_URI_BASE, UNAUTHORIZED_SEEDS_TABLE)
    const val UNAUTHORIZED_SEEDS_AUTH_PURPOSE = "UnauthorizedSeeds_AuthPurpose"
    const val UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS = "UnauthorizedSeeds_HasUnauthorizedSeeds"
    val UNAUTHORIZED_SEEDS_ALL_COLUMNS = arrayOf(
        UNAUTHORIZED_SEEDS_AUTH_PURPOSE,
        UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTENT PROVIDER - ACCOUNTS TABLE
    // ═══════════════════════════════════════════════════════════════════════════
    
    const val ACCOUNTS_TABLE = "accounts"
    val ACCOUNTS_CONTENT_URI: Uri = Uri.withAppendedPath(WALLET_PROVIDER_CONTENT_URI_BASE, ACCOUNTS_TABLE)
    const val ACCOUNTS_ACCOUNT_ID = "_id"  // BaseColumns._ID
    const val ACCOUNTS_BIP32_DERIVATION_PATH = "Accounts_Bip32DerivationPath"
    const val ACCOUNTS_PUBLIC_KEY_RAW = "Accounts_PublicKeyRaw"
    const val ACCOUNTS_PUBLIC_KEY_ENCODED = "Accounts_PublicKeyEncoded"
    const val ACCOUNTS_ACCOUNT_NAME = "Accounts_AccountName"
    const val ACCOUNTS_ACCOUNT_IS_USER_WALLET = "Accounts_IsUserWallet"
    const val ACCOUNTS_ACCOUNT_IS_VALID = "Accounts_IsValid"
    val ACCOUNTS_ALL_COLUMNS = arrayOf(
        ACCOUNTS_ACCOUNT_ID,
        ACCOUNTS_BIP32_DERIVATION_PATH,
        ACCOUNTS_PUBLIC_KEY_RAW,
        ACCOUNTS_PUBLIC_KEY_ENCODED,
        ACCOUNTS_ACCOUNT_NAME,
        ACCOUNTS_ACCOUNT_IS_USER_WALLET,
        ACCOUNTS_ACCOUNT_IS_VALID
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTENT PROVIDER - IMPLEMENTATION LIMITS TABLE
    // ═══════════════════════════════════════════════════════════════════════════
    
    const val IMPLEMENTATION_LIMITS_TABLE = "implementationlimits"
    val IMPLEMENTATION_LIMITS_CONTENT_URI: Uri = Uri.withAppendedPath(WALLET_PROVIDER_CONTENT_URI_BASE, IMPLEMENTATION_LIMITS_TABLE)
    const val IMPLEMENTATION_LIMITS_AUTH_PURPOSE = "ImplementationLimits_AuthPurpose"
    const val IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS = "ImplementationLimits_MaxSigningRequests"
    const val IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES = "ImplementationLimits_MaxRequestedSignatures"
    const val IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS = "ImplementationLimits_MaxRequestedPublicKeys"
    val IMPLEMENTATION_LIMITS_ALL_COLUMNS = arrayOf(
        IMPLEMENTATION_LIMITS_AUTH_PURPOSE,
        IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS,
        IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES,
        IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // LEGACY KEYS (kept for backward compatibility with older code)
    // ═══════════════════════════════════════════════════════════════════════════
    
    const val KEY_PAYLOADS = "payloads"
    const val KEY_SIGNATURES = "signatures"
}
