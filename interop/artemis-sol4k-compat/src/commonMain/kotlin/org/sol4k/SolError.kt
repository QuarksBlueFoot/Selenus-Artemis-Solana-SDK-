/*
 * Artemis innovation on top of sol4k: sealed error hierarchy.
 *
 * Upstream sol4k throws a plain `RpcException` with a free-text message. That
 * makes it hard to write a robust retry policy on the caller side ("rate
 * limited? back off. blockhash expired? refetch. network error? retry
 * immediately"). The sealed hierarchy below gives Kotlin call sites an
 * exhaustive `when` over the common failure modes.
 *
 * The existing `RpcException` keeps working; [SolError.from] classifies a
 * thrown exception into the right [SolError] subtype when the caller wants
 * typed handling.
 */
package org.sol4k

import org.sol4k.exception.RpcException

sealed class SolError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {

    /** HTTP 429 or RPC `-32005` back-off request. Retry with exponential delay. */
    class RateLimited(message: String, cause: Throwable? = null) : SolError(message, cause)

    /** Blockhash expired (`-32002`). Re-fetch latest blockhash and resign. */
    class BlockhashExpired(message: String, cause: Throwable? = null) : SolError(message, cause)

    /** Connection dropped mid-call. Safe to retry. */
    class ConnectionLost(message: String, cause: Throwable? = null) : SolError(message, cause)

    /** Malformed payload from wallet or RPC. Do NOT retry; inspect payload. */
    class Serialization(message: String, cause: Throwable? = null) : SolError(message, cause)

    /** Anything else. Caller decides. */
    class Unknown(message: String, cause: Throwable? = null) : SolError(message, cause)

    companion object {
        /**
         * Classify a thrown exception. Use in catch blocks:
         *
         * ```kotlin
         * try { rpc.sendTransaction(tx) } catch (e: Exception) {
         *     when (val err = SolError.from(e)) {
         *         is SolError.RateLimited -> backoffThenRetry()
         *         is SolError.BlockhashExpired -> refreshAndRetry()
         *         is SolError.ConnectionLost -> retry()
         *         else -> surfaceToUser(err)
         *     }
         * }
         * ```
         */
        @JvmStatic
        fun from(cause: Throwable): SolError {
            val msg = cause.message.orEmpty()
            if (cause is RpcException) {
                return when (cause.code) {
                    -32005 -> RateLimited(msg, cause)
                    -32002 -> BlockhashExpired(msg, cause)
                    else -> Unknown(msg, cause)
                }
            }
            val lower = msg.lowercase()
            return when {
                "429" in lower || "rate limit" in lower -> RateLimited(msg, cause)
                "blockhash" in lower && "expired" in lower -> BlockhashExpired(msg, cause)
                "connection" in lower || "timeout" in lower -> ConnectionLost(msg, cause)
                "serial" in lower || "parse" in lower -> Serialization(msg, cause)
                else -> Unknown(msg, cause)
            }
        }
    }
}
