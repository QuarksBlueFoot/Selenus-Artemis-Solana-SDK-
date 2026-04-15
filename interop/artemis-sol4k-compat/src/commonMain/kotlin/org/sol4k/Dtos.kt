/*
 * Drop-in source compatibility DTOs for the sol4k RPC layer.
 *
 * Each type mirrors the sol4k 0.7.0 shape. The field names and Kotlin nullability
 * are chosen to match what sol4k returns so existing code destructures without
 * change. Conversions from Artemis types happen in `Connection.kt`.
 */
package org.sol4k

/**
 * Typed account information returned by `Connection.getAccountInfo`.
 *
 * sol4k exposes this as a data class. `null` means the account does not exist
 * on-chain.
 */
data class AccountInfo(
    val lamports: Long,
    val owner: PublicKey,
    val data: ByteArray,
    val executable: Boolean,
    val rentEpoch: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccountInfo) return false
        return lamports == other.lamports &&
            owner == other.owner &&
            data.contentEquals(other.data) &&
            executable == other.executable &&
            rentEpoch == other.rentEpoch
    }

    override fun hashCode(): Int {
        var result = lamports.hashCode()
        result = 31 * result + owner.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + executable.hashCode()
        result = 31 * result + rentEpoch.hashCode()
        return result
    }
}

/**
 * Solana cluster epoch info as returned by `getEpochInfo`.
 */
data class EpochInfo(
    val absoluteSlot: Long,
    val blockHeight: Long,
    val epoch: Long,
    val slotIndex: Long,
    val slotsInEpoch: Long,
    val transactionCount: Long?
)

/**
 * Latest blockhash response as returned by `getLatestBlockhashExtended`.
 */
data class Blockhash(
    val blockhash: String,
    val lastValidBlockHeight: Long
)

/** `getHealth` response. */
data class Health(val status: String)

/** `getVersion` response. */
data class Version(val solanaCore: String, val featureSet: Long?)

/** SPL token amount as returned by `getTokenSupply` and `getTokenAccountBalance`. */
data class TokenAmount(
    val amount: String,
    val decimals: Int,
    val uiAmount: Double?,
    val uiAmountString: String
)

/** `getTokenAccountBalance` response body. */
data class TokenAccountBalance(
    val context: Context,
    val value: TokenAmount
) {
    data class Context(val slot: Long)
}

/** A single prioritization fee record from `getRecentPrioritizationFees`. */
data class PrioritizationFee(
    val slot: Long,
    val prioritizationFee: Long
)

/**
 * Simulation result returned by `Connection.simulateTransaction`.
 *
 * The `err` field matches sol4k: it is `null` on success and a json-encoded
 * string on failure.
 */
data class TransactionSimulation(
    val err: String?,
    val logs: List<String>,
    val unitsConsumed: Long?,
    val returnData: ReturnData?
) {
    data class ReturnData(val programId: String, val data: String)
}

/**
 * Signature info returned by `getSignaturesForAddress`.
 */
data class TransactionSignature(
    val signature: String,
    val slot: Long,
    val err: String?,
    val memo: String?,
    val blockTime: Long?,
    val confirmationStatus: String?
)
