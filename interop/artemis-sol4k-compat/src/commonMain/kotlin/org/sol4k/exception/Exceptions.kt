/*
 * Drop-in source compatibility with org.sol4k.exception.
 *
 * sol4k throws two exception types from its public API: `RpcException` for
 * anything JSON-RPC (including server-side errors) and `SerializationException`
 * for wire-format decoding errors. The shim re-publishes both at the same
 * fully qualified name.
 */
package org.sol4k.exception

/**
 * sol4k-compatible RPC exception. Thrown by `Connection` when the cluster
 * returns an error object or the transport fails.
 */
class RpcException(
    val code: Int? = null,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * sol4k-compatible serialization exception. Thrown when decoding a
 * transaction or account blob fails.
 */
class SerializationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
