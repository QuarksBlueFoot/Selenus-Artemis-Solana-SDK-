/*
 * Copyright (c) 2024-2026 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * Solana Actions/Blinks Module Tests - First Android Actions implementation.
 */
package com.selenus.artemis.actions

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for the Solana Actions Client - First complete Android/Kotlin implementation.
 * 
 * These tests verify:
 * 1. Action URL parsing
 * 2. Blink detection
 * 3. Action request building
 * 4. QR code generation
 * 5. Deep link creation
 * 6. Action validation
 */
class ActionsModuleTest {
    
    private lateinit var actions: ActionsClient
    
    @BeforeEach
    fun setup() {
        actions = ActionsClient.create()
    }
    
    // ===========================================
    // Client Initialization Tests
    // ===========================================
    
    @Test
    fun `client creates with default config`() {
        val client = ActionsClient.create()
        assertNotNull(client)
    }
    
    @Test
    fun `client creates with custom config`() {
        val config = ActionsConfig(
            connectTimeoutMs = 5000,
            readTimeoutMs = 15000
        )
        val client = ActionsClient.create(config)
        assertNotNull(client)
    }
    
    // ===========================================
    // Action URL Parsing Tests
    // ===========================================
    
    @Test
    fun `parse solana scheme URL`() {
        val url = "solana:7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"
        val info = actions.parseActionUrl(url)
        
        assertEquals(ActionUrlType.UNKNOWN, info.type) // Plain transfer, not action
    }
    
    @Test
    fun `parse solana-action scheme URL`() {
        val url = "solana-action:https://example.com/api/actions/donate"
        val info = actions.parseActionUrl(url)
        
        assertTrue(info.url.contains("solana-action"))
    }
    
    @Test
    fun `parse direct action URL`() {
        val url = "https://example.com/api/actions/donate"
        val info = actions.parseActionUrl(url)
        
        assertEquals(ActionUrlType.DIRECT_ACTION, info.type)
    }
    
    @Test
    fun `parse blink URL from dial.to`() {
        val url = "https://dial.to/donate/solana-foundation"
        val info = actions.parseActionUrl(url)
        
        assertEquals(ActionUrlType.BLINK, info.type)
    }
    
    // ===========================================
    // Blink Detection Tests
    // ===========================================
    
    @Test
    fun `detect dial.to blink`() {
        assertTrue(actions.isBlink("https://dial.to/donate"))
    }
    
    @Test
    fun `detect blink.to blink`() {
        assertTrue(actions.isBlink("https://blink.to/action"))
    }
    
    @Test
    fun `detect actions.solana.com blink`() {
        assertTrue(actions.isBlink("https://actions.solana.com/donate"))
    }
    
    @Test
    fun `non-blink URL not detected`() {
        assertFalse(actions.isBlink("https://example.com/api/actions/donate"))
    }
    
    // ===========================================
    // QR Code Generation Tests
    // ===========================================
    
    @Test
    fun `generate QR code for action URL`() {
        val actionUrl = "https://example.com/api/actions/donate"
        val qrCode = actions.generateActionQrCode(actionUrl)
        
        assertNotNull(qrCode.data)
        assertTrue(qrCode.data.startsWith("solana-action:"))
        assertEquals("solana-action", qrCode.protocol)
        assertEquals(actionUrl, qrCode.actionUrl)
    }
    
    @Test
    fun `QR code preserves solana-action scheme`() {
        val actionUrl = "solana-action:https://example.com/api/actions/donate"
        val qrCode = actions.generateActionQrCode(actionUrl)
        
        assertEquals(actionUrl, qrCode.data)
    }
    
    // ===========================================
    // Deep Link Tests
    // ===========================================
    
    @Test
    fun `create deep link intent data`() {
        val actionUrl = "https://example.com/api/actions/donate"
        val deepLink = actions.createDeepLinkIntent(actionUrl)
        
        assertTrue(deepLink.uri.startsWith("solana-action:"))
        assertEquals(actionUrl, deepLink.fallbackUrl)
        assertEquals("android.intent.action.VIEW", deepLink.intentAction)
        assertTrue(deepLink.categories.contains("android.intent.category.BROWSABLE"))
        assertTrue(deepLink.schemes.contains("solana-action"))
    }
    
    // ===========================================
    // Action Execute Builder Tests
    // ===========================================
    
    @Test
    fun `action execute builder creates request`() {
        val builder = ActionExecuteBuilder()
        builder.account("7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU")
        builder.input("amount", "1.5")
        builder.input("message", "Test donation")
        
        val request = builder.build()
        
        assertEquals("7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU", request.account)
    }
    
    // ===========================================
    // Action Response Validation Tests
    // ===========================================
    
    @Test
    fun `validate action response structure`() {
        val response = ActionGetResponse(
            icon = "https://example.com/icon.png",
            title = "Donate to Project",
            description = "Support our open source work",
            label = "Donate",
            disabled = false,
            links = null,
            error = null
        )
        
        assertEquals("Donate to Project", response.title)
        assertEquals("Donate", response.label)
        assertFalse(response.disabled ?: false)
    }
    
    @Test
    fun `validate disabled action`() {
        val response = ActionGetResponse(
            icon = "https://example.com/icon.png",
            title = "Sold Out",
            description = "This sale has ended",
            label = "Sold Out",
            disabled = true,
            links = null,
            error = ActionError(message = "Sale ended")
        )
        
        assertTrue(response.disabled ?: false)
        assertNotNull(response.error)
    }
    
    // ===========================================
    // Linked Action Tests
    // ===========================================
    
    @Test
    fun `linked action with parameters`() {
        val linkedAction = LinkedAction(
            href = "https://example.com/api/actions/buy?amount={amount}",
            label = "Buy",
            parameters = listOf(
                ActionParameter(
                    name = "amount",
                    label = "Amount",
                    required = true,
                    type = ParameterType.NUMBER
                )
            )
        )
        
        assertEquals("Buy", linkedAction.label)
        assertEquals(1, linkedAction.parameters?.size)
        assertEquals(ParameterType.NUMBER, linkedAction.parameters?.first()?.type)
    }
    
    // ===========================================
    // Next Action Handling Tests
    // ===========================================
    
    @Test
    fun `next action result complete`() {
        val result = NextActionResult.Complete
        assertTrue(result is NextActionResult.Complete)
    }
    
    @Test
    fun `next action result continue with new action`() {
        val nextAction = ActionGetResponse(
            icon = "https://example.com/icon.png",
            title = "Step 2",
            description = "Continue your action",
            label = "Continue",
            disabled = false,
            links = null,
            error = null
        )
        
        val result = NextActionResult.Continue(nextAction)
        assertTrue(result is NextActionResult.Continue)
        assertEquals("Step 2", (result as NextActionResult.Continue).action.title)
    }
}
