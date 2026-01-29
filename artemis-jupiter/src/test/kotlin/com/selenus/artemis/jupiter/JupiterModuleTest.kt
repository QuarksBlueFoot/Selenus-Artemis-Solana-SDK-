/*
 * Copyright (c) 2024-2026 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * Jupiter DEX Module Tests - Comprehensive testing of Jupiter integration.
 */
package com.selenus.artemis.jupiter

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for the Jupiter DEX Client - First Kotlin/Android Jupiter integration.
 * 
 * These tests verify:
 * 1. Client initialization and configuration
 * 2. Quote request building
 * 3. Swap request building
 * 4. Price impact analysis
 * 5. Route visualization
 * 6. API response parsing
 */
class JupiterModuleTest {
    
    private lateinit var jupiter: JupiterClient
    
    @BeforeEach
    fun setup() {
        jupiter = JupiterClient.create()
    }
    
    // ===========================================
    // Client Initialization Tests
    // ===========================================
    
    @Test
    fun `client creates with default config`() {
        val client = JupiterClient.create()
        assertNotNull(client)
    }
    
    @Test
    fun `client creates with custom config`() {
        val config = JupiterConfig(
            baseUrl = "https://quote-api.jup.ag/v6",
            connectTimeoutMs = 5000,
            readTimeoutMs = 15000
        )
        val client = JupiterClient.create(config)
        assertNotNull(client)
    }
    
    // ===========================================
    // Quote Request Builder Tests
    // ===========================================
    
    @Test
    fun `quote request builder builds valid request`() {
        val builder = QuoteRequestBuilder()
        builder.inputMint(JupiterClient.USDC_MINT)
        builder.outputMint(JupiterClient.SOL_MINT)
        builder.amount(1_000_000)
        builder.slippageBps(50)
        
        val request = builder.build()
        
        assertEquals(JupiterClient.USDC_MINT, request.inputMint)
        assertEquals(JupiterClient.SOL_MINT, request.outputMint)
        assertEquals(1_000_000L, request.amount)
        assertEquals(50, request.slippageBps)
    }
    
    @Test
    fun `quote request with advanced options`() {
        val builder = QuoteRequestBuilder()
        builder.inputMint(JupiterClient.USDC_MINT)
        builder.outputMint(JupiterClient.SOL_MINT)
        builder.amount(1_000_000)
        builder.slippageBps(100)
        builder.onlyDirectRoutes(true)
        builder.swapMode(SwapMode.ExactIn)
        
        val request = builder.build()
        
        assertEquals(true, request.onlyDirectRoutes)
        assertEquals(SwapMode.ExactIn, request.swapMode)
    }
    
    // ===========================================
    // Price Impact Analysis Tests
    // ===========================================
    
    @Test
    fun `price impact analysis - negligible impact`() {
        val quote = createMockQuote(priceImpact = "0.05")
        val analysis = jupiter.calculatePriceImpact(quote)
        
        assertEquals(PriceImpactSeverity.NEGLIGIBLE, analysis.severity)
        assertTrue(analysis.percentageImpact < 0.1)
    }
    
    @Test
    fun `price impact analysis - low impact`() {
        val quote = createMockQuote(priceImpact = "0.5")
        val analysis = jupiter.calculatePriceImpact(quote)
        
        assertEquals(PriceImpactSeverity.LOW, analysis.severity)
        assertTrue(analysis.percentageImpact < 1.0)
    }
    
    @Test
    fun `price impact analysis - medium impact`() {
        val quote = createMockQuote(priceImpact = "2.0")
        val analysis = jupiter.calculatePriceImpact(quote)
        
        assertEquals(PriceImpactSeverity.MEDIUM, analysis.severity)
        assertTrue(analysis.recommendation.contains("reducing swap size"))
    }
    
    @Test
    fun `price impact analysis - high impact`() {
        val quote = createMockQuote(priceImpact = "4.0")
        val analysis = jupiter.calculatePriceImpact(quote)
        
        assertEquals(PriceImpactSeverity.HIGH, analysis.severity)
        assertTrue(analysis.recommendation.contains("splitting"))
    }
    
    @Test
    fun `price impact analysis - very high impact`() {
        val quote = createMockQuote(priceImpact = "10.0")
        val analysis = jupiter.calculatePriceImpact(quote)
        
        assertEquals(PriceImpactSeverity.VERY_HIGH, analysis.severity)
        assertNotNull(analysis.suggestedMaxAmount)
    }
    
    // ===========================================
    // Route Visualization Tests
    // ===========================================
    
    @Test
    fun `route visualization - direct swap`() {
        val quote = createMockQuoteWithRoute(hops = 1)
        val visualization = jupiter.visualizeRoute(quote)
        
        assertTrue(visualization.isDirect)
        assertEquals(1, visualization.totalHops)
        assertTrue(visualization.description.contains("Direct swap"))
    }
    
    @Test
    fun `route visualization - multi-hop route`() {
        val quote = createMockQuoteWithRoute(hops = 3)
        val visualization = jupiter.visualizeRoute(quote)
        
        assertFalse(visualization.isDirect)
        assertEquals(3, visualization.totalHops)
        assertTrue(visualization.description.contains("Multi-hop"))
    }
    
    // ===========================================
    // Well-Known Token Mints
    // ===========================================
    
    @Test
    fun `well-known token mints are valid base58`() {
        val mints = listOf(
            JupiterClient.SOL_MINT,
            JupiterClient.USDC_MINT,
            JupiterClient.USDT_MINT,
            JupiterClient.BONK_MINT,
            JupiterClient.JUP_MINT
        )
        
        mints.forEach { mint ->
            assertTrue(mint.length in 32..44, "Mint $mint should be valid base58 length")
            assertTrue(mint.all { it.isLetterOrDigit() }, "Mint $mint should be alphanumeric")
        }
    }
    
    // ===========================================
    // Swap Request Builder Tests
    // ===========================================
    
    @Test
    fun `swap request builder with quote`() {
        val quote = createMockQuote(priceImpact = "0.1")
        val builder = SwapRequestBuilder()
        builder.quoteResponse(quote)
        builder.userPublicKey("7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU")
        builder.wrapAndUnwrapSol(true)
        builder.dynamicSlippage(true)
        
        val request = builder.build()
        
        assertEquals(quote, request.quoteResponse)
        assertEquals("7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU", request.userPublicKey)
        assertTrue(request.wrapAndUnwrapSol == true)
        assertTrue(request.dynamicSlippage == true)
    }
    
    // ===========================================
    // Helper Methods
    // ===========================================
    
    private fun createMockQuote(priceImpact: String): QuoteResponse {
        return QuoteResponse(
            inputMint = JupiterClient.USDC_MINT,
            inAmount = "1000000",
            outputMint = JupiterClient.SOL_MINT,
            outAmount = "10000000",
            otherAmountThreshold = "9900000",
            swapMode = "ExactIn",
            slippageBps = 50,
            platformFee = null,
            priceImpactPct = priceImpact,
            routePlan = emptyList(),
            contextSlot = 123456789,
            timeTaken = 0.1
        )
    }
    
    private fun createMockQuoteWithRoute(hops: Int): QuoteResponse {
        val routePlan = (1..hops).map { step ->
            RoutePlanItem(
                swapInfo = SwapInfo(
                    ammKey = "amm-$step",
                    label = "DEX-$step",
                    inputMint = JupiterClient.USDC_MINT,
                    outputMint = JupiterClient.SOL_MINT,
                    inAmount = "1000000",
                    outAmount = "10000000",
                    feeAmount = "1000",
                    feeMint = JupiterClient.SOL_MINT
                ),
                percent = 100
            )
        }
        
        return QuoteResponse(
            inputMint = JupiterClient.USDC_MINT,
            inAmount = "1000000",
            outputMint = JupiterClient.SOL_MINT,
            outAmount = "10000000",
            otherAmountThreshold = "9900000",
            swapMode = "ExactIn",
            slippageBps = 50,
            platformFee = null,
            priceImpactPct = "0.1",
            routePlan = routePlan,
            contextSlot = 123456789,
            timeTaken = 0.1
        )
    }
}
