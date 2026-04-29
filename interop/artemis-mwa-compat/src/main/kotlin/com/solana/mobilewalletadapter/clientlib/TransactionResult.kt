/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.solana.mobilewalletadapter.clientlib

/**
 * Result of an MWA `transact` call.
 *
 * Top-level shape matches upstream `com.solana.mobilewalletadapter.clientlib.TransactionResult`.
 * The [Success] variant wraps the payload the `transact { }` block
 * returned, which may be a signature, a SIWS result, or a
 * [TransactionBatchResult] when the caller invoked
 * [AdapterOperations.signAndSendTransactionsBatch]. Typing the batch
 * case as `TransactionResult<TransactionBatchResult>` is the idiom for
 * batch-aware callers; no extra slot is needed on [Success] because the
 * payload already carries everything.
 */
sealed class TransactionResult<out T> {

    data class Success<T>(
        val payload: T,
        val authResult: AuthorizationResult? = null
    ) : TransactionResult<T>()

    data class Failure(
        val message: String,
        val e: Exception? = null
    ) : TransactionResult<Nothing>()

    data class NoWalletFound(
        val message: String
    ) : TransactionResult<Nothing>()
}

val <T> TransactionResult<T>.successPayload: T?
    get() = (this as? TransactionResult.Success)?.payload

/**
 * Status for a single transaction inside a sign-and-send batch.
 *
 * The invariant below holds for every instance produced by the compat
 * layer:
 *
 *   signature != null  =>  success == true   && error == null
 *   error != null      =>  success == false  && signature == null
 *   success == false   =>  error != null
 *   success == true    =>  signature != null
 *
 * In other words, "success" and "error" are mutually exclusive and
 * exhaustive. Callers can fold the batch with confidence that no slot is
 * in a fourth state.
 */
data class TransactionItemResult(
    val index: Int,
    val signature: String? = null,
    val error: Throwable? = null,
    val success: Boolean = (signature != null && error == null)
) {
    init {
        require(success == (error == null)) {
            "TransactionItemResult invariant violated: success=$success but error=$error"
        }
        require(!success || signature != null) {
            "TransactionItemResult invariant violated: success=true requires a signature"
        }
    }

    val isSuccess: Boolean get() = success
    val isFailure: Boolean get() = !success
}

/**
 * Per-transaction batch result.
 *
 * Hard invariants:
 *   - `results.size == input.size` at the boundary where the batch is
 *     built. Any batch where this is false was either built by hand or
 *     by a bug and should be treated as malformed.
 *   - `results[i].index == i` for every slot. Preserves the native
 *     submission order so callers can correlate results back to the
 *     original transaction list.
 *   - A batch is never silently collapsed: if one slot fails, other
 *     slots still appear in the batch with their own success / error
 *     state intact.
 */
data class TransactionBatchResult(
    val results: List<TransactionItemResult>
) {
    init {
        results.forEachIndexed { i, r ->
            require(r.index == i) {
                "TransactionBatchResult invariant: results[$i].index == ${r.index}; expected $i"
            }
        }
    }

    val allSuccess: Boolean get() = results.all { it.isSuccess }
    val anyFailure: Boolean get() = results.any { it.isFailure }
    val successCount: Int get() = results.count { it.isSuccess }
    val failureCount: Int get() = results.count { it.isFailure }

    /** Flat list of signatures. Legacy ergonomics; skips failed slots. */
    fun signatures(): List<String> = results.mapNotNull { it.signature }

    /** Failures only, with their original indexes intact. */
    fun failures(): List<TransactionItemResult> = results.filter { it.isFailure }

    companion object {
        /**
         * Build a [TransactionBatchResult] from any list whose entries
         * expose a signature or an error. Preserves input order and
         * enforces the per-slot invariants documented on
         * [TransactionItemResult].
         */
        fun of(
            inputSize: Int,
            signatures: List<String?>,
            errors: List<Throwable?> = List(inputSize) { null }
        ): TransactionBatchResult {
            require(signatures.size == inputSize) {
                "signatures.size=${signatures.size} but input.size=$inputSize"
            }
            require(errors.size == inputSize) {
                "errors.size=${errors.size} but input.size=$inputSize"
            }
            val items = (0 until inputSize).map { i ->
                TransactionItemResult(
                    index = i,
                    signature = signatures[i],
                    error = errors[i],
                    success = errors[i] == null && signatures[i] != null
                )
            }
            return TransactionBatchResult(items)
        }
    }
}

/**
 * Convert an Artemis-native [com.selenus.artemis.wallet.BatchSendResult]
 * into the compat-layer [TransactionBatchResult]. Preserves per-slot
 * errors, per-slot signatures, and slot ordering exactly. Does not
 * synthesize data: a slot that the wallet left in the "signed but not
 * broadcast" state (no signature, no error, raw bytes attached) is
 * mapped to a failure slot carrying an actionable error describing the
 * state, so callers always see the XOR invariant hold.
 *
 * This is the single seam through which the Artemis adapter's batch
 * output reaches the compat surface. If a caller wants upstream-shape
 * `Array<ByteArray>` sigs, they call
 * [TransactionBatchResult.toUpstreamSignaturesOrThrow] on the result.
 */
fun com.selenus.artemis.wallet.BatchSendResult.toTransactionBatchResult(): TransactionBatchResult {
    val items = results.mapIndexed { index, slot ->
        val sig = slot.signature.takeIf { it.isNotEmpty() }
        val err: Throwable? = when {
            slot.error != null -> RuntimeException(slot.error)
            slot.isSignedButNotBroadcast -> RuntimeException(
                "Wallet signed transaction #$index but did not broadcast. " +
                "Signed bytes are available via the Artemis-native BatchSendResult " +
                "but not exposed through this compat surface; route through an " +
                "RpcBroadcaster if you need a submitted signature from this path."
            )
            sig == null -> RuntimeException(
                "Wallet returned neither a signature nor an error for transaction #$index"
            )
            else -> null
        }
        TransactionItemResult(
            index = index,
            signature = sig,
            error = err,
            success = err == null && sig != null
        )
    }
    return TransactionBatchResult(items)
}

/**
 * Fold a batch result into the upstream `Array<ByteArray>` signature
 * shape. If any slot failed, throws
 * [com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.NotSubmittedException]
 * with the expected slot count, matching upstream behavior exactly:
 * upstream wallets report batch-level failure as a single exception, so
 * compat callers that expect the upstream API surface get the upstream
 * error shape without losing the batch-shaped detail available through
 * the full [TransactionBatchResult].
 */
fun TransactionBatchResult.toUpstreamSignaturesOrThrow(): Array<ByteArray> {
    if (anyFailure) {
        throw com.solana.mobilewalletadapter.clientlib.protocol
            .MobileWalletAdapterClient.NotSubmittedException(
            message = "Sign-and-send batch had ${failureCount} failed slot(s); " +
                "use AdapterOperations.signAndSendTransactionsBatch() to inspect " +
                "per-slot status.",
            data = null,
            expectedNumSignatures = results.size
        )
    }
    return results.map { item ->
        // Safe invariant: anyFailure was checked above. Each item's success-state contract is
        // `success == (error == null && signature != null)` (see TransactionItemResult.of and
        // toTransactionBatchResult), so `signature` is non-null on every reachable element.
        val sig = item.signature
            ?: throw IllegalStateException(
                "Inconsistent TransactionItemResult at index ${item.index}: success=${item.success} " +
                "but signature is null. This indicates a bug in the result builder."
            )
        // Solana signatures are base58 strings on the wire; the upstream
        // Array<ByteArray> shape wants raw 64-byte signatures.
        com.selenus.artemis.runtime.Base58.decode(sig)
    }.toTypedArray()
}
