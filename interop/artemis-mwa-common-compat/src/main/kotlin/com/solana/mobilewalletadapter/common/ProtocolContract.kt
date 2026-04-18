/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.common.ProtocolContract.
 *
 * Constants verified against the upstream Java source. Every name and string
 * literal matches the Java constant byte-for-byte so existing
 * `if (code == ProtocolContract.ERROR_NOT_CLONED)` call sites resolve to
 * the same numeric value and `result["sign_in_result"]` lookups still work.
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.common

object ProtocolContract {
    // ─── JSON-RPC method names ─────────────────────────────────────────────
    const val METHOD_AUTHORIZE: String = "authorize"
    const val METHOD_DEAUTHORIZE: String = "deauthorize"
    const val METHOD_REAUTHORIZE: String = "reauthorize"
    const val METHOD_GET_CAPABILITIES: String = "get_capabilities"
    const val METHOD_SIGN_TRANSACTIONS: String = "sign_transactions"
    const val METHOD_SIGN_AND_SEND_TRANSACTIONS: String = "sign_and_send_transactions"
    const val METHOD_SIGN_MESSAGES: String = "sign_messages"
    const val METHOD_CLONE_AUTHORIZATION: String = "clone_authorization"

    // ─── Authorize / reauthorize parameter keys ────────────────────────────
    const val PARAMETER_IDENTITY: String = "identity"
    const val PARAMETER_IDENTITY_URI: String = "uri"
    const val PARAMETER_IDENTITY_ICON: String = "icon"
    const val PARAMETER_IDENTITY_NAME: String = "name"
    const val PARAMETER_CLUSTER: String = "cluster"
    const val PARAMETER_CHAIN: String = "chain"
    const val PARAMETER_AUTH_TOKEN: String = "auth_token"
    const val PARAMETER_FEATURES: String = "features"
    const val PARAMETER_ADDRESSES: String = "addresses"
    const val PARAMETER_SIGN_IN_PAYLOAD: String = "sign_in_payload"

    // ─── Authorize / reauthorize result keys ───────────────────────────────
    const val RESULT_AUTH_TOKEN: String = "auth_token"
    const val RESULT_ACCOUNTS: String = "accounts"
    const val RESULT_WALLET_URI_BASE: String = "wallet_uri_base"
    const val RESULT_WALLET_ICON: String = "wallet_icon"
    const val RESULT_SIGN_IN: String = "sign_in_result"
    const val RESULT_ACCOUNTS_ADDRESS: String = "address"
    const val RESULT_ACCOUNTS_DISPLAY_ADDRESS: String = "display_address"
    const val RESULT_ACCOUNTS_DISPLAY_ADDRESS_FORMAT: String = "display_address_format"
    const val RESULT_ACCOUNTS_LABEL: String = "label"
    const val RESULT_ACCOUNTS_ICON: String = "icon"
    const val RESULT_ACCOUNTS_CHAINS: String = "chains"
    const val RESULT_ACCOUNTS_FEATURES: String = "features"
    const val RESULT_SIGN_IN_ADDRESS: String = "address"
    const val RESULT_SIGN_IN_SIGNED_MESSAGE: String = "signed_message"
    const val RESULT_SIGN_IN_SIGNATURE: String = "signature"
    const val RESULT_SIGN_IN_SIGNATURE_TYPE: String = "signature_type"

    // ─── get_capabilities result keys ──────────────────────────────────────
    const val RESULT_MAX_TRANSACTIONS_PER_REQUEST: String = "max_transactions_per_request"
    const val RESULT_MAX_MESSAGES_PER_REQUEST: String = "max_messages_per_request"
    const val RESULT_SUPPORTED_TRANSACTION_VERSIONS: String = "supported_transaction_versions"
    const val RESULT_SUPPORTED_FEATURES: String = "features"
    const val RESULT_SUPPORTS_CLONE_AUTHORIZATION: String = "supports_clone_authorization"
    const val RESULT_SUPPORTS_SIGN_AND_SEND_TRANSACTIONS: String =
        "supports_sign_and_send_transactions"

    // ─── sign_payloads / sign_and_send options ─────────────────────────────
    const val PARAMETER_PAYLOADS: String = "payloads"
    const val PARAMETER_OPTIONS: String = "options"
    const val PARAMETER_OPTIONS_MIN_CONTEXT_SLOT: String = "min_context_slot"
    const val PARAMETER_OPTIONS_COMMITMENT: String = "commitment"
    const val PARAMETER_OPTIONS_SKIP_PREFLIGHT: String = "skip_preflight"
    const val PARAMETER_OPTIONS_MAX_RETRIES: String = "max_retries"
    const val PARAMETER_OPTIONS_WAIT_FOR_COMMITMENT: String =
        "wait_for_commitment_to_send_next_transaction"
    const val RESULT_SIGNED_PAYLOADS: String = "signed_payloads"
    const val RESULT_SIGNATURES: String = "signatures"

    // ─── Feature identifiers ───────────────────────────────────────────────
    const val FEATURE_ID_SIGN_IN_WITH_SOLANA: String = "solana:signInWithSolana"
    const val FEATURE_ID_SIGN_TRANSACTIONS: String = "solana:signTransactions"
    const val FEATURE_ID_SIGN_MESSAGES: String = "solana:signMessages"
    const val FEATURE_ID_SIGN_AND_SEND_TRANSACTIONS: String = "solana:signAndSendTransaction"
    const val FEATURE_ID_CLONE_AUTHORIZATION: String = "solana:cloneAuthorization"

    // ─── Chain IDs ─────────────────────────────────────────────────────────
    const val CHAIN_SOLANA_MAINNET: String = "solana:mainnet"
    const val CHAIN_SOLANA_TESTNET: String = "solana:testnet"
    const val CHAIN_SOLANA_DEVNET: String = "solana:devnet"

    // ─── Legacy cluster aliases (retained for MWA v1.0 wire compatibility) ─
    const val CLUSTER_MAINNET_BETA: String = "mainnet-beta"
    const val CLUSTER_TESTNET: String = "testnet"
    const val CLUSTER_DEVNET: String = "devnet"

    // ─── Commitment levels ─────────────────────────────────────────────────
    const val COMMITMENT_PROCESSED: String = "processed"
    const val COMMITMENT_CONFIRMED: String = "confirmed"
    const val COMMITMENT_FINALIZED: String = "finalized"

    // ─── Error codes (negative JSON-RPC domain codes) ──────────────────────
    const val ERROR_AUTHORIZATION_FAILED: Int = -1
    const val ERROR_INVALID_PAYLOADS: Int = -2
    const val ERROR_NOT_SIGNED: Int = -3
    const val ERROR_NOT_SUBMITTED: Int = -4
    const val ERROR_NOT_CLONED: Int = -5
    const val ERROR_TOO_MANY_PAYLOADS: Int = -6
    const val ERROR_CLUSTER_NOT_SUPPORTED: Int = -7
    const val ERROR_CHAIN_NOT_SUPPORTED: Int = -7
    const val ERROR_ATTEST_ORIGIN_ANDROID: Int = -100

    // ─── Error data keys ───────────────────────────────────────────────────
    const val DATA_INVALID_PAYLOADS_VALID: String = "valid"
    const val DATA_NOT_SUBMITTED_SIGNATURES: String = "signatures"
}
