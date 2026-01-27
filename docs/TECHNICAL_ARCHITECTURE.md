# Artemis SDK v2.0.0 - Technical Architecture

## How Each Revolutionary Feature Works

This document provides deep technical explanations of how each of Artemis's six revolutionary features work under the hood.

---

## 1. artemis-anchor - Anchor Program Client

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     AnchorProgram                           │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ AnchorIdl   │  │MethodsBuilder│  │ AccountsBuilder   │ │
│  │ (Parser)    │  │ (Instructions)│ │ (Fetch/Watch)     │ │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬─────────┘ │
│         │                │                     │           │
│  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────────▼─────────┐ │
│  │IdlInstruction│  │BorshSerializer│ │ AccountDeserializer│ │
│  │IdlAccountDef│  │(Borsh Encoding)│ │ (Discriminator)   │ │
│  │IdlTypeDef   │  └──────┬──────┘  └──────────┬─────────┘ │
│  └─────────────┘         │                     │           │
│                   ┌──────▼─────────────────────▼─────────┐ │
│                   │           RPC API                     │ │
│                   │  sendTransaction / getAccountInfo     │ │
│                   └───────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Key Components

#### 1. IDL Parser (`AnchorIdl.kt`)
Parses Anchor IDL JSON into Kotlin data classes:

```kotlin
@Serializable
data class AnchorIdl(
    val version: String,
    val name: String,
    val instructions: List<IdlInstruction>,
    val accounts: List<IdlAccountDef>?,
    val types: List<IdlTypeDef>?,
    val events: List<IdlEvent>?,
    val errors: List<IdlErrorDef>?
)
```

#### 2. Discriminator Computation
Anchor uses 8-byte discriminators derived from instruction/account names:

```kotlin
// Instruction discriminator: sha256("global:<instruction_name>")[0..8]
fun computeInstructionDiscriminator(name: String): ByteArray {
    val preimage = "global:$name"
    val hash = MessageDigest.getInstance("SHA-256").digest(preimage.toByteArray())
    return hash.copyOfRange(0, 8)
}

// Account discriminator: sha256("account:<AccountTypeName>")[0..8]
fun computeAccountDiscriminator(name: String): ByteArray {
    val preimage = "account:$name"
    val hash = MessageDigest.getInstance("SHA-256").digest(preimage.toByteArray())
    return hash.copyOfRange(0, 8)
}
```

#### 3. Borsh Serialization (`BorshSerializer.kt`)
Serializes instruction arguments to Borsh format:

```kotlin
class BorshSerializer {
    private val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    
    fun writeU8(value: Int) = buffer.put(value.toByte())
    fun writeU32(value: Long) = buffer.putInt(value.toInt())
    fun writeU64(value: Long) = buffer.putLong(value)
    fun writeString(value: String) {
        writeU32(value.length.toLong())
        buffer.put(value.toByteArray())
    }
    fun writePubkey(pubkey: Pubkey) = buffer.put(pubkey.bytes)
    
    fun toByteArray(): ByteArray {
        buffer.flip()
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }
}
```

#### 4. Fluent Instruction Builder
Mirrors Anchor TypeScript API:

```kotlin
class MethodsBuilder(private val program: AnchorProgram) {
    fun instruction(name: String): InstructionBuilder {
        val idlInstruction = program.findInstruction(name)
        return InstructionBuilder(program, idlInstruction)
    }
}

class InstructionBuilder(
    private val program: AnchorProgram,
    private val idlInstruction: IdlInstruction
) {
    private val args = mutableMapOf<String, Any>()
    private val accounts = mutableListOf<AccountMeta>()
    
    fun args(values: Map<String, Any>) = apply { args.putAll(values) }
    
    fun accounts(block: AccountsScope.() -> Unit) = apply {
        AccountsScope(accounts, idlInstruction.accounts).block()
    }
    
    fun build(): Instruction {
        // Serialize discriminator + args
        val data = buildInstructionData()
        return Instruction(program.programId, accounts, data)
    }
    
    private fun buildInstructionData(): ByteArray {
        val serializer = BorshSerializer()
        
        // Write 8-byte discriminator
        serializer.writeBytes(program.getInstructionDiscriminator(idlInstruction.name))
        
        // Write args in order defined by IDL
        idlInstruction.args.forEach { arg ->
            val value = args[arg.name] ?: throw IllegalArgumentException("Missing arg: ${arg.name}")
            serializeArg(serializer, value, arg.type)
        }
        
        return serializer.toByteArray()
    }
}
```

---

## 2. artemis-jupiter - Jupiter DEX Integration

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    JupiterClient                            │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────────┐  ┌──────────────────────────────────┐│
│  │  Quote Engine    │  │      Swap Builder               ││
│  │  ┌────────────┐  │  │  ┌────────────────────────────┐ ││
│  │  │ Route      │  │  │  │ Priority Fee Calculator   │ ││
│  │  │ Discovery  │  │  │  └────────────────────────────┘ ││
│  │  └────────────┘  │  │  ┌────────────────────────────┐ ││
│  │  ┌────────────┐  │  │  │ Dynamic Slippage         │ ││
│  │  │ Price      │  │  │  └────────────────────────────┘ ││
│  │  │ Streaming  │  │  │  ┌────────────────────────────┐ ││
│  │  └────────────┘  │  │  │ Transaction Simulation   │ ││
│  └──────────────────┘  │  └────────────────────────────┘ ││
│                        └──────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │                   HTTP Client (OkHttp)                  ││
│  │  - Connection pooling                                   ││
│  │  - Retry with backoff                                   ││
│  │  - Response caching                                     ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### Key Components

#### 1. Quote Request Flow

```kotlin
suspend fun getQuote(request: QuoteRequest): QuoteResponse {
    // Build URL with query parameters
    val url = buildString {
        append(config.quoteApiUrl)
        append("?inputMint=${request.inputMint}")
        append("&outputMint=${request.outputMint}")
        append("&amount=${request.amount}")
        request.slippageBps?.let { append("&slippageBps=$it") }
        // ... more parameters
    }
    
    val httpRequest = Request.Builder()
        .url(url)
        .get()
        .build()
    
    return executeRequest(httpRequest) { body ->
        json.decodeFromString<QuoteResponse>(body)
    }
}
```

#### 2. Streaming Quotes with Flow

```kotlin
fun streamQuotes(
    inputMint: String,
    outputMint: String,
    amount: Long,
    slippageBps: Int = 50,
    interval: Duration = 3.seconds
): Flow<QuoteResponse> = flow {
    while (currentCoroutineContext().isActive) {
        try {
            val quote = getQuote(QuoteRequest(
                inputMint = inputMint,
                outputMint = outputMint,
                amount = amount,
                slippageBps = slippageBps
            ))
            emit(quote)
        } catch (e: Exception) {
            if (e !is CancellationException) {
                // Log and continue - transient failure
            }
        }
        delay(interval)
    }
}.flowOn(Dispatchers.IO)
```

#### 3. Priority Fee Optimization

```kotlin
enum class PriorityLevel {
    NONE,      // 0 lamports
    LOW,       // 10,000 lamports
    MEDIUM,    // 50,000 lamports
    HIGH,      // 200,000 lamports
    VERY_HIGH  // 1,000,000 lamports
}

data class SwapRequest(
    val quoteResponse: QuoteResponse,
    val userPublicKey: String,
    val wrapAndUnwrapSol: Boolean = true,
    val useSharedAccounts: Boolean = true,
    val prioritizationFeeLamports: Long? = null,
    val dynamicSlippage: DynamicSlippage? = null,
    val computeUnitPriceMicroLamports: Long? = null
)
```

---

## 3. artemis-actions - Solana Actions/Blinks

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     ActionsClient                           │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ URL Parser      │  │ Action Fetcher  │  │ Chain Handler│ │
│  │ - solana:       │  │ - GET metadata  │  │ - Callbacks  │ │
│  │ - solana-action:│  │ - POST execute  │  │ - Next action│ │
│  │ - blink detect  │  │ - Identity auth │  │              │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │              Form Input Handler                         ││
│  │  - text, email, number, date, checkbox, radio, select  ││
│  │  - Pattern validation                                   ││
│  │  - URL parameter interpolation                          ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### Key Components

#### 1. URL Parsing

```kotlin
fun parseActionUrl(url: String): ActionUrlInfo {
    return when {
        // Direct Solana URI: solana:<base58>
        url.startsWith("solana:") -> parseSolanaScheme(url)
        
        // Action URI: solana-action:https://...
        url.startsWith("solana-action:") -> parseActionScheme(url)
        
        // Blink URL: https://dial.to/?action=...
        isBlink(url) -> ActionUrlInfo(url, ActionUrlType.BLINK, extractBlinkTarget(url))
        
        // Direct action endpoint
        url.contains("/api/actions/") -> 
            ActionUrlInfo(url, ActionUrlType.DIRECT_ACTION, extractActionPath(url))
        
        else -> ActionUrlInfo(url, ActionUrlType.UNKNOWN, null)
    }
}

private fun parseActionScheme(url: String): ActionUrlInfo {
    // solana-action:https://example.com/api/actions/donate
    val actionUrl = url.removePrefix("solana-action:")
    val decoded = URLDecoder.decode(actionUrl, "UTF-8")
    return ActionUrlInfo(url, ActionUrlType.ACTION_URI, decoded)
}
```

#### 2. Action Execution Flow

```kotlin
suspend fun executeAction(
    actionUrl: String,
    block: ActionExecuteBuilder.() -> Unit
): ActionPostResponse {
    val builder = ActionExecuteBuilder().apply(block)
    
    // Build request
    val request = ActionPostRequest(
        account = builder.account
    )
    
    // Handle input parameters in URL
    val finalUrl = interpolateInputs(actionUrl, builder.inputs)
    
    return postAction(finalUrl, request)
}

private fun interpolateInputs(url: String, inputs: Map<String, String>): String {
    var result = url
    inputs.forEach { (key, value) ->
        // Replace {key} placeholders
        result = result.replace("{$key}", URLEncoder.encode(value, "UTF-8"))
    }
    return result
}
```

#### 3. Action Chaining

```kotlin
suspend fun confirmTransaction(
    response: ActionPostResponse,
    signature: String
): NextActionResult {
    val links = response.links ?: return NextActionResult.Complete
    val next = links.next
    
    return when (next) {
        is NextActionLink.Post -> {
            // Callback to get next action
            val callback = NextActionPostRequest(signature = signature)
            val nextAction = postCallback(next.href, callback)
            NextActionResult.Continue(nextAction)
        }
        is NextActionLink.Inline -> {
            // Action already provided
            NextActionResult.Continue(next.action)
        }
        null -> NextActionResult.Complete
    }
}
```

---

## 4. artemis-universal - Universal Program Client

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                  UniversalProgramClient                     │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────┐ │
│  │                 Discovery Engine                       │ │
│  │  ┌──────────────────┐  ┌──────────────────────────┐   │ │
│  │  │ Transaction      │  │ Account Structure        │   │ │
│  │  │ Analyzer         │  │ Analyzer                 │   │ │
│  │  │ - Fetch recent   │  │ - getProgramAccounts    │   │ │
│  │  │ - Parse IX data  │  │ - Discriminator extract │   │ │
│  │  │ - Pattern match  │  │ - Field inference       │   │ │
│  │  └──────────────────┘  └──────────────────────────┘   │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │              Instruction Builder                       │ │
│  │  - Uses discovered patterns                            │ │
│  │  - Type inference                                      │ │
│  │  - Account ordering                                    │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │              Schema Cache                              │ │
│  │  - In-memory LRU                                       │ │
│  │  - Disk persistence                                    │ │
│  │  - TTL expiration                                      │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Key Innovation: Pattern Recognition

#### 1. Transaction Analysis

```kotlin
private suspend fun analyzeInstructions(
    programId: Pubkey,
    intelligence: ProgramIntelligence
): List<DiscoveredInstruction> {
    val instructionPatterns = mutableMapOf<Discriminator, InstructionPattern>()
    
    intelligence.recentTransactions.forEach { signature ->
        val tx = rpcClient.getTransaction(signature)
        
        tx.transaction.message.instructions
            .filter { it.programId == programId }
            .forEach { ix ->
                // Extract discriminator (first 8 bytes)
                val discriminator = Discriminator(ix.data.copyOfRange(0, 8))
                
                // Get or create pattern
                val pattern = instructionPatterns.getOrPut(discriminator) {
                    InstructionPattern(discriminator)
                }
                
                // Analyze this instance
                pattern.addSample(
                    data = ix.data,
                    accounts = ix.accounts,
                    success = tx.meta.err == null
                )
            }
    }
    
    return instructionPatterns.values.map { it.toDiscoveredInstruction() }
}
```

#### 2. Field Type Inference

```kotlin
private fun inferFieldsFromData(data: ByteArray): Map<String, InferredValue> {
    val fields = mutableMapOf<String, InferredValue>()
    var offset = 0
    var fieldIndex = 0
    
    while (offset < data.size) {
        when {
            // 32 bytes - likely a Pubkey
            offset + 32 <= data.size && looksLikePubkey(data, offset) -> {
                fields["field_$fieldIndex"] = InferredValue.Pubkey(
                    Pubkey(data.copyOfRange(offset, offset + 32))
                )
                offset += 32
            }
            
            // 8 bytes - likely u64 or i64
            offset + 8 <= data.size -> {
                val value = ByteBuffer.wrap(data, offset, 8)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getLong()
                fields["field_$fieldIndex"] = InferredValue.U64(value)
                offset += 8
            }
            
            // 4 bytes - likely u32
            offset + 4 <= data.size -> {
                val value = ByteBuffer.wrap(data, offset, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt()
                fields["field_$fieldIndex"] = InferredValue.U32(value.toLong())
                offset += 4
            }
            
            else -> {
                fields["field_$fieldIndex"] = InferredValue.Bytes(
                    data.copyOfRange(offset, data.size)
                )
                break
            }
        }
        fieldIndex++
    }
    
    return fields
}

private fun looksLikePubkey(data: ByteArray, offset: Int): Boolean {
    // Check if it's on the ed25519 curve (simplified check)
    val bytes = data.copyOfRange(offset, offset + 32)
    return bytes.any { it != 0.toByte() } // Not all zeros
}
```

#### 3. Anchor Detection

```kotlin
private fun detectAnchorProgram(instructions: List<DiscoveredInstruction>): Boolean {
    // Anchor programs have specific patterns:
    // 1. 8-byte discriminators
    // 2. Discriminators are SHA256 hashes of "global:<name>"
    
    return instructions.any { instr ->
        // Check if discriminator matches Anchor pattern
        val possibleNames = listOf("initialize", "create", "update", "transfer", "close")
        possibleNames.any { name ->
            val anchorDisc = computeAnchorDiscriminator(name)
            instr.discriminator.bytes.contentEquals(anchorDisc)
        }
    }
}
```

---

## 5. artemis-nlp - Natural Language Transactions

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                NaturalLanguageBuilder                       │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────────┐  ┌───────────────┐  ┌─────────────────┐ │
│  │   Tokenizer   │  │   Pattern     │  │   Entity        │ │
│  │               │  │   Registry    │  │   Resolver      │ │
│  │ - Normalize   │  │ - SEND        │  │ - Domains       │ │
│  │ - Split       │  │ - SWAP        │  │ - Tokens        │ │
│  │ - Tag         │  │ - STAKE       │  │ - Addresses     │ │
│  └───────────────┘  │ - CREATE      │  │ - Validators    │ │
│                     │ - MINT        │  └─────────────────┘ │
│                     │ - BURN        │                       │
│                     └───────────────┘                       │
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                  Intent Builder                         ││
│  │  - Maps parsed data to transaction parameters           ││
│  │  - Validates completeness                               ││
│  │  - Generates human-readable summary                     ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### Key Components

#### 1. Pattern Definitions

```kotlin
private fun buildPatternRegistry(): List<TransactionPattern> = listOf(
    // SEND pattern: "send <amount> <token> to <recipient>"
    TransactionPattern(
        intentType = IntentType.TRANSFER,
        template = "send {amount} {token} to {recipient}",
        patterns = listOf(
            Regex("(?:send|transfer)\\s+(\\d+(?:\\.\\d+)?)\\s+(\\w+)\\s+to\\s+(.+)"),
            Regex("(?:pay|give)\\s+(.+)\\s+(\\d+(?:\\.\\d+)?)\\s+(\\w+)"),
        ),
        requiredEntities = listOf(EntityType.AMOUNT, EntityType.TOKEN, EntityType.ADDRESS),
        description = "Transfer tokens to another address",
        examples = listOf(
            "send 1 SOL to alice.sol",
            "transfer 100 USDC to 7xKX...abc",
            "pay bob.sol 50 BONK"
        )
    ) { entities ->
        TransferIntent(
            amount = entities.find { it.type == EntityType.AMOUNT }!!.resolved as BigDecimal,
            token = entities.find { it.type == EntityType.TOKEN }!!.resolved as TokenInfo,
            recipient = entities.find { it.type == EntityType.ADDRESS }!!.resolved as Pubkey
        )
    },
    
    // SWAP pattern: "swap <amount> <fromToken> for <toToken>"
    TransactionPattern(
        intentType = IntentType.SWAP,
        template = "swap {amount} {fromToken} for {toToken}",
        patterns = listOf(
            Regex("swap\\s+(\\d+(?:\\.\\d+)?)\\s+(\\w+)\\s+(?:for|to)\\s+(\\w+)"),
            Regex("(?:exchange|convert)\\s+(\\d+(?:\\.\\d+)?)\\s+(\\w+)\\s+(?:to|into)\\s+(\\w+)"),
            Regex("buy\\s+(\\d+(?:\\.\\d+)?)\\s+(\\w+)\\s+(?:worth\\s+of\\s+)?(\\w+)"),
        ),
        requiredEntities = listOf(EntityType.AMOUNT, EntityType.TOKEN, EntityType.TOKEN),
        description = "Swap one token for another",
        examples = listOf(
            "swap 100 USDC for SOL",
            "buy 1 SOL worth of BONK",
            "convert 50 USDT to USDC"
        )
    ) { entities ->
        SwapIntent(
            amount = entities.find { it.type == EntityType.AMOUNT }!!.resolved as BigDecimal,
            fromToken = entities.filter { it.type == EntityType.TOKEN }[0].resolved as TokenInfo,
            toToken = entities.filter { it.type == EntityType.TOKEN }[1].resolved as TokenInfo
        )
    },
    
    // More patterns...
)
```

#### 2. Entity Extraction

```kotlin
class EntityExtractor(private val resolver: EntityResolver) {
    
    fun extract(tokens: List<Token>, expectedTypes: List<EntityType>): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()
        
        tokens.forEachIndexed { index, token ->
            when {
                // Numbers are amounts
                token.text.matches(Regex("\\d+(\\.\\d+)?")) -> {
                    entities.add(ExtractedEntity(
                        type = EntityType.AMOUNT,
                        value = token.text,
                        position = index,
                        confidence = 1.0
                    ))
                }
                
                // .sol domains
                token.text.endsWith(".sol") -> {
                    entities.add(ExtractedEntity(
                        type = EntityType.DOMAIN,
                        value = token.text,
                        position = index,
                        confidence = 1.0
                    ))
                }
                
                // Base58 addresses (32-44 chars, alphanumeric)
                token.text.matches(Regex("[1-9A-HJ-NP-Za-km-z]{32,44}")) -> {
                    entities.add(ExtractedEntity(
                        type = EntityType.ADDRESS,
                        value = token.text,
                        position = index,
                        confidence = 0.9
                    ))
                }
                
                // Known token symbols
                resolver.isKnownToken(token.text.uppercase()) -> {
                    entities.add(ExtractedEntity(
                        type = EntityType.TOKEN,
                        value = token.text.uppercase(),
                        position = index,
                        confidence = 1.0
                    ))
                }
            }
        }
        
        return entities
    }
}
```

#### 3. Domain Resolution

```kotlin
private suspend fun resolveDomain(entity: ExtractedEntity): ResolvedEntity {
    val domain = entity.value
    
    // Query SNS (Solana Name Service)
    val address = resolver.resolveDomain(domain)
    
    return ResolvedEntity(
        original = entity,
        resolved = address ?: entity.value,
        confidence = if (address != null) 1.0 else 0.0
    )
}
```

---

## 6. artemis-streaming - Zero-Copy Account Streaming

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                 ZeroCopyAccountStream                       │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────┐ │
│  │                  Buffer Pool                           │ │
│  │  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐             │ │
│  │  │ Buf │ │ Buf │ │ Buf │ │ Buf │ │ Buf │ ... (N)     │ │
│  │  └─────┘ └─────┘ └─────┘ └─────┘ └─────┘             │ │
│  │  - Pre-allocated                                       │ │
│  │  - Reusable                                           │ │
│  │  - Lock-free acquire/release                          │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │              Zero-Copy Accessor                        │ │
│  │  - Direct ByteBuffer reads                             │ │
│  │  - Schema-based field offsets                          │ │
│  │  - No object allocation                                │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │                Ring Buffer History                     │ │
│  │  ┌───┬───┬───┬───┬───┬───┬───┬───┐                   │ │
│  │  │ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │ 7 │ ← write pointer   │ │
│  │  └───┴───┴───┴───┴───┴───┴───┴───┘                   │ │
│  │  - Fixed size (configurable)                          │ │
│  │  - O(1) insert                                         │ │
│  │  - Time-travel queries                                 │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Key Components

#### 1. Buffer Pool (Zero Allocation)

```kotlin
class BufferPool(
    private val poolSize: Int,
    private val bufferSize: Int
) {
    private val pool = Array(poolSize) { 
        ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.LITTLE_ENDIAN) 
    }
    private val available = ConcurrentLinkedQueue<ByteBuffer>()
    
    init {
        pool.forEach { available.offer(it) }
    }
    
    fun acquire(): ByteBuffer {
        return available.poll() ?: ByteBuffer.allocateDirect(bufferSize)
            .order(ByteOrder.LITTLE_ENDIAN)
    }
    
    fun release(buffer: ByteBuffer) {
        buffer.clear()
        if (available.size < poolSize) {
            available.offer(buffer)
        }
        // Otherwise let GC handle overflow buffers
    }
}
```

#### 2. Zero-Copy Field Accessor

```kotlin
interface ZeroCopyAccessor {
    fun getU8(fieldName: String): Int
    fun getU16(fieldName: String): Int
    fun getU32(fieldName: String): Long
    fun getU64(fieldName: String): Long
    fun getI64(fieldName: String): Long
    fun getPubkey(fieldName: String): Pubkey
    fun getBytes(fieldName: String, length: Int): ByteArray
}

class ZeroCopyAccessorImpl(
    private val buffer: ByteBuffer,
    private val schema: AccountSchema
) : ZeroCopyAccessor {
    
    override fun getU64(fieldName: String): Long {
        val field = schema.getField(fieldName)
        // Direct buffer read - NO ALLOCATION
        return buffer.getLong(field.offset)
    }
    
    override fun getPubkey(fieldName: String): Pubkey {
        val field = schema.getField(fieldName)
        // Only allocate the 32-byte array, not wrapper objects
        val bytes = ByteArray(32)
        buffer.position(field.offset)
        buffer.get(bytes)
        return Pubkey(bytes)
    }
}
```

#### 3. Delta Detection

```kotlin
private fun hasFieldChanges(
    previous: ByteBuffer,
    current: ByteBuffer,
    schema: AccountSchema
): Boolean {
    schema.fields.forEach { field ->
        val prevValue = readFieldValue(previous, field)
        val currValue = readFieldValue(current, field)
        if (prevValue != currValue) {
            return true
        }
    }
    return false
}

private fun readFieldValue(buffer: ByteBuffer, field: SchemaField): Any {
    return when (field.type) {
        FieldType.U8 -> buffer.get(field.offset)
        FieldType.U16 -> buffer.getShort(field.offset)
        FieldType.U32 -> buffer.getInt(field.offset)
        FieldType.U64 -> buffer.getLong(field.offset)
        FieldType.PUBKEY -> {
            val bytes = ByteArray(32)
            buffer.position(field.offset)
            buffer.get(bytes)
            bytes.contentHashCode() // Compare by hash for efficiency
        }
    }
}
```

#### 4. Ring Buffer for History

```kotlin
class RingBuffer<T>(private val capacity: Int) {
    private val buffer = arrayOfNulls<Any?>(capacity)
    private val writeIndex = AtomicLong(0)
    
    fun add(item: T) {
        val index = (writeIndex.getAndIncrement() % capacity).toInt()
        buffer[index] = item
    }
    
    fun toList(): List<T> {
        val result = mutableListOf<T>()
        val currentWrite = writeIndex.get()
        val start = maxOf(0, currentWrite - capacity)
        
        for (i in start until currentWrite) {
            val index = (i % capacity).toInt()
            @Suppress("UNCHECKED_CAST")
            buffer[index]?.let { result.add(it as T) }
        }
        
        return result
    }
    
    fun last(): T? {
        if (writeIndex.get() == 0L) return null
        val index = ((writeIndex.get() - 1) % capacity).toInt()
        @Suppress("UNCHECKED_CAST")
        return buffer[index] as T?
    }
}
```

---

## Performance Characteristics

| Feature | Memory | CPU | Network |
|---------|--------|-----|---------|
| artemis-anchor | O(IDL size) | O(n) serialize | Single RPC |
| artemis-jupiter | O(1) per quote | O(1) | REST polling |
| artemis-actions | O(action size) | O(1) | GET + POST |
| artemis-universal | O(samples) | O(n²) analyze | Multiple RPC |
| artemis-nlp | O(patterns) | O(n) match | Optional |
| artemis-streaming | O(pool + ring) | O(fields) diff | WebSocket |

---

## Thread Safety

All modules are designed for concurrent use:

- **Immutable data classes** for responses
- **ConcurrentHashMap** for caches
- **AtomicReference** for state updates
- **Flow-based** reactive patterns
- **CoroutineScope** isolation

---

*Technical documentation for Artemis SDK v2.0.0*
