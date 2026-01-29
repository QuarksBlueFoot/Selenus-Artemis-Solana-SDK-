/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * MwaEnvironmentDetector - Runtime environment detection for MWA.
 * 
 * Addresses MWA issues with:
 * - WebViews (Issue #1082)
 * - PWAs (Issue #1364)
 * - In-app browsers (Issue #1085)
 * - Firefox mobile (Issue #420)
 * 
 * Provides smart fallback strategies based on detected environment.
 */
package com.selenus.artemis.wallet.mwa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.WebView

/**
 * Environment types for MWA connection strategies.
 */
enum class MwaEnvironment {
    /** Native Android app - full MWA support */
    NATIVE_APP,
    
    /** Progressive Web App - may have session issues */
    PWA,
    
    /** Running inside a WebView - limited support */
    WEBVIEW,
    
    /** In-app browser (e.g., Twitter, Facebook) - often blocked */
    IN_APP_BROWSER,
    
    /** Standard mobile browser */
    MOBILE_BROWSER,
    
    /** Unknown environment */
    UNKNOWN
}

/**
 * Connection strategies based on environment.
 */
sealed class MwaConnectionStrategy {
    /** Use standard MWA protocol */
    data object StandardMwa : MwaConnectionStrategy()
    
    /** Use deep link with custom scheme */
    data class DeepLink(val uri: Uri) : MwaConnectionStrategy()
    
    /** Open in external browser first */
    data class ExternalBrowser(val uri: Uri) : MwaConnectionStrategy()
    
    /** MWA not supported in this environment */
    data class NotSupported(val reason: String, val suggestion: String) : MwaConnectionStrategy()
}

/**
 * Detects the runtime environment and provides appropriate connection strategies.
 * 
 * Usage:
 * ```kotlin
 * val detector = MwaEnvironmentDetector(activity)
 * val env = detector.detect()
 * val strategy = detector.recommendStrategy()
 * 
 * when (strategy) {
 *     is MwaConnectionStrategy.StandardMwa -> connectNormally()
 *     is MwaConnectionStrategy.DeepLink -> openDeepLink(strategy.uri)
 *     is MwaConnectionStrategy.NotSupported -> showError(strategy.reason)
 * }
 * ```
 */
class MwaEnvironmentDetector(private val context: Context) {
    
    companion object {
        // Known in-app browser user agents
        private val IN_APP_BROWSERS = listOf(
            "FBAN", "FBAV",     // Facebook
            "Instagram",        // Instagram
            "Twitter",          // Twitter/X
            "Line/",           // Line
            "Snapchat",        // Snapchat
            "Pinterest",       // Pinterest
            "LinkedIn"         // LinkedIn
        )
        
        // Known MWA-compatible wallets
        private val KNOWN_WALLETS = listOf(
            "app.phantom" to "Phantom",
            "com.solflare.mobile" to "Solflare",
            "com.backpack.android" to "Backpack",
            "com.glow.android" to "Glow",
            "com.ultimate.solanawallet" to "Ultimate"
        )
    }
    
    /**
     * Detect the current runtime environment.
     */
    fun detect(): MwaEnvironment {
        // Check if we're in a WebView
        if (isWebView()) {
            return if (isInAppBrowser()) {
                MwaEnvironment.IN_APP_BROWSER
            } else {
                MwaEnvironment.WEBVIEW
            }
        }
        
        // Check if PWA
        if (isPwa()) {
            return MwaEnvironment.PWA
        }
        
        // Check if mobile browser
        if (isMobileBrowser()) {
            return MwaEnvironment.MOBILE_BROWSER
        }
        
        // Default to native app
        return MwaEnvironment.NATIVE_APP
    }
    
    /**
     * Recommend the best connection strategy for the current environment.
     */
    fun recommendStrategy(): MwaConnectionStrategy {
        return when (detect()) {
            MwaEnvironment.NATIVE_APP -> MwaConnectionStrategy.StandardMwa
            
            MwaEnvironment.PWA -> MwaConnectionStrategy.StandardMwa // With persistence
            
            MwaEnvironment.WEBVIEW -> {
                // WebViews often can't handle MWA - suggest deep link
                MwaConnectionStrategy.DeepLink(
                    Uri.parse("solana-wallet://connect")
                )
            }
            
            MwaEnvironment.IN_APP_BROWSER -> {
                MwaConnectionStrategy.NotSupported(
                    reason = "In-app browsers don't support wallet connections",
                    suggestion = "Please open this page in your default browser (Chrome, Firefox, etc.)"
                )
            }
            
            MwaEnvironment.MOBILE_BROWSER -> MwaConnectionStrategy.StandardMwa
            
            MwaEnvironment.UNKNOWN -> MwaConnectionStrategy.StandardMwa
        }
    }
    
    /**
     * Get list of installed wallet apps.
     */
    fun getInstalledWallets(): List<WalletInfo> {
        val pm = context.packageManager
        return KNOWN_WALLETS.mapNotNull { (pkg, name) ->
            try {
                pm.getPackageInfo(pkg, 0)
                WalletInfo(packageName = pkg, displayName = name, isInstalled = true)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    
    /**
     * Check if any MWA-compatible wallet is installed.
     */
    fun hasCompatibleWallet(): Boolean = getInstalledWallets().isNotEmpty()
    
    /**
     * Get store link for recommended wallet.
     */
    fun getWalletStoreLink(wallet: String = "phantom"): Uri {
        return Uri.parse("https://play.google.com/store/apps/details?id=app.$wallet")
    }
    
    // Detection helpers
    
    private fun isWebView(): Boolean {
        // Check for WebView-specific classes in the call stack
        return try {
            val stackTrace = Thread.currentThread().stackTrace
            stackTrace.any { 
                it.className.contains("WebView") || 
                it.className.contains("chromium") 
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isInAppBrowser(): Boolean {
        // Check user agent if available
        return try {
            val ua = WebView(context).settings.userAgentString
            IN_APP_BROWSERS.any { ua.contains(it, ignoreCase = true) }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isPwa(): Boolean {
        // PWAs typically have specific flags or manifest indicators
        // This is a heuristic based on common patterns
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(context.packageName, 0)
            appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 &&
                context.packageName.contains("pwa", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isMobileBrowser(): Boolean {
        // Check if launched from a browser intent
        return try {
            val intent = (context as? Activity)?.intent
            intent?.action == Intent.ACTION_VIEW &&
                intent.data?.scheme in listOf("http", "https")
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Information about an installed wallet.
 */
data class WalletInfo(
    val packageName: String,
    val displayName: String,
    val isInstalled: Boolean,
    val iconRes: Int? = null
)

/**
 * Extension to create detector from Activity.
 */
fun Activity.mwaEnvironmentDetector() = MwaEnvironmentDetector(this)
