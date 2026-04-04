/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.solana.mobilewalletadapter.clientlib

import android.net.Uri

/**
 * Identifies the dapp to the wallet during MWA authorization.
 *
 * Matches upstream `com.solana.mobilewalletadapter.clientlib.ConnectionIdentity`.
 */
data class ConnectionIdentity(
    val identityUri: Uri,
    val iconUri: Uri,
    val identityName: String
)
