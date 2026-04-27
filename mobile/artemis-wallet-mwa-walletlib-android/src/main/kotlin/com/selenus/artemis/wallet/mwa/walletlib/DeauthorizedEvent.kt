package com.selenus.artemis.wallet.mwa.walletlib

import kotlinx.coroutines.CompletableDeferred

/**
 * Notification that a `deauthorize` request arrived for the current
 * session. Unlike the other request types this is fire-and-forget
 * from the dApp's point of view: the wallet revokes the token (or
 * acknowledges that it was already revoked) and the dispatcher sends
 * a void-success reply.
 *
 * The single [complete] method exists so wallets that need to defer
 * UI cleanup until the `AuthRepository.revoke` call returns can keep
 * the dispatcher waiting; immediate callers just invoke it from the
 * callback body.
 */
class DeauthorizedEvent internal constructor(
    val authToken: String
) {
    private val done = CompletableDeferred<Unit>()

    /** Acknowledge the deauthorize. Idempotent. */
    fun complete() {
        done.complete(Unit)
    }

    /** Suspend until [complete] is invoked. Used by the dispatcher. */
    internal suspend fun await() {
        done.await()
    }
}
