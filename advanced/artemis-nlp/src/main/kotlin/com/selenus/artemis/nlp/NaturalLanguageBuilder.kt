/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * NaturalLanguageTransactionBuilder - Build Solana transactions from plain English.
 * 
 * Allows users to describe transactions naturally:
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
        // ADDRESS requirement can be satisfied by ADDRESS, DOMAIN, SKR_KEY, or WALLET_ALIAS
        val missing = pattern.requiredEntities.filter { required ->
            if (required == EntityType.ADDRESS) {
                // Any address-like entity satisfies the ADDRESS requirement
                entities.none { it.type in ADDRESS_LIKE_TYPES }
            } else {
                entities.none { it.type == required }
            }
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
                        EntityType.SKR_KEY -> resolveSkrKey(entity)
                        EntityType.WALLET_ALIAS -> resolveWalletAlias(entity)
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
        
        // Check if it's a .skr key reference
        if (value.endsWith(".skr")) {
            val resolved = resolver.resolveSkrKey(value)
            return ResolvedEntity(entity, resolved ?: value, if (resolved != null) 1.0 else 0.0)
        }
        
        // Validate as base58 address
        if (isValidBase58(value)) {
            return ResolvedEntity(entity, value, 1.0)
        }
        
        // Try wallet alias as fallback
        val aliasResolved = resolver.resolveWalletAlias(value)
        if (aliasResolved != null) {
            return ResolvedEntity(entity, aliasResolved, 0.9)
        }
        
        return ResolvedEntity(entity, value, 0.0)
    }
    
    private suspend fun resolveSkrKey(entity: ExtractedEntity): ResolvedEntity {
        val resolved = resolver.resolveSkrKey(entity.value)
        return if (resolved != null) {
            ResolvedEntity(entity, resolved, 1.0)
        } else {
            ResolvedEntity(entity, entity.value, 0.0)
        }
    }
    
    private suspend fun resolveWalletAlias(entity: ExtractedEntity): ResolvedEntity {
        val resolved = resolver.resolveWalletAlias(entity.value)
        return if (resolved != null) {
            ResolvedEntity(entity, resolved, 1.0)
        } else {
            ResolvedEntity(entity, entity.value, 0.0)
        }
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
            CreateAtaPattern(),
            
            // Authority patterns
            ApprovePattern(),
            RevokePattern(),
            
            // SOL wrapping
            WrapSolPattern(),
            UnwrapSolPattern(),
            
            // Devnet patterns
            AirdropPattern(),
            BalancePattern(),
            
            // Memo pattern
            MemoPattern()
        )
    }
    
    companion object {
        // Entity types that can satisfy an ADDRESS requirement
        val ADDRESS_LIKE_TYPES = setOf(
            EntityType.ADDRESS,
            EntityType.DOMAIN,
            EntityType.SKR_KEY,
            EntityType.WALLET_ALIAS
        )
        
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
 * Intent type - comprehensive list of all supported operations.
 */
enum class IntentType {
    // Transfers
    TRANSFER_SOL,
    TRANSFER_TOKEN,
    
    // Swaps (Jupiter)
    SWAP,
    
    // Token Operations
    CREATE_TOKEN,
    MINT_TOKEN,
    BURN_TOKEN,
    
    // Staking
    STAKE,
    UNSTAKE,
    DELEGATE,
    
    // NFT Operations
    TRANSFER_NFT,
    BURN_NFT,
    
    // Account Management
    CLOSE_ACCOUNT,
    CREATE_ACCOUNT,
    CREATE_ATA,
    
    // Authority Management
    APPROVE_DELEGATE,
    REVOKE_DELEGATE,
    FREEZE_ACCOUNT,
    THAW_ACCOUNT,
    SET_AUTHORITY,
    
    // SOL Wrapping
    WRAP_SOL,
    UNWRAP_SOL,
    
    // Devnet/Testing
    AIRDROP,
    CHECK_BALANCE,
    
    // Utility
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
    WORD, NUMBER, ADDRESS, DOMAIN, SKR_KEY, SYMBOL, PUNCTUATION, UNKNOWN
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
            word.endsWith(".skr") -> TokenType.SKR_KEY
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
    SKR_KEY("seed vault key"),
    WALLET_ALIAS("wallet alias"),
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
                TokenType.SKR_KEY -> ExtractedEntity(
                    EntityType.SKR_KEY, 
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
        
        // Extract words after "to" as potential wallet aliases
        // Only if no ADDRESS, DOMAIN, or SKR_KEY was found after "to"
        val hasExplicitRecipient = entities.any { 
            it.type in listOf(EntityType.ADDRESS, EntityType.DOMAIN, EntityType.SKR_KEY) 
        }
        if (!hasExplicitRecipient && EntityType.WALLET_ALIAS in expectedTypes) {
            val toIndex = tokens.indexOfFirst { it.value == "to" }
            if (toIndex >= 0 && toIndex < tokens.size - 1) {
                val nextToken = tokens[toIndex + 1]
                // Only treat as alias if it's a simple word (not a known token, etc.)
                if (nextToken.type == TokenType.WORD && 
                    KNOWN_TOKENS[nextToken.value.uppercase()] == null) {
                    entities.add(ExtractedEntity(
                        EntityType.WALLET_ALIAS,
                        nextToken.value,
                        nextToken.value,
                        toIndex + 1
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
    
    /**
     * Resolve a .skr (SeedVault key reference) to its public key.
     * Default implementation returns null.
     */
    suspend fun resolveSkrKey(skrRef: String): String? = null
    
    /**
     * Resolve a wallet alias to its address.
     * Default implementation returns null.
     */
    suspend fun resolveWalletAlias(alias: String): String? = null
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
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN, EntityType.ADDRESS, EntityType.DOMAIN, EntityType.SKR_KEY, EntityType.WALLET_ALIAS)
    
    private val keywords = listOf("transfer")
    
    override fun match(tokens: List<Token>): PatternMatch? {
        val hasTransferKeyword = tokens.any { it.value in keywords }
        val hasTo = tokens.any { it.value == "to" }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }
        // Accept any address-like token or word after "to" (potential alias)
        val hasRecipient = hasRecipientToken(tokens)
        
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
            it.original.type in listOf(EntityType.ADDRESS, EntityType.DOMAIN, EntityType.SKR_KEY, EntityType.WALLET_ALIAS)
        }?.resolvedValue ?: ""
        
        return TransactionIntent(
            type = intentType,
            summary = "Transfer $amount $token",
            description = "Send $amount $token to $recipient",
            entities = mapOf(
                "amount" to entities.first { it.original.type == EntityType.AMOUNT },
                "token" to entities.first { it.original.type == EntityType.TOKEN },
                "recipient" to entities.first { 
                    it.original.type in listOf(EntityType.ADDRESS, EntityType.DOMAIN, EntityType.SKR_KEY, EntityType.WALLET_ALIAS)
                }
            )
        )
    }
    
    override fun getSuggestion(missing: EntityType): String {
        return when (missing) {
            EntityType.AMOUNT -> "How much would you like to transfer? (e.g., '1 SOL')"
            EntityType.TOKEN -> "What token would you like to transfer? (e.g., 'SOL', 'USDC')"
            EntityType.ADDRESS -> "Who would you like to send to? (address, .sol domain, .skr key, or alias)"
            else -> "Please provide more information"
        }
    }
    
    override fun similarityScore(tokens: List<Token>): Double {
        var score = 0.0
        if (tokens.any { it.value in keywords }) score += 0.4
        if (tokens.any { it.value == "to" }) score += 0.2
        if (tokens.any { it.type == TokenType.NUMBER }) score += 0.2
        if (hasRecipientToken(tokens)) score += 0.2
        return score
    }
    
    private fun hasRecipientToken(tokens: List<Token>): Boolean {
        // Check for direct address types
        if (tokens.any { it.type in listOf(TokenType.ADDRESS, TokenType.DOMAIN, TokenType.SKR_KEY) }) {
            return true
        }
        // Check for word after "to" (potential wallet alias)
        val toIndex = tokens.indexOfFirst { it.value == "to" }
        if (toIndex >= 0 && toIndex < tokens.size - 1) {
            val nextToken = tokens[toIndex + 1]
            if (nextToken.type == TokenType.WORD) {
                return true // Could be a wallet alias
            }
        }
        return false
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
        "send 50 USDC to 7xKX...",
        "send 1 SOL to mom"
    )
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.ADDRESS)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN, EntityType.ADDRESS, EntityType.DOMAIN, EntityType.SKR_KEY, EntityType.WALLET_ALIAS)
    
    private val keywords = listOf("send", "pay", "give")
    
    override fun match(tokens: List<Token>): PatternMatch? {
        val hasSendKeyword = tokens.any { it.value in keywords }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }
        // Accept any address-like token or word after "to" (potential alias)
        val hasRecipient = hasRecipientToken(tokens)
        
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
            it.original.type in listOf(EntityType.ADDRESS, EntityType.DOMAIN, EntityType.SKR_KEY, EntityType.WALLET_ALIAS)
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
            EntityType.ADDRESS -> "Who would you like to send to? (address, .sol domain, .skr key, or alias)"
            else -> "Please provide more information"
        }
    }
    
    override fun similarityScore(tokens: List<Token>): Double {
        var score = 0.0
        if (tokens.any { it.value in keywords }) score += 0.5
        if (tokens.any { it.type == TokenType.NUMBER }) score += 0.25
        if (hasRecipientToken(tokens)) score += 0.25
        return score
    }
    
    private fun hasRecipientToken(tokens: List<Token>): Boolean {
        // Check for direct address types
        if (tokens.any { it.type in listOf(TokenType.ADDRESS, TokenType.DOMAIN, TokenType.SKR_KEY) }) {
            return true
        }
        // Check for word after "to" (potential wallet alias)
        val toIndex = tokens.indexOfFirst { it.value == "to" }
        if (toIndex >= 0 && toIndex < tokens.size - 1) {
            val nextToken = tokens[toIndex + 1]
            if (nextToken.type == TokenType.WORD) {
                return true // Could be a wallet alias
            }
        }
        return false
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

class ExchangePattern : TransactionPattern() {
    override val intentType = IntentType.SWAP
    override val template = "exchange [amount] [input_token] for [output_token]"
    override val description = "Exchange one token for another"
    override val examples = listOf("exchange 1 SOL for USDC", "exchange 100 USDC for BONK")
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN)

    override fun match(tokens: List<Token>): PatternMatch? {
        val hasExchange = tokens.any { it.value == "exchange" }
        val hasFor = tokens.any { it.value == "for" }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }
        val tokenCount = tokens.count {
            it.type == TokenType.SYMBOL ||
            EntityExtractor.KNOWN_TOKENS.containsKey(it.value.uppercase())
        }

        return when {
            hasExchange && hasFor && hasAmount && tokenCount >= 2 -> PatternMatch(0.9, emptyMap())
            hasExchange && hasAmount && tokenCount >= 1 -> PatternMatch(0.7, emptyMap())
            hasExchange -> PatternMatch(0.5, emptyMap())
            else -> null
        }
    }

    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "exchange" }

    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val amount = entities.find { it.original.type == EntityType.AMOUNT }?.resolvedValue ?: "0"
        val tokens = entities.filter { it.original.type == EntityType.TOKEN }
        val inputToken = tokens.getOrNull(0)?.resolvedValue ?: "SOL"
        val outputToken = tokens.getOrNull(1)?.resolvedValue ?: "USDC"

        return TransactionIntent(
            type = intentType,
            summary = "Exchange $amount $inputToken for $outputToken",
            description = "Exchange $amount $inputToken for $outputToken via Jupiter aggregator",
            entities = entities.associateBy { it.original.type.displayName + "_" + it.original.position }
        )
    }

    override fun getSuggestion(missing: EntityType) = when (missing) {
        EntityType.AMOUNT -> "How much would you like to exchange?"
        EntityType.TOKEN -> "What tokens would you like to exchange? (e.g., 'USDC for SOL')"
        else -> "Please provide more information"
    }

    override fun similarityScore(tokens: List<Token>): Double {
        var score = 0.0
        if (tokens.any { it.value == "exchange" }) score += 0.4
        if (tokens.any { it.value == "for" }) score += 0.2
        if (tokens.any { it.type == TokenType.NUMBER }) score += 0.2
        if (tokens.count { it.type == TokenType.SYMBOL } >= 2) score += 0.2
        return score
    }
}

class BuyPattern : TransactionPattern() {
    override val intentType = IntentType.SWAP
    override val template = "buy [amount] [token]"
    override val description = "Buy tokens with SOL"
    override val examples = listOf("buy 1000 BONK", "buy 0.5 SOL worth of JUP", "buy some USDC")
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN)

    override fun match(tokens: List<Token>): PatternMatch? {
        val hasBuy = tokens.any { it.value in listOf("buy", "purchase", "acquire") }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }
        val hasToken = tokens.any {
            it.type == TokenType.SYMBOL ||
            EntityExtractor.KNOWN_TOKENS.containsKey(it.value.uppercase())
        }

        return when {
            hasBuy && hasAmount && hasToken -> PatternMatch(0.9, emptyMap())
            hasBuy && hasToken -> PatternMatch(0.7, emptyMap())
            hasBuy && hasAmount -> PatternMatch(0.6, emptyMap())
            else -> null
        }
    }

    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value in listOf("buy", "purchase", "acquire") }

    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val amount = entities.find { it.original.type == EntityType.AMOUNT }?.resolvedValue ?: "0"
        val token = entities.find { it.original.type == EntityType.TOKEN }?.resolvedValue ?: "SOL"

        return TransactionIntent(
            type = intentType,
            summary = "Buy $amount $token",
            description = "Buy $amount worth of $token using SOL via Jupiter aggregator",
            entities = entities.associateBy { it.original.type.displayName }
        )
    }

    override fun getSuggestion(missing: EntityType) = when (missing) {
        EntityType.AMOUNT -> "How much would you like to buy? (e.g., '1000 BONK')"
        EntityType.TOKEN -> "What token would you like to buy? (e.g., 'BONK', 'JUP', 'USDC')"
        else -> "Please provide more information"
    }

    override fun similarityScore(tokens: List<Token>): Double {
        var score = 0.0
        if (tokens.any { it.value in listOf("buy", "purchase", "acquire") }) score += 0.5
        if (tokens.any { it.type == TokenType.NUMBER }) score += 0.25
        if (tokens.any { it.type == TokenType.SYMBOL }) score += 0.25
        return score
    }
}

class SellPattern : TransactionPattern() {
    override val intentType = IntentType.SWAP
    override val template = "sell [amount] [token]"
    override val description = "Sell tokens for SOL"
    override val examples = listOf("sell 1000 BONK", "sell all my JUP", "sell 50 USDC for SOL")
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.TOKEN)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN)

    override fun match(tokens: List<Token>): PatternMatch? {
        val hasSell = tokens.any { it.value in listOf("sell", "dump", "liquidate") }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }
        val hasAll = tokens.any { it.value == "all" }
        val hasToken = tokens.any {
            it.type == TokenType.SYMBOL ||
            EntityExtractor.KNOWN_TOKENS.containsKey(it.value.uppercase())
        }

        return when {
            hasSell && (hasAmount || hasAll) && hasToken -> PatternMatch(0.9, emptyMap())
            hasSell && hasToken -> PatternMatch(0.7, emptyMap())
            hasSell && hasAmount -> PatternMatch(0.6, emptyMap())
            else -> null
        }
    }

    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value in listOf("sell", "dump", "liquidate") }

    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val amount = entities.find { it.original.type == EntityType.AMOUNT }?.resolvedValue ?: "all"
        val token = entities.find { it.original.type == EntityType.TOKEN }?.resolvedValue ?: "SOL"

        return TransactionIntent(
            type = intentType,
            summary = "Sell $amount $token",
            description = "Sell $amount $token for SOL via Jupiter aggregator",
            entities = entities.associateBy { it.original.type.displayName }
        )
    }

    override fun getSuggestion(missing: EntityType) = when (missing) {
        EntityType.AMOUNT -> "How much would you like to sell? (e.g., '1000' or 'all')"
        EntityType.TOKEN -> "What token would you like to sell?"
        else -> "Please provide more information"
    }

    override fun similarityScore(tokens: List<Token>): Double {
        var score = 0.0
        if (tokens.any { it.value in listOf("sell", "dump", "liquidate") }) score += 0.5
        if (tokens.any { it.type == TokenType.NUMBER || it.value == "all" }) score += 0.25
        if (tokens.any { it.type == TokenType.SYMBOL }) score += 0.25
        return score
    }
}

class CreateTokenPattern : TransactionPattern() {
    override val intentType = IntentType.CREATE_TOKEN
    override val template = "create token [name]"
    override val description = "Create a new SPL token"
    override val examples = listOf("create token MyToken", "create a new token", "make a token with 9 decimals")
    override val requiredEntities = emptyList<EntityType>()
    override val entityTypes = listOf(EntityType.TOKEN)

    override fun match(tokens: List<Token>): PatternMatch? {
        val hasCreate = tokens.any { it.value in listOf("create", "make", "deploy", "launch") }
        val hasToken = tokens.any { it.value in listOf("token", "coin", "spl") }

        return when {
            hasCreate && hasToken -> PatternMatch(0.9, emptyMap())
            hasCreate -> PatternMatch(0.4, emptyMap())
            else -> null
        }
    }

    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value in listOf("create", "make") }

    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val name = entities.find { it.original.type == EntityType.TOKEN }?.resolvedValue ?: "NewToken"

        return TransactionIntent(
            type = intentType,
            summary = "Create token $name",
            description = "Create a new SPL token mint with default settings (9 decimals, you as mint authority)",
            entities = entities.associateBy { it.original.type.displayName }
        )
    }

    override fun getSuggestion(missing: EntityType) = "What would you like to name your token?"
    override fun similarityScore(tokens: List<Token>): Double {
        var score = 0.0
        if (tokens.any { it.value in listOf("create", "make") }) score += 0.4
        if (tokens.any { it.value in listOf("token", "coin") }) score += 0.4
        return score
    }
}

class MintTokenPattern : TransactionPattern() {
    override val intentType = IntentType.MINT_TOKEN
    override val template = "mint [amount] [token]"
    override val description = "Mint tokens to your wallet"
    override val examples = listOf("mint 1000 tokens", "mint 1000000 MyToken")
    override val requiredEntities = listOf(EntityType.AMOUNT)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN)

    override fun match(tokens: List<Token>): PatternMatch? {
        // Avoid matching "mint" as an address/pubkey context
        val hasMint = tokens.any { it.value == "mint" && it.type == TokenType.WORD }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }
        val hasBurn = tokens.any { it.value == "burn" }

        return when {
            hasMint && !hasBurn && hasAmount -> PatternMatch(0.9, emptyMap())
            hasMint && !hasBurn -> PatternMatch(0.7, emptyMap())
            else -> null
        }
    }

    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "mint" }

    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val amount = entities.find { it.original.type == EntityType.AMOUNT }?.resolvedValue ?: "0"
        val token = entities.find { it.original.type == EntityType.TOKEN }?.resolvedValue ?: "token"

        return TransactionIntent(
            type = intentType,
            summary = "Mint $amount $token",
            description = "Mint $amount tokens to your wallet (requires mint authority)",
            entities = entities.associateBy { it.original.type.displayName }
        )
    }

    override fun getSuggestion(missing: EntityType) = when (missing) {
        EntityType.AMOUNT -> "How many tokens would you like to mint?"
        else -> "Please provide more information"
    }
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "mint" }) 0.5 else 0.0
}

class BurnTokenPattern : TransactionPattern() {
    override val intentType = IntentType.BURN_TOKEN
    override val template = "burn [amount] [token]"
    override val description = "Permanently burn (destroy) tokens"
    override val examples = listOf("burn 100 tokens", "burn 500 BONK")
    override val requiredEntities = listOf(EntityType.AMOUNT)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN)

    override fun match(tokens: List<Token>): PatternMatch? {
        val hasBurn = tokens.any { it.value == "burn" }
        val hasNft = tokens.any { it.value == "nft" }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }

        return when {
            hasBurn && !hasNft && hasAmount -> PatternMatch(0.9, emptyMap())
            hasBurn && !hasNft -> PatternMatch(0.7, emptyMap())
            else -> null
        }
    }

    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "burn" }

    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val amount = entities.find { it.original.type == EntityType.AMOUNT }?.resolvedValue ?: "0"
        val token = entities.find { it.original.type == EntityType.TOKEN }?.resolvedValue ?: "token"

        return TransactionIntent(
            type = intentType,
            summary = "Burn $amount $token",
            description = "Permanently destroy $amount $token tokens (irreversible)",
            entities = entities.associateBy { it.original.type.displayName },
            warnings = listOf("This action is irreversible. Burned tokens cannot be recovered.")
        )
    }

    override fun getSuggestion(missing: EntityType) = when (missing) {
        EntityType.AMOUNT -> "How many tokens would you like to burn?"
        else -> "Please provide more information"
    }
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "burn" }) 0.5 else 0.0
}

class StakePattern : TransactionPattern() {
    override val intentType = IntentType.STAKE
    override val template = "stake [amount] SOL"
    override val description = "Stake SOL with a validator"
    override val examples = listOf("stake 10 SOL", "stake 5 SOL with Marinade", "stake 100 SOL to validator")
    override val requiredEntities = listOf(EntityType.AMOUNT)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.VALIDATOR)

    override fun match(tokens: List<Token>): PatternMatch? {
        val hasStake = tokens.any { it.value == "stake" }
        val hasUnstake = tokens.any { it.value in listOf("unstake", "deactivate") }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }

        return when {
            hasStake && !hasUnstake && hasAmount -> PatternMatch(0.9, emptyMap())
            hasStake && !hasUnstake -> PatternMatch(0.7, emptyMap())
            else -> null
        }
    }

    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "stake" }

    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val amount = entities.find { it.original.type == EntityType.AMOUNT }?.resolvedValue ?: "0"
        val validator = entities.find { it.original.type == EntityType.VALIDATOR }?.resolvedValue

        return TransactionIntent(
            type = intentType,
            summary = "Stake $amount SOL",
            description = if (validator != null) "Stake $amount SOL with validator $validator"
                         else "Stake $amount SOL (validator will be selected automatically)",
            entities = entities.associateBy { it.original.type.displayName }
        )
    }

    override fun getSuggestion(missing: EntityType) = when (missing) {
        EntityType.AMOUNT -> "How much SOL would you like to stake?"
        EntityType.VALIDATOR -> "Which validator? (optional, will auto-select if not specified)"
        else -> "Please provide more information"
    }
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "stake" }) 0.5 else 0.0
}

class UnstakePattern : TransactionPattern() {
    override val intentType = IntentType.UNSTAKE
    override val template = "unstake [amount] SOL"
    override val description = "Unstake (deactivate) staked SOL"
    override val examples = listOf("unstake 10 SOL", "unstake all", "deactivate my stake")
    override val requiredEntities = emptyList<EntityType>()
    override val entityTypes = listOf(EntityType.AMOUNT)

    override fun match(tokens: List<Token>): PatternMatch? {
        val hasUnstake = tokens.any { it.value in listOf("unstake", "deactivate") }
        return if (hasUnstake) PatternMatch(0.9, emptyMap()) else null
    }

    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value in listOf("unstake", "deactivate") }

    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val amount = entities.find { it.original.type == EntityType.AMOUNT }?.resolvedValue

        return TransactionIntent(
            type = intentType,
            summary = if (amount != null) "Unstake $amount SOL" else "Unstake all SOL",
            description = "Deactivate stake account. SOL will be available to withdraw after the cooldown period (~2-3 days).",
            entities = entities.associateBy { it.original.type.displayName },
            warnings = listOf("Unstaking has a cooldown period of approximately 2-3 days before SOL can be withdrawn.")
        )
    }

    override fun getSuggestion(missing: EntityType) = "How much to unstake? (or 'all')"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value in listOf("unstake", "deactivate") }) 0.5 else 0.0
}

class DelegatePattern : TransactionPattern() {
    override val intentType = IntentType.DELEGATE
    override val template = "delegate [amount] to [validator]"
    override val description = "Delegate staked SOL to a validator"
    override val examples = listOf("delegate 10 SOL to validator", "delegate to Marinade")
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.VALIDATOR)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.VALIDATOR)

    override fun match(tokens: List<Token>): PatternMatch? {
        val hasDelegate = tokens.any { it.value == "delegate" }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }
        val hasTo = tokens.any { it.value == "to" }

        return when {
            hasDelegate && hasAmount && hasTo -> PatternMatch(0.9, emptyMap())
            hasDelegate && hasTo -> PatternMatch(0.7, emptyMap())
            hasDelegate -> PatternMatch(0.6, emptyMap())
            else -> null
        }
    }

    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "delegate" }

    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val amount = entities.find { it.original.type == EntityType.AMOUNT }?.resolvedValue ?: "0"
        val validator = entities.find { it.original.type == EntityType.VALIDATOR }?.resolvedValue ?: "unknown"

        return TransactionIntent(
            type = intentType,
            summary = "Delegate $amount SOL to $validator",
            description = "Delegate $amount SOL stake to validator $validator for earning staking rewards",
            entities = entities.associateBy { it.original.type.displayName }
        )
    }

    override fun getSuggestion(missing: EntityType) = when (missing) {
        EntityType.AMOUNT -> "How much SOL would you like to delegate?"
        EntityType.VALIDATOR -> "Which validator would you like to delegate to?"
        else -> "Please provide delegation details"
    }
    override fun similarityScore(tokens: List<Token>): Double {
        var score = 0.0
        if (tokens.any { it.value == "delegate" }) score += 0.5
        if (tokens.any { it.value == "to" }) score += 0.25
        return score
    }
}

class TransferNftPattern : TransactionPattern() {
    override val intentType = IntentType.TRANSFER_NFT
    override val template = "send NFT to [address]"
    override val description = "Transfer an NFT to another wallet"
    override val examples = listOf("send my NFT to alice.sol", "transfer NFT to 7xKX...")
    override val requiredEntities = listOf(EntityType.ADDRESS)
    override val entityTypes = listOf(EntityType.ADDRESS, EntityType.DOMAIN, EntityType.SKR_KEY, EntityType.WALLET_ALIAS)

    override fun match(tokens: List<Token>): PatternMatch? {
        val hasNft = tokens.any { it.value == "nft" }
        val hasTransfer = tokens.any { it.value in listOf("send", "transfer") }
        val hasTo = tokens.any { it.value == "to" }

        return when {
            hasNft && hasTransfer && hasTo -> PatternMatch(0.95, emptyMap())
            hasNft && hasTransfer -> PatternMatch(0.8, emptyMap())
            else -> null
        }
    }

    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "nft" }

    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val recipient = entities.find {
            it.original.type in listOf(EntityType.ADDRESS, EntityType.DOMAIN, EntityType.SKR_KEY, EntityType.WALLET_ALIAS)
        }?.resolvedValue ?: ""

        return TransactionIntent(
            type = intentType,
            summary = "Transfer NFT",
            description = "Send NFT to $recipient (creates recipient's token account if needed)",
            entities = entities.associateBy { it.original.type.displayName }
        )
    }

    override fun getSuggestion(missing: EntityType) = "Who would you like to send the NFT to? (address, .sol domain, or alias)"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "nft" }) 0.5 else 0.0
}

class BurnNftPattern : TransactionPattern() {
    override val intentType = IntentType.BURN_NFT
    override val template = "burn NFT"
    override val description = "Permanently burn (destroy) an NFT"
    override val examples = listOf("burn my NFT", "destroy NFT")
    override val requiredEntities = emptyList<EntityType>()
    override val entityTypes = emptyList<EntityType>()

    override fun match(tokens: List<Token>): PatternMatch? {
        val hasBurn = tokens.any { it.value in listOf("burn", "destroy") }
        val hasNft = tokens.any { it.value == "nft" }
        return if (hasBurn && hasNft) PatternMatch(0.9, emptyMap()) else null
    }

    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "nft" } && tokens.any { it.value in listOf("burn", "destroy") }

    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(
        type = intentType,
        summary = "Burn NFT",
        description = "Permanently destroy the NFT and close the token account (irreversible)",
        entities = emptyMap(),
        warnings = listOf("This action is irreversible. The NFT will be permanently destroyed.")
    )

    override fun getSuggestion(missing: EntityType) = "Which NFT would you like to burn?"
    override fun similarityScore(tokens: List<Token>): Double {
        var score = 0.0
        if (tokens.any { it.value == "nft" }) score += 0.3
        if (tokens.any { it.value in listOf("burn", "destroy") }) score += 0.3
        return score
    }
}

class CloseAccountPattern : TransactionPattern() {
    override val intentType = IntentType.CLOSE_ACCOUNT
    override val template = "close account"
    override val description = "Close a token account and reclaim rent"
    override val examples = listOf("close empty accounts", "close my BONK account", "reclaim rent")
    override val requiredEntities = emptyList<EntityType>()
    override val entityTypes = listOf(EntityType.TOKEN)

    override fun match(tokens: List<Token>): PatternMatch? {
        val hasClose = tokens.any { it.value in listOf("close", "reclaim") }
        val hasAccount = tokens.any { it.value in listOf("account", "accounts", "rent") }
        return when {
            hasClose && hasAccount -> PatternMatch(0.9, emptyMap())
            hasClose -> PatternMatch(0.7, emptyMap())
            else -> null
        }
    }

    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value in listOf("close", "reclaim") }

    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val token = entities.find { it.original.type == EntityType.TOKEN }?.resolvedValue

        return TransactionIntent(
            type = intentType,
            summary = if (token != null) "Close $token account" else "Close token account",
            description = "Close the token account and return the rent-exempt balance to your wallet",
            entities = entities.associateBy { it.original.type.displayName }
        )
    }

    override fun getSuggestion(missing: EntityType) = "Which token account would you like to close?"
    override fun similarityScore(tokens: List<Token>): Double {
        var score = 0.0
        if (tokens.any { it.value == "close" }) score += 0.4
        if (tokens.any { it.value in listOf("account", "accounts") }) score += 0.3
        return score
    }
}

class MemoPattern : TransactionPattern() {
    override val intentType = IntentType.MEMO
    override val template = "add memo [message]"
    override val description = "Add a memo message to a transaction"
    override val examples = listOf("add memo 'Hello World'", "memo 'payment for services'")
    override val requiredEntities = listOf(EntityType.MEMO)
    override val entityTypes = listOf(EntityType.MEMO)

    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "memo" }) PatternMatch(0.9, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "memo" }

    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val memo = entities.find { it.original.type == EntityType.MEMO }?.resolvedValue ?: ""

        return TransactionIntent(
            type = intentType,
            summary = "Add memo",
            description = "Add an on-chain memo: \"$memo\"",
            entities = entities.associateBy { it.original.type.displayName }
        )
    }

    override fun getSuggestion(missing: EntityType) = "What message would you like to include in the memo?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "memo" }) 0.5 else 0.0
}

// ===========================================
// Additional Comprehensive Patterns
// ===========================================

class CreateAtaPattern : TransactionPattern() {
    override val intentType = IntentType.CREATE_ATA
    override val template = "create account for [token]"
    override val description = "Create an associated token account"
    override val examples = listOf("create account for USDC", "create token account for BONK")
    override val requiredEntities = listOf(EntityType.TOKEN)
    override val entityTypes = listOf(EntityType.TOKEN, EntityType.ADDRESS)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "create" } && tokens.any { it.value in listOf("account", "ata") }) PatternMatch(0.85, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "create" && tokens.any { it.value == "account" } }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Create token account", "Create associated token account", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "Which token do you want to create an account for?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "create" }) 0.4 else 0.0
}

class ApprovePattern : TransactionPattern() {
    override val intentType = IntentType.APPROVE_DELEGATE
    override val template = "approve [amount] [token] to [address]"
    override val description = "Approve a delegate to spend tokens"
    override val examples = listOf("approve 100 USDC to delegate.sol", "approve 50 SOL spending")
    override val requiredEntities = listOf(EntityType.AMOUNT, EntityType.TOKEN, EntityType.ADDRESS)
    override val entityTypes = listOf(EntityType.AMOUNT, EntityType.TOKEN, EntityType.ADDRESS, EntityType.DOMAIN)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "approve" }) PatternMatch(0.9, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "approve" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Approve delegate", "Approve delegate to spend tokens", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = when (missing) {
        EntityType.AMOUNT -> "How many tokens to approve?"
        EntityType.ADDRESS -> "Who to approve as delegate?"
        else -> "Please provide more details"
    }
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "approve" }) 0.5 else 0.0
}

class RevokePattern : TransactionPattern() {
    override val intentType = IntentType.REVOKE_DELEGATE
    override val template = "revoke [token] approval"
    override val description = "Revoke token spending approval"
    override val examples = listOf("revoke USDC approval", "revoke delegate access")
    override val requiredEntities = listOf(EntityType.TOKEN)
    override val entityTypes = listOf(EntityType.TOKEN)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "revoke" }) PatternMatch(0.9, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "revoke" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Revoke approval", "Revoke delegate spending approval", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "Which token account to revoke approval for?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "revoke" }) 0.5 else 0.0
}

class WrapSolPattern : TransactionPattern() {
    override val intentType = IntentType.WRAP_SOL
    override val template = "wrap [amount] SOL"
    override val description = "Wrap SOL to wSOL"
    override val examples = listOf("wrap 1 SOL", "wrap 5 SOL to wSOL")
    override val requiredEntities = listOf(EntityType.AMOUNT)
    override val entityTypes = listOf(EntityType.AMOUNT)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "wrap" } && tokens.any { it.value.uppercase() == "SOL" }) PatternMatch(0.95, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "wrap" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Wrap SOL", "Wrap native SOL to wSOL token", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "How much SOL to wrap?"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "wrap" }) 0.5 else 0.0
}

class UnwrapSolPattern : TransactionPattern() {
    override val intentType = IntentType.UNWRAP_SOL
    override val template = "unwrap [amount] wSOL"
    override val description = "Unwrap wSOL to native SOL"
    override val examples = listOf("unwrap wSOL", "unwrap all wSOL", "unwrap 1 wSOL")
    override val requiredEntities = emptyList<EntityType>()
    override val entityTypes = listOf(EntityType.AMOUNT)
    override fun match(tokens: List<Token>) = if (tokens.any { it.value == "unwrap" }) PatternMatch(0.95, emptyMap()) else null
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "unwrap" }
    override fun buildIntent(entities: List<ResolvedEntity>) = TransactionIntent(intentType, "Unwrap wSOL", "Unwrap wSOL token to native SOL", entities.associateBy { it.original.type.displayName })
    override fun getSuggestion(missing: EntityType) = "This will unwrap all wSOL to native SOL"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "unwrap" }) 0.5 else 0.0
}

class AirdropPattern : TransactionPattern() {
    override val intentType = IntentType.AIRDROP
    override val template = "airdrop [amount] SOL"
    override val description = "Request devnet SOL airdrop"
    override val examples = listOf("airdrop 1 SOL", "airdrop 2 SOL", "get devnet SOL")
    override val requiredEntities = listOf(EntityType.AMOUNT)
    override val entityTypes = listOf(EntityType.AMOUNT)
    
    override fun match(tokens: List<Token>): PatternMatch? {
        val hasAirdrop = tokens.any { it.value in listOf("airdrop", "faucet") }
        val hasDevnet = tokens.any { it.value == "devnet" }
        val hasAmount = tokens.any { it.type == TokenType.NUMBER }
        
        return when {
            hasAirdrop && hasAmount -> PatternMatch(0.95, emptyMap())
            hasAirdrop -> PatternMatch(0.8, emptyMap())
            hasDevnet && tokens.any { it.value in listOf("sol", "get", "request") } -> PatternMatch(0.7, emptyMap())
            else -> null
        }
    }
    
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value in listOf("airdrop", "faucet", "devnet") }
    
    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val amount = entities.find { it.original.type == EntityType.AMOUNT }?.resolvedValue ?: "1"
        return TransactionIntent(
            type = intentType,
            summary = "Airdrop $amount SOL",
            description = "Request $amount SOL from devnet faucet",
            entities = entities.associateBy { it.original.type.displayName },
            warnings = listOf("Only works on devnet/testnet")
        )
    }
    
    override fun getSuggestion(missing: EntityType) = "How much devnet SOL to airdrop? (max 2 per request)"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "airdrop" }) 0.6 else 0.0
}

class BalancePattern : TransactionPattern() {
    override val intentType = IntentType.CHECK_BALANCE
    override val template = "check balance"
    override val description = "Check wallet balance"
    override val examples = listOf("check balance", "show my balance", "how much SOL do I have", "what's my balance")
    override val requiredEntities = emptyList<EntityType>()
    override val entityTypes = listOf(EntityType.TOKEN, EntityType.ADDRESS)
    
    override fun match(tokens: List<Token>): PatternMatch? {
        val hasBalance = tokens.any { it.value == "balance" }
        val hasCheck = tokens.any { it.value in listOf("check", "show", "get", "view", "my") }
        val hasHow = tokens.any { it.value in listOf("how", "what", "whats") }
        val hasMuch = tokens.any { it.value == "much" }
        
        return when {
            hasBalance && hasCheck -> PatternMatch(0.95, emptyMap())
            hasBalance -> PatternMatch(0.85, emptyMap())
            hasHow && hasMuch && tokens.any { it.value.uppercase() in listOf("SOL", "USDC") } -> PatternMatch(0.8, emptyMap())
            else -> null
        }
    }
    
    override fun partialMatch(tokens: List<Token>) = tokens.any { it.value == "balance" }
    
    override fun buildIntent(entities: List<ResolvedEntity>): TransactionIntent {
        val token = entities.find { it.original.type == EntityType.TOKEN }?.resolvedValue
        val tokenDesc = token ?: "SOL"
        return TransactionIntent(
            type = intentType,
            summary = "Check $tokenDesc balance",
            description = "Query current $tokenDesc balance",
            entities = entities.associateBy { it.original.type.displayName }
        )
    }
    
    override fun getSuggestion(missing: EntityType) = "Which token balance to check? (default: SOL)"
    override fun similarityScore(tokens: List<Token>) = if (tokens.any { it.value == "balance" }) 0.5 else 0.0
}

/**
 * Transaction builder - produces instruction templates from parsed intent.
 *
 * This builder creates [InstructionTemplate] descriptors that describe the
 * instructions needed to fulfill the intent. To execute the transaction, pass
 * the intent to [NlpExecutor.execute].
 *
 * ```kotlin
 * val result = nlb.parse("send 1 SOL to alice.sol")
 * if (result is ParseResult.Success) {
 *     // Inspect what will be built
 *     val templates = result.builder.getInstructionTemplates()
 *     templates.forEach { println(it) }
 *
 *     // Execute via NlpExecutor
 *     val executionResult = executor.execute(result.intent)
 * }
 * ```
 */
class TransactionBuilder(
    private val intent: TransactionIntent,
    private val resolver: EntityResolver
) {

    /**
     * Get the instruction templates that describe this transaction.
     */
    fun getInstructionTemplates(): List<InstructionTemplate> {
        return when (intent.type) {
            IntentType.TRANSFER_SOL -> {
                val amount = intent.entities["amount"]?.resolvedValue ?: "0"
                val recipient = intent.entities["recipient"]?.resolvedValue ?: ""
                listOf(
                    InstructionTemplate(
                        program = "System Program",
                        action = "transfer",
                        params = mapOf("lamports" to "${(amount.toDoubleOrNull() ?: 0.0) * 1_000_000_000}", "to" to recipient),
                        description = "Transfer $amount SOL to $recipient"
                    )
                )
            }
            IntentType.TRANSFER_TOKEN -> {
                val amount = intent.entities["amount"]?.resolvedValue ?: "0"
                val token = intent.entities["token"]?.resolvedValue ?: ""
                val recipient = intent.entities["recipient"]?.resolvedValue ?: ""
                listOf(
                    InstructionTemplate(
                        program = "Associated Token Program",
                        action = "createAssociatedTokenAccountIdempotent",
                        params = mapOf("owner" to recipient, "mint" to token),
                        description = "Ensure recipient token account exists"
                    ),
                    InstructionTemplate(
                        program = "Token Program",
                        action = "transferChecked",
                        params = mapOf("amount" to amount, "mint" to token, "to" to recipient),
                        description = "Transfer $amount tokens to $recipient"
                    )
                )
            }
            IntentType.SWAP -> {
                val amount = intent.entities.values.find { it.original.type == EntityType.AMOUNT }?.resolvedValue ?: "0"
                val tokens = intent.entities.values.filter { it.original.type == EntityType.TOKEN }
                val inputToken = tokens.getOrNull(0)?.resolvedValue ?: "SOL"
                val outputToken = tokens.getOrNull(1)?.resolvedValue ?: "USDC"
                listOf(
                    InstructionTemplate(
                        program = "Jupiter Aggregator",
                        action = "route",
                        params = mapOf("inputMint" to inputToken, "outputMint" to outputToken, "amount" to amount),
                        description = "Swap $amount $inputToken for $outputToken via Jupiter"
                    )
                )
            }
            IntentType.STAKE -> {
                val amount = intent.entities["amount"]?.resolvedValue ?: "0"
                listOf(
                    InstructionTemplate(
                        program = "Stake Program",
                        action = "delegate",
                        params = mapOf("amount" to amount),
                        description = "Stake $amount SOL"
                    )
                )
            }
            IntentType.UNSTAKE -> listOf(
                InstructionTemplate(
                    program = "Stake Program",
                    action = "deactivate",
                    params = emptyMap(),
                    description = "Deactivate stake account"
                )
            )
            IntentType.DELEGATE -> {
                val amount = intent.entities["amount"]?.resolvedValue ?: "0"
                val validator = intent.entities["validator"]?.resolvedValue ?: ""
                listOf(
                    InstructionTemplate(
                        program = "Stake Program",
                        action = "delegate",
                        params = mapOf("amount" to amount, "voteAccount" to validator),
                        description = "Delegate $amount SOL to $validator"
                    )
                )
            }
            IntentType.CREATE_TOKEN -> listOf(
                InstructionTemplate(
                    program = "Token Program",
                    action = "initializeMint",
                    params = emptyMap(),
                    description = "Create a new SPL token mint"
                )
            )
            IntentType.MINT_TOKEN -> {
                val amount = intent.entities["amount"]?.resolvedValue ?: "0"
                listOf(
                    InstructionTemplate(
                        program = "Token Program",
                        action = "mintTo",
                        params = mapOf("amount" to amount),
                        description = "Mint $amount tokens"
                    )
                )
            }
            IntentType.BURN_TOKEN -> {
                val amount = intent.entities["amount"]?.resolvedValue ?: "0"
                listOf(
                    InstructionTemplate(
                        program = "Token Program",
                        action = "burn",
                        params = mapOf("amount" to amount),
                        description = "Burn $amount tokens"
                    )
                )
            }
            IntentType.TRANSFER_NFT -> {
                val recipient = intent.entities["recipient"]?.resolvedValue
                    ?: intent.entities["address"]?.resolvedValue ?: ""
                listOf(
                    InstructionTemplate(
                        program = "Associated Token Program",
                        action = "createAssociatedTokenAccountIdempotent",
                        params = mapOf("owner" to recipient),
                        description = "Ensure recipient NFT account exists"
                    ),
                    InstructionTemplate(
                        program = "Token Program",
                        action = "transfer",
                        params = mapOf("amount" to "1", "to" to recipient),
                        description = "Transfer NFT to $recipient"
                    )
                )
            }
            IntentType.BURN_NFT -> listOf(
                InstructionTemplate(
                    program = "Token Program",
                    action = "burn",
                    params = mapOf("amount" to "1"),
                    description = "Burn NFT (1 token)"
                )
            )
            IntentType.CLOSE_ACCOUNT -> listOf(
                InstructionTemplate(
                    program = "Token Program",
                    action = "closeAccount",
                    params = emptyMap(),
                    description = "Close token account and reclaim rent"
                )
            )
            IntentType.CREATE_ATA -> {
                val token = intent.entities["token"]?.resolvedValue ?: ""
                listOf(
                    InstructionTemplate(
                        program = "Associated Token Program",
                        action = "createAssociatedTokenAccount",
                        params = mapOf("mint" to token),
                        description = "Create associated token account for $token"
                    )
                )
            }
            IntentType.APPROVE_DELEGATE -> {
                val amount = intent.entities["amount"]?.resolvedValue ?: "0"
                val delegate = intent.entities["address"]?.resolvedValue ?: ""
                listOf(
                    InstructionTemplate(
                        program = "Token Program",
                        action = "approve",
                        params = mapOf("amount" to amount, "delegate" to delegate),
                        description = "Approve $delegate to spend $amount tokens"
                    )
                )
            }
            IntentType.REVOKE_DELEGATE -> listOf(
                InstructionTemplate(
                    program = "Token Program",
                    action = "revoke",
                    params = emptyMap(),
                    description = "Revoke delegate spending approval"
                )
            )
            IntentType.WRAP_SOL -> {
                val amount = intent.entities["amount"]?.resolvedValue ?: "0"
                listOf(
                    InstructionTemplate(
                        program = "System Program + Token Program",
                        action = "wrapSOL",
                        params = mapOf("amount" to amount),
                        description = "Wrap $amount SOL to wSOL"
                    )
                )
            }
            IntentType.UNWRAP_SOL -> listOf(
                InstructionTemplate(
                    program = "Token Program",
                    action = "closeAccount",
                    params = mapOf("mint" to "wSOL"),
                    description = "Unwrap wSOL to native SOL"
                )
            )
            IntentType.AIRDROP -> {
                val amount = intent.entities["amount"]?.resolvedValue ?: "1"
                listOf(
                    InstructionTemplate(
                        program = "RPC",
                        action = "requestAirdrop",
                        params = mapOf("amount" to amount),
                        description = "Request $amount SOL from devnet faucet"
                    )
                )
            }
            IntentType.CHECK_BALANCE -> listOf(
                InstructionTemplate(
                    program = "RPC",
                    action = "getBalance",
                    params = emptyMap(),
                    description = "Query wallet balance"
                )
            )
            IntentType.MEMO -> {
                val memo = intent.entities["memo"]?.resolvedValue ?: ""
                listOf(
                    InstructionTemplate(
                        program = "Memo Program",
                        action = "memo",
                        params = mapOf("message" to memo),
                        description = "Add memo: $memo"
                    )
                )
            }
            else -> emptyList()
        }
    }

    /**
     * Get human-readable summary.
     */
    fun getSummary(): String = intent.summary

    /**
     * Get detailed description.
     */
    fun getDescription(): String = intent.description

    /**
     * Get the underlying intent for use with [NlpExecutor].
     */
    fun getIntent(): TransactionIntent = intent
}

/**
 * Describes a single instruction that will be part of the transaction.
 */
data class InstructionTemplate(
    /** The on-chain program that will process this instruction */
    val program: String,
    /** The action/method being called */
    val action: String,
    /** Parameters for this instruction */
    val params: Map<String, String>,
    /** Human-readable description */
    val description: String
)
