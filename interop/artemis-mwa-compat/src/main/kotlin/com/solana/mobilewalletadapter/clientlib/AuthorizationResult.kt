/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.solana.mobilewalletadapter.clientlib

import android.net.Uri

/**
 * Result of an MWA authorization request.
 *
 * Matches upstream `com.solana.mobilewalletadapter.clientlib.AuthorizationResult`.
 */
data class AuthorizationResult(
    val authToken: String,
    val publicKey: ByteArray,
    val accountLabel: String?,
    val walletUriBase: Uri?,
    val accounts: List<Account> = emptyList()
) {
    data class Account(
        val publicKey: ByteArray,
        val displayAddress: String?,
        val displayAddressFormat: String?,
        val label: String?,
        val chains: List<String>?,
        val features: List<String>?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Account) return false
            return publicKey.contentEquals(other.publicKey) && displayAddress == other.displayAddress
        }

        override fun hashCode(): Int = publicKey.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuthorizationResult) return false
        return authToken == other.authToken && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = 31 * authToken.hashCode() + publicKey.contentHashCode()
}
