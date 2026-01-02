package com.selenus.artemis.rpc

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.tx.Transaction
import kotlinx.serialization.json.JsonObject

/**
 * High-level Solana connection.
 *
 * Matches the Connection pattern used by sol4k, @solana/web3.js,
 * and the Solana Mobile SDK ecosystem:
 *
 * ```kotlin
 * val connection = Connection(SolanaCluster.DEVNET, Commitment.CONFIRMED)
 *
 * // Type-safe commitment per call
 * val balance = connection.getBalance("...", Commitment.CONFIRMED)
 * val info    = connection.getAccountInfo("...", Commitment.FINALIZED)
 *
 * // High-level convenience
 * val sol = connection.getBalanceSol(pubkey)
 * val sig = connection.sendAndConfirm(tx, listOf(signer))
 * ```
 */
class Connection : RpcApi {
    /** The default commitment level for all RPC calls made through this connection. */
    val defaultCommitment: Commitment

    @JvmOverloads
    constructor(endpoint: String, commitment: Commitment = Commitment.FINALIZED) : super(JsonRpcClient(endpoint)) {
        this.defaultCommitment = commitment
    }

    @JvmOverloads
    constructor(cluster: SolanaCluster, commitment: Commitment = Commitment.FINALIZED) : super(JsonRpcClient(cluster.endpoint)) {
        this.defaultCommitment = commitment
    }

    /**
     * Create a connection backed by an endpoint pool for automatic failover.
     *
     * On each RPC call, the pool selects the healthiest endpoint. Failed
     * endpoints are circuit-broken and re-probed after a cooldown period.
     *
     * ```kotlin
     * val pool = RpcEndpointPool(listOf(
     *     "https://mainnet.helius-rpc.com/?api-key=KEY",
     *     "https://api.mainnet-beta.solana.com"
     * ))
     * val connection = Connection(pool, Commitment.CONFIRMED)
     * ```
     */
    @JvmOverloads
    constructor(pool: RpcEndpointPool, commitment: Commitment = Commitment.FINALIZED) : super(JsonRpcClient(pool)) {
        this.defaultCommitment = commitment
    }

    /**
     * Create a connection backed by a smart router for method-based routing.
     *
     * Different RPC methods can be directed to different endpoints for optimal
     * performance, cost, and reliability.
     *
     * ```kotlin
     * val router = RpcRouter.build {
     *     route("sendTransaction") to "https://quicknode.example.com"
     *     route("getAccountInfo", "getBalance") to "https://helius.example.com"
     *     fallback("https://api.mainnet-beta.solana.com")
     * }
     * val connection = Connection(router, Commitment.CONFIRMED)
     * ```
     */
    @JvmOverloads
    constructor(router: RpcRouter, commitment: Commitment = Commitment.FINALIZED) : super(JsonRpcClient(router)) {
        this.defaultCommitment = commitment
    }

    // ════════════════════════════════════════════════════════════════════════
    // TYPE-SAFE COMMITMENT OVERLOADS
    // These accept the Commitment enum directly, matching sol4k's API style.
    // ════════════════════════════════════════════════════════════════════════

    suspend fun getBalance(pubkey: String, commitment: Commitment): BalanceResult =
        getBalance(pubkey, commitment.value)

    suspend fun getAccountInfo(pubkey: String, commitment: Commitment, encoding: String = "base64"): JsonObject =
        getAccountInfo(pubkey, commitment.value, encoding)

    suspend fun getAccountInfoParsed(pubkey: String, commitment: Commitment): AccountInfo? =
        getAccountInfoParsed(pubkey, commitment.value)

    suspend fun getLatestBlockhash(commitment: Commitment): BlockhashResult =
        getLatestBlockhash(commitment.value)

    suspend fun getTokenAccountsByOwner(owner: String, mint: String? = null, programId: String? = null, commitment: Commitment): JsonObject =
        getTokenAccountsByOwner(owner, mint, programId, commitment.value)

    suspend fun getSignaturesForAddress(address: String, limit: Int = 1000, before: String? = null, until: String? = null, commitment: Commitment): kotlinx.serialization.json.JsonArray =
        getSignaturesForAddress(address, limit, before, until, commitment.value)

    suspend fun getTransaction(signature: String, commitment: Commitment, encoding: String = "jsonParsed", maxSupportedTransactionVersion: Int = 0): JsonObject =
        getTransaction(signature, commitment.value, encoding, maxSupportedTransactionVersion)

    suspend fun requestAirdrop(pubkey: String, lamports: Long, commitment: Commitment): String =
        requestAirdrop(pubkey, lamports, commitment.value)

    suspend fun confirmTransaction(signature: String, maxAttempts: Int = 30, sleepMs: Long = 500, commitment: Commitment): Boolean =
        confirmTransaction(signature, maxAttempts, sleepMs, commitment.value)

    suspend fun isBlockhashValid(blockhash: String, commitment: Commitment): Boolean =
        isBlockhashValid(blockhash, commitment.value)

    suspend fun getSlot(commitment: Commitment): Long =
        getSlot(commitment.value)

    suspend fun getBlockHeight(commitment: Commitment): Long =
        getBlockHeight(commitment.value)

    suspend fun getTokenAccountBalance(account: String, commitment: Commitment): JsonObject =
        getTokenAccountBalance(account, commitment.value)

    suspend fun getTokenSupply(mint: String, commitment: Commitment): JsonObject =
        getTokenSupply(mint, commitment.value)

    suspend fun getMinimumBalanceForRentExemption(dataLength: Long, commitment: Commitment): Long =
        getMinimumBalanceForRentExemption(dataLength, commitment.value)

    // ════════════════════════════════════════════════════════════════════════
    // HIGH-LEVEL CONVENIENCE METHODS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Get balance in SOL (not lamports).
     */
    suspend fun getBalanceSol(pubkey: String): Double =
        getBalance(pubkey, defaultCommitment.value).lamports / 1_000_000_000.0

    /**
     * Get balance in SOL for a Pubkey.
     */
    suspend fun getBalanceSol(pubkey: Pubkey): Double =
        getBalanceSol(pubkey.toBase58())

    /**
     * Sign, send, and confirm a transaction in one call.
     * This is the most common operation for mobile apps.
     */
    suspend fun sendAndConfirm(
        transaction: Transaction,
        signers: List<Signer>,
        skipPreflight: Boolean = false
    ): String {
        val bh = getLatestBlockhash(defaultCommitment.value)
        transaction.recentBlockhash = bh.blockhash
        if (transaction.feePayer == null && signers.isNotEmpty()) {
            transaction.feePayer = signers.first().publicKey
        }
        for (signer in signers) {
            transaction.sign(signer)
        }
        return sendAndConfirmRawTransaction(
            transaction.serialize(),
            skipPreflight = skipPreflight,
            confirmCommitment = defaultCommitment.value
        )
    }

    /**
     * Request airdrop and wait for confirmation. Devnet helper.
     */
    @JvmOverloads
    suspend fun requestAirdropAndConfirm(
        pubkey: String,
        lamports: Long,
        commitment: String = defaultCommitment.value
    ): String {
        val sig = requestAirdrop(pubkey, lamports, commitment)
        val ok = confirmTransaction(sig, requireConfirmationStatus = commitment)
        if (!ok) throw RpcException("Airdrop not confirmed: $sig")
        return sig
    }
}
