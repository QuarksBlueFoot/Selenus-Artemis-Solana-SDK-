/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * MwaDialogTimeoutHandler - Handles disambiguation dialog timeout (Issue #406)
 * 
 * The Android disambiguation dialog (wallet selector) doesn't automatically
 * dismiss when the WebSocket connection times out, leaving users stuck.
 * This handler monitors the connection and provides timeout callbacks.
 */
package com.selenus.artemis.wallet.mwa

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Result of a wallet connection attempt with timeout handling.
 */
sealed class MwaConnectionResult<T> {
    data class Success<T>(val value: T) : MwaConnectionResult<T>()
    data class Timeout<T>(val elapsed: Long) : MwaConnectionResult<T>()
    data class UserCancelled<T>(val reason: String? = null) : MwaConnectionResult<T>()
    data class Error<T>(val exception: Exception) : MwaConnectionResult<T>()
}

/**
 * Callback interface for dialog timeout events.
 */
interface DialogTimeoutListener {
    /** Called when the timeout threshold is reached */
    fun onTimeout(elapsedMs: Long)
    
    /** Called when connection succeeds before timeout */
    fun onConnected()
    
    /** Called when user explicitly cancels */
    fun onCancelled()
}

/**
 * Handles the disambiguation dialog timeout issue (MWA Issue #406).
 * 
 * When a user taps "Connect Wallet", Android shows a disambiguation dialog
 * if multiple wallets are installed. If the user doesn't select a wallet
 * or the WebSocket times out, the dialog remains visible indefinitely.
 * 
 * This handler:
 * 1. Tracks connection start time
 * 2. Monitors for timeout
 * 3. Provides callbacks to dismiss dialogs and show appropriate UI
 * 
 * Usage:
 * ```kotlin
 * val handler = MwaDialogTimeoutHandler(activity, timeoutMs = 30_000)
 * 
 * handler.withTimeout { 
 *     // Your MWA connect logic
 *     adapter.connect()
 * }.let { result ->
 *     when (result) {
 *         is MwaConnectionResult.Success -> // Handle success
 *         is MwaConnectionResult.Timeout -> showTimeoutMessage()
 *         is MwaConnectionResult.UserCancelled -> // Handle cancel
 *         is MwaConnectionResult.Error -> showError(result.exception)
 *     }
 * }
 * ```
 */
class MwaDialogTimeoutHandler(
    private val activity: Activity,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val warningThresholdMs: Long = WARNING_THRESHOLD_MS
) {
    companion object {
        /** Default timeout matching MWA WebSocket timeout */
        const val DEFAULT_TIMEOUT_MS = 30_000L
        
        /** Threshold to show "taking longer than expected" warning */
        const val WARNING_THRESHOLD_MS = 10_000L
        
        /** Action broadcast when user dismisses wallet selector */
        private const val ACTION_WALLET_SELECTOR_DISMISSED = 
            "com.selenus.artemis.wallet.mwa.WALLET_SELECTOR_DISMISSED"
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isConnecting = AtomicBoolean(false)
    private var startTime: Long = 0
    private var listener: DialogTimeoutListener? = null
    private var warningJob: Job? = null
    
    /**
     * Execute a connection operation with timeout handling.
     * 
     * @param operation The suspend function to execute (typically your connect logic)
     * @return MwaConnectionResult indicating success, timeout, or error
     */
    suspend fun <T> withTimeout(
        operation: suspend () -> T
    ): MwaConnectionResult<T> = coroutineScope {
        isConnecting.set(true)
        startTime = System.currentTimeMillis()
        
        // Start warning timer
        warningJob = launch {
            delay(warningThresholdMs)
            if (isConnecting.get()) {
                listener?.onTimeout(System.currentTimeMillis() - startTime)
            }
        }
        
        try {
            val result = withTimeoutOrNull(timeoutMs) {
                operation()
            }
            
            warningJob?.cancel()
            
            if (result != null) {
                isConnecting.set(false)
                listener?.onConnected()
                MwaConnectionResult.Success(result)
            } else {
                isConnecting.set(false)
                val elapsed = System.currentTimeMillis() - startTime
                listener?.onTimeout(elapsed)
                MwaConnectionResult.Timeout(elapsed)
            }
        } catch (e: CancellationException) {
            warningJob?.cancel()
            isConnecting.set(false)
            listener?.onCancelled()
            MwaConnectionResult.UserCancelled(e.message)
        } catch (e: Exception) {
            warningJob?.cancel()
            isConnecting.set(false)
            MwaConnectionResult.Error(e)
        }
    }
    
    /**
     * Set a listener for timeout events.
     */
    fun setListener(listener: DialogTimeoutListener) {
        this.listener = listener
    }
    
    /**
     * Cancel any ongoing connection attempt.
     */
    fun cancel() {
        isConnecting.set(false)
        warningJob?.cancel()
        listener?.onCancelled()
    }
    
    /**
     * Check if a connection is currently in progress.
     */
    fun isConnecting(): Boolean = isConnecting.get()
    
    /**
     * Get elapsed time since connection started.
     */
    fun getElapsedMs(): Long = 
        if (isConnecting.get()) System.currentTimeMillis() - startTime else 0
}

/**
 * Extension to create timeout handler from Activity.
 */
fun Activity.mwaDialogTimeoutHandler(
    timeoutMs: Long = MwaDialogTimeoutHandler.DEFAULT_TIMEOUT_MS
) = MwaDialogTimeoutHandler(this, timeoutMs)

/**
 * DSL builder for timeout-aware connections.
 */
class MwaTimeoutBuilder(private val handler: MwaDialogTimeoutHandler) {
    private var onTimeoutAction: ((Long) -> Unit)? = null
    private var onConnectedAction: (() -> Unit)? = null
    private var onCancelledAction: (() -> Unit)? = null
    
    fun onTimeout(action: (Long) -> Unit) {
        onTimeoutAction = action
    }
    
    fun onConnected(action: () -> Unit) {
        onConnectedAction = action
    }
    
    fun onCancelled(action: () -> Unit) {
        onCancelledAction = action
    }
    
    internal fun build() {
        handler.setListener(object : DialogTimeoutListener {
            override fun onTimeout(elapsedMs: Long) {
                onTimeoutAction?.invoke(elapsedMs)
            }
            
            override fun onConnected() {
                onConnectedAction?.invoke()
            }
            
            override fun onCancelled() {
                onCancelledAction?.invoke()
            }
        })
    }
}

/**
 * DSL for timeout-aware wallet connections.
 * 
 * Example:
 * ```kotlin
 * activity.connectWithTimeout(adapter) {
 *     onTimeout { elapsed ->
 *         showToast("Connection timed out after ${elapsed}ms")
 *     }
 *     onConnected {
 *         navigateToMain()
 *     }
 *     onCancelled {
 *         showCancelledMessage()
 *     }
 * }
 * ```
 */
suspend fun <T> Activity.connectWithTimeout(
    timeoutMs: Long = MwaDialogTimeoutHandler.DEFAULT_TIMEOUT_MS,
    builderConfig: MwaTimeoutBuilder.() -> Unit = {},
    operation: suspend () -> T
): MwaConnectionResult<T> {
    val handler = mwaDialogTimeoutHandler(timeoutMs)
    val builder = MwaTimeoutBuilder(handler)
    builder.builderConfig()
    builder.build()
    return handler.withTimeout(operation)
}
