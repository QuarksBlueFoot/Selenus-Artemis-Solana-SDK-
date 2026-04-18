/*
 * Drop-in source compatibility with org.sol4k.Connection.
 *
 * Implementation strategy: every RPC method delegates to the Artemis `RpcApi`
 * and converts the Artemis return type into the sol4k shape at the boundary.
 * Applications that held a `org.sol4k.Connection` handle recompile against the
 * Artemis-backed version without touching any call site.
 *
 * Innovations beyond upstream sol4k:
 *
 * - The underlying Artemis `RpcApi` runs every request through a blockhash
 *   cache, endpoint pool, and circuit breaker that sol4k never had.
 * - Simulation, batching, and websocket subscriptions are exposed through
 *   the companion Artemis modules for callers that opt in via [asArtemis].
 */
package org.sol4k

import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.PlatformBase64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger

/**
 * sol4k-compatible `Connection` class.
 *
 * Construction matches the sol4k shape:
 *
 * ```kotlin
 * val connection = Connection("https://api.mainnet-beta.solana.com", Commitment.CONFIRMED)
 * val balance = connection.getBalance(pubkey)
 * ```
 *
 * The shim is synchronous because sol4k's API is synchronous. Internally it
 * wraps the Artemis async `RpcApi` in a `runBlocking` call. Callers that want
 * to stay on a coroutine scope can reach the native API through [asArtemis].
 */
class Connection @JvmOverloads constructor(
    private val rpcUrl: String,
    val commitment: Commitment = Commitment.FINALIZED
) {

    /**
     * Convenience constructor matching sol4k's `Connection(RpcUrl, Commitment)`.
     *
     * Using the enum variant lets callers stay on the cluster preset layer
     * without having to know the actual URL string.
     */
    @JvmOverloads
    constructor(
        rpcUrl: RpcUrl,
        commitment: Commitment = Commitment.FINALIZED
    ) : this(rpcUrl.value, commitment)

    private val client: JsonRpcClient = JsonRpcClient(rpcUrl)
    private val rpc: RpcApi = RpcApi(client)

    /** Escape hatch: return the native Artemis RPC client. */
    fun asArtemis(): RpcApi = rpc

    /** Return the balance of [walletAddress] in lamports. */
    fun getBalance(walletAddress: PublicKey): BigInteger = runBlocking {
        BigInteger.valueOf(rpc.getBalance(walletAddress.toBase58(), commitment.value).lamports)
    }

    /** Return the token account balance of [accountAddress]. */
    @JvmOverloads
    fun getTokenAccountBalance(accountAddress: PublicKey, commitment: Commitment = this.commitment): TokenAccountBalance = runBlocking {
        val json = rpc.getTokenAccountBalance(accountAddress.toBase58(), commitment.value)
        val context = json["context"]!!.jsonObject
        val value = json["value"]!!.jsonObject
        TokenAccountBalance(
            context = TokenAccountBalance.Context(slot = context["slot"]!!.jsonPrimitive.long),
            value = parseTokenAmount(value)
        )
    }

    /** Return the latest blockhash as a base58 string. */
    @JvmOverloads
    fun getLatestBlockhash(commitment: Commitment = this.commitment): String = runBlocking {
        rpc.getLatestBlockhash(commitment.value).blockhash
    }

    /** Return the latest blockhash with its validity window. */
    @JvmOverloads
    fun getLatestBlockhashExtended(commitment: Commitment = this.commitment): Blockhash = runBlocking {
        val result = rpc.getLatestBlockhash(commitment.value)
        Blockhash(
            blockhash = result.blockhash,
            lastValidBlockHeight = result.lastValidBlockHeight
        )
    }

    /** Return whether [blockhash] is still valid on the cluster. */
    @JvmOverloads
    fun isBlockhashValid(blockhash: String, commitment: Commitment = this.commitment): Boolean = runBlocking {
        rpc.isBlockhashValid(blockhash, commitment.value)
    }

    /** Return the cluster health status. */
    fun getHealth(): Health = runBlocking {
        if (rpc.getHealth().equals("ok", ignoreCase = true)) Health.OK else Health.ERROR
    }

    /** Return the current epoch info. */
    fun getEpochInfo(): EpochInfo = runBlocking {
        val typed = rpc.getEpochInfoTyped(commitment.value)
        EpochInfo(
            absoluteSlot = typed.absoluteSlot,
            blockHeight = typed.blockHeight,
            epoch = typed.epoch,
            slotIndex = typed.slotIndex,
            slotsInEpoch = typed.slotsInEpoch,
            transactionCount = typed.transactionCount
        )
    }

    /** Return the validator identity public key. */
    fun getIdentity(): PublicKey = runBlocking {
        PublicKey(rpc.getIdentity())
    }

    /** Return the validator version string. */
    fun getVersion(): Version = runBlocking {
        val typed = rpc.getVersionTyped()
        Version(solanaCore = typed.solanaCore, featureSet = typed.featureSet?.toLong())
    }

    /** Return the current total number of transactions processed. */
    fun getTransactionCount(): Long = runBlocking {
        rpc.getTransactionCount(commitment.value)
    }

    /** Return the [AccountInfo] for [accountAddress], or `null` if it does not exist. */
    fun getAccountInfo(accountAddress: PublicKey): AccountInfo? = runBlocking {
        val parsed = rpc.getAccountInfoParsed(accountAddress.toBase58(), commitment.value) ?: return@runBlocking null
        AccountInfo(
            lamports = parsed.lamports,
            owner = PublicKey(parsed.owner.bytes),
            data = parsed.data,
            executable = parsed.executable,
            rentEpoch = parsed.rentEpoch
        )
    }

    /** Return [AccountInfo] records for each pubkey in [accountAddresses]. */
    fun getMultipleAccounts(accountAddresses: List<PublicKey>): List<AccountInfo?> = runBlocking {
        val json = rpc.getMultipleAccounts(accountAddresses.map { it.toBase58() }, commitment.value)
        val values = json["value"]?.jsonArray ?: return@runBlocking emptyList<AccountInfo?>()
        values.map { entry ->
            if (entry is JsonNull) {
                null
            } else {
                val obj = entry.jsonObject
                val lamports = obj["lamports"]?.jsonPrimitive?.long ?: 0L
                val ownerStr = obj["owner"]?.jsonPrimitive?.content ?: return@map null
                val executable = obj["executable"]?.jsonPrimitive?.content?.toBoolean() ?: false
                val rentEpoch = obj["rentEpoch"]?.jsonPrimitive?.long ?: 0L
                val dataArr = obj["data"]?.jsonArray
                val data = if (dataArr != null && dataArr.isNotEmpty()) {
                    PlatformBase64.decode(dataArr[0].jsonPrimitive.content)
                } else {
                    ByteArray(0)
                }
                AccountInfo(
                    lamports = lamports,
                    owner = PublicKey(ownerStr),
                    data = data,
                    executable = executable,
                    rentEpoch = rentEpoch
                )
            }
        }
    }

    /** Return the minimum lamports required to make a [space]-byte account rent-exempt. */
    fun getMinimumBalanceForRentExemption(space: Int): Long = runBlocking {
        rpc.getMinimumBalanceForRentExemption(space.toLong(), commitment.value)
    }

    /** Return the token supply for [tokenPubkey]. */
    fun getTokenSupply(tokenPubkey: String): TokenAmount = runBlocking {
        val typed = rpc.getTokenSupplyTyped(tokenPubkey, commitment.value)
        TokenAmount(
            amount = typed.amount,
            decimals = typed.decimals,
            uiAmount = typed.uiAmount,
            uiAmountString = typed.uiAmountString ?: typed.amount
        )
    }

    /** Request [amount] lamports from the faucet for [pubkey] (devnet / testnet only). */
    fun requestAirdrop(pubkey: PublicKey, amount: Long): String = runBlocking {
        rpc.requestAirdrop(pubkey.toBase58(), amount, commitment.value)
    }

    /** Send a base64-encoded raw transaction. */
    fun sendTransaction(rawTransaction: ByteArray): String = runBlocking {
        rpc.sendRawTransaction(rawTransaction, skipPreflight = false)
    }

    /** Send a [Transaction] that has already been signed. Matches upstream sol4k. */
    fun sendTransaction(transaction: Transaction): String = sendTransaction(transaction.serialize())

    /** Send a [VersionedTransaction] that has already been signed. */
    fun sendTransaction(transaction: VersionedTransaction): String =
        sendTransaction(transaction.serialize())

    /** Simulate a base64-encoded raw transaction. */
    fun simulateTransaction(rawTransaction: ByteArray): TransactionSimulation = runBlocking {
        val json = rpc.simulateTransaction(
            PlatformBase64.encode(rawTransaction),
            replaceRecentBlockhash = true
        )
        parseSimulation(json)
    }

    /** Simulate a built [Transaction] without submitting. */
    fun simulateTransaction(transaction: Transaction): TransactionSimulation =
        simulateTransaction(transaction.serialize())

    /** Simulate a built [VersionedTransaction] without submitting. */
    fun simulateTransaction(transaction: VersionedTransaction): TransactionSimulation =
        simulateTransaction(transaction.serialize())

    /**
     * Return the fee in lamports for a serialized message, or `null` when the
     * RPC cannot price it. Matches upstream sol4k's `Long?` return shape.
     * Accepts either a [TransactionMessage] or a pre-serialized message blob.
     */
    fun getFeeForMessage(message: TransactionMessage): Long? = getFeeForMessage(message.serialize())

    fun getFeeForMessage(serializedMessage: ByteArray): Long? = runBlocking {
        val encoded = PlatformBase64.encode(serializedMessage)
        val fee = rpc.getFeeForMessage(encoded, commitment.value)
        if (fee == 0L) null else fee
    }

    /** Return the list of recent prioritization fees. */
    @JvmOverloads
    fun getRecentPrioritizationFees(accountAddresses: List<PublicKey> = emptyList()): List<PrioritizationFee> = runBlocking {
        val addresses = if (accountAddresses.isEmpty()) null else accountAddresses.map { it.toBase58() }
        val array = rpc.getRecentPrioritizationFees(addresses)
        array.map { element ->
            val obj = element.jsonObject
            PrioritizationFee(
                slot = obj["slot"]!!.jsonPrimitive.long,
                prioritizationFee = obj["prioritizationFee"]!!.jsonPrimitive.long
            )
        }
    }

    /**
     * Return the list of signatures that touched [address], newest first.
     *
     * [before] and [until] pagination cursors match sol4k.
     */
    @JvmOverloads
    fun getSignaturesForAddress(
        address: PublicKey,
        limit: Int = 1000,
        commitment: Commitment = this.commitment,
        before: String? = null,
        until: String? = null
    ): List<TransactionSignature> = runBlocking {
        val array = rpc.getSignaturesForAddress(
            address = address.toBase58(),
            limit = limit,
            before = before,
            until = until,
            commitment = commitment.value
        )
        array.map { entry ->
            val obj = entry.jsonObject
            TransactionSignature(
                signature = obj["signature"]!!.jsonPrimitive.content,
                slot = obj["slot"]!!.jsonPrimitive.long,
                err = obj["err"]?.takeUnless { it is JsonNull }?.toString(),
                memo = obj["memo"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                blockTime = obj["blockTime"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.long,
                confirmationStatus = obj["confirmationStatus"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
            )
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private fun parseTokenAmount(obj: JsonObject): TokenAmount = TokenAmount(
        amount = obj["amount"]!!.jsonPrimitive.content,
        decimals = obj["decimals"]!!.jsonPrimitive.content.toInt(),
        uiAmount = obj["uiAmount"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toDoubleOrNull(),
        uiAmountString = obj["uiAmountString"]?.jsonPrimitive?.content ?: obj["amount"]!!.jsonPrimitive.content
    )

    private fun parseSimulation(json: JsonObject): TransactionSimulation {
        val value = json["value"]?.jsonObject
            ?: return TransactionSimulationError("empty response")
        val err = value["err"]?.takeUnless { it is JsonNull }?.toString()
        if (err != null) return TransactionSimulationError(err)
        val logs = value["logs"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val unitsConsumed = value["unitsConsumed"]?.jsonPrimitive?.long
        val returnData = value["returnData"]?.takeUnless { it is JsonNull }?.jsonObject?.let { rd ->
            val programId = rd["programId"]!!.jsonPrimitive.content
            val dataArr = rd["data"]?.jsonArray
            val dataStr = dataArr?.firstOrNull()?.jsonPrimitive?.content ?: ""
            TransactionSimulation.ReturnData(programId = programId, data = dataStr)
        }
        return TransactionSimulationSuccess(
            logs = logs,
            unitsConsumed = unitsConsumed,
            returnData = returnData
        )
    }

    @Suppress("unused")
    private fun buildParams(block: () -> Unit) = buildJsonArray { block() }

    @Suppress("unused")
    private fun stringPrimitive(value: String) = JsonPrimitive(value)
}
