/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.solana.mobilewalletadapter.clientlib

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wrapper for launching wallet Activities and receiving results.
 *
 * Matches upstream `com.solana.mobilewalletadapter.clientlib.ActivityResultSender`.
 *
 * Must be created during or before `Activity.onCreate()`.
 */
class ActivityResultSender(private val activity: ComponentActivity) {

    @Volatile
    private var pendingCallback: ((ActivityResult) -> Unit)? = null

    private val launcher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            pendingCallback?.invoke(result)
            pendingCallback = null
        }

    internal val hostActivity: Activity get() = activity

    internal suspend fun startActivityForResult(intent: Intent): ActivityResult =
        suspendCancellableCoroutine { cont ->
            pendingCallback = { result -> cont.resume(result) }
            cont.invokeOnCancellation { pendingCallback = null }
            launcher.launch(intent)
        }
}
