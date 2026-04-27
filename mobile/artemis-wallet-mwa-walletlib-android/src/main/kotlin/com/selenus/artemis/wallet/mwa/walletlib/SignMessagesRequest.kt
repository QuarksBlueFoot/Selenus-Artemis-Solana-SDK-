package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri

/**
 * Wallet-side view of a `sign_messages` JSON-RPC request.
 *
 * Per the MWA 2.0 spec, the wallet returns one signature per
 * `(payload, address)` pair, and the on-wire `signed_payloads` field
 * is the concatenation of the original message bytes and the produced
 * signature. The wallet's [completeWithSignedPayloads] takes the
 * already-concatenated form so callers that build SIWS signatures
 * with their own framing keep full control.
 *
 * @property payloads One opaque message per entry. Wallets typically
 *   render the bytes as UTF-8 text plus a hex fallback in the prompt.
 * @property addresses One per payload. The wallet must use the
 *   matching keypair (resolved through [authorizedAccounts]).
 */
open class SignMessagesRequest internal constructor(
    val payloads: List<ByteArray>,
    val addresses: List<ByteArray>,
    val authorizedAccounts: List<AuthorizedAccount>,
    val identityName: String?,
    val identityUri: Uri?,
    val iconRelativeUri: Uri?
) : MwaRequest() {

    init {
        // Caller of the constructor (the dispatcher) is expected to
        // have verified payloads.size == addresses.size; assert here so
        // a future refactor that bypasses that path fails fast in
        // tests instead of producing skewed signed payloads at runtime.
        require(payloads.size == addresses.size) {
            "payloads.size (${payloads.size}) must match addresses.size (${addresses.size})"
        }
    }

    /**
     * Wallet signed every message. Each entry of [signedPayloads] is
     * `message || 64-byte ed25519 signature` per the MWA spec; the
     * dApp parses the trailing 64 bytes as the signature.
     */
    fun completeWithSignedPayloads(signedPayloads: List<ByteArray>) {
        require(signedPayloads.size == payloads.size) {
            "signedPayloads.size (${signedPayloads.size}) must match payloads.size (${payloads.size})"
        }
        signedPayloads.forEachIndexed { idx, signed ->
            val expectedMin = payloads[idx].size + 64
            require(signed.size >= expectedMin) {
                "signedPayloads[$idx] is ${signed.size} bytes; expected at least $expectedMin (message || ed25519 signature)"
            }
        }
        completeInternal(MwaCompletion.Result(SignedMessagesResult(signedPayloads)))
    }

    /** User declined. Surfaced as `NOT_SIGNED`. */
    fun completeWithDecline() {
        completeInternal(
            MwaCompletion.Error(
                code = MwaErrorCodes.NOT_SIGNED,
                message = "user declined to sign messages"
            )
        )
    }

    /**
     * One or more `(payload, address)` pairs were rejected: the
     * payload bytes failed an internal sanity check, the address was
     * not part of [authorizedAccounts], etc. [valid] must be the same
     * length as [payloads]; `false` entries identify the rejected
     * indices.
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
     * [MobileWalletAdapterConfig.maxMessagesPerSigningRequest].
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
     * The auth token referenced by this session is no longer valid.
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

    internal data class SignedMessagesResult(val signedPayloads: List<ByteArray>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SignedMessagesResult) return false
            if (signedPayloads.size != other.signedPayloads.size) return false
            return signedPayloads.zip(other.signedPayloads).all { (a, b) -> a.contentEquals(b) }
        }
        override fun hashCode(): Int =
            signedPayloads.fold(0) { acc, bytes -> 31 * acc + bytes.contentHashCode() }
    }

    internal data class InvalidPayloadsErrorData(val valid: List<Boolean>)
}
