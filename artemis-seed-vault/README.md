# Artemis Seed Vault

A modern, Coroutine-first implementation of the Solana Seed Vault SDK for Android.

This module allows your Android application to interact with the system-level Seed Vault (on Saga and other Solana Mobile devices) to request signatures without ever handling the user's private keys.

## Features

- **Coroutine-first API**: No more callbacks. Everything is `suspend` functions.
- **Internalized Logic**: Does not depend on the legacy `client-lib-ktx`.
- **Drop-in Compatibility**: Includes `com.solanamobile.seedvault` compatibility classes so you can migrate existing code instantly.
- **Better Error Handling**: Typed exceptions and structured results.

## Installation

```kotlin
implementation("xyz.selenus:artemis-seed-vault:1.0.5")
```

## Usage

### 1. Initialize the Manager

```kotlin
val seedVaultManager = SeedVaultManager(context)
```

### 2. Authorize Access

To use the Seed Vault, you must first request authorization. This launches a system Activity.

```kotlin
// In your Activity/Fragment
val intent = seedVaultManager.buildAuthorizeIntent("sign_transaction")
startActivityForResult(intent, REQUEST_CODE_AUTHORIZE)

// In onActivityResult
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_CODE_AUTHORIZE && resultCode == Activity.RESULT_OK) {
        val auth = seedVaultManager.parseAuthorizationResult(data!!)
        val authToken = auth.authToken
        // Store this authToken securely
    }
}
```

### 3. Get Accounts

```kotlin
val accounts = seedVaultManager.getAccounts(authToken)
accounts.forEach { account ->
    println("Account: ${account.name} - ${account.accountId}")
}
```

### 4. Sign Transactions

```kotlin
val signatures = seedVaultManager.signTransactions(authToken, listOf(txBytes))
```

## Migration from `client-lib-ktx`

If you are using the official Solana Mobile `client-lib-ktx`, you can replace it with this module. We provide backward-compatible classes in the `com.solanamobile.seedvault` package.

**Old Code:**
```kotlin
Wallet.authorizeSeed(context, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
```

**New Code (with Artemis):**
```kotlin
// Works exactly the same! The `Wallet` class is provided by Artemis.
Wallet.authorizeSeed(context, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
```

Under the hood, these compatibility classes delegate to the optimized Artemis `SeedVaultManager`.
