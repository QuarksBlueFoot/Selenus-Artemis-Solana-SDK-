/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.solana.mobilewalletadapter.clientlib

/**
 * Result of an MWA `transact` call.
 *
 * Matches upstream `com.solana.mobilewalletadapter.clientlib.TransactionResult`.
 */
sealed class TransactionResult<out T> {

    /**
     * Matches upstream: `authResult` is nullable so call sites that only want
     * to wrap a payload (like `connect` / `signIn`) don't have to synthesise
     * a dummy [AuthorizationResult].
     */
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
