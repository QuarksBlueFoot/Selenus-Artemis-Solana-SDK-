/*
 * Drop-in source compatibility with com.solana.rpc (rpc-core 0.2.5).
 *
 * `SolanaRpcClient` is the Solana Mobile team's Kotlin-first RPC client. This
 * shim re-publishes the upstream class at the same fully qualified name so
 * apps that `import com.solana.rpc.SolanaRpcClient` continue to compile. The
 * internal implementation delegates to Artemis's `RpcApi`, which means
 * consumers inherit the Artemis reliability surface (blockhash cache,
 * endpoint pool, retry pipeline) transparently.
 */
package com.solana.rpc

import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi as ArtemisRpcApi
import com.solana.networking.ArtemisHttpNetworkDriver
import com.solana.networking.HttpNetworkDriver
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Transaction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Commitment level matching rpc-core's `com.solana.rpc.Commitment`. */
enum class Commitment(val value: String) {
    PROCESSED("processed"),
    CONFIRMED("confirmed"),
    FINALIZED("finalized");

    override fun toString(): String = value
}

/** Transaction encoding enum matching rpc-core's `com.solana.rpc.Encoding`. */
enum class Encoding(val value: String) {
    BASE64("base64"),
    BASE58("base58");

    override fun toString(): String = value
}

/**
 * Transaction options used by `sendTransaction` / `simulateTransaction`.
 *
 * Matches the upstream data class field by field so callers that use named
 * arguments keep working.
 */
data class TransactionOptions(
    val commitment: Commitment = Commitment.FINALIZED,
    val encoding: Encoding = Encoding.BASE64,
    val skipPreflight: Boolean = false,
    val preflightCommitment: Commitment? = null,
    val maxRetries: Int? = null,
    val minContextSlot: Long? = null,
    val timeout: Long = 30_000L
)

/** Blockhash response envelope. */
data class BlockhashResponse(
    val blockhash: String,
    val lastValidBlockHeight: Long
)

/** Signature status record. */
data class SignatureStatus(
    val slot: Long,
    val confirmations: Long?,
    val err: String?,
    val confirmationStatus: String?
)

/** Context envelope matching rpc-core's `Context(apiVersion, slot)`. */
data class Context(val apiVersion: String, val slot: ULong)

/** Typed RPC response envelope: `{ context, value }`. */
data class SolanaResponse<V>(val context: Context, val value: V)

/** Typed account info. Generic over the decoded data payload. */
data class AccountInfo<D>(
    val data: D,
    val executable: Boolean,
    val lamports: Long,
    val owner: String,
    val rentEpoch: Long,
    val space: Long
)

/** Pair of account info plus public key (used by `getProgramAccounts`). */
data class AccountInfoWithPublicKey<P>(
    val publicKey: SolanaPublicKey,
    val account: AccountInfo<P>
)

/**
 * Non-generic account record matching rpc-core's `SolanaAccount`.
 *
 * Upstream uses this shape when the caller doesn't care about decoding the
 * account data and just wants the raw bytes plus metadata.
 */
data class SolanaAccount(
    val lamports: Long,
    val owner: SolanaPublicKey,
    val data: ByteArray,
    val executable: Boolean,
    val rentEpoch: Long,
    val space: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SolanaAccount) return false
        return lamports == other.lamports &&
            owner == other.owner &&
            data.contentEquals(other.data) &&
            executable == other.executable &&
            rentEpoch == other.rentEpoch &&
            space == other.space
    }

    override fun hashCode(): Int {
        var result = lamports.hashCode()
        result = 31 * result + owner.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + executable.hashCode()
        result = 31 * result + rentEpoch.hashCode()
        result = 31 * result + space.hashCode()
        return result
    }
}

/**
 * Request shapes used by `getAccountInfo` / `getMultipleAccounts`. Matches
 * the upstream `AccountRequest` nested types so named-arg callers keep
 * working.
 */
object AccountRequest {
    /**
     * Byte slice of the account data to return. Upstream ships this nested
     * on the rpc-core `AccountRequest` type; Artemis exposes it flat inside
     * the `AccountRequest` container.
     */
    data class DataSlice(
        val offset: Int,
        val length: Int
    ) {
        init {
            require(offset >= 0) { "offset must be non-negative" }
            require(length >= 0) { "length must be non-negative" }
        }
    }
}

/**
 * Simulation result. Mirrors upstream field ordering.
 */
data class SimulationResult(
    val logs: List<String>,
    val unitsConsumed: Long?,
    val err: String?,
    val accounts: List<AccountInfo<String>?>? = null
)

/**
 * rpc-core compatible `SolanaRpcClient`.
 *
 * Two-constructor shape matches upstream: either pass a pre-built [Rpc20Driver]
 * or a URL + [HttpNetworkDriver]. A third constructor accepting only a URL
 * wires the default Artemis transport so simple cases stay one-liners.
 */
class SolanaRpcClient private constructor(
    private val rpcDriver: Rpc20Driver,
    private val defaultTransactionOptions: TransactionOptions,
    internalUrl: String
) {

    /**
     * Primary constructor taking a pre-built [Rpc20Driver]. Matches upstream.
     * The second parameter slot is kept for forward-compat with upstream's
     * `defaultTransactionOptions`; a third internal url is captured so Artemis
     * can route through its own RPC pipeline.
     */
    constructor(
        rpcDriver: Rpc20Driver,
        defaultTransactionOptions: TransactionOptions = TransactionOptions()
    ) : this(
        rpcDriver = rpcDriver,
        defaultTransactionOptions = defaultTransactionOptions,
        internalUrl = "https://api.mainnet-beta.solana.com"
    )

    /**
     * Secondary constructor matching upstream's `SolanaRpcClient(url, driver, opts)`.
     */
    constructor(
        url: String,
        networkDriver: HttpNetworkDriver,
        defaultTransactionOptions: TransactionOptions = TransactionOptions()
    ) : this(
        rpcDriver = Rpc20Driver(url = url, networkDriver = networkDriver),
        defaultTransactionOptions = defaultTransactionOptions,
        internalUrl = url
    )

    private val rpc: ArtemisRpcApi = ArtemisRpcApi(JsonRpcClient(internalUrl))

    /** Escape hatch returning the native Artemis RPC client. */
    fun asArtemis(): ArtemisRpcApi = rpc

    suspend fun requestAirdrop(
        address: SolanaPublicKey,
        amountSol: Float,
        requestId: String? = null
    ): Result<String> = runCatching {
        rpc.requestAirdrop(
            pubkey = address.base58(),
            lamports = (amountSol * 1_000_000_000f).toLong(),
            commitment = defaultTransactionOptions.commitment.value
        )
    }

    suspend fun getBalance(
        address: SolanaPublicKey,
        commitment: Commitment = Commitment.CONFIRMED,
        requestId: String? = null
    ): Result<Long> = runCatching {
        rpc.getBalance(address.base58(), commitment.value).lamports
    }

    suspend fun getLatestBlockhash(
        commitment: Commitment? = null,
        minContextSlot: Long? = null,
        requestId: String? = null
    ): Result<BlockhashResponse> = runCatching {
        val c = commitment?.value ?: defaultTransactionOptions.commitment.value
        val result = rpc.getLatestBlockhash(c)
        BlockhashResponse(blockhash = result.blockhash, lastValidBlockHeight = result.lastValidBlockHeight)
    }

    suspend fun getMinBalanceForRentExemption(
        size: Long,
        commitment: Commitment? = null,
        requestId: String? = null
    ): Result<Long> = runCatching {
        rpc.getMinimumBalanceForRentExemption(
            size,
            (commitment ?: defaultTransactionOptions.commitment).value
        )
    }

    suspend fun getAccountInfo(
        publicKey: SolanaPublicKey,
        commitment: Commitment? = null,
        minContextSlot: Long? = null,
        requestId: String? = null
    ): Result<AccountInfo<ByteArray>?> = runCatching {
        val parsed = rpc.getAccountInfoParsed(
            publicKey.base58(),
            (commitment ?: defaultTransactionOptions.commitment).value
        ) ?: return@runCatching null
        AccountInfo(
            data = parsed.data,
            executable = parsed.executable,
            lamports = parsed.lamports,
            owner = parsed.owner.toBase58(),
            rentEpoch = parsed.rentEpoch,
            space = parsed.data.size.toLong()
        )
    }

    /**
     * Typed `getAccountInfo` using a caller-supplied [decode] function to
     * transform raw account bytes into [D]. Matches upstream rpc-core's
     * generic overload ergonomically without binding the shim to a specific
     * deserialization framework (borsh, Anchor, kotlinx.serialization), so
     * consumers plug in whatever decoder they use today.
     */
    suspend fun <D> getAccountInfo(
        publicKey: SolanaPublicKey,
        decode: (ByteArray) -> D,
        commitment: Commitment? = null,
        minContextSlot: Long? = null,
        requestId: String? = null
    ): Result<AccountInfo<D>?> = runCatching {
        val raw = getAccountInfo(publicKey, commitment, minContextSlot, requestId)
            .getOrThrow() ?: return@runCatching null
        AccountInfo(
            data = decode(raw.data),
            executable = raw.executable,
            lamports = raw.lamports,
            owner = raw.owner,
            rentEpoch = raw.rentEpoch,
            space = raw.space
        )
    }

    /**
     * Typed `getMultipleAccounts` using a caller-supplied [decode] function.
     * Null entries (missing accounts) are preserved in the returned list.
     */
    suspend fun <D> getMultipleAccounts(
        publicKeys: List<SolanaPublicKey>,
        decode: (ByteArray) -> D,
        commitment: Commitment? = null,
        requestId: String? = null
    ): Result<List<AccountInfo<D>?>> = runCatching {
        val raw = getMultipleAccounts(publicKeys, commitment, requestId).getOrThrow()
        raw.map { entry ->
            entry?.let {
                AccountInfo(
                    data = decode(it.data),
                    executable = it.executable,
                    lamports = it.lamports,
                    owner = it.owner,
                    rentEpoch = it.rentEpoch,
                    space = it.space
                )
            }
        }
    }

    suspend fun getMultipleAccounts(
        publicKeys: List<SolanaPublicKey>,
        commitment: Commitment? = null,
        requestId: String? = null
    ): Result<List<AccountInfo<ByteArray>?>> = runCatching {
        val json = rpc.getMultipleAccounts(
            publicKeys.map { it.base58() },
            (commitment ?: defaultTransactionOptions.commitment).value
        )
        val values: JsonArray = json["value"]?.jsonArray ?: return@runCatching emptyList()
        values.map { entry ->
            if (entry is JsonNull) null else {
                val obj = entry.jsonObject
                val lamports = obj["lamports"]?.jsonPrimitive?.long ?: 0L
                val owner = obj["owner"]?.jsonPrimitive?.content ?: return@map null
                val executable = obj["executable"]?.jsonPrimitive?.content?.toBoolean() ?: false
                val rentEpoch = obj["rentEpoch"]?.jsonPrimitive?.long ?: 0L
                val dataArr = obj["data"]?.jsonArray
                val data: ByteArray = if (dataArr != null && dataArr.isNotEmpty()) {
                    com.selenus.artemis.runtime.PlatformBase64.decode(dataArr[0].jsonPrimitive.content)
                } else {
                    ByteArray(0)
                }
                AccountInfo(
                    data = data,
                    executable = executable,
                    lamports = lamports,
                    owner = owner,
                    rentEpoch = rentEpoch,
                    space = data.size.toLong()
                )
            }
        }
    }

    suspend fun sendTransaction(
        transaction: Transaction,
        options: TransactionOptions = defaultTransactionOptions,
        requestId: String? = null
    ): Result<String> = runCatching {
        val bytes = transaction.serialize()
        rpc.sendRawTransaction(
            bytes,
            skipPreflight = options.skipPreflight,
            maxRetries = options.maxRetries,
            preflightCommitment = (options.preflightCommitment ?: options.commitment).value
        )
    }

    suspend fun sendAndConfirmTransaction(
        transaction: Transaction,
        options: TransactionOptions = defaultTransactionOptions
    ): Result<Boolean> = runCatching {
        val sig = sendTransaction(transaction, options).getOrThrow()
        rpc.confirmTransaction(sig, requireConfirmationStatus = options.commitment.value)
    }

    suspend fun confirmTransaction(
        transactionSignature: String,
        options: TransactionOptions = defaultTransactionOptions
    ): Result<Boolean> = runCatching {
        rpc.confirmTransaction(
            transactionSignature,
            requireConfirmationStatus = options.commitment.value
        )
    }

    suspend fun getSignatureStatuses(
        signatures: List<String>,
        searchTransactionHistory: Boolean = false,
        requestId: String? = null
    ): Result<List<SignatureStatus?>> = runCatching {
        val json = rpc.getSignatureStatuses(signatures, searchTransactionHistory)
        val values = json["value"]?.jsonArray ?: return@runCatching List(signatures.size) { null }
        values.map { entry ->
            if (entry is JsonNull) null else {
                val obj = entry.jsonObject
                SignatureStatus(
                    slot = obj["slot"]?.jsonPrimitive?.long ?: 0L,
                    confirmations = obj["confirmations"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.long,
                    err = obj["err"]?.takeUnless { it is JsonNull }?.toString(),
                    confirmationStatus = obj["confirmationStatus"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
                )
            }
        }
    }

    suspend fun simulateTransaction(
        transaction: Transaction,
        commitment: Commitment? = null,
        encoding: Encoding = Encoding.BASE64,
        replaceRecentBlockhash: Boolean = false,
        sigVerify: Boolean = false,
        minContextSlot: Long? = null,
        innerInstructions: Boolean? = null,
        accounts: List<SolanaPublicKey>? = null,
        attemptJsonParseAccounts: Boolean = false
    ): Result<SimulationResult> = runCatching {
        val b64 = com.selenus.artemis.runtime.PlatformBase64.encode(transaction.serialize())
        val json = rpc.simulateTransaction(
            b64,
            sigVerify = sigVerify,
            replaceRecentBlockhash = replaceRecentBlockhash,
            commitment = (commitment ?: defaultTransactionOptions.commitment).value
        )
        val value = json["value"]?.jsonObject ?: return@runCatching SimulationResult(emptyList(), null, "empty response")
        SimulationResult(
            logs = value["logs"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            unitsConsumed = value["unitsConsumed"]?.jsonPrimitive?.long,
            err = value["err"]?.takeUnless { it is JsonNull }?.toString(),
            accounts = null
        )
    }

}
