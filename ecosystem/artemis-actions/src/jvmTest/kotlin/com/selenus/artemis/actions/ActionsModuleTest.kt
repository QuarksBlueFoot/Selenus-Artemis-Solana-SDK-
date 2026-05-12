/*
 * Copyright (c) 2024-2026 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.selenus.artemis.actions

import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking

/**
 * Tests for the Solana Actions Client.
 */
class ActionsModuleTest {
    
    private lateinit var actions: ActionsClient
    
    @BeforeTest
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
        
        assertEquals(ActionUrlType.SOLANA_PAY, info.type) // solana: scheme is Solana Pay
    }
    
    @Test
    fun `parse solana-action scheme URL`() {
        val url = "solana-action:https://example.com/api/actions/donate"
        val info = actions.parseActionUrl(url)
        
        assertTrue(info.originalUrl.contains("solana-action"))
    }
    
    @Test
    fun `parse direct action URL`() {
        val url = "https://example.com/api/actions/donate"
        val info = actions.parseActionUrl(url)
        
        assertEquals(ActionUrlType.DIRECT_ACTION, info.type)
    }
    
    @Test
    fun `parse blink URL from dial-to`() {
        val url = "https://dial.to/donate/solana-foundation"
        val info = actions.parseActionUrl(url)
        
        assertEquals(ActionUrlType.BLINK, info.type)
    }
    
    // ===========================================
    // Blink Detection Tests
    // ===========================================
    
    @Test
    fun `detect dial-to blink`() {
        assertTrue(actions.isBlink("https://dial.to/donate"))
    }
    
    @Test
    fun `detect blink-to blink`() {
        assertTrue(actions.isBlink("https://blink.to/action"))
    }
    
    @Test
    fun `detect actions-solana-com blink`() {
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
                    type = ActionParameterType.NUMBER
                )
            )
        )
        
        assertEquals("Buy", linkedAction.label)
        assertEquals(1, linkedAction.parameters?.size)
        assertEquals(ActionParameterType.NUMBER, linkedAction.parameters?.first()?.type)
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

    @Test
    fun `confirmTransaction returns terminal completed result for inline completed action`() = runBlocking {
        val completed = ActionGetResponse(
            type = "completed",
            icon = "https://example.com/done.png",
            title = "Done",
            description = "The action chain is complete",
            label = "Done"
        )
        val post = ActionPostResponse(
            transaction = "tx",
            links = PostResponseLinks(
                next = NextAction.InlineAction(completed)
            )
        )

        val result = actions.confirmTransaction(post, signature = "sig")

        assertTrue(result is NextActionResult.Completed)
        assertEquals("Done", result.action.title)
    }

    @Test
    fun `confirmTransaction returns continue for inline executable action`() = runBlocking {
        val next = ActionGetResponse(
            type = "action",
            icon = "https://example.com/next.png",
            title = "Next Step",
            description = "Continue the chain",
            label = "Continue"
        )
        val post = ActionPostResponse(
            transaction = "tx",
            links = PostResponseLinks(
                next = NextAction.InlineAction(next)
            )
        )

        val result = actions.confirmTransaction(post, signature = "sig")

        assertTrue(result is NextActionResult.Continue)
        assertEquals("Next Step", result.action.title)
    }

    @Test
    fun `confirmTransaction callback posts signature and account then decodes completed action`() = runBlocking {
        val http = RecordingHttpApiClient(
            postResponse = """
                {
                  "type": "completed",
                  "icon": "https://example.com/done.png",
                  "title": "Receipt Ready",
                  "description": "Confirmed on chain",
                  "label": "Close"
                }
            """.trimIndent()
        )
        val client = ActionsClient.create(http)
        val post = ActionPostResponse(
            transaction = "tx",
            links = PostResponseLinks(
                next = NextAction.PostAction("https://example.com/api/actions/callback")
            )
        )

        val result = client.confirmTransaction(
            response = post,
            signature = "5ig",
            account = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"
        )

        assertTrue(result is NextActionResult.Completed)
        assertEquals("https://example.com/api/actions/callback", http.lastPostUrl)
        assertTrue(http.lastPostBody!!.contains("\"signature\":\"5ig\""))
        assertTrue(http.lastPostBody!!.contains("\"account\":\"7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU\""))
    }

    @Test
    fun `confirmTransaction callback omits account when unavailable`() = runBlocking {
        val http = RecordingHttpApiClient(
            postResponse = """
                {
                  "type": "action",
                  "icon": "https://example.com/next.png",
                  "title": "Continue",
                  "description": "Next action",
                  "label": "Continue"
                }
            """.trimIndent()
        )
        val client = ActionsClient.create(http)
        val post = ActionPostResponse(
            transaction = "tx",
            links = PostResponseLinks(
                next = NextAction.PostAction("https://example.com/api/actions/callback")
            )
        )

        val result = client.confirmTransaction(post, signature = "5ig")

        assertTrue(result is NextActionResult.Continue)
        assertFalse(http.lastPostBody!!.contains("account"))
    }

    @Test
    fun `resolveActionApiUrl maps cross-origin actions json wildcard and preserves query`() = runBlocking {
        val http = RecordingHttpApiClient(
            getResponses = mapOf(
                "https://example.com/actions.json" to """
                    {
                      "rules": [
                        {
                          "pathPattern": "/donate/*",
                          "apiPath": "https://api.example.net/v1/donate/*"
                        }
                      ]
                    }
                """.trimIndent()
            ),
            postResponse = "{}"
        )
        val client = ActionsClient.create(http)

        val resolved = client.resolveActionApiUrl("https://example.com/donate/alice?ref=mobile")

        assertEquals("https://api.example.net/v1/donate/alice?ref=mobile", resolved)
        assertTrue(client.isActionAllowed("https://example.com/donate/alice?ref=mobile"))
    }

    @Test
    fun `getAction follows actions json delegation for website urls`() = runBlocking {
        val http = RecordingHttpApiClient(
            getResponses = mapOf(
                "https://example.com/actions.json" to """
                    {
                      "rules": [
                        {
                          "pathPattern": "/swap/**",
                          "apiPath": "/api/actions/swap/**"
                        }
                      ]
                    }
                """.trimIndent(),
                "https://example.com/api/actions/swap/SOL/USDC?amount=1" to """
                    {
                      "type": "action",
                      "icon": "https://example.com/icon.png",
                      "title": "Swap",
                      "description": "Swap SOL to USDC",
                      "label": "Swap"
                    }
                """.trimIndent()
            ),
            postResponse = "{}"
        )
        val client = ActionsClient.create(http)

        val action = client.getAction("https://example.com/swap/SOL/USDC?amount=1")

        assertEquals("Swap", action.title)
        assertEquals("https://example.com/api/actions/swap/SOL/USDC?amount=1", http.lastGetUrl)
    }

    private class RecordingHttpApiClient(
        private val getResponse: String = "{}",
        private val getResponses: Map<String, String> = emptyMap(),
        private val postResponse: String
    ) : HttpApiClient {
        var lastGetUrl: String? = null
            private set
        var lastPostUrl: String? = null
            private set
        var lastPostBody: String? = null
            private set

        override fun get(url: String, headers: Map<String, String>): HttpApiClient.Response {
            lastGetUrl = url
            return HttpApiClient.Response(200, getResponses[url] ?: getResponse)
        }

        override fun post(
            url: String,
            body: String,
            headers: Map<String, String>
        ): HttpApiClient.Response {
            lastPostUrl = url
            lastPostBody = body
            return HttpApiClient.Response(200, postResponse)
        }
    }
}
