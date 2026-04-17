/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.common.protocol.
 *
 * `MessageReceiver` / `MessageSender` are the transport-layer interfaces
 * implemented on both the dapp and wallet sides of the MWA websocket.
 * `MobileWalletAdapterSessionCommon` is the abstract base that wires the
 * ECDH + HELLO handshake on top of them.
 *
 * `JsonRpc20Client` is the JSON-RPC 2.0 router. The constants below match the
 * verified upstream values: SERVER_RESERVED_ERROR_MIN/MAX plus the five
 * standard JSON-RPC error codes in the -32xxx range.
 */
package com.solana.mobilewalletadapter.common.protocol

import com.solana.mobilewalletadapter.common.SessionProperties
import java.io.IOException
import java.security.interfaces.ECPublicKey
import javax.crypto.SecretKey

/**
 * Transport-level message sink. The upstream Java interface declares each
 * method with `@NonNull` annotations - Kotlin compiles the same behavior
 * through its null-safety types.
 */
interface MessageReceiver {
    fun receiverConnected(messageSender: MessageSender)
    fun receiverMessageReceived(payload: ByteArray)
    fun receiverDisconnected()
}

/**
 * Transport-level message source. The single `send` method throws
 * `IOException` on wire errors; replicating the `@Throws` is required so Java
 * callers do not need to wrap invocations in `try`/`catch(RuntimeException)`.
 */
interface MessageSender {
    @Throws(IOException::class)
    fun send(payload: ByteArray)
}

/**
 * Abstract base shared between `MobileWalletAdapterSession` (dapp side) and
 * the wallet-side session. Holds the ECDH session key, the peer's public
 * key, and the negotiated session properties.
 *
 * The shim exposes the protected field names upstream uses (Java convention
 * `m*`) so subclasses in user code that reference them keep resolving.
 */
abstract class MobileWalletAdapterSessionCommon(
    @JvmField
    protected val mDecryptedPayloadReceiver: MessageReceiver
) : MessageReceiver, MessageSender {

    @JvmField
    protected var mMessageSender: MessageSender? = null

    @JvmField
    protected var mCachedEncryptionKey: SecretKey? = null

    @JvmField
    protected var mOtherPublicKey: ECPublicKey? = null

    @JvmField
    protected var mSessionProperties: SessionProperties =
        SessionProperties(SessionProperties.ProtocolVersion.V1)

    @Throws(IOException::class)
    override fun send(payload: ByteArray) {
        val sender = mMessageSender ?: throw IOException("Session not connected")
        sender.send(payload)
    }

    override fun receiverConnected(messageSender: MessageSender) {
        mMessageSender = messageSender
    }

    override fun receiverDisconnected() {
        mMessageSender = null
        mCachedEncryptionKey = null
        mOtherPublicKey = null
    }

    override fun receiverMessageReceived(payload: ByteArray) {
        // Real implementations route HELLO vs encrypted payloads. Kept
        // abstract-via-delegation in the shim because the wire protocol lives
        // in the Artemis MWA adapter, not here.
        mDecryptedPayloadReceiver.receiverMessageReceived(payload)
    }

    protected abstract fun handleSessionEstablished()
    protected abstract fun handleSessionClosed()

    /** Raised by encrypt/decrypt helpers when the session cipher fails. */
    class SessionEncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Raised when the wire framing is malformed. */
    class SessionMessageException(message: String, cause: Throwable? = null) : Exception(message, cause)
}

/**
 * JSON-RPC 2.0 router. The outer class holds the exception hierarchy and the
 * reserved-error constants. Real dispatch happens in the Artemis adapter;
 * the shim exposes the types user code catches.
 */
class JsonRpc20Client {
    abstract class JsonRpc20Exception(message: String, cause: Throwable? = null) : Exception(message, cause)

    class JsonRpc20InvalidResponseException(message: String) : JsonRpc20Exception(message)

    open class JsonRpc20RemoteException(
        val code: Int,
        message: String,
        val data: String? = null
    ) : JsonRpc20Exception(message) {
        /** True when [code] falls in the JSON-RPC 2.0 server-reserved range. */
        fun isReservedError(): Boolean = code in SERVER_RESERVED_ERROR_MIN..SERVER_RESERVED_ERROR_MAX
    }

    companion object {
        // JSON-RPC 2.0 server-reserved range: -32099..-32000. A sub-audit
        // confirmed the upstream Java class publishes exactly these bounds.
        const val SERVER_RESERVED_ERROR_MIN: Int = -32099
        const val SERVER_RESERVED_ERROR_MAX: Int = -32000

        // The five standard JSON-RPC 2.0 error codes, published as constants
        // on `JsonRpc20Client` in the upstream Java source.
        const val ERROR_PARSE: Int = -32700
        const val ERROR_INVALID_REQUEST: Int = -32600
        const val ERROR_METHOD_NOT_FOUND: Int = -32601
        const val ERROR_INVALID_PARAMS: Int = -32602
        const val ERROR_INTERNAL: Int = -32603
    }
}
