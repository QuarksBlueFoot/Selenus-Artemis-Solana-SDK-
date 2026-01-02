# artemis-universal - Universal Program Client

## 🌟 First Dynamic Client Generator for Solana Programs

Call any Solana program by just providing its address. No IDL files, no code generation, no recompilation. Perfect for exploratory tools, debuggers, and dynamic integrations.

---

## Overview

`artemis-universal` creates fully-typed program clients at runtime by fetching and analyzing on-chain IDL accounts. This enables:

- ✅ Call **any** Anchor program dynamically
- ✅ No code generation or recompilation needed
- ✅ Perfect for exploratory and debugging tools
- ✅ Type-safe instruction and account building
- ✅ Automatic discriminator handling
- ✅ IDL caching for performance

---

## Installation

```kotlin
implementation("xyz.selenus:artemis-universal:2.0.0")
```

---

## Quick Start

```kotlin
import com.selenus.artemis.universal.*

// Create a universal client for any program
val programId = Pubkey.fromBase58("JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4")
val client = UniversalClient.create(rpc, programId)

// List all available instructions
client.instructions.forEach { instruction ->
    println("${instruction.name}:")
    instruction.args.forEach { arg ->
        println("  ${arg.name}: ${arg.type}")
    }
}

// Build an instruction dynamically
val ix = client.instruction("swap") {
    arg("inAmount", 1_000_000UL)
    arg("minimumOutAmount", 900_000UL)
    account("tokenProgram", TokenProgram.PROGRAM_ID)
    account("user", wallet.publicKey)
    account("userTokenAccount", userAta)
    // ... more accounts
}

// Create and send transaction
val tx = Transaction.create(recentBlockhash) {
    add(ix)
    feePayer = wallet.publicKey
}
```

---

## How It Works

### 1. IDL Discovery

The Universal Client fetches the program's IDL from multiple sources:

```kotlin
// Anchor programs store IDL at a deterministic address
val idlAddress = Pubkey.findProgramAddress(
    listOf("idl".toByteArray(), programId.toByteArray()),
    ANCHOR_IDL_PROGRAM
)

// Or from Anchor's IDL account
val idlAccountData = rpc.getAccountInfo(idlAddress)
val idl = IdlParser.parse(idlAccountData)
```

### 2. Type Mapping

IDL types are automatically mapped to Kotlin types:

| IDL Type | Kotlin Type |
|----------|-------------|
| `u8` | `UByte` |
| `u16` | `UShort` |
| `u32` | `UInt` |
| `u64` | `ULong` |
| `u128` | `UBigInteger` |
| `i8` | `Byte` |
| `i16` | `Short` |
| `i32` | `Int` |
| `i64` | `Long` |
| `i128` | `BigInteger` |
| `bool` | `Boolean` |
| `string` | `String` |
| `publicKey` | `Pubkey` |
| `bytes` | `ByteArray` |
| `vec<T>` | `List<T>` |
| `option<T>` | `T?` |
| `defined<X>` | Generated data class |

### 3. Discriminator Handling

The client automatically calculates and prepends discriminators:

```kotlin
// Anchor uses SHA256 hash of "global:<instruction_name>"
val discriminator = Sha256.hash(
    "global:$instructionName".toByteArray()
).take(8)
```

---

## Program Exploration

### List All Instructions

```kotlin
val client = UniversalClient.create(rpc, programId)

println("=== Program: ${client.programName} ===")
println("Version: ${client.version}")
println()

client.instructions.forEach { ix ->
    println("📝 ${ix.name}")
    
    if (ix.args.isNotEmpty()) {
        println("   Arguments:")
        ix.args.forEach { arg ->
            println("     - ${arg.name}: ${arg.type}")
        }
    }
    
    println("   Accounts:")
    ix.accounts.forEach { acc ->
        val flags = buildList {
            if (acc.isMut) add("mut")
            if (acc.isSigner) add("signer")
        }.joinToString(", ")
        println("     - ${acc.name} [$flags]")
    }
    println()
}
```

### List All Account Types

```kotlin
client.accounts.forEach { account ->
    println("📦 ${account.name}")
    account.fields.forEach { field ->
        println("   ${field.name}: ${field.type}")
    }
}
```

### List All Custom Types

```kotlin
client.types.forEach { type ->
    when (type) {
        is IdlStruct -> {
            println("struct ${type.name} {")
            type.fields.forEach { f ->
                println("    ${f.name}: ${f.type},")
            }
            println("}")
        }
        is IdlEnum -> {
            println("enum ${type.name} {")
            type.variants.forEach { v ->
                println("    ${v.name},")
            }
            println("}")
        }
    }
}
```

---

## Building Instructions

### Basic Instruction

```kotlin
val ix = client.instruction("initialize") {
    // Arguments
    arg("name", "My Token")
    arg("symbol", "MTK")
    arg("decimals", 9.toByte())
    
    // Accounts
    account("mint", mintKeypair.publicKey)
    account("authority", wallet.publicKey)
    account("payer", wallet.publicKey)
    account("systemProgram", SystemProgram.PROGRAM_ID)
    account("tokenProgram", TokenProgram.PROGRAM_ID)
}
```

### Complex Types

```kotlin
// Struct argument
val config = mapOf(
    "maxSupply" to 1_000_000UL,
    "freezeAuthority" to wallet.publicKey,
    "mintAuthority" to wallet.publicKey
)

val ix = client.instruction("createWithConfig") {
    arg("config", config)
    // accounts...
}

// Enum argument
val ix = client.instruction("setStatus") {
    arg("status", EnumValue("Active"))
    // or with data
    arg("status", EnumValue("Paused", mapOf("until" to 1699999999L)))
}

// Vector argument
val ix = client.instruction("addWhitelist") {
    arg("addresses", listOf(
        Pubkey.fromBase58("..."),
        Pubkey.fromBase58("..."),
        Pubkey.fromBase58("...")
    ))
}

// Optional argument
val ix = client.instruction("update") {
    arg("newName", "Updated Name")
    arg("newSymbol", null)  // Option::None
}
```

### Remaining Accounts

```kotlin
val ix = client.instruction("routeSwap") {
    // Regular args and accounts
    arg("amount", 1000UL)
    account("user", wallet.publicKey)
    
    // Additional accounts for routing
    remainingAccounts(
        AccountMeta(pool1, writable = true),
        AccountMeta(pool2, writable = true),
        AccountMeta(pool3, writable = true)
    )
}
```

---

## Reading Accounts

### Fetch and Deserialize

```kotlin
// Get account data
val accountData = rpc.getAccountInfo(accountAddress)

// Deserialize using IDL schema
val parsed = client.deserializeAccount("UserAccount", accountData.data)

// Access fields
println("Owner: ${parsed["owner"]}")
println("Balance: ${parsed["balance"]}")
println("Created: ${parsed["createdAt"]}")
```

### Account Subscription

```kotlin
// Subscribe to account changes
client.subscribeAccount("UserAccount", accountAddress) { account ->
    println("Balance updated: ${account["balance"]}")
}
```

### Get Multiple Accounts

```kotlin
val addresses = listOf(account1, account2, account3)
val accounts = client.getAccounts("UserAccount", addresses)

accounts.forEach { (address, data) ->
    println("$address: balance = ${data["balance"]}")
}
```

---

## Building a Program Explorer

### ViewModel

```kotlin
class ProgramExplorerViewModel(
    private val rpc: RpcClient
) : ViewModel() {
    
    private val _programId = MutableStateFlow("")
    val programId = _programId.asStateFlow()
    
    private val _client = MutableStateFlow<UniversalClient?>(null)
    val client = _client.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    
    fun loadProgram(programIdInput: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            
            try {
                val pubkey = Pubkey.fromBase58(programIdInput)
                val newClient = UniversalClient.create(rpc, pubkey)
                
                _programId.value = programIdInput
                _client.value = newClient
            } catch (e: IdlNotFoundException) {
                _error.value = "Program has no published IDL. " +
                    "Only Anchor programs with published IDLs are supported."
            } catch (e: Exception) {
                _error.value = "Failed to load: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }
}
```

### Compose UI

```kotlin
@Composable
fun ProgramExplorerScreen(viewModel: ProgramExplorerViewModel) {
    var input by remember { mutableStateOf("") }
    val client by viewModel.client.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Search bar
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Program ID") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(
                    onClick = { viewModel.loadProgram(input) },
                    enabled = !loading
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Search, "Load")
                    }
                }
            }
        )
        
        Spacer(Modifier.height(16.dp))
        
        error?.let { errorMsg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMsg,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        client?.let { c ->
            // Program info
            Text(
                text = c.programName ?: "Unknown Program",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Version ${c.version ?: "unknown"}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Tab layout for instructions/accounts/types
            var selectedTab by remember { mutableStateOf(0) }
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Instructions (${c.instructions.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Accounts (${c.accounts.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Types (${c.types.size})") }
                )
            }
            
            when (selectedTab) {
                0 -> InstructionsList(c.instructions)
                1 -> AccountsList(c.accounts)
                2 -> TypesList(c.types)
            }
        }
    }
}

@Composable
fun InstructionsList(instructions: List<IdlInstruction>) {
    LazyColumn {
        items(instructions) { ix ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = ix.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    if (ix.args.isNotEmpty()) {
                        Text(
                            text = "Arguments:",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        ix.args.forEach { arg ->
                            Text(
                                text = "  ${arg.name}: ${arg.type}",
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    Text(
                        text = "Accounts (${ix.accounts.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    ix.accounts.forEach { acc ->
                        val flags = buildString {
                            if (acc.isMut) append("mut ")
                            if (acc.isSigner) append("signer")
                        }.trim()
                        Text(
                            text = "  ${acc.name}" + if (flags.isNotEmpty()) " ($flags)" else "",
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
```

---

## IDL Caching

For performance, the Universal Client caches IDLs:

```kotlin
// Create with custom cache
val cache = IdlCache.create(
    maxSize = 100,
    ttl = Duration.hours(24)
)

val client = UniversalClient.create(rpc, programId, cache)

// Or use disk cache
val diskCache = DiskIdlCache(
    directory = context.cacheDir.resolve("idl"),
    maxSize = 50 * 1024 * 1024  // 50MB
)
```

---

## Error Handling

### No IDL Found

```kotlin
try {
    val client = UniversalClient.create(rpc, programId)
} catch (e: IdlNotFoundException) {
    // Program doesn't have a published IDL
    // Only Anchor programs with `anchor idl init` have IDLs
    showError("This program doesn't have a published IDL")
}
```

### Invalid Instruction

```kotlin
try {
    val ix = client.instruction("nonexistent") { }
} catch (e: InstructionNotFoundException) {
    println("Available instructions: ${client.instructions.map { it.name }}")
}
```

### Missing Arguments

```kotlin
try {
    val ix = client.instruction("transfer") {
        arg("amount", 1000UL)
        // Missing required account
    }
} catch (e: MissingArgumentException) {
    println("Missing: ${e.argumentName}")
}
```

### Type Mismatch

```kotlin
try {
    val ix = client.instruction("transfer") {
        arg("amount", "not a number")  // Wrong type
    }
} catch (e: TypeMismatchException) {
    println("Expected ${e.expected}, got ${e.actual}")
}
```

---

## Integration with Other Modules

### With artemis-jupiter

```kotlin
// Use Universal Client to call custom Jupiter routes
val jupiterProgram = UniversalClient.create(rpc, Jupiter.PROGRAM_ID)

val routeIx = jupiterProgram.instruction("route") {
    arg("routePlan", encodedRoute)
    arg("inAmount", 1000000UL)
    arg("quotedOutAmount", 950000UL)
    arg("slippageBps", 50)
    // ... accounts from Jupiter quote
}
```

### With artemis-anchor

```kotlin
// Universal client can complement typed Anchor clients
// when you need dynamic access
val typedClient = MyProgramClient(rpc, programId)  // Compile-time typed
val dynamicClient = UniversalClient.create(rpc, programId)  // Runtime discovery

// Use typed for known operations
typedClient.transfer(from, to, amount)

// Use dynamic for exploration
println("All instructions: ${dynamicClient.instructions.map { it.name }}")
```

---

## Best Practices

### 1. Validate User Input

```kotlin
fun buildFromUserInput(
    client: UniversalClient,
    ixName: String,
    args: Map<String, String>,
    accounts: Map<String, String>
): TransactionInstruction {
    // Get instruction schema
    val ixSchema = client.instructions.find { it.name == ixName }
        ?: throw IllegalArgumentException("Unknown instruction: $ixName")
    
    return client.instruction(ixName) {
        // Parse and validate each argument
        ixSchema.args.forEach { argSchema ->
            val value = args[argSchema.name]
                ?: throw IllegalArgumentException("Missing arg: ${argSchema.name}")
            
            val parsed = parseArg(value, argSchema.type)
            arg(argSchema.name, parsed)
        }
        
        // Validate and add accounts
        ixSchema.accounts.forEach { accSchema ->
            val value = accounts[accSchema.name]
                ?: throw IllegalArgumentException("Missing account: ${accSchema.name}")
            
            account(accSchema.name, Pubkey.fromBase58(value))
        }
    }
}
```

### 2. Handle Version Differences

```kotlin
val client = UniversalClient.create(rpc, programId)

// Check if instruction exists (handles version differences)
if (client.hasInstruction("newFeature")) {
    client.instruction("newFeature") { /* ... */ }
} else {
    // Fallback for older version
    client.instruction("legacyMethod") { /* ... */ }
}
```

### 3. Use Type-Safe Wrappers

```kotlin
// Create type-safe extension for frequently used instructions
fun UniversalClient.transfer(
    from: Pubkey,
    to: Pubkey,
    amount: ULong
): TransactionInstruction = instruction("transfer") {
    arg("amount", amount)
    account("from", from)
    account("to", to)
    account("authority", from)
}

// Usage
val ix = client.transfer(walletPubkey, recipientPubkey, 1_000_000UL)
```

---

## API Reference

### UniversalClient

```kotlin
class UniversalClient private constructor(
    val rpc: RpcClient,
    val programId: Pubkey,
    val idl: Idl
) {
    val programName: String?
    val version: String?
    val instructions: List<IdlInstruction>
    val accounts: List<IdlAccount>
    val types: List<IdlType>
    val events: List<IdlEvent>
    val errors: List<IdlError>
    
    fun instruction(name: String, block: InstructionBuilder.() -> Unit): TransactionInstruction
    fun hasInstruction(name: String): Boolean
    fun deserializeAccount(typeName: String, data: ByteArray): Map<String, Any?>
    suspend fun getAccounts(typeName: String, addresses: List<Pubkey>): Map<Pubkey, Map<String, Any?>>
    
    companion object {
        suspend fun create(rpc: RpcClient, programId: Pubkey, cache: IdlCache? = null): UniversalClient
    }
}
```

### InstructionBuilder

```kotlin
class InstructionBuilder {
    fun arg(name: String, value: Any?)
    fun account(name: String, pubkey: Pubkey)
    fun remainingAccounts(vararg accounts: AccountMeta)
}
```

---

*artemis-universal - Interact with any Solana program, dynamically*
