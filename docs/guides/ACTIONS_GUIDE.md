# artemis-actions - Solana Actions & Blinks

## 🌟 First Kotlin Implementation of Solana Actions

Full implementation of the Solana Actions specification, enabling Blinks (Blockchain Links) support for mobile wallets. Create shareable, executable blockchain actions.

---

## Overview

`artemis-actions` implements the complete [Solana Actions Specification](https://solana.com/docs/advanced/actions) for Kotlin/JVM, enabling:

- ✅ **Action URL parsing** and validation
- ✅ **Action metadata fetching** with icons and labels
- ✅ **Transaction request handling** for any action
- ✅ **Parameter validation** with type checking
- ✅ **QR code generation** for Blinks
- ✅ **Deep link handling** for mobile integration

---

## Installation

```kotlin
implementation("xyz.selenus:artemis-actions:2.0.0")
```

---

## Quick Start

### Executing an Action

```kotlin
import com.selenus.artemis.actions.*

// Parse an action URL
val actionUrl = "solana-action:https://example.com/api/donate"
val client = ActionsClient.create()

// Fetch action metadata
val action = client.fetchAction(actionUrl)

println("Title: ${action.title}")
println("Description: ${action.description}")
println("Icon: ${action.icon}")

// Show available actions
action.links.actions.forEach { linkedAction ->
    println("Action: ${linkedAction.label}")
}

// Execute an action
val tx = client.execute(action.links.actions[0]) {
    account = wallet.publicKey
}

// Sign and send
val signature = wallet.signAndSend(tx)
```

### Handling Actions with Parameters

```kotlin
// Action with input parameters
val action = client.fetchAction("solana-action:https://example.com/api/swap")

// Find the action with parameters
val swapAction = action.links.actions.find { it.parameters != null }!!

// Build parameter map
val params = mapOf(
    "amount" to "100",
    "token" to "USDC"
)

// Execute with parameters
val tx = client.execute(swapAction, params) {
    account = wallet.publicKey
}
```

---

## Action Structure

### Understanding the Actions Spec

```kotlin
data class Action(
    val type: ActionType,           // "action" | "completed" | "error"
    val title: String,              // Display title
    val description: String,        // Description
    val icon: String,               // Icon URL
    val label: String,              // Primary button label
    val disabled: Boolean?,         // If action is disabled
    val error: ActionError?,        // Error info if failed
    val links: ActionLinks?         // Linked actions
)

data class ActionLinks(
    val actions: List<LinkedAction>
)

data class LinkedAction(
    val label: String,              // Button label
    val href: String,               // Action endpoint
    val parameters: List<ActionParameter>?  // Input parameters
)

data class ActionParameter(
    val name: String,               // Parameter name
    val label: String?,             // Display label
    val required: Boolean?,         // If required
    val type: ParameterType?,       // text | email | url | number | date | etc
    val pattern: String?,           // Regex pattern for validation
    val patternDescription: String?, // Error message for pattern
    val min: String?,               // Min value (for number/date)
    val max: String?,               // Max value (for number/date)
    val options: List<ActionOption>? // For select parameters
)
```

---

## Building a Blinks-Enabled Wallet

### ViewModel

```kotlin
class ActionViewModel(
    private val actionsClient: ActionsClient,
    private val wallet: WalletAdapter
) : ViewModel() {
    
    private val _action = MutableStateFlow<Action?>(null)
    val action = _action.asStateFlow()
    
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    
    private val _parameters = MutableStateFlow<Map<String, String>>(emptyMap())
    val parameters = _parameters.asStateFlow()
    
    fun loadAction(url: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            
            try {
                val parsed = ActionsClient.parseUrl(url)
                val fetchedAction = actionsClient.fetchAction(parsed)
                _action.value = fetchedAction
            } catch (e: InvalidActionUrlException) {
                _error.value = "Invalid action URL"
            } catch (e: ActionFetchException) {
                _error.value = "Failed to fetch action: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }
    
    fun setParameter(name: String, value: String) {
        _parameters.value = _parameters.value + (name to value)
    }
    
    fun executeAction(linkedAction: LinkedAction) {
        viewModelScope.launch {
            _loading.value = true
            
            try {
                // Validate parameters
                linkedAction.parameters?.forEach { param ->
                    if (param.required == true) {
                        val value = _parameters.value[param.name]
                        if (value.isNullOrBlank()) {
                            throw MissingParameterException(param.name)
                        }
                    }
                }
                
                // Execute action
                val tx = actionsClient.execute(
                    linkedAction, 
                    _parameters.value
                ) {
                    account = wallet.publicKey
                }
                
                val signature = wallet.signAndSend(tx)
                
                // Handle success
                _action.value = Action(
                    type = ActionType.COMPLETED,
                    title = "Success!",
                    description = "Transaction confirmed",
                    icon = _action.value?.icon ?: "",
                    label = "Done"
                )
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }
}
```

### Action Display Composable

```kotlin
@Composable
fun ActionScreen(viewModel: ActionViewModel) {
    val action by viewModel.action.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val parameters by viewModel.parameters.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        action?.let { a ->
            // Header
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon
                    AsyncImage(
                        model = a.icon,
                        contentDescription = a.title,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Title
                    Text(
                        text = a.title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    // Description
                    Text(
                        text = a.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Action buttons
            when (a.type) {
                ActionType.ACTION -> {
                    a.links?.actions?.forEach { linkedAction ->
                        ActionButton(
                            action = linkedAction,
                            parameters = parameters,
                            onParameterChange = { name, value ->
                                viewModel.setParameter(name, value)
                            },
                            onExecute = {
                                viewModel.executeAction(linkedAction)
                            },
                            loading = loading
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                
                ActionType.COMPLETED -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        modifier = Modifier.size(64.dp),
                        tint = Color.Green
                    )
                }
                
                ActionType.ERROR -> {
                    a.error?.let { err ->
                        Text(
                            text = err.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        
        error?.let { errorMsg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = errorMsg,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun ActionButton(
    action: LinkedAction,
    parameters: Map<String, String>,
    onParameterChange: (String, String) -> Unit,
    onExecute: () -> Unit,
    loading: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Parameter inputs
            action.parameters?.forEach { param ->
                ParameterInput(
                    parameter = param,
                    value = parameters[param.name] ?: "",
                    onValueChange = { onParameterChange(param.name, it) }
                )
                Spacer(Modifier.height(8.dp))
            }
            
            // Execute button
            Button(
                onClick = onExecute,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(action.label)
                }
            }
        }
    }
}

@Composable
fun ParameterInput(
    parameter: ActionParameter,
    value: String,
    onValueChange: (String) -> Unit
) {
    when (parameter.type) {
        ParameterType.SELECT -> {
            var expanded by remember { mutableStateOf(false) }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(parameter.label ?: parameter.name) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    parameter.options?.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onValueChange(option.value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        
        ParameterType.NUMBER -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(parameter.label ?: parameter.name) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        else -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(parameter.label ?: parameter.name) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
```

---

## Creating Actions (Server-Side)

### Action Endpoint

While `artemis-actions` is for consuming actions, here's how to create them:

```kotlin
// Using Ktor server
fun Application.configureActionsRoutes() {
    routing {
        // GET returns action metadata
        get("/api/donate") {
            call.respond(Action(
                type = ActionType.ACTION,
                icon = "https://example.com/icon.png",
                title = "Donate to Project",
                description = "Support open source development",
                label = "Donate",
                links = ActionLinks(
                    actions = listOf(
                        LinkedAction(
                            label = "Donate 1 SOL",
                            href = "/api/donate?amount=1"
                        ),
                        LinkedAction(
                            label = "Donate 5 SOL",
                            href = "/api/donate?amount=5"
                        ),
                        LinkedAction(
                            label = "Custom Amount",
                            href = "/api/donate?amount={amount}",
                            parameters = listOf(
                                ActionParameter(
                                    name = "amount",
                                    label = "Amount (SOL)",
                                    required = true,
                                    type = ParameterType.NUMBER,
                                    min = "0.01"
                                )
                            )
                        )
                    )
                )
            ))
        }
        
        // POST executes the action
        post("/api/donate") {
            val request = call.receive<ActionPostRequest>()
            val amount = call.parameters["amount"]?.toDoubleOrNull()
                ?: throw BadRequestException("Invalid amount")
            
            // Build transaction
            val tx = Transaction.create(recentBlockhash) {
                add(SystemProgram.transfer(
                    fromPubkey = Pubkey.fromBase58(request.account),
                    toPubkey = DONATION_WALLET,
                    lamports = (amount * LAMPORTS_PER_SOL).toLong().toULong()
                ))
                feePayer = Pubkey.fromBase58(request.account)
            }
            
            call.respond(ActionPostResponse(
                transaction = Base64.encode(tx.serialize()),
                message = "Thank you for your donation of $amount SOL!"
            ))
        }
    }
}
```

---

## Deep Link Handling

### Android Manifest

```xml
<activity android:name=".ActionActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        
        <!-- Handle solana-action: scheme -->
        <data android:scheme="solana-action" />
    </intent-filter>
    
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        
        <!-- Handle solana: scheme for Blinks -->
        <data android:scheme="solana" />
    </intent-filter>
</activity>
```

### Activity Handler

```kotlin
class ActionActivity : ComponentActivity() {
    
    private val actionsClient = ActionsClient.create()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uri = intent.data
        
        setContent {
            MaterialTheme {
                when {
                    uri?.scheme == "solana-action" -> {
                        // Direct action URL
                        ActionScreen(uri.toString())
                    }
                    uri?.scheme == "solana" -> {
                        // Blink URL
                        val actionUrl = ActionsClient.blinkToAction(uri.toString())
                        ActionScreen(actionUrl)
                    }
                    else -> {
                        ErrorScreen("Invalid URL")
                    }
                }
            }
        }
    }
}
```

---

## QR Code Support

### Generate Action QR Code

```kotlin
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

fun generateActionQR(actionUrl: String, size: Int = 512): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(
        "solana-action:$actionUrl",
        BarcodeFormat.QR_CODE,
        size,
        size
    )
    
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(
                x, y,
                if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            )
        }
    }
    return bitmap
}
```

### Scan Action QR Code

```kotlin
class QRScannerActivity : ComponentActivity() {
    
    private val scanner = GmsBarcodeScanning.getClient(this)
    
    fun startScanning() {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.let { url ->
                    if (url.startsWith("solana-action:")) {
                        navigateToAction(url)
                    }
                }
            }
    }
}
```

---

## URL Validation

### Validate Action URLs

```kotlin
// Check if URL is valid action
val isValid = ActionsClient.isValidActionUrl(url)

// Parse and validate
try {
    val parsed = ActionsClient.parseUrl(url)
    println("Valid action URL: ${parsed.endpoint}")
} catch (e: InvalidActionUrlException) {
    println("Invalid: ${e.reason}")
}
```

### Security Checks

```kotlin
// Verify action comes from trusted source
val trustedDomains = listOf(
    "jupiter.exchange",
    "dialect.io",
    "tensor.trade"
)

val parsed = ActionsClient.parseUrl(actionUrl)
val isTrusted = trustedDomains.any { domain ->
    parsed.endpoint.host?.endsWith(domain) == true
}

if (!isTrusted) {
    showWarning("This action is from an unverified source")
}
```

---

## Caching

### Cache Action Metadata

```kotlin
// Create client with caching
val client = ActionsClient.create {
    cache = ActionCache.create(
        maxSize = 100,
        ttl = Duration.minutes(5)
    )
}

// First call fetches from network
val action1 = client.fetchAction(url)

// Second call returns cached
val action2 = client.fetchAction(url)  // Instant!

// Force refresh
val action3 = client.fetchAction(url, forceRefresh = true)
```

---

## Error Handling

### Action Errors

```kotlin
try {
    val action = client.fetchAction(url)
    
    // Check for error state
    if (action.type == ActionType.ERROR) {
        showError(action.error?.message ?: "Unknown error")
        return
    }
    
    // Check if disabled
    if (action.disabled == true) {
        showError("This action is currently disabled")
        return
    }
    
} catch (e: InvalidActionUrlException) {
    showError("Invalid action URL format")
} catch (e: ActionFetchException) {
    showError("Failed to fetch action: ${e.message}")
} catch (e: ActionExecutionException) {
    showError("Failed to execute: ${e.message}")
}
```

### Transaction Errors

```kotlin
try {
    val tx = client.execute(action, params) { account = wallet.publicKey }
    val sig = wallet.signAndSend(tx)
} catch (e: ActionTransactionException) {
    // Action server returned error
    showError(e.message)
} catch (e: TransactionFailedException) {
    // Transaction simulation/execution failed
    showError("Transaction failed: ${e.logs?.lastOrNull()}")
}
```

---

## Best Practices

### 1. Always Preview Before Execution

```kotlin
// Show transaction simulation before signing
val tx = client.execute(action, params) { account = wallet.publicKey }

val simulation = rpc.simulateTransaction(tx)
if (simulation.err != null) {
    showError("Transaction would fail: ${simulation.err}")
    return
}

// Show what will happen
val changes = parseSimulation(simulation)
showPreview(changes)
```

### 2. Validate Parameters Client-Side

```kotlin
fun validateParameter(
    param: ActionParameter,
    value: String
): ValidationResult {
    if (param.required == true && value.isBlank()) {
        return ValidationResult.Error("${param.label} is required")
    }
    
    param.pattern?.let { pattern ->
        if (!Regex(pattern).matches(value)) {
            return ValidationResult.Error(
                param.patternDescription ?: "Invalid format"
            )
        }
    }
    
    when (param.type) {
        ParameterType.NUMBER -> {
            val num = value.toDoubleOrNull()
                ?: return ValidationResult.Error("Must be a number")
            
            param.min?.toDoubleOrNull()?.let { min ->
                if (num < min) return ValidationResult.Error("Minimum is $min")
            }
            param.max?.toDoubleOrNull()?.let { max ->
                if (num > max) return ValidationResult.Error("Maximum is $max")
            }
        }
        // ... other types
    }
    
    return ValidationResult.Valid
}
```

### 3. Handle Chained Actions

```kotlin
// Some actions return new actions after completion
suspend fun executeWithChaining(action: LinkedAction) {
    val response = client.execute(action, params) { account = wallet.publicKey }
    
    when (response) {
        is ActionResponse.Transaction -> {
            val sig = wallet.signAndSend(response.transaction)
            showSuccess(response.message)
        }
        is ActionResponse.NextAction -> {
            // Action returned a new action to execute
            navigateToAction(response.nextAction)
        }
        is ActionResponse.Message -> {
            // No transaction, just a message
            showMessage(response.message)
        }
    }
}
```

---

## API Reference

### ActionsClient

```kotlin
class ActionsClient {
    suspend fun fetchAction(url: String): Action
    suspend fun execute(
        action: LinkedAction,
        parameters: Map<String, String> = emptyMap(),
        config: ExecuteConfig.() -> Unit
    ): Transaction
    
    companion object {
        fun create(config: ClientConfig.() -> Unit = {}): ActionsClient
        fun parseUrl(url: String): ParsedActionUrl
        fun isValidActionUrl(url: String): Boolean
        fun blinkToAction(blinkUrl: String): String
    }
}
```

### Action Types

```kotlin
enum class ActionType {
    ACTION,     // Normal executable action
    COMPLETED,  // Action completed successfully
    ERROR       // Action in error state
}

enum class ParameterType {
    TEXT,       // Text input
    EMAIL,      // Email input
    URL,        // URL input
    NUMBER,     // Numeric input
    DATE,       // Date picker
    DATETIME,   // Date and time picker
    CHECKBOX,   // Boolean checkbox
    RADIO,      // Radio button group
    SELECT,     // Dropdown select
    TEXTAREA    // Multi-line text
}
```

---

*artemis-actions - Bringing Blinks to Kotlin wallets*
