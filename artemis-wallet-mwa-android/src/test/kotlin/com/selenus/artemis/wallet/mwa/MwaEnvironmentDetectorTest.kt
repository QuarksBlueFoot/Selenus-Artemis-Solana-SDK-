/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.selenus.artemis.wallet.mwa

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class MwaEnvironmentDetectorTest {
    
    private lateinit var detector: MwaEnvironmentDetector
    
    @BeforeEach
    fun setup() {
        detector = MwaEnvironmentDetector()
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Environment Detection Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `native Android app detection`() {
        val signals = EnvironmentSignals(
            isAndroid = true,
            hasMobileWalletAdapterPackage = true,
            hasWebView = false,
            userAgent = null,
            windowLocation = null,
            isEmbedded = false
        )
        
        val result = detector.detectEnvironment(signals)
        
        assertEquals(MwaEnvironment.NATIVE_ANDROID, result.environment)
        assertEquals(MwaConnectionStrategy.STANDARD_MWA, result.recommendedStrategy)
        assertTrue(result.confidence >= 0.9)
    }
    
    @Test
    fun `PWA detection from user agent and features`() {
        val signals = EnvironmentSignals(
            isAndroid = true,
            hasMobileWalletAdapterPackage = false,
            hasWebView = false,
            userAgent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36",
            windowLocation = "https://myapp.com",
            isEmbedded = false,
            isPwaMode = true
        )
        
        val result = detector.detectEnvironment(signals)
        
        assertEquals(MwaEnvironment.PWA, result.environment)
        assertEquals(MwaConnectionStrategy.DEEP_LINK_FALLBACK, result.recommendedStrategy)
    }
    
    @Test
    fun `WebView detection`() {
        val signals = EnvironmentSignals(
            isAndroid = true,
            hasMobileWalletAdapterPackage = false,
            hasWebView = true,
            userAgent = "Mozilla/5.0 wv",
            windowLocation = null,
            isEmbedded = true
        )
        
        val result = detector.detectEnvironment(signals)
        
        assertEquals(MwaEnvironment.WEBVIEW, result.environment)
        assertEquals(MwaConnectionStrategy.BRIDGE_TO_NATIVE, result.recommendedStrategy)
    }
    
    @Test
    fun `in-app browser detection`() {
        val signals = EnvironmentSignals(
            isAndroid = true,
            hasMobileWalletAdapterPackage = false,
            hasWebView = false,
            userAgent = "Mozilla/5.0 Instagram",
            windowLocation = "https://example.com",
            isEmbedded = true
        )
        
        val result = detector.detectEnvironment(signals)
        
        assertEquals(MwaEnvironment.IN_APP_BROWSER, result.environment)
        assertEquals(MwaConnectionStrategy.EXTERNAL_BROWSER, result.recommendedStrategy)
    }
    
    @Test
    fun `mobile Chrome detection`() {
        val signals = EnvironmentSignals(
            isAndroid = true,
            hasMobileWalletAdapterPackage = false,
            hasWebView = false,
            userAgent = "Mozilla/5.0 Chrome/120.0.0.0 Mobile",
            windowLocation = "https://webapp.com",
            isEmbedded = false
        )
        
        val result = detector.detectEnvironment(signals)
        
        assertEquals(MwaEnvironment.MOBILE_BROWSER, result.environment)
        assertEquals(MwaConnectionStrategy.DEEP_LINK_FALLBACK, result.recommendedStrategy)
    }
    
    @Test
    fun `Firefox detection triggers fallback strategy`() {
        val signals = EnvironmentSignals(
            isAndroid = true,
            hasMobileWalletAdapterPackage = false,
            hasWebView = false,
            userAgent = "Mozilla/5.0 Firefox/121.0",
            windowLocation = "https://dapp.com",
            isEmbedded = false
        )
        
        val result = detector.detectEnvironment(signals)
        
        // Firefox has known MWA issues
        assertEquals(MwaConnectionStrategy.DEEP_LINK_FALLBACK, result.recommendedStrategy)
        assertTrue(result.warnings.any { it.contains("Firefox") })
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Connection Strategy Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `strategy fallback chain for native app`() {
        val env = MwaEnvironment.NATIVE_ANDROID
        
        val strategies = detector.getFallbackStrategies(env)
        
        assertEquals(MwaConnectionStrategy.STANDARD_MWA, strategies.first())
        assertTrue(strategies.size >= 2) // Should have fallbacks
    }
    
    @Test
    fun `strategy fallback chain for PWA`() {
        val env = MwaEnvironment.PWA
        
        val strategies = detector.getFallbackStrategies(env)
        
        assertEquals(MwaConnectionStrategy.DEEP_LINK_FALLBACK, strategies.first())
        assertTrue(strategies.contains(MwaConnectionStrategy.WALLET_STANDARD))
    }
    
    @Test
    fun `strategy fallback chain for in-app browser`() {
        val env = MwaEnvironment.IN_APP_BROWSER
        
        val strategies = detector.getFallbackStrategies(env)
        
        // Should recommend opening external browser
        assertEquals(MwaConnectionStrategy.EXTERNAL_BROWSER, strategies.first())
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Wallet Detection Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `detect Phantom wallet`() {
        val walletPackages = listOf("app.phantom")
        
        val wallets = detector.detectInstalledWallets(walletPackages)
        
        assertEquals(1, wallets.size)
        assertEquals("Phantom", wallets[0].name)
        assertTrue(wallets[0].supportsMwa2)
    }
    
    @Test
    fun `detect multiple wallets`() {
        val walletPackages = listOf(
            "app.phantom",
            "com.solflare.mobile",
            "com.backpack.wallet"
        )
        
        val wallets = detector.detectInstalledWallets(walletPackages)
        
        assertEquals(3, wallets.size)
        assertTrue(wallets.all { it.supportsMwa2 })
    }
    
    @Test
    fun `unknown package is not a wallet`() {
        val packages = listOf("com.some.random.app")
        
        val wallets = detector.detectInstalledWallets(packages)
        
        assertEquals(0, wallets.size)
    }
    
    @Test
    fun `empty package list returns empty wallets`() {
        val wallets = detector.detectInstalledWallets(emptyList())
        
        assertTrue(wallets.isEmpty())
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // User Agent Parsing Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `parse Chrome version from user agent`() {
        val userAgent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
        
        val browserInfo = detector.parseBrowserInfo(userAgent)
        
        assertEquals("Chrome", browserInfo.name)
        assertEquals("120.0.6099.144", browserInfo.version)
        assertTrue(browserInfo.isMobile)
    }
    
    @Test
    fun `parse Firefox version from user agent`() {
        val userAgent = "Mozilla/5.0 (Android 13; Mobile; rv:121.0) Gecko/121.0 Firefox/121.0"
        
        val browserInfo = detector.parseBrowserInfo(userAgent)
        
        assertEquals("Firefox", browserInfo.name)
        assertEquals("121.0", browserInfo.version)
        assertTrue(browserInfo.isMobile)
    }
    
    @Test
    fun `detect WebView from user agent`() {
        val userAgent = "Mozilla/5.0 (Linux; Android 13; wv) AppleWebKit/537.36"
        
        val browserInfo = detector.parseBrowserInfo(userAgent)
        
        assertTrue(browserInfo.isWebView)
    }
    
    @Test
    fun `detect Instagram in-app browser`() {
        val userAgent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Instagram 320.0"
        
        val browserInfo = detector.parseBrowserInfo(userAgent)
        
        assertTrue(browserInfo.isInAppBrowser)
        assertEquals("Instagram", browserInfo.inAppBrowserApp)
    }
    
    @Test
    fun `detect Twitter in-app browser`() {
        val userAgent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Twitter/10.1"
        
        val browserInfo = detector.parseBrowserInfo(userAgent)
        
        assertTrue(browserInfo.isInAppBrowser)
        assertEquals("Twitter", browserInfo.inAppBrowserApp)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `null user agent is handled`() {
        val browserInfo = detector.parseBrowserInfo(null)
        
        assertEquals("Unknown", browserInfo.name)
        assertFalse(browserInfo.isInAppBrowser)
    }
    
    @Test
    fun `empty signals result in unknown environment`() {
        val signals = EnvironmentSignals(
            isAndroid = false,
            hasMobileWalletAdapterPackage = false,
            hasWebView = false,
            userAgent = null,
            windowLocation = null,
            isEmbedded = false
        )
        
        val result = detector.detectEnvironment(signals)
        
        assertEquals(MwaEnvironment.UNKNOWN, result.environment)
        assertTrue(result.confidence < 0.5)
    }
    
    @Test
    fun `iOS detection returns unsupported`() {
        val signals = EnvironmentSignals(
            isAndroid = false,
            hasMobileWalletAdapterPackage = false,
            hasWebView = false,
            userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X)",
            windowLocation = null,
            isEmbedded = false
        )
        
        val result = detector.detectEnvironment(signals)
        
        // MWA is Android-only
        assertEquals(MwaEnvironment.IOS_UNSUPPORTED, result.environment)
        assertEquals(MwaConnectionStrategy.WALLET_STANDARD, result.recommendedStrategy)
    }
}
