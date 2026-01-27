# Artemis SDK - Mobile App Development Guide

## Building Solana Mobile Apps with Artemis v2.0.0

This guide explains how to use Artemis SDK's revolutionary features in your Android/iOS mobile applications. Artemis is the most comprehensive Solana SDK for mobile, offering capabilities not available in any other SDK.

---

## 📱 Quick Start

### 1. Add Dependencies

```kotlin
// build.gradle.kts
dependencies {
    // Core SDK
    implementation("xyz.selenus:artemis-core:2.0.0")
    implementation("xyz.selenus:artemis-rpc:2.0.0")
    implementation("xyz.selenus:artemis-tx:2.0.0")
    
    // Mobile Wallet Adapter
    implementation("xyz.selenus:artemis-wallet-mwa-android:2.0.0")
    
    // Revolutionary Features (v2.0)
    implementation("xyz.selenus:artemis-anchor:2.0.0")     // Anchor programs
    implementation("xyz.selenus:artemis-jupiter:2.0.0")    // DEX swaps
    implementation("xyz.selenus:artemis-actions:2.0.0")    // Solana Actions/Blinks
    implementation("xyz.selenus:artemis-universal:2.0.0")  // Any program
    implementation("xyz.selenus:artemis-nlp:2.0.0")        // Natural language
    implementation("xyz.selenus:artemis-streaming:2.0.0")  // Real-time updates
}
```

### 2. Initialize the SDK

```kotlin
class MyApplication : Application() {
    
    lateinit var artemis: ArtemisClient
    
    override fun onCreate() {
        super.onCreate()
        
        artemis = ArtemisClient.builder()
            .endpoint(Cluster.MAINNET_BETA)
            .commitment(Commitment.CONFIRMED)
            .build()
    }
}
```

---

## 🎯 Feature 1: Natural Language Transactions (artemis-nlp)

**The world's first natural language transaction builder for blockchain.**

### Use Case: Chat-Based Wallet Interface

Build a wallet where users type commands like "send 1 SOL to alice.sol" instead of filling complex forms.

```kotlin
class ChatWalletViewModel(
    private val nlpBuilder: NaturalLanguageBuilder,
    private val wallet: WalletAdapter
) : ViewModel() {
    
    private val _chatState = MutableStateFlow<ChatState>(ChatState.Idle)
    val chatState = _chatState.asStateFlow()
    
    fun processMessage(userInput: String) = viewModelScope.launch {
        _chatState.value = ChatState.Processing
        
        when (val result = nlpBuilder.parse(userInput)) {
            is ParseResult.Success -> {
                // Show confirmation to user
                _chatState.value = ChatState.Confirmation(
                    summary = result.intent.summary,
                    details = result.intent.details,
                    confidence = result.confidence,
                    onConfirm = { executeTransaction(result) },
                    onCancel = { _chatState.value = ChatState.Idle }
                )
            }
            
            is ParseResult.Ambiguous -> {
                // Let user choose
                _chatState.value = ChatState.ChooseIntent(
                    options = listOf(result.primary) + result.alternatives,
                    onSelect = { selected -> confirmIntent(selected) }
                )
            }
            
            is ParseResult.NeedsInfo -> {
                // Ask for missing information
                _chatState.value = ChatState.AskForInfo(
                    question = "What's the ${result.missing.first()}?",
                    hint = result.suggestion
                )
            }
            
            is ParseResult.Unknown -> {
                _chatState.value = ChatState.Suggestions(
                    message = "I didn't understand that. Try:",
                    suggestions = result.suggestions
                )
            }
        }
    }
    
    private suspend fun executeTransaction(result: ParseResult.Success) {
        val transaction = result.builder.buildTransaction()
        val signedTx = wallet.signTransaction(transaction)
        val signature = artemis.rpc.sendTransaction(signedTx)
        
        _chatState.value = ChatState.Success(
            message = "Transaction sent!",
            signature = signature
        )
    }
}
```

### Composable UI

```kotlin
@Composable
fun ChatWalletScreen(viewModel: ChatWalletViewModel) {
    val state by viewModel.chatState.collectAsState()
    var input by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Chat history
        LazyColumn(modifier = Modifier.weight(1f)) {
            // ... display chat messages
        }
        
        // Suggestions
        LazyRow {
            items(getSuggestions()) { suggestion ->
                SuggestionChip(
                    text = suggestion,
                    onClick = { input = suggestion }
                )
            }
        }
        
        // Input
        TextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Try: Send 1 SOL to alice.sol") },
            trailingIcon = {
                IconButton(onClick = { viewModel.processMessage(input) }) {
                    Icon(Icons.Default.Send, "Send")
                }
            }
        )
    }
}

fun getSuggestions() = listOf(
    "Send 1 SOL to...",
    "Swap 100 USDC for SOL",
    "Stake 10 SOL",
    "Check my balance"
)
```

---

## 🔄 Feature 2: Jupiter DEX Integration (artemis-jupiter)

**Complete DEX aggregator for the best swap prices.**

### Use Case: Token Swap Screen

```kotlin
class SwapViewModel(
    private val jupiter: JupiterClient,
    private val wallet: WalletAdapter
) : ViewModel() {
    
    private val _inputToken = MutableStateFlow(USDC)
    private val _outputToken = MutableStateFlow(SOL)
    private val _inputAmount = MutableStateFlow(0L)
    
    // Live quote updates
    val quote: StateFlow<QuoteState> = combine(
        _inputToken, _outputToken, _inputAmount
    ) { input, output, amount ->
        if (amount > 0) {
            QuoteParams(input, output, amount)
        } else null
    }
    .filterNotNull()
    .debounce(300) // Debounce user input
    .flatMapLatest { params ->
        jupiter.streamQuotes(
            inputMint = params.input.mint,
            outputMint = params.output.mint,
            amount = params.amount,
            slippageBps = 50,
            interval = 3.seconds
        )
    }
    .map { quote -> 
        QuoteState.Ready(
            outputAmount = quote.outAmount,
            priceImpact = quote.priceImpactPct,
            route = quote.routePlan.map { it.swapInfo.label },
            fee = quote.platformFee
        )
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), QuoteState.Loading)
    
    fun executeSwap() = viewModelScope.launch {
        val currentQuote = (quote.value as? QuoteState.Ready)?.quote ?: return@launch
        
        try {
            // Build swap transaction
            val swapResponse = jupiter.swap {
                quote(currentQuote)
                userPublicKey(wallet.publicKey)
                priorityFee(PriorityLevel.MEDIUM)
                dynamicSlippage(true)
            }
            
            // Sign and send
            val signedTx = wallet.signTransaction(swapResponse.transaction)
            val signature = rpc.sendTransaction(signedTx)
            
            _swapState.value = SwapState.Success(signature)
        } catch (e: Exception) {
            _swapState.value = SwapState.Error(e.message)
        }
    }
}
```

### Price Impact Warning

```kotlin
@Composable
fun PriceImpactWarning(priceImpact: Double) {
    val severity = when {
        priceImpact < 0.01 -> Severity.LOW
        priceImpact < 0.05 -> Severity.MEDIUM
        else -> Severity.HIGH
    }
    
    if (severity != Severity.LOW) {
        Card(
            backgroundColor = severity.color.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    tint = severity.color,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Price impact: ${(priceImpact * 100).format(2)}%",
                    color = severity.color
                )
            }
        }
    }
}
```

---

## ⚡ Feature 3: Solana Actions/Blinks (artemis-actions)

**Interact with Solana Actions from your mobile app.**

### Use Case: Blink Scanner

```kotlin
class BlinkScannerViewModel(
    private val actionsClient: ActionsClient,
    private val wallet: WalletAdapter
) : ViewModel() {
    
    fun handleScannedUrl(url: String) = viewModelScope.launch {
        val urlInfo = actionsClient.parseActionUrl(url)
        
        when (urlInfo.type) {
            ActionUrlType.DIRECT_ACTION,
            ActionUrlType.BLINK -> {
                loadAction(urlInfo.actionUrl!!)
            }
            ActionUrlType.SOLANA_PAY -> {
                // Handle Solana Pay
            }
            else -> {
                _state.value = ScanState.NotAnAction
            }
        }
    }
    
    private suspend fun loadAction(url: String) {
        val action = actionsClient.getAction(url)
        
        _state.value = ScanState.ActionLoaded(
            title = action.title,
            description = action.description,
            iconUrl = action.icon,
            buttons = action.links?.actions?.map { linkedAction ->
                ActionButton(
                    label = linkedAction.label,
                    inputs = linkedAction.parameters ?: emptyList(),
                    onClick = { inputs -> 
                        executeAction(url, linkedAction, inputs)
                    }
                )
            } ?: listOf(
                ActionButton(
                    label = action.label,
                    onClick = { executeAction(url, null, emptyMap()) }
                )
            )
        )
    }
    
    private suspend fun executeAction(
        actionUrl: String,
        linkedAction: LinkedAction?,
        inputs: Map<String, String>
    ) {
        val response = if (linkedAction != null) {
            actionsClient.executeLinkedAction(action, linkedAction) {
                account(wallet.publicKey)
                inputs.forEach { (key, value) -> input(key, value) }
            }
        } else {
            actionsClient.executeAction(actionUrl) {
                account(wallet.publicKey)
            }
        }
        
        // Sign and send
        val signedTx = wallet.signTransaction(response.transaction)
        val signature = rpc.sendTransaction(signedTx)
        
        // Handle action chaining
        actionsClient.confirmTransaction(response, signature)
    }
}
```

### Action Card UI

```kotlin
@Composable
fun ActionCard(action: ActionGetResponse, onExecute: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = action.icon,
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(action.title, style = MaterialTheme.typography.h6)
                    Text(
                        action.description,
                        style = MaterialTheme.typography.body2,
                        maxLines = 2
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Action buttons
            action.links?.actions?.forEach { linkedAction ->
                ActionInputForm(linkedAction)
            } ?: Button(
                onClick = onExecute,
                enabled = !action.disabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(action.label)
            }
        }
    }
}
```

---

## 🔧 Feature 4: Anchor Program Integration (artemis-anchor)

**Type-safe Anchor program clients for Kotlin.**

### Use Case: NFT Marketplace

```kotlin
class MarketplaceViewModel(
    private val rpc: RpcApi
) : ViewModel() {
    
    // Load marketplace IDL
    private val marketplaceProgram by lazy {
        val idlJson = assets.open("marketplace_idl.json").readText()
        val idl = AnchorIdl.parse(idlJson)
        AnchorProgram(idl, MARKETPLACE_PROGRAM_ID, rpc)
    }
    
    suspend fun listNft(nftMint: Pubkey, price: Long): String {
        // Derive listing PDA
        val listingPda = marketplaceProgram.pda.derive("listing") {
            seed(nftMint)
            seed(wallet.publicKey)
        }
        
        // Build instruction using Anchor-style API
        val instruction = marketplaceProgram.methods
            .instruction("list_nft")
            .args(mapOf("price" to price))
            .accounts {
                account("listing", listingPda.address)
                account("nft_mint", nftMint)
                signer("seller", wallet.publicKey)
                program("token_program", TOKEN_PROGRAM_ID)
                program("system_program", SYSTEM_PROGRAM_ID)
            }
            .build()
        
        val tx = Transaction(instruction)
        val signedTx = wallet.signTransaction(tx)
        return rpc.sendTransaction(signedTx)
    }
    
    fun watchListings() = marketplaceProgram.account
        .type("Listing")
        .watchAll()
        .map { listings ->
            listings.map { listing ->
                NftListing(
                    mint = listing.get<Pubkey>("nft_mint"),
                    price = listing.get<Long>("price"),
                    seller = listing.get<Pubkey>("seller")
                )
            }
        }
}
```

---

## 🌐 Feature 5: Universal Program Client (artemis-universal)

**Interact with ANY Solana program - no IDL required.**

### Use Case: Explore Unknown Programs

```kotlin
class ProgramExplorerViewModel(
    private val universal: UniversalProgramClient
) : ViewModel() {
    
    fun exploreProgram(programId: String) = viewModelScope.launch {
        _state.value = ExplorerState.Discovering
        
        // Discover program structure from on-chain data
        val program = universal.discover(programId)
        
        _state.value = ExplorerState.Discovered(
            programId = program.programId.toBase58(),
            isAnchor = program.isAnchorProgram,
            confidence = program.confidence,
            instructions = program.instructions.map { instr ->
                DiscoveredInstruction(
                    name = instr.name,
                    discriminator = instr.discriminator.hex,
                    accounts = instr.accounts.size,
                    dataPattern = instr.dataPattern.description
                )
            },
            accountTypes = program.accountTypes.map { type ->
                DiscoveredAccountType(
                    name = type.name,
                    discriminator = type.discriminator.hex,
                    fields = type.fieldPattern.fields.map { it.name }
                )
            },
            stats = DiscoveryStats(
                transactionsSampled = program.transactionsSampled,
                accountsSampled = program.accountsSampled,
                discoveredAt = program.discoveredAt
            )
        )
    }
    
    fun callInstruction(
        program: DiscoveredProgram,
        instructionName: String,
        args: Map<String, Any>
    ) = viewModelScope.launch {
        val ix = universal.buildInstruction(program, instructionName) {
            args.forEach { (key, value) ->
                when (value) {
                    is Long -> u64(key, value)
                    is String -> if (value.length == 44) {
                        pubkey(key, Pubkey.fromBase58(value))
                    } else {
                        string(key, value)
                    }
                    // Add more types as needed
                }
            }
        }
        
        // Build and send transaction
        val tx = Transaction(ix.toInstruction())
        val signedTx = wallet.signTransaction(tx)
        val signature = rpc.sendTransaction(signedTx)
        
        _result.value = ExecutionResult.Success(signature)
    }
}
```

---

## 📊 Feature 6: Zero-Copy Streaming (artemis-streaming)

**Memory-efficient real-time account updates for mobile.**

### Use Case: DeFi Dashboard with Live Prices

```kotlin
class DeFiDashboardViewModel(
    private val stream: ZeroCopyAccountStream
) : ViewModel() {
    
    private val tokenAccounts = listOf(
        "USDC_POOL_ACCOUNT" to TokenPoolSchema,
        "SOL_POOL_ACCOUNT" to TokenPoolSchema,
        "BONK_POOL_ACCOUNT" to TokenPoolSchema
    )
    
    val prices: StateFlow<Map<String, TokenPrice>> = 
        stream.subscribeBatchFlow(tokenAccounts)
            .map { updates ->
                updates.mapValues { (_, accessor) ->
                    // Zero-copy field access - no full deserialization
                    TokenPrice(
                        reserve0 = accessor.getU64("reserve0"),
                        reserve1 = accessor.getU64("reserve1"),
                        lastUpdate = accessor.getI64("last_update_slot")
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())
    
    // Monitor specific fields for changes
    fun watchTokenBalance(tokenAccount: String): Flow<Long> {
        return stream.accountFlow(tokenAccount, TokenAccountSchema)
            .map { accessor -> accessor.getU64("amount") }
            .distinctUntilChanged() // Only emit when balance changes
    }
    
    // Get historical states for charts
    fun getBalanceHistory(tokenAccount: String): List<BalancePoint> {
        return stream.getHistory(tokenAccount).map { snapshot ->
            BalancePoint(
                timestamp = snapshot.timestamp,
                balance = TokenAccountSchema.getU64(snapshot.data, "amount")
            )
        }
    }
}
```

### Battery-Efficient Implementation

```kotlin
@Composable
fun LivePriceDisplay(viewModel: DeFiDashboardViewModel) {
    val prices by viewModel.prices.collectAsState()
    
    // Only recompose changed items
    LazyColumn {
        items(prices.entries.toList(), key = { it.key }) { (token, price) ->
            PriceRow(
                token = token,
                price = price,
                modifier = Modifier.animateItemPlacement()
            )
        }
    }
}

@Composable
fun PriceRow(token: String, price: TokenPrice, modifier: Modifier) {
    // This row only recomposes when its specific price changes
    Row(modifier = modifier.padding(16.dp)) {
        Text(token, modifier = Modifier.weight(1f))
        AnimatedPriceText(
            price = price.calculatePrice(),
            previousPrice = LocalPreviousPrice.current
        )
    }
}
```

---

## 🏗️ Architecture Best Practices

### 1. Dependency Injection

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ArtemisModule {
    
    @Provides
    @Singleton
    fun provideRpcClient(): RpcApi = RpcClient.create(Cluster.MAINNET_BETA)
    
    @Provides
    @Singleton
    fun provideJupiterClient(): JupiterClient = JupiterClient.create()
    
    @Provides
    @Singleton
    fun provideActionsClient(): ActionsClient = ActionsClient.create()
    
    @Provides
    @Singleton
    fun provideNlpBuilder(resolver: EntityResolver): NaturalLanguageBuilder = 
        NaturalLanguageBuilder.create(resolver)
    
    @Provides
    @Singleton
    fun provideUniversalClient(rpc: RpcApi): UniversalProgramClient = 
        UniversalProgramClient.create(rpc)
    
    @Provides
    @Singleton
    fun provideAccountStream(wsClient: WebSocketClient): ZeroCopyAccountStream = 
        ZeroCopyAccountStream.create(wsClient)
}
```

### 2. Error Handling

```kotlin
sealed class ArtemisResult<T> {
    data class Success<T>(val data: T) : ArtemisResult<T>()
    data class Error<T>(val error: ArtemisError) : ArtemisResult<T>()
}

sealed class ArtemisError {
    data class RpcError(val code: Int, val message: String) : ArtemisError()
    data class NetworkError(val cause: Throwable) : ArtemisError()
    data class TransactionError(val logs: List<String>) : ArtemisError()
    data class ValidationError(val message: String) : ArtemisError()
}

// Extension function for safe calls
suspend fun <T> safeArtemisCall(block: suspend () -> T): ArtemisResult<T> = try {
    ArtemisResult.Success(block())
} catch (e: RpcException) {
    ArtemisResult.Error(ArtemisError.RpcError(e.code, e.message))
} catch (e: IOException) {
    ArtemisResult.Error(ArtemisError.NetworkError(e))
}
```

### 3. Offline Support

```kotlin
class OfflineFirstRepository(
    private val localCache: ArtemisCache,
    private val remoteSource: RpcApi
) {
    suspend fun getTokenBalances(): Flow<List<TokenBalance>> = flow {
        // Emit cached first
        emit(localCache.getTokenBalances())
        
        // Then fetch fresh and update cache
        try {
            val fresh = remoteSource.getTokenAccountsByOwner(wallet)
            localCache.saveTokenBalances(fresh)
            emit(fresh)
        } catch (e: Exception) {
            // Network error - cached data already emitted
        }
    }
}
```

---

## 📱 Platform-Specific Integration

### Android Deep Links for Blinks

```xml
<!-- AndroidManifest.xml -->
<activity android:name=".BlinkHandlerActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data android:scheme="solana"/>
        <data android:scheme="solana-action"/>
    </intent-filter>
</activity>
```

```kotlin
class BlinkHandlerActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        intent?.data?.let { uri ->
            val actionsClient = ActionsClient.create()
            val urlInfo = actionsClient.parseActionUrl(uri.toString())
            
            if (urlInfo.type != ActionUrlType.UNKNOWN) {
                // Navigate to action handling screen
                navigateToAction(urlInfo)
            }
        }
    }
}
```

### iOS Integration (via KMM)

```kotlin
// shared/src/iosMain/kotlin
actual class PlatformWallet : WalletAdapter {
    actual suspend fun signTransaction(tx: Transaction): SignedTransaction {
        // Use iOS-specific signing
        return withContext(Dispatchers.Main) {
            PhantomWalletIOS.signTransaction(tx)
        }
    }
}
```

---

## 🎓 Learning Resources

1. **Sample Apps:** Check `/samples` directory for complete examples
2. **API Documentation:** Full KDoc at `docs.selenus.xyz`
3. **Video Tutorials:** Coming soon on YouTube
4. **Discord Community:** Join for support

---

## 📄 License

Artemis SDK is licensed under Apache 2.0. See [LICENSE](../LICENSE) for details.

---

*Built with ❤️ by Selenus Technologies*
