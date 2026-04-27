package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri

/**
 * Wallet-side view of a `sign_and_send_transactions` JSON-RPC
 * request. This is the MWA 2.0 mandatory feature: the wallet signs
 * each transaction and broadcasts it to the network, returning the
 * resulting signatures.
 *
 * The transport options (`commitment`, `skipPreflight`, etc.) come
 * straight from the dApp request; the wallet should honour them when
 * forwarding to its RPC backend, falling back to sensible defaults
 * when the dApp omitted a value.
 *
 * @property minContextSlot Lower bound on the slot the transaction is
 *   evaluated at. `null` = use the wallet's default.
 * @property commitment One of `processed`, `confirmed`, `finalized`.
 *   `null` = use the wallet's default.
 * @property skipPreflight Skip the `simulateTransaction` preflight
 *   check on the RPC node. `null` = use the wallet's default.
 * @property maxRetries Maximum number of times the RPC node retries
 *   pushing the transaction to the leader. `null` = RPC default.
 * @property waitForCommitmentToSendNextTransaction When the request
 *   carries multiple payloads, wait for the previous one to reach
 *   [commitment] before submitting the next. Critical for dependent
 *   transactions.
 */
open class SignAndSendTransactionsRequest internal constructor(
    val payloads: List<ByteArray>,
    val authorizedAccounts: List<AuthorizedAccount>,
    val identityName: String?,
    val identityUri: Uri?,
    val iconRelativeUri: Uri?,
    val minContextSlot: Int?,
    val commitment: String?,
    val skipPreflight: Boolean?,
    val maxRetries: Int?,
    val waitForCommitmentToSendNextTransaction: Boolean?
) : MwaRequest() {

    /**
     * Wallet signed and submitted every payload. [signatures] must be
     * the same length as [payloads] and ordered to match. Each entry
     * is the 64-byte Ed25519 transaction signature.
     */
    fun completeWithSignatures(signatures: List<ByteArray>) {
        require(signatures.size == payloads.size) {
            "signatures.size (${signatures.size}) must match payloads.size (${payloads.size})"
        }
        signatures.forEachIndexed { idx, sig ->
            require(sig.size == 64) {
                "signatures[$idx] must be 64 bytes (got ${sig.size})"
            }
        }
        completeInternal(MwaCompletion.Result(SignaturesResult(signatures)))
    }

    /** User declined. Surfaced as `NOT_SIGNED`. */
    fun completeWithDecline() {
        completeInternal(
            MwaCompletion.Error(
                code = MwaErrorCodes.NOT_SIGNED,
                message = "user declined to sign transactions"
            )
        )
    }

    /**
     * One or more payloads were malformed and never reached the
     * network. [valid] is the per-payload validity mask.
     */
    fun completeWithInvalidSignatures(valid: List<Boolean>) {
        require(valid.size == payloads.size) {
            "valid.size (${valid.size}) must match payloads.size (${payloads.size})"
        }
        require(valid.any { !it }) {
            "completeWithInvalidSignatures requires at least one false entry"
        }
        completeInternal(
            MwaCompletion.Error(
                code = MwaErrorCodes.INVALID_PAYLOADS,
                message = "wallet rejected one or more payloads as invalid",
                data = InvalidSignaturesErrorData(valid)
            )
        )
    }

    /**
     * Wallet signed every payload but the RPC submission failed for
     * some of them. [signatures] is the same length as [payloads],
     * with `null` entries marking the unsubmitted slots so the dApp
     * can retry exactly those.
     */
    fun completeWithNotSubmitted(signatures: List<ByteArray?>) {
        require(signatures.size == payloads.size) {
            "signatures.size (${signatures.size}) must match payloads.size (${payloads.size})"
        }
        signatures.forEach { sig ->
            if (sig != null) require(sig.size == 64) {
                "every non-null signature must be 64 bytes (got ${sig.size})"
            }
        }
        completeInternal(
            MwaCompletion.Error(
                code = MwaErrorCodes.NOT_SUBMITTED,
                message = "one or more transactions did not reach the network",
                data = NotSubmittedErrorData(signatures)
            )
        )
    }

    /**
     * Request exceeded
     * [MobileWalletAdapterConfig.maxTransactionsPerSigningRequest].
     */
    fun completeWithTooManyPayloads() {
        completeInternal(
            MwaCompletion.Error(
                code = MwaErrorCodes.TOO_MANY_PAYLOADS,
                message = "request exceeded the wallet's per-call payload cap"
            )
        )
    }

    /** Auth token no longer valid. Surfaced as `AUTHORIZATION_FAILED`. */
    fun completeWithAuthorizationNotValid() {
        completeInternal(
            MwaCompletion.Error(
                code = MwaErrorCodes.AUTHORIZATION_FAILED,
                message = "authorization no longer valid; reauthorize"
            )
        )
    }

    internal data class SignaturesResult(val signatures: List<ByteArray>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SignaturesResult) return false
            if (signatures.size != other.signatures.size) return false
            return signatures.zip(other.signatures).all { (a, b) -> a.contentEquals(b) }
        }
        override fun hashCode(): Int =
            signatures.fold(0) { acc, bytes -> 31 * acc + bytes.contentHashCode() }
    }

    internal data class InvalidSignaturesErrorData(val valid: List<Boolean>)
    internal data class NotSubmittedErrorData(val signatures: List<ByteArray?>)
}
