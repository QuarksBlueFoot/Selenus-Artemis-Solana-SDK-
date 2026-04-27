package com.selenus.artemis.wallet.mwa.walletlib

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Result type produced by every wallet-side completion call.
 *
 * The dispatcher serializes the variant into a JSON-RPC reply:
 *  - [Result] becomes a successful `result` envelope.
 *  - [Error] becomes an `error` envelope with the carried code/message.
 *
 * Concrete request subclasses expose typed completion methods (e.g.
 * [SignTransactionsRequest.completeWithSignedPayloads]) that internally
 * route to one of these variants; tests and the dispatcher itself
 * never need to construct them directly.
 *
 * The class is public (not `internal`) only because the [MwaRequest]
 * subclass `completeInternal` parameter type leaks into the
 * Kotlin-visibility surface of every protected member. Direct
 * construction by SDK consumers is not part of the supported API; the
 * typed `completeWith*` methods are the way in.
 */
sealed class MwaCompletion {
    data class Result(val payload: Any?) : MwaCompletion()
    data class Error(val code: Int, val message: String, val data: Any? = null) : MwaCompletion()
}

/**
 * Common one-shot completion plumbing for every wallet-side request.
 *
 * `completeWith*` wrappers in subclasses go through [completeInternal]
 * exactly once: the second call throws [IllegalStateException] so a
 * misbehaving UI flow that fires both `approve()` and `decline()`
 * cannot produce two JSON-RPC replies for the same request id. The
 * dispatcher uses [awaitCompletion] to suspend until the wallet
 * resolves the request.
 *
 * This base class is `internal`-flavoured (the constructor is
 * `internal`) but the type itself is public so subclasses can be
 * sealed without leaking implementation detail.
 */
abstract class MwaRequest internal constructor() {
    private val completion: CompletableDeferred<MwaCompletion> = CompletableDeferred()
    private val finished: AtomicBoolean = AtomicBoolean(false)

    /**
     * `true` once any `completeWith*` method has been invoked. Useful
     * for tests that want to assert UI flows did not double-complete.
     */
    val isComplete: Boolean get() = finished.get()

    /**
     * Suspending handle on the completion. Returns the chosen
     * [MwaCompletion] variant once a completion method is invoked, or
     * fails with [java.util.concurrent.CancellationException] if the
     * scenario was closed before the wallet replied.
     */
    internal suspend fun awaitCompletion(): MwaCompletion = completion.await()

    /** Non-suspending [Deferred] view used by the dispatcher's select loops. */
    internal fun completionAsDeferred(): Deferred<MwaCompletion> = completion

    /**
     * Subclass entry point. Every concrete `completeWith*` method
     * funnels through here. The first call wins; subsequent calls
     * throw so the wallet's UI cannot accidentally settle the same
     * request twice (which would push a second JSON-RPC reply on the
     * wire and corrupt the dApp's id-to-future map).
     */
    protected fun completeInternal(value: MwaCompletion) {
        if (!finished.compareAndSet(false, true)) {
            throw IllegalStateException("MwaRequest already completed")
        }
        completion.complete(value)
    }

    /**
     * Cancel the completion when the scenario tears down before the
     * wallet replied. Idempotent. Does NOT throw if already complete;
     * a normal completion that races with teardown is a benign race
     * and not a programmer error.
     */
    internal fun cancel(reason: String) {
        if (finished.compareAndSet(false, true)) {
            completion.completeExceptionally(
                java.util.concurrent.CancellationException(reason)
            )
        }
    }
}
