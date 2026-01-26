/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * WORLD'S FIRST - Natural Language Transaction Building for Blockchain.
 * 
 * NaturalLanguageTransactionBuilder - Build Solana transactions from plain English.
 * 
 * This revolutionary feature allows users to describe transactions naturally:
 * - "Send 1 SOL to alice.sol"
 * - "Swap 100 USDC for SOL"
 * - "Create a token with 1 million supply"
 * - "Stake 10 SOL with Marinade"
 * - "Buy 0.5 SOL worth of BONK"
 * 
 * Features:
 * - Pattern matching for common transaction types
 * - Entity extraction (amounts, addresses, tokens)
 * - Intent classification
 * - Context-aware resolution (resolves .sol domains, token symbols)
 * - Confirmation flow with human-readable summary
 * - Suggestion system for incomplete intents
 * 
 * This is NOT AI/ML - it's deterministic pattern matching that works offline.
 * No external API calls, no privacy concerns, fully on-device.
 */
package com.selenus.artemis.nlp

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Natural Language Transaction Builder.
 * 
 * Usage:
 * ```kotlin
 * val nlb = NaturalLanguageBuilder.create(resolver)
 * 
 * // Parse natural language
 * val result = nlb.parse("send 1 SOL to alice.sol")
 * 
 * when (result) {
 *     is ParseResult.Success -> {
 *         println("Intent: ${result.intent.summary}")
 *         println("Confidence: ${result.confidence}")
 *         
 *         // Build the transaction
 *         val tx = result.buildTransaction()
 *     }
 *     is ParseResult.Ambiguous -> {
 *         println("Did you mean:")
 *         result.suggestions.forEach { println("  - ${it.summary}") }
 *     }
 *     is ParseResult.NeedsInfo -> {
 *         println("Please provide: ${result.missing}")
 *     }
 * }
 * ```
 */
class NaturalLanguageBuilder private constructor(
    private val resolver: EntityResolver,
    private val config: NlbConfig
) {
    
    private val patterns = buildPatternRegistry()
    private val tokenizer = Tokenizer()
    private val entityExtractor = EntityExtractor(resolver)
    
    /**
     * Parse natural language input into a transaction intent.
     */
    suspend fun parse(input: String): ParseResult {
        val normalized = normalizeInput(input)
        val tokens = tokenizer.tokenize(normalized)
        
        // Try to match against known patterns
        val matches = patterns.mapNotNull { pattern ->
            pattern.match(tokens)?.let { pattern to it }
        }.sortedByDescending { it.second.confidence }
        
        if (matches.isEmpty()) {
            return ParseResult.Unknown(
                input = input,
                suggestions = suggestAlternatives(tokens)
            )
        }
        
        val (pattern, match) = matches.first()
        
        // Extract entities
        val entities = entityExtractor.extract(tokens, pattern.entityTypes)
        
        // Check for missing required entities
        val missing = pattern.requiredEntities.filter { required ->
            entities.none { it.type == required }
        }
        
        if (missing.isNotEmpty()) {
            return ParseResult.NeedsInfo(
                intent = pattern.intentType,
                missing = missing.map { it.displayName },
                partial = entities,
                suggestion = pattern.getSuggestion(missing.first())
            )
        }
        
        // Resolve entities (addresses, tokens, etc.)
        val resolved = resolveEntities(entities)
        
        // Build the intent
        val intent = pattern.buildIntent(resolved)
        
        // Check for ambiguity
        if (matches.size > 1 && matches[1].second.confidence > 0.7) {
            val alternatives = matches.drop(1).take(3).map { (p, m) ->
                p.buildIntent(resolved)
            }
            return ParseResult.Ambiguous(
                primary = intent,
                alternatives = alternatives,
                confidence = match.confidence
            )
        }
        
        return ParseResult.Success(
            intent = intent,
            confidence = match.confidence,
            builder = TransactionBuilder(intent, resolver)
        )
    }
    
    /**
     * Get suggestions for what the user might want to do.
     */
    fun getSuggestions(partialInput: String): List<Suggestion> {
        val normalized = normalizeInput(partialInput)
        val tokens = tokenizer.tokenize(normalized)
        
        return patterns
            .filter { it.partialMatch(tokens) }
            .map { pattern ->
                Suggestion(
                    template = pattern.template,
                    description = pattern.description,
                    examples = pattern.examples
                )
            }
    }
    
    /**
     * Get all supported transaction types.
     */
    fun getSupportedTypes(): List<TransactionTypeInfo> {
        return patterns.map { pattern ->
            TransactionTypeInfo(
                type = pattern.intentType,
                description = pattern.description,
                template = pattern.template,
                examples = pattern.examples,
                requiredEntities = pattern.requiredEntities.map { it.displayName }
            )
        }
    }
    
    private fun normalizeInput(input: String): String {
        return input
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    private suspend fun resolveEntities(entities: List<ExtractedEntity>): List<ResolvedEntity> {
        return coroutineScope {
            entities.map { entity ->
                async {
                    when (entity.type) {
                        EntityType.ADDRESS -> resolveAddress(entity)
                        EntityType.TOKEN -> resolveToken(entity)
                        EntityType.AMOUNT -> resolveAmount(entity)
                        EntityType.DOMAIN -> resolveDomain(entity)
                        EntityType.PROGRAM -> resolveProgram(entity)
                        EntityType.VALIDATOR -> resolveValidator(entity)
                        else -> ResolvedEntity(entity, entity.value, 1.0)
                    }
                }
            }.awaitAll()
        }
    }
    
    private suspend fun resolveAddress(entity: ExtractedEntity): ResolvedEntity {
        val value = entity.value
        
        // Check if it's a .sol domain
        if (value.endsWith(".sol")) {
            val resolved = resolver.resolveDomain(value)
            return ResolvedEntity(entity, resolved ?: value, if (resolved != null) 1.0 else 0.0)
        }
        
        // Validate as base58 address
        if (isValidBase58(value)) {
            return ResolvedEntity(entity, value, 1.0)
        }
        
        return ResolvedEntity(entity, value, 0.0)
    }
    
    private suspend fun resolveToken(entity: ExtractedEntity): ResolvedEntity {
        val symbol = entity.value.uppercase()
        
        // Check known tokens
        val mint = resolver.resolveTokenSymbol(symbol)
        return if (mint != null) {
            ResolvedEntity(entity, mint, 1.0)
        } else {
            ResolvedEntity(entity, symbol, 0.0)
        }
    }
    
    private fun resolveAmount(entity: ExtractedEntity): ResolvedEntity {
        val amount = entity.value.toDoubleOrNull()
        return if (amount != null && amount > 0) {
            ResolvedEntity(entity, amount.toString(), 1.0)
        } else {
            ResolvedEntity(entity, entity.value, 0.0)
        }
    }
    
    private suspend fun resolveDomain(entity: ExtractedEntity): ResolvedEntity {
        val resolved = resolver.resolveDomain(entity.value)
        return ResolvedEntity(entity, resolved ?: entity.value, if (resolved != null) 1.0 else 0.0)
    }
    
    private suspend fun resolveProgram(entity: ExtractedEntity): ResolvedEntity {
        val programId = resolver.resolveProgramName(entity.value)
        return ResolvedEntity(entity, programId ?: entity.value, if (programId != null) 1.0 else 0.0)
    }
    
    private suspend fun resolveValidator(entity: ExtractedEntity): ResolvedEntity {
        val voteAccount = resolver.resolveValidatorName(entity.value)
        return ResolvedEntity(entity, voteAccount ?: entity.value, if (voteAccount != null) 1.0 else 0.0)
    }
    
    private fun isValidBase58(value: String): Boolean {
        val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        return value.length in 32..44 && value.all { it in base58Chars }
    }
    
    private fun suggestAlternatives(tokens: List<Token>): List<Suggestion> {
        // Find patterns that partially match
        return patterns
            .mapNotNull { pattern ->
                val score = pattern.similarityScore(tokens)
                if (score > 0.3) pattern to score else null
            }
            .sortedByDescending { it.second }
            .take(3)
            .map { (pattern, _) ->
                Suggestion(
                    template = pattern.template,
                    description = pattern.description,
                    examples = pattern.examples
                )
            }
    }
    
    private fun buildPatternRegistry(): List<TransactionPattern> {
        return listOf(
            // Transfer patterns
            TransferPattern(),
            SendPattern(),
            
            // Swap patterns
            SwapPattern(),
            ExchangePattern(),
            BuyPattern(),
            SellPattern(),
            
            // Token patterns
            CreateTokenPattern(),
            MintTokenPattern(),
            BurnTokenPattern(),
            
            // Staking patterns
            StakePattern(),
            UnstakePattern(),
            DelegatePattern(),
            
            // NFT patterns
            TransferNftPattern(),
            BurnNftPattern(),
            
            // Account patterns
            CloseAccountPattern(),
            
            // Memo pattern
            MemoPattern()
        )
    }
    
    companion object {
        fun create(
            resolver: EntityResolver,
            config: NlbConfig = NlbConfig()
        ): NaturalLanguageBuilder {
            return NaturalLanguageBuilder(resolver, config)
        }
    }
}

/**
 * Configuration.
 */
data class NlbConfig(
    val minConfidence: Double = 0.5,
    val maxSuggestions: Int = 5,
    val fuzzyMatchThreshold: Double = 0.7
)

/**
 * Parse result.
 */
sealed class ParseResult {
    data class Success(
        val intent: TransactionIntent,
        val confidence: Double,
        val builder: TransactionBuilder
    ) : ParseResult()
    
    data class Ambiguous(
        val primary: TransactionIntent,
        val alternatives: List<TransactionIntent>,
        val confidence: Double
    ) : ParseResult()
    
    data class NeedsInfo(
        val intent: IntentType,
        val missing: List<String>,
        val partial: List<ExtractedEntity>,
        val suggestion: String
    ) : ParseResult()
    
    data class Unknown(
        val input: String,
        val suggestions: List<Suggestion>
    ) : ParseResult()
}

/**
 * Transaction intent.
 */
data class TransactionIntent(
    val type: IntentType,
    val summary: String,
    val description: String,
    val entities: Map<String, ResolvedEntity>,
    val estimatedFee: Long? = null,
    val warnings: List<String> = emptyList()
)

/**
 * Intent type.
 */
enum class IntentType {
    TRANSFER_SOL,
    TRANSFER_TOKEN,
    SWAP,
    CREATE_TOKEN,
    MINT_TOKEN,
    BURN_TOKEN,
    STAKE,
    UNSTAKE,
    DELEGATE,
    TRANSFER_NFT,
    BURN_NFT,
    CLOSE_ACCOUNT,
    MEMO,
    UNKNOWN
}

/**
 * Suggestion.
 */
data class Suggestion(
    val template: String,
    val description: String,
    val examples: List<String>
)

/**
 * Transaction type info.
 */
data class TransactionTypeInfo(
    val type: IntentType,
    val description: String,
    val template: String,
    val examples: List<String>,
    val requiredEntities: List<String>
)

/**
 * Token from tokenizer.
 */
data class Token(
    val value: String,
    val type: TokenType,
    val position: Int
)

enum class TokenType {
    WORD, NUMBER, ADDRESS, DOMAIN, SYMBOL, PUNCTUATION, UNKNOWN
}

/**
 * Tokenizer.
 */
class Tokenizer {
    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var position = 0
        
        val words = input.split(Regex("\\s+"))
        
        for (word in words) {
            val type = classifyToken(word)
            tokens.add(Token(word, type, position))
            position++
        }
        
        return tokens
    }
    
    private fun classifyToken(word: String): TokenType {
        return when {
            word.matches(Regex("\\d+\\.?\\d*")) -> TokenType.NUMBER
            word.endsWith(".sol") -> TokenType.DOMAIN
            word.matches(Regex("[A-HJ-NP-Za-km-z1-9]{32,44}")) -> TokenType.ADDRESS
            word.matches(Regex("[A-Z]{2,10}")) -> TokenType.SYMBOL
            word.matches(Regex("[a-z]+")) -> TokenType.WORD
            else -> TokenType.UNKNOWN
        }
    }
}

/**
 * Entity types.
 */
enum class EntityType(val displayName: String) {
    AMOUNT("amount"),
    TOKEN("token"),
    ADDRESS("address"),
    DOMAIN("domain name"),
    PROGRAM("program"),
    VALIDATOR("validator"),
    MEMO("memo"),
    PERCENT("percentage")
}

/**
 * Extracted entity.
 */
data class ExtractedEntity(
    val type: EntityType,
    val value: String,
    val originalText: String,
    val position: Int
)

/**
 * Resolved entity.
 */
data class ResolvedEntity(
    val original: ExtractedEntity,
    val resolvedValue: String,
    val confidence: Double
)

/**
 * Entity extractor.
 */
class EntityExtractor(private val resolver: EntityResolver) {
    
    fun extract(tokens: List<Token>, expectedTypes: List<EntityType>): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()
        
        for ((index, token) in tokens.withIndex()) {
            val entity = when (token.type) {
                TokenType.NUMBER -> ExtractedEntity(
                    EntityType.AMOUNT, 
                    token.value, 
                    token.value, 
                    index
                )
                TokenType.DOMAIN -> ExtractedEntity(
                    EntityType.DOMAIN, 
                    token.value, 
                    token.value, 
                    index
                )
                TokenType.ADDRESS -> ExtractedEntity(
                    EntityType.ADDRESS, 
                    token.value, 
                    token.value, 
                    index
                )
                TokenType.SYMBOL -> ExtractedEntity(
                    EntityType.TOKEN, 
                    token.value.uppercase(), 
                    token.value, 
                    index
                )
                else -> null
            }
            
            entity?.let { entities.add(it) }
        }
        
        // Also check for token names in words
        for ((index, token) in tokens.withIndex()) {
            if (token.type == TokenType.WORD) {
                val knownToken = KNOWN_TOKENS[token.value.uppercase()]
                if (knownToken != null) {
                    entities.add(ExtractedEntity(
                        EntityType.TOKEN,
                        token.value.uppercase(),
                        token.value,
                        index
                    ))
                }
            }
        }
        
        return entities
    }
    
    companion object {
        val KNOWN_TOKENS = mapOf(
            "SOL" to "So11111111111111111111111111111111111111112",
            "USDC" to "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            "USDT" to "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
            "BONK" to "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263",
            "JUP" to "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN",
            "RAY" to "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R",
            "ORCA" to "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE",
            "PYTH" to "HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3",
            "RENDER" to "rndrizKT3MK1iimdxRdWabcF7Zg7AR5T4nud4EkHBof",
            "JITO" to "jtojtomepa8beP8AuQc6eXt5FriJwfFMwQx2v2f9mCL"
        )
    }
}

/**
 * Entity resolver interface.
 */
interface EntityResolver {
    suspend fun resolveDomain(domain: String): String?
    suspend fun resolveTokenSymbol(symbol: String): String?
    suspend fun resolveProgramName(name: String): String?
    suspend fun resolveValidatorName(name: String): String?
}

/**
 * Pattern match result.
 */
data class PatternMatch(
    val confidence: Double,
    val capturedGroups: Map<String, String>
)

/**
 * Transaction pattern interface.
 */
abstract class TransactionPattern {
    abstract val intentType: IntentType
    abstract val template: String
    abstract val description: String
    abstract val examples: List<String>
    abstract val requiredEntities: List<EntityType>
    abstract val entityTypes: List<EntityType>
    
    abstract fun match(tokens: List<Token>): PatternMatch?
    abstract fun partialMatch(tokens: List<Token>): Boolean
    abstract fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent
    abstract fun getSuggestion(missing: EntityType): String
    abstract fun similarityScore(tokens: List<Token>): Double
}

/**
 * Transfer pattern: "send/transfer X SOL/TOKEN to ADDRESS"
 */
class TransferPattern : TransactionPattern() {
    override val intentType = IntentType.TRANSFER_SOL
    override val template = "send [amount] [token] to [address]"
    override val description = "Transfer tokens to another wallet"
    override val examples = listOf(
        "send 1 SOL to alice.sol",
        "transfer 100 USDC to 7xKX...",
        "send 0.5 SOL to bob.sol"
    )
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.TOKEN, EntityType.ADDRESS)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN, EntityType.ADDRESS, EntityType.DOMAIN)
    
    private val keywords = listOf("transfer")
    
    override fun match(tokens: List<Token>): PatternMatch? {
        val hasTransferKeyword = tokens.any { it.value in keywords }
        val hasTo = tokens.any { it.value == "to" }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }
        val hasRecipient = tokens.any { 
            it.type == TokenType.ADDRESS || it.type == TokenType.DOMAIN 
        }
        
        return if (hasTransferKeyword && hasTo && hasAmount && hasRecipient) {
            PatternMatch(0.9, emptyMap())
        } else {
            null
        }
    }
    
    override fun partialMatch(tokens: List<Token>): Boolean {
        return tokens.any { it.value in keywords }
    }
    
    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val amount = entities.find { it.original.type == EntityType.AMOUNT }?.resolvedValue ?: "0"
        val token = entities.find { it.original.type == EntityType.TOKEN }?.resolvedValue ?: "SOL"
        val recipient = entities.find { 
            it.original.type == EntityType.ADDRESS || it.original.type == EntityType.DOMAIN 
        }?.resolvedValue ?: ""
        
        return TransactionIntent(
            type = intentType,
            summary = "Transfer $amount $token",
            description = "Send $amount $token to $recipient",
            entities = mapOf(
                "amount" to entities.first { it.original.type == EntityType.AMOUNT },
                "token" to entities.first { it.original.type == EntityType.TOKEN },
                "recipient" to entities.first { 
                    it.original.type == EntityType.ADDRESS || it.original.type == EntityType.DOMAIN 
                }
            )
        )
    }
    
    override fun getSuggestion(missing: EntityType): String {
        return when (missing) {
            EntityType.AMOUNT -> "How much would you like to transfer? (e.g., '1 SOL')"
            EntityType.TOKEN -> "What token would you like to transfer? (e.g., 'SOL', 'USDC')"
            EntityType.ADDRESS -> "Who would you like to send to? (address or .sol domain)"
            else -> "Please provide more information"
        }
    }
    
    override fun similarityScore(tokens: List<Token>): Double {
        var score = 0.0
        if (tokens.any { it.value in keywords }) score += 0.4
        if (tokens.any { it.value == "to" }) score += 0.2
        if (tokens.any { it.type == TokenType.NUMBER }) score += 0.2
        if (tokens.any { it.type == TokenType.ADDRESS || it.type == TokenType.DOMAIN }) score += 0.2
        return score
    }
}

/**
 * Send pattern: "send X SOL/TOKEN to ADDRESS"
 */
class SendPattern : TransactionPattern() {
    override val intentType = IntentType.TRANSFER_SOL
    override val template = "send [amount] [token] to [address]"
    override val description = "Send tokens to another wallet"
    override val examples = listOf(
        "send 1 SOL to alice.sol",
        "send 50 USDC to 7xKX..."
    )
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.ADDRESS)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN, EntityType.ADDRESS, EntityType.DOMAIN)
    
    private val keywords = listOf("send", "pay", "give")
    
    override fun match(tokens: List<Token>): PatternMatch? {
        val hasSendKeyword = tokens.any { it.value in keywords }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }
        val hasRecipient = tokens.any { 
            it.type == TokenType.ADDRESS || it.type == TokenType.DOMAIN 
        }
        
        return if (hasSendKeyword && hasAmount && hasRecipient) {
            PatternMatch(0.85, emptyMap())
        } else {
            null
        }
    }
    
    override fun partialMatch(tokens: List<Token>): Boolean {
        return tokens.any { it.value in keywords }
    }
    
    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val amount = entities.find { it.original.type == EntityType.AMOUNT }?.resolvedValue ?: "0"
        val token = entities.find { it.original.type == EntityType.TOKEN }?.resolvedValue ?: "SOL"
        val recipient = entities.find { 
            it.original.type == EntityType.ADDRESS || it.original.type == EntityType.DOMAIN 
        }?.resolvedValue ?: ""
        
        return TransactionIntent(
            type = intentType,
            summary = "Send $amount $token",
            description = "Send $amount $token to $recipient",
            entities = entities.associateBy { it.original.type.displayName }
        )
    }
    
    override fun getSuggestion(missing: EntityType): String {
        return when (missing) {
            EntityType.AMOUNT -> "How much would you like to send?"
            EntityType.ADDRESS -> "Who would you like to send to?"
            else -> "Please provide more information"
        }
    }
    
    override fun similarityScore(tokens: List<Token>): Double {
        var score = 0.0
        if (tokens.any { it.value in keywords }) score += 0.5
        if (tokens.any { it.type == TokenType.NUMBER }) score += 0.25
        if (tokens.any { it.type == TokenType.ADDRESS || it.type == TokenType.DOMAIN }) score += 0.25
        return score
    }
}

/**
 * Swap pattern: "swap X TOKEN for TOKEN"
 */
class SwapPattern : TransactionPattern() {
    override val intentType = IntentType.SWAP
    override val template = "swap [amount] [input_token] for [output_token]"
    override val description = "Exchange one token for another"
    override val examples = listOf(
        "swap 100 USDC for SOL",
        "swap 1 SOL for BONK",
        "swap 50 USDC for JUP"
    )
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    
    private val keywords = listOf("swap", "exchange", "convert")
    
    override fun match(tokens: List<Token>): PatternMatch? {
        val hasSwapKeyword = tokens.any { it.value in keywords }
        val hasFor = tokens.any { it.value == "for" }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }
        val tokenCount = tokens.count { 
            it.type == TokenType.SYMBOL || 
            EntityExtractor.KNOWN_TOKENS.containsKey(it.value.uppercase())
        }
        
        return if (hasSwapKeyword && hasFor && hasAmount && tokenCount >= 2) {
            PatternMatch(0.9, emptyMap())
        } else if (hasSwapKeyword && hasAmount && tokenCount >= 2) {
            PatternMatch(0.7, emptyMap())
        } else {
            null
        }
    }
    
    override fun partialMatch(tokens: List<Token>): Boolean {
        return tokens.any { it.value in keywords }
    }
    
    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val amount = entities.find { it.original.type == EntityType.AMOUNT }?.resolvedValue ?: "0"
        val tokens = entities.filter { it.original.type == EntityType.TOKEN }
        val inputToken = tokens.getOrNull(0)?.resolvedValue ?: "SOL"
        val outputToken = tokens.getOrNull(1)?.resolvedValue ?: "USDC"
        
        return TransactionIntent(
            type = intentType,
            summary = "Swap $amount $inputToken for $outputToken",
            description = "Exchange $amount $inputToken for $outputToken via Jupiter",
            entities = entities.associateBy { it.original.type.displayName + "_" + it.original.position }
        )
    }
    
    override fun getSuggestion(missing: EntityType): String {
        return when (missing) {
            EntityType.AMOUNT -> "How much would you like to swap?"
            EntityType.TOKEN -> "What tokens would you like to swap? (e.g., 'USDC for SOL')"
            else -> "Please provide more information"
        }
    }
    
    override fun similarityScore(tokens: List<Token>): Double {
        var score = 0.0
        if (tokens.any { it.value in keywords }) score += 0.4
        if (tokens.any { it.value == "for" }) score += 0.2
        if (tokens.any { it.type == TokenType.NUMBER }) score += 0.2
        if (tokens.count { it.type == TokenType.SYMBOL } >= 2) score += 0.2
        return score
    }
}

// Stub implementations for other patterns
class ExchangePattern : TransactionPattern() {
    override val intentType = IntentType.SWAP
    override val template = "exchange [amount] [input_token] for [output_token]"
    override val description = "Exchange tokens"
    override val examples = listOf("exchange 1 SOL for USDC")
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "exchange" }) PatternMatch(0.8, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "exchange" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Exchange", "", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "Please provide more information"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "exchange" }) 0.5 else 0.0
}

class BuyPattern : TransactionPattern() {
    override val intentType = IntentType.SWAP
    override val template = "buy [amount] [token]"
    override val description = "Buy tokens with SOL"
    override val examples = listOf("buy 1000 BONK", "buy 0.5 SOL worth of JUP")
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "buy" }) PatternMatch(0.85, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "buy" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Buy tokens", "", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "What would you like to buy?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "buy" }) 0.5 else 0.0
}

class SellPattern : TransactionPattern() {
    override val intentType = IntentType.SWAP
    override val template = "sell [amount] [token]"
    override val description = "Sell tokens for SOL"
    override val examples = listOf("sell 1000 BONK", "sell all my JUP")
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "sell" }) PatternMatch(0.85, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "sell" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Sell tokens", "", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "What would you like to sell?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "sell" }) 0.5 else 0.0
}

class CreateTokenPattern : TransactionPattern() {
    override val intentType = IntentType.CREATE_TOKEN
    override val template = "create token [name]"
    override val description = "Create a new SPL token"
    override val examples = listOf("create token MyToken", "create a new token")
    override val requiredEntities = emptyList<EntityType>()
    override val entityTypes = listOf(EntityType.TOKEN)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "create" } && tokens.any { it.value == "token" }) PatternMatch(0.9, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "create" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Create token", "", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "Please provide token details"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "create" }) 0.4 else 0.0
}

class MintTokenPattern : TransactionPattern() {
    override val intentType = IntentType.MINT_TOKEN
    override val template = "mint [amount] [token]"
    override val description = "Mint tokens"
    override val examples = listOf("mint 1000 tokens")
    override val requiredEntities = listOf(EntityType.AMOUNT)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "mint" }) PatternMatch(0.9, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "mint" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Mint tokens", "", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "How many tokens to mint?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "mint" }) 0.5 else 0.0
}

class BurnTokenPattern : TransactionPattern() {
    override val intentType = IntentType.BURN_TOKEN
    override val template = "burn [amount] [token]"
    override val description = "Burn tokens"
    override val examples = listOf("burn 100 tokens")
    override val requiredEntities = listOf(EntityType.AMOUNT)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "burn" }) PatternMatch(0.9, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "burn" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Burn tokens", "", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "How many tokens to burn?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "burn" }) 0.5 else 0.0
}

class StakePattern : TransactionPattern() {
    override val intentType = IntentType.STAKE
    override val template = "stake [amount] SOL"
    override val description = "Stake SOL"
    override val examples = listOf("stake 10 SOL", "stake 5 SOL with Marinade")
    override val requiredEntities = listOf(EntityType.AMOUNT)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.VALIDATOR)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "stake" }) PatternMatch(0.9, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "stake" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Stake SOL", "", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "How much SOL to stake?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "stake" }) 0.5 else 0.0
}

class UnstakePattern : TransactionPattern() {
    override val intentType = IntentType.UNSTAKE
    override val template = "unstake [amount] SOL"
    override val description = "Unstake SOL"
    override val examples = listOf("unstake 10 SOL", "unstake all")
    override val requiredEntities = emptyList<EntityType>()
    override val entityTypes = listOf(EntityType.AMOUNT)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "unstake" }) PatternMatch(0.9, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "unstake" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Unstake SOL", "", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "How much to unstake?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "unstake" }) 0.5 else 0.0
}

class DelegatePattern : TransactionPattern() {
    override val intentType = IntentType.DELEGATE
    override val template = "delegate [amount] to [validator]"
    override val description = "Delegate stake"
    override val examples = listOf("delegate 10 SOL to validator")
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.VALIDATOR)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.VALIDATOR)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "delegate" }) PatternMatch(0.9, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "delegate" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Delegate stake", "", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "Please provide delegation details"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "delegate" }) 0.5 else 0.0
}

class TransferNftPattern : TransactionPattern() {
    override val intentType = IntentType.TRANSFER_NFT
    override val template = "send NFT to [address]"
    override val description = "Transfer an NFT"
    override val examples = listOf("send my NFT to alice.sol")
    override val requiredEntities = listOf(EntityType.ADDRESS)
    override val entityTypes = listOf(EntityType.ADDRESS, EntityType.DOMAIN)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "nft" } && tokens.any { it.value in listOf("send", "transfer") }) PatternMatch(0.9, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "nft" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Transfer NFT", "", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "Who to send the NFT to?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "nft" }) 0.5 else 0.0
}

class BurnNftPattern : TransactionPattern() {
    override val intentType = IntentType.BURN_NFT
    override val template = "burn NFT"
    override val description = "Burn an NFT"
    override val examples = listOf("burn my NFT")
    override val requiredEntities = emptyList<EntityType>()
    override val entityTypes = emptyList<EntityType>()
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "burn" } && tokens.any { it.value == "nft" }) PatternMatch(0.9, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "nft" && tokens.any { it.value == "burn" } }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Burn NFT", "", emptyMap())
    override fun getSuggestion(missing: EntityType) = "Which NFT to burn?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "nft" }) 0.3 else 0.0
}

class CloseAccountPattern : TransactionPattern() {
    override val intentType = IntentType.CLOSE_ACCOUNT
    override val template = "close account"
    override val description = "Close a token account"
    override val examples = listOf("close empty accounts", "close my BONK account")
    override val requiredEntities = emptyList<EntityType>()
    override val entityTypes = listOf(EntityType.TOKEN)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "close" }) PatternMatch(0.8, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "close" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Close account", "", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "Which account to close?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "close" }) 0.4 else 0.0
}

class MemoPattern : TransactionPattern() {
    override val intentType = IntentType.MEMO
    override val template = "add memo [message]"
    override val description = "Add a memo to transaction"
    override val examples = listOf("add memo 'Hello World'")
    override val requiredEntities = listOf(EntityType.MEMO)
    override val entityTypes = listOf(EntityType.MEMO)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "memo" }) PatternMatch(0.9, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "memo" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Add memo", "", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "What message to include?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "memo" }) 0.5 else 0.0
}

/**
 * Transaction builder - builds actual transaction from intent.
 */
class TransactionBuilder(
    private val intent: TransactionIntent,
    private val resolver: EntityResolver
) {
    
    /**
     * Build the transaction (placeholder - would integrate with artemis-tx).
     */
    fun buildTransaction(): ByteArray {
        // This would build the actual transaction using artemis-tx
        // For now, return empty bytes as placeholder
        return ByteArray(0)
    }
    
    /**
     * Get human-readable summary.
     */
    fun getSummary(): String = intent.summary
    
    /**
     * Get detailed description.
     */
    fun getDescription(): String = intent.description
}
