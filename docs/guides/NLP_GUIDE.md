# artemis-nlp - Natural Language Transaction Builder

## 🌟 World's First Natural Language Blockchain Transaction Builder

Build Solana transactions by simply typing what you want to do in plain English. No forms, no technical knowledge, no learning curve.

---

## Overview

`artemis-nlp` is a revolutionary module that parses natural language input and converts it into ready-to-sign Solana transactions. Unlike AI/LLM-based approaches, this uses deterministic pattern matching that:

- ✅ Works completely **offline**
- ✅ No external API calls
- ✅ No privacy concerns
- ✅ Instant response
- ✅ Predictable behavior

---

## Installation

```kotlin
implementation("xyz.selenus:artemis-nlp:2.0.0")
```

---

## Quick Start

```kotlin
import com.selenus.artemis.nlp.*

// Create the NLP builder
val resolver = EntityResolver.create(rpc)
val nlp = NaturalLanguageBuilder.create(resolver)

// Parse user input
val result = nlp.parse("send 1 SOL to alice.sol")

when (result) {
    is ParseResult.Success -> {
        // Transaction understood!
        println(result.intent.summary)
        // "Transfer 1 SOL to alice.sol (7xKXn...)"
        
        // Build and send
        val tx = result.buildTransaction()
        val signature = wallet.signAndSend(tx)
    }
    
    is ParseResult.Ambiguous -> {
        // Multiple interpretations possible
        println("Did you mean:")
        result.alternatives.forEach { 
            println("  - ${it.summary}") 
        }
    }
    
    is ParseResult.NeedsInfo -> {
        // Missing required information
        println("Please provide: ${result.missing}")
        println("Suggestion: ${result.suggestion}")
    }
    
    is ParseResult.Unknown -> {
        // Couldn't understand
        println("Try one of these:")
        result.suggestions.forEach { 
            println("  - ${it.template}") 
        }
    }
}
```

---

## Supported Commands

### Transfers

```
"send 1 SOL to alice.sol"
"send 100 USDC to 7xKXn..."
"transfer 50 BONK to bob.sol"
"pay alice.sol 10 USDT"
```

**Entities extracted:**
- Amount (required)
- Token (required, defaults to SOL)
- Recipient (required, .sol domain or base58 address)

### Swaps

```
"swap 100 USDC for SOL"
"exchange 50 USDT to USDC"
"convert 1 SOL to BONK"
"buy 0.5 SOL worth of BONK"
"sell 1000 BONK for USDC"
```

**Entities extracted:**
- Amount (required)
- Input token (required)
- Output token (required)

### Staking

```
"stake 10 SOL"
"stake 10 SOL with Marinade"
"unstake 5 SOL"
"claim staking rewards"
```

**Entities extracted:**
- Amount (required for stake/unstake)
- Validator/Protocol (optional)

### Token Operations

```
"create token with 1M supply"
"mint 1000 tokens to alice.sol"
"burn 500 tokens"
```

### NFT Operations

```
"list my NFT for 5 SOL"
"buy NFT at 7xKXn..."
"transfer NFT to alice.sol"
```

---

## Entity Resolution

The NLP builder automatically resolves various entity types:

### Addresses
- **Base58 addresses:** `7xKXn...` → Validated and used directly
- **SNS domains:** `alice.sol` → Resolved via Solana Name Service
- **Known aliases:** `@alice` → Looked up in local address book

### Tokens
- **Symbols:** `SOL`, `USDC`, `BONK` → Resolved to mint addresses
- **Names:** `Solana`, `USD Coin` → Matched to known tokens
- **Addresses:** Direct mint addresses supported

### Amounts
- **Integers:** `100` → Converted to lamports/smallest unit
- **Decimals:** `1.5` → Properly scaled
- **Suffixes:** `1M`, `1K` → Expanded (1M = 1,000,000)

---

## Building a Chat-Based Wallet

### ViewModel

```kotlin
class ChatWalletViewModel(
    private val nlp: NaturalLanguageBuilder,
    private val wallet: WalletAdapter
) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()
    
    private val _pendingTransaction = MutableStateFlow<PendingTransaction?>(null)
    val pendingTransaction = _pendingTransaction.asStateFlow()
    
    fun sendMessage(text: String) {
        // Add user message
        addMessage(ChatMessage.User(text))
        
        viewModelScope.launch {
            processInput(text)
        }
    }
    
    private suspend fun processInput(input: String) {
        when (val result = nlp.parse(input)) {
            is ParseResult.Success -> {
                addMessage(ChatMessage.Bot(
                    "Got it! Here's what I'll do:\n\n" +
                    "**${result.intent.summary}**\n\n" +
                    "Confirm to proceed."
                ))
                
                _pendingTransaction.value = PendingTransaction(
                    intent = result.intent,
                    builder = result.builder
                )
            }
            
            is ParseResult.NeedsInfo -> {
                addMessage(ChatMessage.Bot(
                    "I need more information. ${result.suggestion}"
                ))
            }
            
            is ParseResult.Ambiguous -> {
                addMessage(ChatMessage.Bot(
                    "I found multiple options:\n\n" +
                    result.alternatives.mapIndexed { i, alt ->
                        "${i + 1}. ${alt.summary}"
                    }.joinToString("\n")
                ))
            }
            
            is ParseResult.Unknown -> {
                addMessage(ChatMessage.Bot(
                    "I didn't understand that. Try:\n\n" +
                    result.suggestions.take(3).joinToString("\n") { 
                        "• ${it.template}" 
                    }
                ))
            }
        }
    }
    
    fun confirmTransaction() {
        val pending = _pendingTransaction.value ?: return
        
        viewModelScope.launch {
            try {
                addMessage(ChatMessage.Bot("Sending transaction..."))
                
                val tx = pending.builder.buildTransaction()
                val signature = wallet.signAndSend(tx)
                
                addMessage(ChatMessage.Bot(
                    "✅ Transaction sent!\n\n" +
                    "Signature: `${signature.take(20)}...`"
                ))
                
                _pendingTransaction.value = null
            } catch (e: Exception) {
                addMessage(ChatMessage.Bot("❌ Failed: ${e.message}"))
            }
        }
    }
    
    fun cancelTransaction() {
        _pendingTransaction.value = null
        addMessage(ChatMessage.Bot("Transaction cancelled."))
    }
}
```

### Compose UI

```kotlin
@Composable
fun ChatWalletScreen(viewModel: ChatWalletViewModel) {
    val messages by viewModel.messages.collectAsState()
    val pending by viewModel.pendingTransaction.collectAsState()
    var input by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Chat messages
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true
        ) {
            items(messages.reversed()) { message ->
                ChatBubble(message)
            }
        }
        
        // Confirmation buttons
        pending?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.cancelTransaction() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { viewModel.confirmTransaction() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Confirm")
                }
            }
        }
        
        // Quick suggestions
        LazyRow(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickSuggestions) { suggestion ->
                SuggestionChip(
                    onClick = { input = suggestion },
                    label = { Text(suggestion) }
                )
            }
        }
        
        // Input field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a command...") },
                singleLine = true
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        viewModel.sendMessage(input)
                        input = ""
                    }
                }
            ) {
                Icon(Icons.Default.Send, "Send")
            }
        }
    }
}

val quickSuggestions = listOf(
    "send 1 SOL to...",
    "swap USDC for SOL",
    "check balance",
    "stake SOL"
)
```

---

## Custom Patterns

You can add custom patterns for your specific use case:

```kotlin
val customNlp = NaturalLanguageBuilder.create(resolver) {
    // Add custom pattern
    pattern(
        intentType = IntentType.CUSTOM,
        template = "vote {option} on proposal {id}",
        patterns = listOf(
            Regex("vote\\s+(yes|no|abstain)\\s+on\\s+proposal\\s+(\\d+)")
        ),
        requiredEntities = listOf(EntityType.STRING, EntityType.NUMBER),
        description = "Vote on a DAO proposal"
    ) { entities ->
        VoteIntent(
            option = entities[0].value,
            proposalId = entities[1].value.toLong()
        )
    }
}

// Now works:
val result = customNlp.parse("vote yes on proposal 42")
```

---

## Custom Entity Resolver

```kotlin
class MyEntityResolver(
    private val addressBook: AddressBook,
    private val tokenRegistry: TokenRegistry
) : EntityResolver {
    
    override suspend fun resolveDomain(domain: String): Pubkey? {
        // Custom SNS resolution
        return snsClient.resolve(domain)
    }
    
    override suspend fun resolveToken(symbol: String): TokenInfo? {
        // Check local registry first
        return tokenRegistry.find(symbol)
            ?: jupiterClient.getTokenInfo(symbol)
    }
    
    override fun isKnownToken(symbol: String): Boolean {
        return tokenRegistry.contains(symbol) ||
               COMMON_TOKENS.contains(symbol.uppercase())
    }
    
    override suspend fun resolveAddress(input: String): Pubkey? {
        // Check address book
        return addressBook.lookup(input)
            ?: if (input.length in 32..44) {
                Pubkey.fromBase58OrNull(input)
            } else null
    }
}
```

---

## Localization

The NLP builder supports multiple languages:

```kotlin
val nlp = NaturalLanguageBuilder.create(resolver) {
    language(Language.SPANISH)
}

// Spanish commands
nlp.parse("enviar 1 SOL a alice.sol")
nlp.parse("cambiar 100 USDC por SOL")
```

Supported languages:
- English (default)
- Spanish
- Portuguese
- French
- German
- Chinese (Simplified)
- Japanese
- Korean

---

## Voice Integration

Combine with Android Speech Recognition:

```kotlin
class VoiceWalletActivity : AppCompatActivity() {
    
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
    private val nlp = NaturalLanguageBuilder.create(resolver)
    
    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        speechRecognizer.startListening(intent)
    }
    
    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                matches?.firstOrNull()?.let { spokenText ->
                    lifecycleScope.launch {
                        val result = nlp.parse(spokenText)
                        handleResult(result)
                    }
                }
            }
            // ... other callbacks
        })
    }
}
```

---

## Best Practices

### 1. Always Show Confirmation
```kotlin
// Never auto-execute transactions
when (result) {
    is ParseResult.Success -> {
        showConfirmationDialog(
            summary = result.intent.summary,
            details = result.intent.details,
            onConfirm = { execute(result) },
            onCancel = { /* dismiss */ }
        )
    }
}
```

### 2. Provide Context
```kotlin
// Set context for better resolution
nlp.setContext(NlpContext(
    defaultToken = "SOL",
    recentRecipients = listOf("alice.sol", "bob.sol"),
    preferredDex = "Jupiter"
))
```

### 3. Handle Errors Gracefully
```kotlin
when (result) {
    is ParseResult.Unknown -> {
        // Show helpful suggestions
        showSuggestions(result.suggestions)
    }
    is ParseResult.NeedsInfo -> {
        // Ask for specific missing info
        promptForInput(result.missing.first(), result.suggestion)
    }
}
```

---

## API Reference

### ParseResult

```kotlin
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
```

### TransactionIntent

```kotlin
sealed class TransactionIntent {
    abstract val summary: String
    abstract val details: Map<String, String>
    
    data class Transfer(
        val amount: BigDecimal,
        val token: TokenInfo,
        val recipient: Pubkey
    ) : TransactionIntent()
    
    data class Swap(
        val amount: BigDecimal,
        val fromToken: TokenInfo,
        val toToken: TokenInfo
    ) : TransactionIntent()
    
    // ... more intent types
}
```

---

## Troubleshooting

### "Unknown command"
- Check spelling
- Use simpler phrasing
- Check supported command list

### Domain resolution fails
- Ensure `.sol` domain exists
- Check network connectivity
- Verify SNS is accessible

### Token not recognized
- Use standard symbol (USDC, not "US Dollar Coin")
- Check token is in registry
- Use mint address directly

---

*artemis-nlp - Making blockchain accessible to everyone*
