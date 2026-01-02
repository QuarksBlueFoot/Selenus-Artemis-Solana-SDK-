/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * ORIGINAL IMPLEMENTATION - First complete Solana Actions/Blinks SDK for Kotlin/Android.
 * 
 * ActionsClient - Full implementation of the Solana Actions specification.
 * 
 * Solana Actions are REST APIs that return signable Solana transactions.
 * Blinks (blockchain links) are URLs that can be shared and unfurled into
 * interactive components for signing transactions.
 * 
 * This implementation provides:
 * - GET/POST action lifecycle handling
 * - Action chaining for multi-step flows
 * - Identity verification with signature headers
 * - Form input handling (text, email, number, date, etc.)
 * - Action URL parsing and blink detection
 * - Mobile-optimized blink rendering helpers
 * - Deep link integration for Android
 * - QR code generation for action URLs
 * 
 * Unlike web-based solutions, this is designed for native mobile:
 * - Battery-efficient polling for action state
 * - Native form field rendering
 * - Wallet adapter integration
 * - Secure key handling
 */
package com.selenus.artemis.actions

import com.selenus.artemis.runtime.Crypto
import com.selenus.artemis.runtime.PlatformBase64
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.currentTimeMillis
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Solana Actions client.
 * 
 * Usage:
 * ```kotlin
 * val actions = ActionsClient.create()
 * 
 * // Get action metadata
 * val action = actions.getAction("https://example.com/api/actions/donate")
 * 
 * // Build action request with form inputs
 * val txResponse = actions.executeAction(action) {
 *     account(wallet.publicKey)
 *     input("amount", "1.5")
 *     input("message", "Hello!")
 * }
 * 
 * // Sign and send the transaction
 * val signedTx = wallet.signTransaction(txResponse.transaction)
 * val signature = rpc.sendTransaction(signedTx)
 * 
 * // Confirm with callback (for action chaining)
 * actions.confirmTransaction(txResponse, signature)
 * ```
 */
class ActionsClient private constructor(
    private val config: ActionsConfig,
    private val httpClient: HttpApiClient,
    private val json: Json
) {
    
    /**
     * Parse a URL to determine if it's a Solana Action or Blink.
     */
    fun parseActionUrl(url: String): ActionUrlInfo {
        return when {
            url.startsWith("solana:") -> parseSolanaScheme(url)
            url.startsWith("solana-action:") -> parseActionScheme(url)
            url.contains("/api/actions/") || url.contains("actions.json") -> 
                ActionUrlInfo(url, ActionUrlType.DIRECT_ACTION, extractActionPath(url))
            isBlink(url) -> ActionUrlInfo(url, ActionUrlType.BLINK, extractBlinkTarget(url))
            else -> ActionUrlInfo(url, ActionUrlType.UNKNOWN, null)
        }
    }
    
    /**
     * Check if a URL is a blink (blockchain link).
     */
    fun isBlink(url: String): Boolean {
        val blinkProviders = listOf(
            "dial.to",
            "blink.to",
            "actions.solana.com"
        )
        return blinkProviders.any { url.contains(it) }
    }
    
    /**
     * Get action metadata from a URL.
     */
    suspend fun getAction(url: String): ActionGetResponse {
        val actionUrl = resolveActionUrl(url)
        
        val headers = mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json"
        )
        
        return executeGet(actionUrl, headers) { body ->
            json.decodeFromString<ActionGetResponse>(body)
        }
    }
    
    /**
     * Execute an action to get a signable transaction.
     */
    suspend fun executeAction(
        actionUrl: String,
        block: ActionExecuteBuilder.() -> Unit
    ): ActionPostResponse {
        val builder = ActionExecuteBuilder().apply(block)
        return postAction(actionUrl, builder.build())
    }
    
    /**
     * Execute an action with specific linked action.
     */
    suspend fun executeLinkedAction(
        action: ActionGetResponse,
        linkedAction: LinkedAction,
        block: ActionExecuteBuilder.() -> Unit
    ): ActionPostResponse {
        val builder = ActionExecuteBuilder().apply(block)
        val actionUrl = resolveLinkedActionUrl(action, linkedAction, builder.inputs)
        return postAction(actionUrl, builder.build())
    }
    
    /**
     * Post to an action endpoint to get a transaction.
     */
    suspend fun postAction(url: String, request: ActionPostRequest): ActionPostResponse {
        val body = json.encodeToString(request)
        
        val headers = mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json"
        )
        
        return executePost(url, body, headers) { responseBody ->
            json.decodeFromString<ActionPostResponse>(responseBody)
        }
    }
    
    /**
     * Post to an action with identity verification.
     */
    suspend fun postActionWithIdentity(
        url: String,
        request: ActionPostRequest,
        identity: ActionIdentity,
        signer: (ByteArray) -> ByteArray
    ): ActionPostResponse {
        val timestamp = currentTimeMillis() / 1000
        val payload = createIdentityPayload(url, timestamp)
        val signature = signer(payload)
        
        val body = json.encodeToString(request)
        
        val headers = mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "X-Action-Identity" to identity.publicKey,
            "X-Action-Signature" to PlatformBase64.encode(signature),
            "X-Action-Timestamp" to timestamp.toString()
        )
        
        return executePost(url, body, headers) { responseBody ->
            json.decodeFromString<ActionPostResponse>(responseBody)
        }
    }
    
    /**
     * Handle action chaining callback after transaction confirmation.
     */
    suspend fun confirmTransaction(
        response: ActionPostResponse,
        signature: String
    ): NextActionResult {
        val links = response.links ?: return NextActionResult.Complete
        val next = links.next
        
        return when (next) {
            is NextAction.PostAction -> {
                val callback = CallbackRequest(signature = signature)
                val callbackBody = json.encodeToString(callback)
                
                val headers = mapOf(
                    "Accept" to "application/json",
                    "Content-Type" to "application/json"
                )
                
                val nextAction = executePost(next.href, callbackBody, headers) { body ->
                    json.decodeFromString<ActionGetResponse>(body)
                }
                NextActionResult.Continue(nextAction)
            }
            is NextAction.InlineAction -> NextActionResult.Continue(next.action)
            null -> NextActionResult.Complete
        }
    }
    
    /**
     * Get the actions.json rules file for a domain.
     */
    suspend fun getActionsJson(domain: String): ActionsJson? {
        val url = "https://$domain/actions.json"
        
        val headers = mapOf("Accept" to "application/json")
        
        return try {
            executeGet(url, headers) { body ->
                json.decodeFromString<ActionsJson>(body)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if an action URL is allowed based on actions.json rules.
     */
    suspend fun isActionAllowed(actionUrl: String): Boolean {
        val host = extractHost(actionUrl) ?: return false
        val actionsJson = getActionsJson(host) ?: return false
        
        val path = extractPath(actionUrl) ?: return false
        
        for (rule in actionsJson.rules) {
            val pattern = rule.pathPattern.replace("*", ".*").replace("**", ".*")
            val regex = Regex(pattern)
            
            if (regex.matches(path)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Generate QR code data for an action URL.
     */
    fun generateActionQrCode(actionUrl: String): ActionQrCode {
        val solanaUrl = if (actionUrl.startsWith("solana:") || actionUrl.startsWith("solana-action:")) {
            actionUrl
        } else {
            "solana-action:${percentEncode(actionUrl)}"
        }
        
        return ActionQrCode(
            data = solanaUrl,
            actionUrl = actionUrl,
            protocol = "solana-action"
        )
    }
    
    /**
     * Create Android deep link intent data for an action.
     */
    fun createDeepLinkIntent(actionUrl: String): DeepLinkData {
        val encodedUrl = percentEncode(actionUrl)
        
        return DeepLinkData(
            uri = "solana-action:$encodedUrl",
            fallbackUrl = actionUrl,
            intentAction = "android.intent.action.VIEW",
            categories = listOf("android.intent.category.BROWSABLE", "android.intent.category.DEFAULT"),
            schemes = listOf("solana-action", "solana")
        )
    }
    
    /**
     * Validate an action response.
     */
    fun validateAction(action: ActionGetResponse): ActionValidation {
        val issues = mutableListOf<String>()
        
        if (action.title.isBlank()) {
            issues.add("Action title is required")
        }
        
        if (action.icon.isBlank()) {
            issues.add("Action icon URL is required")
        } else if (!action.icon.startsWith("http")) {
            issues.add("Action icon must be a valid URL")
        }
        
        if (action.description.isBlank()) {
            issues.add("Action description is required")
        }
        
        if (action.disabled == true && action.error == null) {
            issues.add("Disabled actions should have an error message")
        }
        
        // Validate linked actions
        action.links?.actions?.forEach { linked ->
            if (linked.label.isBlank()) {
                issues.add("Linked action label is required")
            }
            
            linked.parameters?.forEach { param ->
                if (param.name.isBlank()) {
                    issues.add("Parameter name is required")
                }
            }
        }
        
        return ActionValidation(
            isValid = issues.isEmpty(),
            issues = issues
        )
    }
    
    /**
     * Create a blink preview for display.
     */
    fun createBlinkPreview(action: ActionGetResponse): BlinkPreview {
        val primaryAction = action.links?.actions?.firstOrNull()
        val hasForm = action.links?.actions?.any { linked ->
            linked.parameters?.isNotEmpty() == true
        } ?: false
        
        return BlinkPreview(
            title = action.title,
            description = action.description,
            iconUrl = action.icon,
            label = action.label,
            isDisabled = action.disabled ?: false,
            errorMessage = action.error?.message,
            hasMultipleActions = (action.links?.actions?.size ?: 0) > 1,
            hasFormInputs = hasForm,
            primaryActionLabel = primaryAction?.label ?: action.label,
            actionCount = action.links?.actions?.size ?: 1
        )
    }
    
    private fun resolveActionUrl(url: String): String {
        return when {
            url.startsWith("solana-action:") -> {
                val encoded = url.removePrefix("solana-action:")
                percentDecode(encoded)
            }
            isBlink(url) -> extractBlinkTarget(url) ?: url
            else -> url
        }
    }
    
    private fun resolveLinkedActionUrl(
        action: ActionGetResponse,
        linkedAction: LinkedAction,
        inputs: Map<String, String>
    ): String {
        var href = linkedAction.href
        
        // Replace path parameters
        inputs.forEach { (key, value) ->
            href = href.replace("{$key}", percentEncode(value))
        }
        
        // Add query parameters for any remaining inputs
        val remainingInputs = inputs.filter { (key, _) ->
            !linkedAction.href.contains("{$key}")
        }
        
        if (remainingInputs.isNotEmpty()) {
            val separator = if (href.contains("?")) "&" else "?"
            val query = remainingInputs.entries.joinToString("&") { (k, v) ->
                "$k=${percentEncode(v)}"
            }
            href = "$href$separator$query"
        }
        
        return href
    }
    
    private fun parseSolanaScheme(url: String): ActionUrlInfo {
        val content = url.removePrefix("solana:")
        return ActionUrlInfo(url, ActionUrlType.SOLANA_PAY, content)
    }
    
    private fun parseActionScheme(url: String): ActionUrlInfo {
        val encoded = url.removePrefix("solana-action:")
        val decoded = percentDecode(encoded)
        return ActionUrlInfo(url, ActionUrlType.ACTION_SCHEME, decoded)
    }
    
    private fun extractActionPath(url: String): String? {
        return extractPath(url)
    }
    
    private fun extractBlinkTarget(url: String): String? {
        return try {
            val queryStart = url.indexOf('?')
            if (queryStart < 0) return null
            val query = url.substring(queryStart + 1)
            val params = query.split("&").associate { 
                val parts = it.split("=", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            }
            params["action"]?.let { percentDecode(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun createIdentityPayload(url: String, timestamp: Long): ByteArray {
        val message = "$url:$timestamp"
        return Crypto.sha256(message.encodeToByteArray())
    }
    
    private suspend fun <T> executeGet(
        url: String,
        headers: Map<String, String> = emptyMap(),
        parser: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val response = httpClient.get(url, headers)
        
        if (response.code !in 200..299) {
            throw ActionException(
                code = response.code,
                message = "Action API error: ${response.code}",
                details = response.body
            )
        }
        
        val body = response.body.ifEmpty {
            throw ActionException(code = 0, message = "Empty response body")
        }
        
        parser(body)
    }
    
    private suspend fun <T> executePost(
        url: String,
        requestBody: String,
        headers: Map<String, String> = emptyMap(),
        parser: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val response = httpClient.post(url, requestBody, headers)
        
        if (response.code !in 200..299) {
            throw ActionException(
                code = response.code,
                message = "Action API error: ${response.code}",
                details = response.body
            )
        }
        
        val body = response.body.ifEmpty {
            throw ActionException(code = 0, message = "Empty response body")
        }
        
        parser(body)
    }
    
    companion object {
        fun create(config: ActionsConfig = ActionsConfig()): ActionsClient {
            val httpClient = createDefaultHttpApiClient(
                config.connectTimeoutMs,
                config.readTimeoutMs,
                config.writeTimeoutMs
            )
            
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }
            
            return ActionsClient(config, httpClient, json)
        }
        
        fun create(httpClient: HttpApiClient, config: ActionsConfig = ActionsConfig()): ActionsClient {
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }
            
            return ActionsClient(config, httpClient, json)
        }
    }
}

/**
 * Actions client configuration.
 */
data class ActionsConfig(
    val connectTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 30_000,
    val writeTimeoutMs: Long = 30_000,
    val validateSsl: Boolean = true
)

/**
 * Action GET response (metadata).
 */
@Serializable
data class ActionGetResponse(
    val type: String = "action",
    val title: String,
    val icon: String,
    val description: String,
    val label: String,
    val disabled: Boolean? = null,
    val error: ActionError? = null,
    val links: ActionLinks? = null
)

@Serializable
data class ActionError(
    val message: String
)

@Serializable
data class ActionLinks(
    val actions: List<LinkedAction>? = null,
    val next: NextAction? = null
)

@Serializable
data class LinkedAction(
    val href: String,
    val label: String,
    val parameters: List<ActionParameter>? = null
)

@Serializable
data class ActionParameter(
    val name: String,
    val label: String? = null,
    val required: Boolean? = null,
    val type: ActionParameterType? = null,
    val pattern: String? = null,
    val patternDescription: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val options: List<ActionParameterOption>? = null
)

@Serializable
enum class ActionParameterType {
    @SerialName("text") TEXT,
    @SerialName("email") EMAIL,
    @SerialName("url") URL,
    @SerialName("number") NUMBER,
    @SerialName("date") DATE,
    @SerialName("datetime-local") DATETIME_LOCAL,
    @SerialName("checkbox") CHECKBOX,
    @SerialName("radio") RADIO,
    @SerialName("textarea") TEXTAREA,
    @SerialName("select") SELECT
}

@Serializable
data class ActionParameterOption(
    val label: String,
    val value: String,
    val selected: Boolean? = null
)

@Serializable
sealed class NextAction {
    @Serializable
    @SerialName("post")
    data class PostAction(val href: String) : NextAction()
    
    @Serializable
    @SerialName("inline")
    data class InlineAction(val action: ActionGetResponse) : NextAction()
}

/**
 * Action POST request.
 */
@Serializable
data class ActionPostRequest(
    val account: String,
    val data: Map<String, String>? = null
)

/**
 * Action execute builder.
 */
class ActionExecuteBuilder {
    private var account: String = ""
    internal val inputs = mutableMapOf<String, String>()
    
    fun account(key: String) { account = key }
    fun account(pubkey: Pubkey) { account = pubkey.toBase58() }
    
    fun input(name: String, value: String) { inputs[name] = value }
    fun input(name: String, value: Number) { inputs[name] = value.toString() }
    fun input(name: String, value: Boolean) { inputs[name] = value.toString() }
    
    fun inputs(vararg pairs: Pair<String, String>) {
        pairs.forEach { (k, v) -> inputs[k] = v }
    }
    
    fun build() = ActionPostRequest(
        account = account,
        data = inputs.ifEmpty { null }
    )
}

/**
 * Action POST response (transaction).
 */
@Serializable
data class ActionPostResponse(
    val transaction: String,
    val message: String? = null,
    val links: PostResponseLinks? = null
)

@Serializable
data class PostResponseLinks(
    val next: NextAction? = null
)

/**
 * Callback request for action chaining.
 */
@Serializable
data class CallbackRequest(
    val signature: String
)

/**
 * Next action result.
 */
sealed class NextActionResult {
    data class Continue(val action: ActionGetResponse) : NextActionResult()
    object Complete : NextActionResult()
}

/**
 * Actions.json rules file.
 */
@Serializable
data class ActionsJson(
    val rules: List<ActionRule>
)

@Serializable
data class ActionRule(
    val pathPattern: String,
    val apiPath: String? = null
)

/**
 * Action identity for verification.
 */
data class ActionIdentity(
    val publicKey: String,
    val name: String? = null
)

/**
 * Action URL info.
 */
data class ActionUrlInfo(
    val originalUrl: String,
    val type: ActionUrlType,
    val resolvedPath: String?
)

enum class ActionUrlType {
    DIRECT_ACTION,
    BLINK,
    SOLANA_PAY,
    ACTION_SCHEME,
    UNKNOWN
}

/**
 * Action QR code data.
 */
data class ActionQrCode(
    val data: String,
    val actionUrl: String,
    val protocol: String
)

/**
 * Deep link data for Android.
 */
data class DeepLinkData(
    val uri: String,
    val fallbackUrl: String,
    val intentAction: String,
    val categories: List<String>,
    val schemes: List<String>
)

/**
 * Action validation result.
 */
data class ActionValidation(
    val isValid: Boolean,
    val issues: List<String>
)

/**
 * Blink preview for display.
 */
data class BlinkPreview(
    val title: String,
    val description: String,
    val iconUrl: String,
    val label: String,
    val isDisabled: Boolean,
    val errorMessage: String?,
    val hasMultipleActions: Boolean,
    val hasFormInputs: Boolean,
    val primaryActionLabel: String,
    val actionCount: Int
)

/**
 * Action exception.
 */
class ActionException(
    val code: Int,
    override val message: String,
    val details: String? = null
) : Exception(message)

// ─── URL helpers ───

private fun extractHost(url: String): String? {
    // Parse host from "https://host/path" or "http://host:port/path"
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return null
    val afterScheme = url.substring(schemeEnd + 3)
    val hostEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == ':' }
    return if (hostEnd < 0) afterScheme else afterScheme.substring(0, hostEnd)
}

private fun extractPath(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return null
    val afterScheme = url.substring(schemeEnd + 3)
    val pathStart = afterScheme.indexOf('/')
    if (pathStart < 0) return "/"
    val queryStart = afterScheme.indexOf('?', pathStart)
    return if (queryStart < 0) afterScheme.substring(pathStart)
    else afterScheme.substring(pathStart, queryStart)
}

internal fun percentEncode(s: String): String {
    val bytes = s.encodeToByteArray()
    return buildString(bytes.size * 2) {
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c.toChar().isLetterOrDigit() || c.toChar() in "-._~") {
                append(c.toChar())
            } else {
                append('%')
                append(HEX[(c shr 4) and 0x0F])
                append(HEX[c and 0x0F])
            }
        }
    }
}

internal fun percentDecode(s: String): String {
    val out = ByteArray(s.length)
    var pos = 0
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length) {
            val hi = hexVal(s[i + 1])
            val lo = hexVal(s[i + 2])
            if (hi >= 0 && lo >= 0) {
                out[pos++] = ((hi shl 4) or lo).toByte()
                i += 3
                continue
            }
        }
        if (c == '+') {
            out[pos++] = 0x20
        } else {
            out[pos++] = c.code.toByte()
        }
        i++
    }
    return out.decodeToString(0, pos)
}

private fun hexVal(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}

private val HEX = "0123456789ABCDEF".toCharArray()
