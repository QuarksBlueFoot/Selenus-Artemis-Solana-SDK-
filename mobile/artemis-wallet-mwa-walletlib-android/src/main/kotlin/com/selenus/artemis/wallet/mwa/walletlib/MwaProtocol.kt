package com.selenus.artemis.wallet.mwa.walletlib

/**
 * MWA 2.0 JSON-RPC error codes. Values match
 * `com.solana.mobilewalletadapter.common.ProtocolContract` exactly so a
 * dApp using upstream's clientlib resolves them through the existing
 * `JsonRpc20RemoteException` switch.
 */
object MwaErrorCodes {
    const val AUTHORIZATION_FAILED: Int = -1
    const val INVALID_PAYLOADS: Int = -2
    const val NOT_SIGNED: Int = -3
    const val NOT_SUBMITTED: Int = -4
    const val NOT_CLONED: Int = -5
    const val TOO_MANY_PAYLOADS: Int = -6
    const val CHAIN_NOT_SUPPORTED: Int = -7

    /** Standard JSON-RPC: method does not exist / is not available. */
    const val METHOD_NOT_FOUND: Int = -32601
    /** Standard JSON-RPC: invalid params. */
    const val INVALID_PARAMS: Int = -32602
    /** Standard JSON-RPC: internal error. */
    const val INTERNAL_ERROR: Int = -32603
}

/**
 * MWA RPC method names. Centralised as `const`s instead of inlined
 * strings so a typo in either the dispatcher or the dApp-side client
 * surfaces as an unresolved reference at compile time rather than as
 * `METHOD_NOT_FOUND` at runtime.
 */
object MwaMethods {
    const val GET_CAPABILITIES: String = "get_capabilities"
    const val AUTHORIZE: String = "authorize"
    const val REAUTHORIZE: String = "reauthorize"
    const val DEAUTHORIZE: String = "deauthorize"
    const val SIGN_TRANSACTIONS: String = "sign_transactions"
    const val SIGN_AND_SEND_TRANSACTIONS: String = "sign_and_send_transactions"
    const val SIGN_MESSAGES: String = "sign_messages"
    const val CLONE_AUTHORIZATION: String = "clone_authorization"
}

/**
 * Wire-protocol constants the wallet-side dispatcher and any compat
 * shims share. Mirrors `com.solana.mobilewalletadapter.common.
 * ProtocolContract` so a dApp using upstream's clientlib resolves the
 * same string values.
 */
object ProtocolContract {
    /** Default chain when the dApp omits `chain` in `authorize`. */
    const val CHAIN_SOLANA_MAINNET: String = "solana:mainnet"
    const val CHAIN_SOLANA_TESTNET: String = "solana:testnet"
    const val CHAIN_SOLANA_DEVNET: String = "solana:devnet"

    /** Legacy cluster strings kept for MWA 1.x dApps. */
    const val CLUSTER_MAINNET_BETA: String = "mainnet-beta"
    const val CLUSTER_TESTNET: String = "testnet"
    const val CLUSTER_DEVNET: String = "devnet"

    /**
     * Map a CAIP-2 chain identifier to the legacy cluster string used
     * by MWA 1.x. Returns `null` for unknown chains so callers can fall
     * back to passing the CAIP-2 string verbatim.
     */
    fun clusterForChain(chain: String?): String? = when (chain) {
        CHAIN_SOLANA_MAINNET -> CLUSTER_MAINNET_BETA
        CHAIN_SOLANA_TESTNET -> CLUSTER_TESTNET
        CHAIN_SOLANA_DEVNET -> CLUSTER_DEVNET
        else -> null
    }
}
