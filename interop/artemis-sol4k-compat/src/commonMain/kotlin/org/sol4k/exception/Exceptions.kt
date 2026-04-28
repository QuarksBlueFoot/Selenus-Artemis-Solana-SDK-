/*
 * Drop-in source compatibility with org.sol4k.exception.
 *
 * Upstream sol4k publishes both exceptions as `data class`es with
 * specific positional ctors. Apps that destructure or `.copy()` them
 * rely on that shape, so the shim mirrors it exactly:
 *  - RpcException(code: Int, message: String, rawResponse: String)
 *  - SerializationException(message: String)
 *
 * The optional secondary constructors below preserve compatibility
 * with code that constructed our older non-data-class shape; both
 * compile against the upstream data-class form.
 */
package org.sol4k.exception

/**
 * sol4k-compatible RPC exception. Thrown by `Connection` when the
 * cluster returns a JSON-RPC error object or the transport fails.
 *
 * Upstream is `data class RpcException(val code: Int, override val
 * message: String, val rawResponse: String)`. The fields are exposed
 * as `val` so destructuring (`val (code, msg, raw) = e`) and `.copy()`
 * keep working against existing user code.
 */
data class RpcException(
    val code: Int,
    override val message: String,
    val rawResponse: String
) : RuntimeException(message) {

    /**
     * Backwards-compat ctor for the previous Artemis-specific shape
     * `(code: Int?, message, cause: Throwable?)`. Folds the optional
     * code into 0 and the cause into a placeholder rawResponse so
     * existing throw sites keep compiling.
     */
    constructor(
        code: Int?,
        message: String,
        cause: Throwable?
    ) : this(
        code = code ?: 0,
        message = message,
        rawResponse = cause?.message ?: ""
    ) {
        if (cause != null) initCause(cause)
    }
}

/**
 * sol4k-compatible serialization exception. Thrown when decoding a
 * transaction or account blob fails.
 *
 * Upstream is `data class SerializationException(override val message: String)`.
 */
data class SerializationException(
    override val message: String
) : RuntimeException(message) {

    /**
     * Backwards-compat ctor that captures a [cause]. Wires the cause
     * via `initCause` so the resulting exception still chains, while
     * the data-class ctor remains the canonical one.
     */
    constructor(message: String, cause: Throwable?) : this(message) {
        if (cause != null) initCause(cause)
    }
}
