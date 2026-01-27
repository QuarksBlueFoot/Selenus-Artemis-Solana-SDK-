# artemis-anchor - Native Anchor Client Generator

## 🌟 First Compile-Time Anchor Client Generator for Kotlin

Generate type-safe Kotlin clients from Anchor IDL files at compile time. Full instruction builders, account deserializers, and event parsers with zero runtime reflection.

---

## Overview

`artemis-anchor` provides a KSP (Kotlin Symbol Processing) plugin that generates complete program clients from Anchor IDL files, enabling:

- ✅ **Compile-time code generation** from IDL
- ✅ **Type-safe instruction builders** with named parameters
- ✅ **Account deserializers** with proper discriminator handling
- ✅ **Event parsing** for transaction logs
- ✅ **PDA derivation helpers** with seed building
- ✅ **Error code mapping** for friendly error messages

---

## Installation

### Gradle Configuration

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
}

dependencies {
    implementation("xyz.selenus:artemis-anchor:2.0.0")
    ksp("xyz.selenus:artemis-anchor-processor:2.0.0")
}

// Configure IDL source directory
ksp {
    arg("anchor.idl.dir", "$projectDir/src/main/idl")
    arg("anchor.output.package", "com.myapp.programs")
}
```

---

## Quick Start

### 1. Add IDL File

Place your Anchor IDL in `src/main/idl/my_program.json`:

```json
{
  "version": "0.1.0",
  "name": "my_program",
  "instructions": [
    {
      "name": "initialize",
      "accounts": [
        { "name": "authority", "isMut": false, "isSigner": true },
        { "name": "state", "isMut": true, "isSigner": false },
        { "name": "systemProgram", "isMut": false, "isSigner": false }
      ],
      "args": [
        { "name": "name", "type": "string" },
        { "name": "bump", "type": "u8" }
      ]
    }
  ],
  "accounts": [
    {
      "name": "State",
      "type": {
        "kind": "struct",
        "fields": [
          { "name": "authority", "type": "publicKey" },
          { "name": "name", "type": "string" },
          { "name": "counter", "type": "u64" }
        ]
      }
    }
  ]
}
```

### 2. Build Project

```bash
./gradlew build
```

### 3. Use Generated Client

```kotlin
import com.myapp.programs.myprogram.*

// Create client
val client = MyProgramClient(rpc, MY_PROGRAM_ID)

// Build instruction with type safety
val ix = client.initialize(
    authority = wallet.publicKey,
    state = statePda,
    systemProgram = SystemProgram.PROGRAM_ID,
    name = "My State",
    bump = stateBump
)

// Create and send transaction
val tx = Transaction.create(recentBlockhash) {
    add(ix)
    feePayer = wallet.publicKey
}

val signature = wallet.signAndSend(tx)
```

---

## Generated Code Structure

For each IDL, the processor generates:

```
com.myapp.programs.myprogram/
├── MyProgramClient.kt       # Main client class
├── Instructions.kt          # Instruction builders
├── Accounts.kt              # Account data classes
├── Types.kt                 # Custom types (structs, enums)
├── Events.kt                # Event data classes
├── Errors.kt                # Error code enum
└── Pdas.kt                  # PDA derivation helpers
```

---

## Instruction Builders

### Generated Instruction Methods

```kotlin
// Generated from IDL
class MyProgramClient(
    val rpc: RpcClient,
    val programId: Pubkey
) {
    /**
     * Initialize a new state account
     *
     * @param authority The authority that controls the state
     * @param state The state account to initialize
     * @param systemProgram System program for account creation
     * @param name The name for this state
     * @param bump PDA bump seed
     */
    fun initialize(
        authority: Pubkey,
        state: Pubkey,
        systemProgram: Pubkey,
        name: String,
        bump: UByte
    ): TransactionInstruction {
        val data = buildInstructionData {
            // Discriminator
            write(INITIALIZE_DISCRIMINATOR)
            // Args
            writeString(name)
            writeU8(bump)
        }
        
        val accounts = listOf(
            AccountMeta(authority, isSigner = true, isWritable = false),
            AccountMeta(state, isSigner = false, isWritable = true),
            AccountMeta(systemProgram, isSigner = false, isWritable = false)
        )
        
        return TransactionInstruction(programId, accounts, data)
    }
    
    companion object {
        val INITIALIZE_DISCRIMINATOR = byteArrayOf(
            0xaf.toByte(), 0xaf.toByte(), 0x6d.toByte(), 0x1f.toByte(),
            0x0d.toByte(), 0x98.toByte(), 0x9b.toByte(), 0xed.toByte()
        )
    }
}
```

### Complex Instruction Types

```kotlin
// Struct arguments
data class ConfigInput(
    val maxSupply: ULong,
    val mintPrice: ULong,
    val startTime: Long?
)

fun createWithConfig(
    config: ConfigInput,
    // ... accounts
): TransactionInstruction

// Enum arguments
sealed class Status {
    object Active : Status()
    object Paused : Status()
    data class Scheduled(val startAt: Long) : Status()
}

fun setStatus(
    status: Status,
    // ... accounts
): TransactionInstruction

// Usage
client.setStatus(Status.Scheduled(startAt = 1699999999L), ...)
```

---

## Account Deserialization

### Generated Account Classes

```kotlin
// Generated from IDL accounts
@Serializable
data class State(
    val authority: Pubkey,
    val name: String,
    val counter: ULong
) {
    companion object {
        val DISCRIMINATOR = byteArrayOf(/* ... */)
        const val SIZE = 8 + 32 + 4 + 256 + 8  // discriminator + fields
        
        fun deserialize(data: ByteArray): State {
            require(data.sliceArray(0..7).contentEquals(DISCRIMINATOR)) {
                "Invalid account discriminator"
            }
            return BorshDecoder(data.drop(8).toByteArray()).run {
                State(
                    authority = readPubkey(),
                    name = readString(),
                    counter = readU64()
                )
            }
        }
    }
}
```

### Fetching Accounts

```kotlin
// Fetch and deserialize
val stateData = rpc.getAccountInfo(stateAddress)
val state = State.deserialize(stateData.data)

println("Authority: ${state.authority}")
println("Name: ${state.name}")
println("Counter: ${state.counter}")

// Using client helper
val state = client.fetchState(stateAddress)

// Fetch multiple
val states = client.fetchAllStates(listOf(addr1, addr2, addr3))

// With filters
val myStates = client.findStates {
    memcmp(8, wallet.publicKey.toByteArray())  // filter by authority
}
```

---

## PDA Derivation

### Generated PDA Helpers

```kotlin
// IDL with seeds
{
  "name": "State",
  "pda": {
    "seeds": [
      { "kind": "const", "value": [115, 116, 97, 116, 101] },
      { "kind": "account", "path": "authority" }
    ]
  }
}

// Generated code
object StatePda {
    fun find(
        authority: Pubkey,
        programId: Pubkey = MY_PROGRAM_ID
    ): PdaResult {
        val seeds = listOf(
            "state".toByteArray(),
            authority.toByteArray()
        )
        return Pubkey.findProgramAddress(seeds, programId)
    }
}

// Usage
val (statePda, bump) = StatePda.find(wallet.publicKey)
```

### Complex PDA Seeds

```kotlin
// With multiple dynamic seeds
object UserStatePda {
    fun find(
        user: Pubkey,
        stateId: ULong,
        programId: Pubkey = MY_PROGRAM_ID
    ): PdaResult {
        val seeds = listOf(
            "user_state".toByteArray(),
            user.toByteArray(),
            stateId.toLeByteArray()
        )
        return Pubkey.findProgramAddress(seeds, programId)
    }
}
```

---

## Event Parsing

### Generated Event Classes

```kotlin
// IDL events
{
  "events": [
    {
      "name": "TransferEvent",
      "fields": [
        { "name": "from", "type": "publicKey", "index": true },
        { "name": "to", "type": "publicKey", "index": true },
        { "name": "amount", "type": "u64", "index": false }
      ]
    }
  ]
}

// Generated code
@Serializable
data class TransferEvent(
    val from: Pubkey,
    val to: Pubkey,
    val amount: ULong
) {
    companion object {
        val DISCRIMINATOR = byteArrayOf(/* ... */)
        
        fun parse(data: ByteArray): TransferEvent? {
            if (!data.sliceArray(0..7).contentEquals(DISCRIMINATOR)) {
                return null
            }
            return BorshDecoder(data.drop(8).toByteArray()).run {
                TransferEvent(
                    from = readPubkey(),
                    to = readPubkey(),
                    amount = readU64()
                )
            }
        }
    }
}
```

### Parsing Events from Logs

```kotlin
// Parse events from transaction
val tx = rpc.getTransaction(signature)

val events = client.parseEvents(tx.meta.logMessages)

events.forEach { event ->
    when (event) {
        is TransferEvent -> {
            println("Transfer: ${event.amount} from ${event.from} to ${event.to}")
        }
        is InitializeEvent -> {
            println("Initialized: ${event.name}")
        }
    }
}
```

### Subscribe to Events

```kotlin
// Real-time event subscription
client.subscribeEvents { event ->
    when (event) {
        is TransferEvent -> updateBalance(event)
        is NewStateEvent -> addState(event)
    }
}
```

---

## Error Handling

### Generated Error Enum

```kotlin
// IDL errors
{
  "errors": [
    { "code": 6000, "name": "Unauthorized", "msg": "You are not authorized" },
    { "code": 6001, "name": "InvalidAmount", "msg": "Amount must be greater than zero" },
    { "code": 6002, "name": "AlreadyInitialized", "msg": "Account already initialized" }
  ]
}

// Generated code
enum class MyProgramError(val code: Int, val message: String) {
    UNAUTHORIZED(6000, "You are not authorized"),
    INVALID_AMOUNT(6001, "Amount must be greater than zero"),
    ALREADY_INITIALIZED(6002, "Account already initialized");
    
    companion object {
        fun fromCode(code: Int): MyProgramError? =
            entries.find { it.code == code }
            
        fun fromTransaction(tx: TransactionResponse): MyProgramError? {
            val errorCode = tx.meta?.err?.instructionError?.custom
            return errorCode?.let { fromCode(it) }
        }
    }
}
```

### Using Errors

```kotlin
try {
    val sig = wallet.signAndSend(tx)
    val result = rpc.confirmTransaction(sig)
    
    if (result.err != null) {
        val error = MyProgramError.fromCode(result.err.instructionError.custom)
        when (error) {
            MyProgramError.UNAUTHORIZED -> 
                showError("You don't have permission")
            MyProgramError.INVALID_AMOUNT -> 
                showError("Please enter a valid amount")
            else -> 
                showError(error?.message ?: "Unknown error")
        }
    }
} catch (e: RpcException) {
    val error = MyProgramError.fromCode(e.code)
    showError(error?.message ?: e.message)
}
```

---

## Custom Types

### Structs

```kotlin
// IDL type
{
  "name": "UserProfile",
  "type": {
    "kind": "struct",
    "fields": [
      { "name": "name", "type": "string" },
      { "name": "level", "type": "u8" },
      { "name": "experience", "type": "u64" },
      { "name": "achievements", "type": { "vec": "u32" } }
    ]
  }
}

// Generated code
@Serializable
data class UserProfile(
    val name: String,
    val level: UByte,
    val experience: ULong,
    val achievements: List<UInt>
)
```

### Enums

```kotlin
// IDL type
{
  "name": "Rarity",
  "type": {
    "kind": "enum",
    "variants": [
      { "name": "Common" },
      { "name": "Uncommon" },
      { "name": "Rare" },
      { "name": "Epic" },
      { "name": "Legendary" }
    ]
  }
}

// Generated code
sealed class Rarity {
    object Common : Rarity()
    object Uncommon : Rarity()
    object Rare : Rarity()
    object Epic : Rarity()
    object Legendary : Rarity()
    
    fun ordinal(): Int = when (this) {
        Common -> 0
        Uncommon -> 1
        Rare -> 2
        Epic -> 3
        Legendary -> 4
    }
}
```

### Enums with Data

```kotlin
// IDL type with tuple variants
{
  "name": "Reward",
  "type": {
    "kind": "enum",
    "variants": [
      { "name": "None" },
      { "name": "Tokens", "fields": [{ "name": "amount", "type": "u64" }] },
      { "name": "Nft", "fields": [{ "name": "mint", "type": "publicKey" }] }
    ]
  }
}

// Generated code
sealed class Reward {
    object None : Reward()
    data class Tokens(val amount: ULong) : Reward()
    data class Nft(val mint: Pubkey) : Reward()
}
```

---

## Complete Example

### Token Staking Program

```kotlin
// staking.json IDL placed in src/main/idl/

// Generated client usage
class StakingViewModel(
    private val rpc: RpcClient,
    private val wallet: WalletAdapter
) : ViewModel() {
    
    private val client = StakingClient(rpc, STAKING_PROGRAM_ID)
    
    private val _stakes = MutableStateFlow<List<StakeAccount>>(emptyList())
    val stakes = _stakes.asStateFlow()
    
    init {
        loadStakes()
    }
    
    private fun loadStakes() {
        viewModelScope.launch {
            // Find all stakes for this user
            val userStakes = client.findStakeAccounts {
                memcmp(8, wallet.publicKey.toByteArray())
            }
            _stakes.value = userStakes
        }
    }
    
    suspend fun stake(amount: ULong) {
        // Derive PDAs
        val (stakePda, stakeBump) = StakeAccountPda.find(wallet.publicKey)
        val (vaultPda, vaultBump) = VaultPda.find()
        
        // Build instruction
        val ix = client.stake(
            user = wallet.publicKey,
            stakeAccount = stakePda,
            vault = vaultPda,
            userTokenAccount = userAta,
            tokenProgram = TokenProgram.PROGRAM_ID,
            amount = amount,
            stakeBump = stakeBump.toUByte(),
            vaultBump = vaultBump.toUByte()
        )
        
        // Send transaction
        val tx = Transaction.create(rpc.getLatestBlockhash()) {
            add(ix)
            feePayer = wallet.publicKey
        }
        
        wallet.signAndSend(tx)
        loadStakes()
    }
    
    suspend fun unstake(stakeAccount: StakeAccount) {
        val ix = client.unstake(
            user = wallet.publicKey,
            stakeAccount = stakeAccount.address,
            vault = VaultPda.find().first,
            userTokenAccount = userAta,
            tokenProgram = TokenProgram.PROGRAM_ID
        )
        
        val tx = Transaction.create(rpc.getLatestBlockhash()) {
            add(ix)
            feePayer = wallet.publicKey
        }
        
        wallet.signAndSend(tx)
        loadStakes()
    }
    
    suspend fun claimRewards() {
        val (stakePda, _) = StakeAccountPda.find(wallet.publicKey)
        
        val ix = client.claim(
            user = wallet.publicKey,
            stakeAccount = stakePda,
            rewardVault = RewardVaultPda.find().first,
            userRewardAccount = userRewardAta,
            tokenProgram = TokenProgram.PROGRAM_ID
        )
        
        val tx = Transaction.create(rpc.getLatestBlockhash()) {
            add(ix)
            feePayer = wallet.publicKey
        }
        
        wallet.signAndSend(tx)
    }
}
```

---

## Configuration Options

### KSP Arguments

```kotlin
ksp {
    // Required: IDL directory
    arg("anchor.idl.dir", "$projectDir/src/main/idl")
    
    // Required: Output package
    arg("anchor.output.package", "com.myapp.programs")
    
    // Optional: Generate suspend functions
    arg("anchor.generate.suspend", "true")
    
    // Optional: Include fetch helpers
    arg("anchor.generate.fetch", "true")
    
    // Optional: Include subscription helpers
    arg("anchor.generate.subscribe", "true")
    
    // Optional: Generate Compose-friendly State wrappers
    arg("anchor.generate.compose", "true")
}
```

### Multiple Programs

```kotlin
// Place multiple IDL files in the idl directory
// Each generates its own package

// src/main/idl/
//   staking.json -> com.myapp.programs.staking.*
//   marketplace.json -> com.myapp.programs.marketplace.*
//   governance.json -> com.myapp.programs.governance.*
```

---

## Best Practices

### 1. Keep IDLs Updated

```bash
# Download latest IDL from chain
anchor idl fetch <PROGRAM_ID> -o src/main/idl/my_program.json
```

### 2. Version Your Clients

```kotlin
// Check program version before using
val programData = rpc.getAccountInfo(programId)
val version = client.parseVersion(programData)

if (version.major != EXPECTED_VERSION) {
    showError("Program updated - please update the app")
}
```

### 3. Use Extension Functions

```kotlin
// Add convenience methods
fun StakingClient.stakeMax(wallet: Pubkey) = viewModelScope.launch {
    val balance = rpc.getTokenBalance(userAta)
    stake(balance.amount)
}
```

---

## API Reference

### Processor Annotations

```kotlin
// Force regeneration
@AnchorIdl("my_program.json")
interface MyProgramMarker

// Custom program ID
@AnchorIdl(
    file = "my_program.json",
    programId = "MyProg111111111111111111111111111111111"
)
interface MyProgramMarker
```

### Generated Client

```kotlin
class [ProgramName]Client(
    val rpc: RpcClient,
    val programId: Pubkey
) {
    // For each instruction
    fun [instructionName](
        // accounts...
        // args...
    ): TransactionInstruction
    
    // For each account type
    suspend fun fetch[AccountName](address: Pubkey): [AccountName]
    suspend fun findAll[AccountName](filter: FilterBuilder.() -> Unit): List<[AccountName]>
    
    // Event parsing
    fun parseEvents(logs: List<String>): List<[ProgramName]Event>
}
```

---

*artemis-anchor - Type-safe Anchor clients for Kotlin*
