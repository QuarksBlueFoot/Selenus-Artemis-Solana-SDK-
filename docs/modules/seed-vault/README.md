# Artemis Seed Vault

The `artemis-seed-vault` module provides a comprehensive, Coroutine-first implementation of the [Solana Seed Vault](https://github.com/solana-mobile/seed-vault-sdk) protocol. It is designed to be a drop-in replacement for the official SDK while offering a more idiomatic Kotlin API.

## Features

- **Compatibility:** Fully supports the `com.solanamobile.seedvault` contract namespace. Existing code using the official SDK can work without changes.
- **Coroutines:** All asynchronous operations are suspended functions, eliminating callback hell.
- **Simplified API:** A unified `SeedVaultManager` handles permissions, intent resolution, and connection management.

## Installation

```kotlin
dependencies {
    implementation("com.selenus.artemis:artemis-seed-vault:1.0.4")
}
```

## Usage

### 1. Initialize

```kotlin
val seedVaultManager = SeedVaultManager(context)
```

### 2. Authorization (Activity)

For operations requiring UI (Authorize, Create, Import), use the builder methods to get an Intent:

```kotlin
// In your Activity
val intent = seedVaultManager.buildAuthorizeIntent(purpose = "Sign Transaction")
startActivityForResult(intent, REQUEST_CODE)

// Handle Result
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
        val auth = seedVaultManager.parseAuthorizationResult(data!!)
        println("Auth Token: ${auth.authToken}")
    }
}
```

### 3. Signing (Background)

Once authorized, signing operations happen in the background:

```kotlin
scope.launch {
    try {
        val signatures = seedVaultManager.signTransactions(authToken, txByteArray)
        // Use signatures
    } catch (e: Exception) {
        // Handle error
    }
}
```

## Compatibility Mode

If you have existing code using `com.solanamobile.seedvault.Wallet` or `SeedVault`:

```kotlin
// This works exactly as before, delegating to Artemis internals
if (SeedVault.isAvailable(context)) {
    val intent = Wallet.authorizeSeed(context, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
    startActivityForResult(intent, 0)
}
```
