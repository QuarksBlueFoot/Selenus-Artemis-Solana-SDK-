/*
 * Typed request configuration objects for the solana-kmp drop-in RPC surface.
 *
 * Upstream solana-kmp expands its RPC interface into a family of `Rpc*Configuration`
 * data classes that each carry commitment, encoding, context slot, filters, and
 * data-slice options. The base `RpcInterface` in `RPC.kt` ships a flat
 * `RpcRequestConfiguration` for the common path; this file adds the full set so
 * existing solana-kmp call-sites that name a specific config compile unchanged.
 *
 * Every type is wire-identical to upstream. The bodies are thin value carriers
 * and exist only so `foundation.metaplex.rpc.*` imports resolve against Artemis.
 */
package foundation.metaplex.rpc

import foundation.metaplex.solanapublickeys.PublicKey

/**
 * Encoding flag for RPC responses. `base64_zstd` is serialized as
 * `"base64+zstd"` per the upstream convention.
 */
enum class Encoding(val value: String) {
    base64("base64"),
    jsonParsed("jsonParsed"),
    base58("base58"),
    base64_zstd("base64+zstd");

    override fun toString(): String = value
}

/**
 * Common options that every upstream `Rpc*Configuration` carries. Exposed as a
 * sealed interface so call-sites that take a generic options bag keep working.
 */
sealed interface RpcBaseOptions {
    val encoding: Encoding?
    val commitment: Commitment?
    val minContextSlot: Long?
}

/** Data-slice applied to account reads. Matches upstream `RpcDataSlice`. */
data class RpcDataSlice(val offset: Long, val length: Long)

/**
 * Filter for `getProgramAccounts`. Upstream is a sealed class with two
 * variants - data-size and memcmp. Both are preserved.
 */
sealed class RpcDataFilter {
    data class Size(val dataSize: Long) : RpcDataFilter()
    data class Memcmp(val memcmp: MemcmpFilter) : RpcDataFilter()
}

/**
 * Upstream type alias for the memcmp filter body. Exposed as a real class so
 * Kotlin destructuring works the same way on both sides.
 */
data class MemcmpFilter(val offset: Long, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemcmpFilter) return false
        return offset == other.offset && bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = 31 * offset.hashCode() + bytes.contentHashCode()
}

/** Matches upstream `RpcDataFilterSize(dataSize)`. */
@Suppress("FunctionName")
fun RpcDataFilterSize(dataSize: Long): RpcDataFilter.Size = RpcDataFilter.Size(dataSize)

/** Matches upstream `RpcDataFilterMemcmp(memcmp)`. */
@Suppress("FunctionName")
fun RpcDataFilterMemcmp(memcmp: MemcmpFilter): RpcDataFilter.Memcmp = RpcDataFilter.Memcmp(memcmp)

// ---------------------------------------------------------------------------
// Typed configuration objects.
// ---------------------------------------------------------------------------

data class RpcGetAccountInfoConfiguration(
    override val encoding: Encoding = Encoding.base64,
    override val commitment: Commitment? = null,
    override val minContextSlot: Long? = null,
    val dataSlice: RpcDataSlice? = null,
) : RpcBaseOptions

data class RpcGetMultipleAccountsConfiguration(
    override val encoding: Encoding = Encoding.base64,
    override val commitment: Commitment? = null,
    override val minContextSlot: Long? = null,
    val dataSlice: RpcDataSlice? = null,
) : RpcBaseOptions

data class RpcGetProgramAccountsConfiguration(
    override val encoding: Encoding = Encoding.base64,
    override val commitment: Commitment? = null,
    override val minContextSlot: Long? = null,
    val dataSlice: RpcDataSlice? = null,
    val filters: List<RpcDataFilter>? = null,
) : RpcBaseOptions

data class RpcGetLatestBlockhashConfiguration(
    override val encoding: Encoding? = null,
    override val commitment: Commitment? = null,
    override val minContextSlot: Long? = null,
) : RpcBaseOptions

data class RpcGetSlotConfiguration(
    override val encoding: Encoding? = null,
    override val commitment: Commitment? = null,
    override val minContextSlot: Long? = null,
) : RpcBaseOptions

data class RpcGetBalanceConfiguration(
    override val encoding: Encoding? = null,
    override val commitment: Commitment? = null,
    override val minContextSlot: Long? = null,
) : RpcBaseOptions

data class RpcRequestAirdropConfiguration(
    val publicKey: PublicKey,
    val lamports: foundation.metaplex.amount.Amount,
    val commitment: Commitment? = null,
)

data class RpcSendTransactionConfiguration(
    override val encoding: Encoding = Encoding.base64,
    override val commitment: Commitment? = null,
    override val minContextSlot: Long? = null,
    val skipPreflight: Boolean? = null,
    val preflightCommitment: Commitment? = null,
    val maxRetries: UInt? = null,
) : RpcBaseOptions

/**
 * Latest-blockhash response including its validity window. Matches upstream
 * `BlockhashWithExpiryBlockHeight` exactly.
 */
data class BlockhashWithExpiryBlockHeight(
    val blockhash: String,
    val lastValidBlockHeight: ULong,
)
