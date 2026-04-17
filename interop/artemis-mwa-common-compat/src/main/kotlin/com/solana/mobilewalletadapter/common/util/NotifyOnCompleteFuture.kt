/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.common.util.
 *
 * Upstream's `NotifyOnCompleteFuture<T>` is the return type of every method
 * on the non-ktx `MobileWalletAdapterClient`. It extends `java.util.concurrent.Future<T>`
 * and adds a single `notifyOnComplete` method that invokes an
 * `OnCompleteCallback<T>` when the future resolves.
 *
 * Replicating the interface is required: upstream apps hold the return type
 * in `Future<T>` variables and call `notifyOnComplete` on them.
 */
package com.solana.mobilewalletadapter.common.util

import java.util.concurrent.Future

/**
 * Callback interface invoked exactly once when a [NotifyOnCompleteFuture]
 * resolves (success or failure).
 *
 * Upstream bounds `T` to `Future<?>`. Replicating the bound is required so
 * user code that writes `OnCompleteCallback<NotifyOnCompleteFuture<AuthorizationResult>>`
 * type-checks against the same generic constraint.
 */
fun interface OnCompleteCallback<T : Future<*>> {
    fun onComplete(future: T)
}

/**
 * A `Future<T>` that also supports registering a completion callback.
 *
 * Implementations guarantee the callback is invoked exactly once from a
 * neutral thread the Future controls.
 */
interface NotifyOnCompleteFuture<T> : Future<T> {
    fun notifyOnComplete(cb: OnCompleteCallback<in NotifyOnCompleteFuture<T>>)
}
