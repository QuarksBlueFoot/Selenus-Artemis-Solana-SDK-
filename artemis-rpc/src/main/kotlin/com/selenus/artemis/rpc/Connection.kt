package com.selenus.artemis.rpc

/**
 * Compatibility wrapper for solana-kt users.
 *
 * Allows usage like:
 * ```kotlin
 * val connection = Connection("https://api.mainnet-beta.solana.com")
 * val connection = Connection(SolanaCluster.DEVNET)
 * val connection = Connection(SolanaCluster.MAINNET_BETA, Commitment.CONFIRMED)
 * ```
 */
class Connection : RpcApi {
    /** The default commitment level for all RPC calls made through this connection. */
    val defaultCommitment: Commitment

    constructor(endpoint: String, commitment: Commitment = Commitment.FINALIZED) : super(JsonRpcClient(endpoint)) {
        this.defaultCommitment = commitment
    }

    constructor(cluster: SolanaCluster, commitment: Commitment = Commitment.FINALIZED) : super(JsonRpcClient(cluster.endpoint)) {
        this.defaultCommitment = commitment
    }
}
