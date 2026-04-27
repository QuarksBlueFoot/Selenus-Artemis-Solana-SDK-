package com.selenus.artemis.wallet.mwa.walletlib

/**
 * Wallet-side proof that the user signed the SIWS message presented by
 * the dApp during `authorize`.
 *
 * Returned through [AuthorizeRequest.completeWithAuthorize] when the
 * dApp included a [SignInPayload] in its request. The dApp ultimately
 * verifies [signature] over [signedMessage] under [publicKey].
 *
 * @property publicKey 32-byte Ed25519 public key whose private half
 *   produced [signature]. Will appear in `accounts[0].publicKey` for
 *   the issued auth token.
 * @property signedMessage Exact bytes the wallet ran through Ed25519
 *   signing. Includes the SIWS preamble; the dApp re-derives this
 *   from the original payload to confirm the wallet did not silently
 *   substitute a different message.
 * @property signature 64-byte detached Ed25519 signature.
 * @property signatureType Wire identifier for the signature scheme.
 *   Defaults to `"ed25519"`; future schemes (e.g. secp256k1 for
 *   non-Solana chains using the same wallet binary) override.
 */
data class SignInResult(
    val publicKey: ByteArray,
    val signedMessage: ByteArray,
    val signature: ByteArray,
    val signatureType: String = "ed25519"
) {
    init {
        require(publicKey.size == 32) {
            "Ed25519 publicKey must be 32 bytes, got ${publicKey.size}"
        }
        require(signature.size == 64) {
            "Ed25519 signature must be 64 bytes, got ${signature.size}"
        }
        require(signedMessage.isNotEmpty()) { "signedMessage must not be empty" }
        require(signatureType.isNotBlank()) { "signatureType must not be blank" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignInResult) return false
        return publicKey.contentEquals(other.publicKey) &&
            signedMessage.contentEquals(other.signedMessage) &&
            signature.contentEquals(other.signature) &&
            signatureType == other.signatureType
    }

    override fun hashCode(): Int {
        var h = publicKey.contentHashCode()
        h = 31 * h + signedMessage.contentHashCode()
        h = 31 * h + signature.contentHashCode()
        h = 31 * h + signatureType.hashCode()
        return h
    }
}
