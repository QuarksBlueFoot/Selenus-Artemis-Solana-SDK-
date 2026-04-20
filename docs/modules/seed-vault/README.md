# Artemis Seed Vault

`artemis-seed-vault` is a coroutine-first client for the [Solana Seed Vault](https://github.com/solana-mobile/seed-vault-sdk). Keys stay behind the system service; signing happens on device. The Artemis native path lives in `com.selenus.artemis.seedvault`; the drop-in compat layer at `interop/artemis-seedvault-compat/` re-exposes the upstream `com.solanamobile.seedvault.*` namespace so existing apps keep compiling.

## What it gives you

- `SeedVaultManager(context)` suspends-on-bind, so the first RPC call never races the binder handshake (`connectSuspending()` in [SeedVaultManager.kt](../../../mobile/artemis-seed-vault/src/main/kotlin/com/selenus/artemis/seedvault/SeedVaultManager.kt)).
- Signature-verified provider resolution via [SeedVaultCheck.isTrustedProvider](../../../mobile/artemis-seed-vault/src/main/kotlin/com/selenus/artemis/seedvault/internal/SeedVaultCheck.kt). Rejects intents that resolve to third-party packages unless they match the platform signing cert or an explicit allowlist.
- SLIP-0010 / BIP-44 derivation helpers in `SolanaDerivation` with golden vectors covering standard, Ledger Live, and Ed25519-BIP32 schemes. See [SolanaDerivationTest.kt](../../../mobile/artemis-seed-vault/src/test/kotlin/com/selenus/artemis/seedvault/SolanaDerivationTest.kt).
- AIDL reconciled with the internal Kotlin binder proxy: both declare the same nine methods in the same transaction order. No more IPC drift.
- `ArtemisKeyStore` interface that abstracts Seed Vault vs any other hardware-backed store; `SeedVaultKeyStore` is the default implementation.

## Install

```kotlin
dependencies {
    implementation("xyz.selenus:artemis-seed-vault:2.2.0")
    implementation("xyz.selenus:artemis-core:2.2.0")
}
```

Published version is `2.2.0`. Source of truth is `version` in [gradle.properties](../../../gradle.properties).

## Authorise and sign (native Artemis API)

```kotlin
val manager = SeedVaultManager(context)

// 1. Launch the authorize UI. Purpose is an Int constant, not a String.
val authIntent = manager.buildAuthorizeIntent(
    purpose = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION
)
startActivityForResult(authIntent, REQ_AUTHORIZE)

// 2. Handle the result.
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode != REQ_AUTHORIZE || resultCode != RESULT_OK || data == null) return
    val tokenResult = manager.parseAuthorizationResult(data)
    //   tokenResult.token     : Long
    //   tokenResult.accountId : Long
    saveAuthToken(tokenResult.token)
}

// 3. Sign transactions off the main thread once the binder is bound.
lifecycleScope.launch {
    manager.connectSuspending()
    val signed: List<ByteArray> = manager.signTransactions(
        authToken = savedToken.toString(),
        transactions = listOf(txBytes)
    )
}
```

The auth-token result is `SeedVaultTokenResult(token: Long, accountId: Long)` defined in [SeedVaultTypes.kt](../../../mobile/artemis-seed-vault/src/main/kotlin/com/selenus/artemis/seedvault/SeedVaultTypes.kt). `SeedVaultManager.signTransactions` takes `authToken: String` and returns `List<ByteArray>`.

## Drop-in for existing `com.solanamobile.seedvault.*` callers

The `artemis-seedvault-compat` module preserves the upstream FQNs:

```kotlin
// This compiles unchanged against Artemis. SeedVault + Wallet delegate
// to the Artemis internal manager and provider checks.
if (SeedVault.isAvailable(context)) {
    val intent = Wallet.authorizeSeed(context, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
    startActivityForResult(intent, 0)
}

val token: Long = Wallet.onAuthorizeSeedResult(resultCode, data)
```

The validated upstream-compat surface is dumped to [artemis-seedvault-compat.api](../../../interop/artemis-seedvault-compat/api/artemis-seedvault-compat.api) and diffed in CI so changes to the namespace show up at PR time.

## Known test-mode caveat

`SeedVaultConstants` publishes `content://...` URIs that only parse on a device (the JVM `android.jar` stub returns `null`). The constants are therefore lazy: class loading works under unit tests, and the `Uri.withAppendedPath(...)` calls materialise only on real Android. Two legacy contract tests under `src/test/.../wallet/seedvault/` that still need a real `Uri` are excluded from unit tests via `build.gradle.kts`; run them via `androidTest` on a device or emulator.

## License

Apache License 2.0.
