package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri

/**
 * Wallet-side view of a `sign_transactions` JSON-RPC request.
 *
 * The dApp passes one or more raw transaction byte strings; the wallet
 * signs each and replies with the signed payload. Per the MWA 2.0 spec
 * this method is OPTIONAL: wallets advertise it through
 * `optionalFeatures` containing
 * [MobileWalletAdapterConfig.FEATURE_SIGN_TRANSACTIONS]. When omitted,
 * the dispatcher rejects inbound requests with
 * `AUTHORIZATION_NOT_VALID` before they reach the callback.
 *
 * @property payloads Each entry is a full serialized Solana
 *   transaction (legacy or v0). The wallet decodes per its supported
 *   versions.
 * @property authorizedAccounts Snapshot of the auth-token's account
 *   list at the time the request arrived. The wallet should reject any
 *   payload that requires a signer outside this set.
 */
open class SignTransactionsRequest internal constructor(
    val payloads: List<ByteArray>,
    val authorizedAccounts: List<AuthorizedAccount>,
    val identityName: String?,
    val identityUri: Uri?,
    val iconRelativeUri: Uri?
) : MwaRequest() {

    /**
     * Wallet signed every payload. [signedPayloads] must be the same
     * length as [payloads] and ordered to match.
     */
    fun completeWithSignedPayloads(signedPayloads: List<ByteArray>) {
        require(signedPayloads.size == payloads.size) {
            "signedPayloads.size (${signedPayloads.size}) must match payloads.size (${payloads.size})"
        }
        completeInternal(MwaCompletion.Result(SignedPayloadsResult(signedPayloads)))
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
     * One or more payloads were malformed (failed to decode, used an
     * unsupported version, etc.). [valid] must be the same length as
     * [payloads]; `false` entries identify the rejected indices. The
     * dApp's clientlib unpacks this through
     * `MobileWalletAdapterClient.InvalidPayloadsException`.
     */
    fun completeWithInvalidPayloads(valid: List<Boolean>) {
        require(valid.size == payloads.size) {
            "valid.size (${valid.size}) must match payloads.size (${payloads.size})"
        }
        require(valid.any { !it }) {
            "completeWithInvalidPayloads requires at least one false entry"
        }
        completeInternal(
            MwaCompletion.Error(
                code = MwaErrorCodes.INVALID_PAYLOADS,
                message = "wallet rejected one or more payloads as invalid",
                data = InvalidPayloadsErrorData(valid)
            )
        )
    }

    /**
     * Request exceeded
     * [MobileWalletAdapterConfig.maxTransactionsPerSigningRequest].
     * Surfaced as `TOO_MANY_PAYLOADS`.
     */
    fun completeWithTooManyPayloads() {
        completeInternal(
            MwaCompletion.Error(
                code = MwaErrorCodes.TOO_MANY_PAYLOADS,
                message = "request exceeded the wallet's per-call payload cap"
            )
        )
    }

    /**
     * The auth token referenced by this session is no longer valid
     * (typically because the user revoked it on a different device).
     * Surfaced as `AUTHORIZATION_FAILED`.
     */
    fun completeWithAuthorizationNotValid() {
        completeInternal(
            MwaCompletion.Error(
                code = MwaErrorCodes.AUTHORIZATION_FAILED,
                message = "authorization no longer valid; reauthorize"
            )
        )
    }

    internal data class SignedPayloadsResult(val signedPayloads: List<ByteArray>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SignedPayloadsResult) return false
            if (signedPayloads.size != other.signedPayloads.size) return false
            return signedPayloads.zip(other.signedPayloads).all { (a, b) -> a.contentEquals(b) }
        }
        override fun hashCode(): Int {
            return signedPayloads.fold(0) { acc, bytes -> 31 * acc + bytes.contentHashCode() }
        }
    }

    internal data class InvalidPayloadsErrorData(val valid: List<Boolean>)
}
