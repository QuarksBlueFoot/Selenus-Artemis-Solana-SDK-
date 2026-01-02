# artemis-jupiter - Native Jupiter DEX Integration

## 🌟 First Native Kotlin Jupiter SDK for Mobile

Complete Jupiter DEX integration with quote fetching, route building, and swap execution. Perfect for mobile wallets and DeFi applications.

---

## Overview

`artemis-jupiter` provides native Kotlin/JVM access to Jupiter's swap aggregator, enabling:

- ✅ **Quote fetching** with optimal routes
- ✅ **Slippage protection** and price impact warnings
- ✅ **Route visualization** for user transparency
- ✅ **Dynamic compute units** based on route complexity
- ✅ **Priority fee integration** for fast execution
- ✅ **Streaming price updates** for real-time UX

---

## Installation

```kotlin
implementation("xyz.selenus:artemis-jupiter:2.0.0")
```

---

## Quick Start

```kotlin
import com.selenus.artemis.jupiter.*

// Create Jupiter client
val jupiter = JupiterClient.create()

// Get a quote
val quote = jupiter.quote(
    inputMint = Tokens.USDC,
    outputMint = Tokens.SOL,
    amount = 100_000_000UL,  // 100 USDC (6 decimals)
    slippageBps = 50          // 0.5%
)

println("Input: ${quote.inputAmount} USDC")
println("Output: ${quote.outputAmount} SOL")
println("Price Impact: ${quote.priceImpactPct}%")
println("Routes: ${quote.routePlan.size} hops")

// Build swap transaction
val tx = jupiter.swap(quote) {
    userPublicKey = wallet.publicKey
    wrapUnwrapSOL = true
    feeAccount = null  // Optional referral fee
}

// Sign and send
val signature = wallet.signAndSend(tx)
println("Swap executed: $signature")
```

---

## Quote Options

### Basic Quote

```kotlin
val quote = jupiter.quote(
    inputMint = Tokens.USDC,
    outputMint = Tokens.SOL,
    amount = 100_000_000UL
)
```

### Advanced Quote Options

```kotlin
val quote = jupiter.quote {
    inputMint = Tokens.USDC
    outputMint = Tokens.SOL
    amount = 100_000_000UL
    
    // Slippage
    slippageBps = 50  // 0.5%
    
    // Route restrictions
    onlyDirectRoutes = false
    maxAccounts = 64
    
    // DEX filtering
    excludeDexes = listOf("GooseFX", "Cropper")
    // or
    restrictDexes = listOf("Orca", "Raydium")
    
    // Platform fee
    platformFeeBps = 20  // 0.2% fee to your platform
}
```

### Exact Output Quote

```kotlin
// When user wants exactly X output tokens
val quote = jupiter.quoteExactOut {
    inputMint = Tokens.USDC
    outputMint = Tokens.SOL
    outputAmount = 1_000_000_000UL  // Exactly 1 SOL
    slippageBps = 100
}

println("Need ${quote.inputAmount} USDC for exactly 1 SOL")
```

---

## Swap Execution

### Basic Swap

```kotlin
val tx = jupiter.swap(quote) {
    userPublicKey = wallet.publicKey
}

val signature = wallet.signAndSend(tx)
```

### With Compute Budget

```kotlin
val tx = jupiter.swap(quote) {
    userPublicKey = wallet.publicKey
    
    // Auto-calculate based on route complexity
    dynamicComputeUnitLimit = true
    
    // Or set manually
    computeUnitLimit = 400_000
    computeUnitPriceMicroLamports = 10_000
}
```

### With Unwrap/Wrap SOL

```kotlin
val tx = jupiter.swap(quote) {
    userPublicKey = wallet.publicKey
    
    // Automatically wrap input SOL to wSOL
    wrapUnwrapSOL = true
    
    // Use existing wSOL account instead
    // useSharedAccounts = true
}
```

### Referral Fees

```kotlin
val tx = jupiter.swap(quote) {
    userPublicKey = wallet.publicKey
    
    // Collect platform fees
    feeAccount = myPlatformFeeAccount
    
    // Track attribution
    trackingAccount = myTrackingAccount
}
```

---

## Route Visualization

### Display Route Path

```kotlin
val quote = jupiter.quote(...)

// Get human-readable route
val routeInfo = quote.routeInfo

println("Swap Path:")
routeInfo.route.forEachIndexed { index, leg ->
    val arrow = if (index < routeInfo.route.lastIndex) " → " else ""
    println("  ${leg.inputSymbol} → ${leg.outputSymbol} via ${leg.dexName}$arrow")
}

println("\nBreakdown:")
println("  Input: ${routeInfo.inputAmount} ${routeInfo.inputSymbol}")
println("  Output: ${routeInfo.outputAmount} ${routeInfo.outputSymbol}")
println("  Price Impact: ${routeInfo.priceImpact}%")
println("  Platform Fee: ${routeInfo.platformFee ?: "None"}")
```

### Compose Route Visualization

```kotlin
@Composable
fun SwapRouteDisplay(quote: JupiterQuote) {
    val routeInfo = quote.routeInfo
    
    Column {
        // Route path
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            routeInfo.route.forEachIndexed { index, leg ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Token icon
                    AsyncImage(
                        model = leg.inputLogoUri,
                        contentDescription = leg.inputSymbol,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(leg.inputSymbol, style = MaterialTheme.typography.bodySmall)
                }
                
                if (index <= routeInfo.route.lastIndex) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ArrowForward, "→")
                        Text(
                            leg.dexName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            
            // Final output token
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = routeInfo.route.last().outputLogoUri,
                    contentDescription = routeInfo.outputSymbol,
                    modifier = Modifier.size(32.dp)
                )
                Text(routeInfo.outputSymbol, style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Price Impact", style = MaterialTheme.typography.labelSmall)
                val impactColor = when {
                    routeInfo.priceImpact < 0.1 -> Color.Green
                    routeInfo.priceImpact < 1.0 -> Color.Yellow
                    else -> Color.Red
                }
                Text(
                    "${routeInfo.priceImpact}%",
                    color = impactColor
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text("Minimum Received", style = MaterialTheme.typography.labelSmall)
                Text("${routeInfo.minimumOutput} ${routeInfo.outputSymbol}")
            }
        }
    }
}
```

---

## Price Streaming

### Real-time Price Updates

```kotlin
// Stream price updates for live UI
val priceFlow = jupiter.streamPrices(
    inputMint = Tokens.USDC,
    outputMint = Tokens.SOL,
    amount = 100_000_000UL,
    interval = Duration.seconds(5)
)

priceFlow.collect { quote ->
    updateUI(quote)
}
```

### ViewModel Integration

```kotlin
class SwapViewModel(
    private val jupiter: JupiterClient
) : ViewModel() {
    
    private val _inputToken = MutableStateFlow(Tokens.USDC)
    private val _outputToken = MutableStateFlow(Tokens.SOL)
    private val _inputAmount = MutableStateFlow("")
    
    private val _quote = MutableStateFlow<JupiterQuote?>(null)
    val quote = _quote.asStateFlow()
    
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    
    init {
        // Auto-refresh quotes when inputs change
        viewModelScope.launch {
            combine(
                _inputToken,
                _outputToken,
                _inputAmount.debounce(300)
            ) { input, output, amount ->
                Triple(input, output, amount)
            }.collect { (input, output, amountStr) ->
                val amount = amountStr.toBigDecimalOrNull()
                if (amount != null && amount > BigDecimal.ZERO) {
                    fetchQuote(input, output, amount)
                } else {
                    _quote.value = null
                }
            }
        }
    }
    
    private suspend fun fetchQuote(
        input: TokenInfo,
        output: TokenInfo,
        amount: BigDecimal
    ) {
        _loading.value = true
        try {
            val atomicAmount = amount
                .multiply(BigDecimal.TEN.pow(input.decimals))
                .toLong()
                .toULong()
            
            val newQuote = jupiter.quote(
                inputMint = input.mint,
                outputMint = output.mint,
                amount = atomicAmount,
                slippageBps = 50
            )
            _quote.value = newQuote
        } catch (e: Exception) {
            _quote.value = null
            // Handle error
        } finally {
            _loading.value = false
        }
    }
    
    fun setInputAmount(amount: String) {
        _inputAmount.value = amount
    }
    
    fun swapTokens() {
        val temp = _inputToken.value
        _inputToken.value = _outputToken.value
        _outputToken.value = temp
    }
    
    suspend fun executeSwap(wallet: WalletAdapter): String {
        val currentQuote = _quote.value
            ?: throw IllegalStateException("No quote available")
        
        val tx = jupiter.swap(currentQuote) {
            userPublicKey = wallet.publicKey
            wrapUnwrapSOL = true
            dynamicComputeUnitLimit = true
        }
        
        return wallet.signAndSend(tx)
    }
}
```

---

## Token Lists

### Fetch Available Tokens

```kotlin
// Get all tradeable tokens
val tokens = jupiter.tokens()

tokens.take(10).forEach { token ->
    println("${token.symbol}: ${token.name}")
    println("  Mint: ${token.address}")
    println("  Decimals: ${token.decimals}")
    println("  Logo: ${token.logoURI}")
}
```

### Token Search

```kotlin
// Search by name or symbol
val results = jupiter.searchTokens("USD")

results.forEach { token ->
    println("${token.symbol} - ${token.name}")
}
```

### Token Prices

```kotlin
// Get USD prices
val prices = jupiter.prices(listOf(Tokens.SOL, Tokens.USDC, Tokens.BONK))

prices.forEach { (mint, price) ->
    println("${mint}: \$${price.price}")
}
```

---

## Complete Swap UI

### SwapScreen Composable

```kotlin
@Composable
fun SwapScreen(
    viewModel: SwapViewModel,
    wallet: WalletAdapter
) {
    val quote by viewModel.quote.collectAsState()
    val loading by viewModel.loading.collectAsState()
    var inputAmount by remember { mutableStateOf("") }
    var showConfirmation by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Input token
        TokenInputField(
            label = "You Pay",
            amount = inputAmount,
            onAmountChange = { 
                inputAmount = it
                viewModel.setInputAmount(it)
            },
            token = viewModel.inputToken,
            onTokenSelect = { /* show token picker */ }
        )
        
        // Swap button
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { viewModel.swapTokens() },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = "Swap",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        
        // Output token
        TokenInputField(
            label = "You Receive",
            amount = quote?.let { 
                formatAmount(it.outAmount, it.outputDecimals) 
            } ?: "",
            onAmountChange = { },
            token = viewModel.outputToken,
            onTokenSelect = { /* show token picker */ },
            readOnly = true
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Route info
        quote?.let { q ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SwapRouteDisplay(q)
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rate")
                        Text("1 ${q.inputSymbol} ≈ ${q.rate} ${q.outputSymbol}")
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Price Impact")
                        Text(
                            "${q.priceImpactPct}%",
                            color = when {
                                q.priceImpactPct < 0.1 -> Color.Green
                                q.priceImpactPct < 1.0 -> Color(0xFFFFA500)
                                else -> Color.Red
                            }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Minimum Received")
                        Text("${q.minimumReceived} ${q.outputSymbol}")
                    }
                }
            }
        }
        
        Spacer(Modifier.weight(1f))
        
        // Swap button
        Button(
            onClick = { showConfirmation = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = quote != null && !loading
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Swap", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    
    // Confirmation dialog
    if (showConfirmation && quote != null) {
        SwapConfirmationDialog(
            quote = quote!!,
            onConfirm = {
                showConfirmation = false
                // Execute swap
            },
            onDismiss = { showConfirmation = false }
        )
    }
}
```

---

## Error Handling

### Quote Errors

```kotlin
try {
    val quote = jupiter.quote(...)
} catch (e: InsufficientLiquidityException) {
    showError("Not enough liquidity for this trade")
} catch (e: RouteNotFoundException) {
    showError("No route found between these tokens")
} catch (e: JupiterApiException) {
    showError("Jupiter API error: ${e.message}")
}
```

### Swap Errors

```kotlin
try {
    val tx = jupiter.swap(quote) { ... }
    val sig = wallet.signAndSend(tx)
} catch (e: SlippageExceededException) {
    showError("Price moved too much. Try increasing slippage.")
} catch (e: InsufficientBalanceException) {
    showError("Insufficient balance")
} catch (e: TransactionFailedException) {
    when (e.errorCode) {
        6000 -> showError("Slippage exceeded")
        6001 -> showError("Expired quote")
        else -> showError("Transaction failed: ${e.message}")
    }
}
```

---

## Advanced Features

### Limit Orders

```kotlin
// Create limit order (requires Jupiter Limit Order program)
val order = jupiter.createLimitOrder {
    inputMint = Tokens.USDC
    outputMint = Tokens.SOL
    inputAmount = 100_000_000UL  // 100 USDC
    outputAmount = 500_000_000UL  // 0.5 SOL (limit price)
    expiry = Clock.System.now() + Duration.hours(24)
}

val signature = wallet.signAndSend(order)
```

### DCA (Dollar Cost Average)

```kotlin
// Set up recurring swaps
val dca = jupiter.createDCA {
    inputMint = Tokens.USDC
    outputMint = Tokens.SOL
    inAmount = 10_000_000UL  // 10 USDC per swap
    inAmountPerCycle = 10_000_000UL
    cycleFrequency = Duration.days(1)  // Daily
    minOutAmount = null  // No minimum
    maxOutAmount = null  // No maximum
    startAt = Clock.System.now()
}
```

---

## Best Practices

### 1. Always Show Price Impact

```kotlin
quote?.let { q ->
    when {
        q.priceImpactPct >= 5.0 -> {
            showWarning("HIGH PRICE IMPACT: ${q.priceImpactPct}%")
        }
        q.priceImpactPct >= 1.0 -> {
            showCaution("Price impact: ${q.priceImpactPct}%")
        }
    }
}
```

### 2. Refresh Quotes Before Execution

```kotlin
suspend fun executeSwap() {
    // Refresh quote to get latest prices
    val freshQuote = jupiter.quote(
        inputMint = currentQuote.inputMint,
        outputMint = currentQuote.outputMint,
        amount = currentQuote.inputAmount
    )
    
    // Check if price changed significantly
    val priceDiff = abs(freshQuote.outputAmount - currentQuote.outputAmount) / 
                    currentQuote.outputAmount.toDouble()
    
    if (priceDiff > 0.01) {  // 1% change
        showConfirmation("Price changed. New output: ${freshQuote.outputAmount}")
        return
    }
    
    // Execute with fresh quote
    val tx = jupiter.swap(freshQuote) { ... }
}
```

### 3. Use Dynamic Compute Units

```kotlin
val tx = jupiter.swap(quote) {
    userPublicKey = wallet.publicKey
    
    // Let Jupiter calculate optimal compute units
    dynamicComputeUnitLimit = true
    
    // This prevents failed txs due to compute limits
}
```

---

## API Reference

### JupiterClient

```kotlin
class JupiterClient {
    suspend fun quote(
        inputMint: Pubkey,
        outputMint: Pubkey,
        amount: ULong,
        slippageBps: Int = 50,
        swapMode: SwapMode = SwapMode.EXACT_IN
    ): JupiterQuote
    
    suspend fun quote(block: QuoteRequest.() -> Unit): JupiterQuote
    
    suspend fun swap(
        quote: JupiterQuote,
        block: SwapRequest.() -> Unit
    ): Transaction
    
    suspend fun tokens(): List<TokenInfo>
    suspend fun prices(mints: List<Pubkey>): Map<Pubkey, TokenPrice>
    
    fun streamPrices(
        inputMint: Pubkey,
        outputMint: Pubkey,
        amount: ULong,
        interval: Duration = Duration.seconds(10)
    ): Flow<JupiterQuote>
    
    companion object {
        fun create(
            baseUrl: String = "https://quote-api.jup.ag",
            httpClient: HttpClient? = null
        ): JupiterClient
    }
}
```

### JupiterQuote

```kotlin
data class JupiterQuote(
    val inputMint: Pubkey,
    val outputMint: Pubkey,
    val inAmount: ULong,
    val outAmount: ULong,
    val otherAmountThreshold: ULong,
    val swapMode: SwapMode,
    val slippageBps: Int,
    val priceImpactPct: Double,
    val routePlan: List<RoutePlanStep>,
    val contextSlot: Long,
    val timeTaken: Double
) {
    val routeInfo: RouteInfo
    val rate: BigDecimal
    val minimumReceived: BigDecimal
}
```

---

*artemis-jupiter - Bringing Jupiter DEX to Kotlin/Android*
