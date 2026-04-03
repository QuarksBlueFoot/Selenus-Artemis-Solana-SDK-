/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Drop-in replacement for the Solana Mobile MWA client library.
 * Provides familiar API surface backed by Artemis MWA implementation.
 */
package com.solana.mobilewalletadapter.clientlib

import android.app.Activity
import android.net.Uri
import com.selenus.artemis.wallet.mwa.MwaWalletAdapter
import com.selenus.artemis.wallet.mwa.AuthTokenStore
import com.selenus.artemis.wallet.mwa.InMemoryAuthTokenStore

/**
 * Compatibility bridge for `com.solana.mobilewalletadapter.clientlib`.
 *
 * Wraps Artemis [MwaWalletAdapter] with the familiar Solana Mobile constructor pattern.
 * Existing code using the official MWA client can migrate by swapping the dependency
 * to `artemis-mwa-compat`.
 *
 * Usage:
 * ```kotlin
 * val adapter = MobileWalletAdapterCompat.create(
 *     activity = this,
 *     identityUri = Uri.parse("https://myapp.com"),
 *     iconUri = Uri.parse("https://myapp.com/icon.png"),
 *     identityName = "My App",
 *     cluster = "solana:mainnet"
 * )
 * val pubkey = adapter.connect()
 * ```
 */
object MobileWalletAdapterCompat {
    /**
     * Create a new MWA wallet adapter with the specified identity.
     *
     * @param activity The Activity that will handle wallet intents
     * @param identityUri URI identifying the dapp
     * @param iconUri Absolute HTTPS URI for the dapp icon
     * @param identityName Human-readable dapp name
     * @param cluster Solana cluster chain ID (e.g. "solana:mainnet", "solana:devnet")
     * @param authStore Optional persistent auth token store
     * @return Fully configured [MwaWalletAdapter]
     */
    fun create(
        activity: Activity,
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        cluster: String = "solana:mainnet",
        authStore: AuthTokenStore = InMemoryAuthTokenStore()
    ): MwaWalletAdapter = MwaWalletAdapter(
        activity = activity,
        identityUri = identityUri,
        iconPath = iconUri.toString(),
        identityName = identityName,
        chain = cluster,
        authStore = authStore
    )
}
