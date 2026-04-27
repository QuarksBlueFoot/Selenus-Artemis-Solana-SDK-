package com.selenus.artemis.wallet.mwa.walletlib

/**
 * Raised when a `solana-wallet://...` association URI fails to parse.
 *
 * The exception is intentionally typed (not a plain
 * [IllegalArgumentException]) so the wallet's Activity entry point can
 * `catch` it and render a typed error UI without grabbing arbitrary
 * runtime exceptions from elsewhere in the parser stack.
 */
class MwaAssociationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Raised when the wallet-side handshake fails: the dApp signature does
 * not verify under the URI's `association` public key, the HELLO_REQ
 * payload is malformed, or the underlying socket dies before the
 * exchange completes.
 *
 * Distinct from [MwaAssociationException] because the URI parsed fine;
 * something went wrong on the wire. Tests rely on the distinct type to
 * assert the stage at which a fault occurred.
 */
class MwaHandshakeException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
