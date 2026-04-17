/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.common.AssociationContract.
 *
 * Defines the URI scheme and query parameters used by local and remote
 * association intents. Every field name is canonical per the MWA spec.
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.common

object AssociationContract {
    const val SCHEME_MOBILE_WALLET_ADAPTER: String = "solana-wallet"

    const val PARAMETER_ASSOCIATION_TOKEN: String = "association"
    const val PARAMETER_PROTOCOL_VERSION: String = "v"

    const val LOCAL_PATH_SUFFIX: String = "v1/associate/local"
    const val LOCAL_PARAMETER_PORT: String = "port"

    const val REMOTE_PATH_SUFFIX: String = "v1/associate/remote"
    const val REMOTE_PARAMETER_REFLECTOR_HOST_AUTHORITY: String = "reflector"
    const val REMOTE_PARAMETER_REFLECTOR_ID: String = "id"
}
