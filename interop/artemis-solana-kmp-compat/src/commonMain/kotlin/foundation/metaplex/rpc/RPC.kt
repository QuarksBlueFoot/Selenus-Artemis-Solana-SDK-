/*
 * Drop-in source compatibility with foundation.metaplex.rpc.
 *
 * solana-kmp exposes an `RPC(rpcUrl)` entry point and an `RpcInterface` with a
 * handful of generic methods. This shim wraps the Artemis `RpcApi` in that
 * shape so upstream callers can import from `foundation.metaplex.rpc` unchanged.
 *
 * Innovations over upstream:
 * - Requests are routed through Artemis's endpoint pool, circuit breaker, and
 *   blockhash cache by default. Upstream had no failover surface.
 * - `sendTransaction` takes the serialized bytes directly, matching sol4k, and
 *   does not require a caller-supplied KSerializer for the send path.
 */
package foundation.metaplex.rpc

import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi as ArtemisRpcApi
import com.selenus.artemis.runtime.PlatformBase64
import foundation.metaplex.amount.Amount
import foundation.metaplex.solanapublickeys.PublicKey
import kotlinx.coroutines.runBlocking

/** Commitment level matching upstream solana-kmp. */
enum class Commitment(val value: String) {
    PROCESSED("processed"),
    CONFIRMED("confirmed"),
    FINALIZED("finalized");

    override fun toString(): String = value
}

/**
 * Request-scoped configuration carried through every `RpcInterface` call.
 */
data class RpcRequestConfiguration @JvmOverloads constructor(
    val commitment: Commitment = Commitment.CONFIRMED,
    val encoding: String = "base64",
    val minContextSlot: Long? = null
)

/**
 * solana-kmp compatible `RpcInterface`.
 *
 * Upstream exposes generic methods that take a caller-supplied KSerializer and
 * return typed account payloads. The shim preserves the method names and
 * argument positions; the non-typed `sendTransaction` and `getBalance` paths
 * are directly usable. The typed getAccountInfo family returns raw bytes so
 * callers can plug in their own decoding.
 */
interface RpcInterface {
    suspend fun getBalance(
        publicKey: PublicKey,
        configuration: RpcRequestConfiguration = RpcRequestConfiguration()
    ): Amount

    suspend fun getLatestBlockhash(
        configuration: RpcRequestConfiguration = RpcRequestConfiguration()
    ): String

    suspend fun getMinimumBalanceForRentExemption(
        size: Long,
        configuration: RpcRequestConfiguration = RpcRequestConfiguration()
    ): Long

    suspend fun getSlot(
        configuration: RpcRequestConfiguration = RpcRequestConfiguration()
    ): Long

    suspend fun requestAirdrop(
        publicKey: PublicKey,
        amount: Amount,
        configuration: RpcRequestConfiguration = RpcRequestConfiguration()
    ): String

    suspend fun getAccountInfo(
        publicKey: PublicKey,
        configuration: RpcRequestConfiguration = RpcRequestConfiguration()
    ): AccountInfo?

    suspend fun getMultipleAccounts(
        publicKeys: List<PublicKey>,
        configuration: RpcRequestConfiguration = RpcRequestConfiguration()
    ): List<AccountInfo?>

    suspend fun sendTransaction(
        serializedTransaction: ByteArray,
        configuration: RpcRequestConfiguration = RpcRequestConfiguration()
    ): String
}

/**
 * solana-kmp compatible `AccountInfo`.
 *
 * Upstream uses a parameterized `AccountInfo<T>` where `T` is the decoded
 * payload. Most apps work with raw bytes and deserialize on their side, so
 * the shim exposes the byte form directly and leaves typed decoding to the
 * caller.
 */
data class AccountInfo(
    val owner: PublicKey,
    val lamports: Long,
    val data: ByteArray,
    val executable: Boolean,
    val rentEpoch: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccountInfo) return false
        return owner == other.owner &&
            lamports == other.lamports &&
            data.contentEquals(other.data) &&
            executable == other.executable &&
            rentEpoch == other.rentEpoch
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + lamports.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + executable.hashCode()
        result = 31 * result + rentEpoch.hashCode()
        return result
    }
}

/**
 * solana-kmp compatible `RPC` entry point.
 *
 * Construction matches upstream: `RPC(rpcUrl)`. Internally this wraps an
 * Artemis `JsonRpcClient` + `RpcApi` pair, so all the Artemis reliability
 * features (blockhash cache, retries, batching) are available through
 * [asArtemis] for callers who want to opt in.
 */
class RPC(rpcUrl: String) : RpcInterface {

    private val rpc: ArtemisRpcApi = ArtemisRpcApi(JsonRpcClient(rpcUrl))

    /** Escape hatch returning the native Artemis RPC API. */
    fun asArtemis(): ArtemisRpcApi = rpc

    override suspend fun getBalance(
        publicKey: PublicKey,
        configuration: RpcRequestConfiguration
    ): Amount {
        val lamports = rpc.getBalance(publicKey.toBase58(), configuration.commitment.value).lamports
        return Amount(basisPoints = lamports, identifier = "SOL", decimals = 9)
    }

    override suspend fun getLatestBlockhash(configuration: RpcRequestConfiguration): String =
        rpc.getLatestBlockhash(configuration.commitment.value).blockhash

    override suspend fun getMinimumBalanceForRentExemption(size: Long, configuration: RpcRequestConfiguration): Long =
        rpc.getMinimumBalanceForRentExemption(size, configuration.commitment.value)

    override suspend fun getSlot(configuration: RpcRequestConfiguration): Long =
        rpc.getSlot(configuration.commitment.value)

    override suspend fun requestAirdrop(
        publicKey: PublicKey,
        amount: Amount,
        configuration: RpcRequestConfiguration
    ): String = rpc.requestAirdrop(publicKey.toBase58(), amount.basisPoints, configuration.commitment.value)

    override suspend fun getAccountInfo(
        publicKey: PublicKey,
        configuration: RpcRequestConfiguration
    ): AccountInfo? {
        val parsed = rpc.getAccountInfoParsed(publicKey.toBase58(), configuration.commitment.value) ?: return null
        return AccountInfo(
            owner = PublicKey(parsed.owner.bytes),
            lamports = parsed.lamports,
            data = parsed.data,
            executable = parsed.executable,
            rentEpoch = parsed.rentEpoch
        )
    }

    override suspend fun getMultipleAccounts(
        publicKeys: List<PublicKey>,
        configuration: RpcRequestConfiguration
    ): List<AccountInfo?> {
        val base64List = rpc.getMultipleAccountsBase64(
            publicKeys.map { it.toBase58() },
            configuration.commitment.value
        )
        // getMultipleAccountsBase64 loses owner / executable / rentEpoch metadata,
        // so supplement by calling getAccountInfoParsed for any non-null slots.
        return publicKeys.mapIndexed { index, pk ->
            if (base64List.getOrNull(index) == null) {
                null
            } else {
                rpc.getAccountInfoParsed(pk.toBase58(), configuration.commitment.value)?.let { parsed ->
                    AccountInfo(
                        owner = PublicKey(parsed.owner.bytes),
                        lamports = parsed.lamports,
                        data = parsed.data,
                        executable = parsed.executable,
                        rentEpoch = parsed.rentEpoch
                    )
                }
            }
        }
    }

    override suspend fun sendTransaction(
        serializedTransaction: ByteArray,
        configuration: RpcRequestConfiguration
    ): String = rpc.sendRawTransaction(
        serializedTransaction,
        skipPreflight = false,
        preflightCommitment = configuration.commitment.value
    )

    /**
     * Blocking convenience for the rare upstream caller that expects a synchronous
     * `getBalance`. Runs the suspend call in a `runBlocking` scope.
     */
    fun getBalanceBlocking(publicKey: PublicKey): Amount = runBlocking {
        getBalance(publicKey)
    }
}
