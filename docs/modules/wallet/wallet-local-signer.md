# Local signer adapter

`LocalSignerWalletAdapter` is a reference adapter for tests or server-side usage.

```kotlin
val wallet = LocalSignerWalletAdapter.generate()
val pk = wallet.publicKey
```

It returns a detached signature pack, which is useful for offline verification workflows.
Apps that need raw transaction bytes should compile and attach signatures at the transaction layer.
