package com.selenus.artemis.rpc

/**
 * Compatibility wrapper for solana-kt users.
 *
 * Allows usage like: val connection = Connection("https://api.mainnet-beta.solana.com")
 */
class Connection(endpoint: String) : RpcApi(JsonRpcClient(endpoint))
