# Migration from solana-kt to Artemis

## Common Issues

### TransactionService.kt: Unresolved reference: api

**Issue:**
The build fails with `Unresolved reference: api` in `TransactionService.kt`. This happens because Artemis's RPC service (or the `SolanaRpcService` equivalent) does not expose an `api` property for direct access, but rather provides methods like `sendTransaction` directly.

**Resolution:**
Update `TransactionService.kt` to serialize the transaction and use the RPC service's send method directly.

*Note: As of version 1.0.3, `RpcApi` includes:*
1.  *A compatibility `api` property that returns `this`.*
2.  *Overloads for `sendTransaction` and `simulateTransaction` that accept `ByteArray`.*
3.  *Aliases for legacy methods like `getRecentBlockhash`, `getConfirmedTransaction`, and `getConfirmedSignaturesForAddress2`.*
4.  *Convenience method `getSignatureStatus(String)` for checking a single signature.*
5.  *A `Connection` class that wraps `RpcApi` for users migrating from `solana-kt`'s `Connection`.*
6.  *Type aliases for `PublicKey` (maps to `Pubkey`), `Account` (maps to `Keypair`), and `TransactionInstruction` (maps to `Instruction`).*
7.  *`PROGRAM_ID` constants in `SystemProgram` and `TokenProgram` objects.*

*These additions allow for a near drop-in replacement without significant code changes.*

**Changes:**

1.  **TransactionService.kt**
    *   Implement `signAndSendBase64Transaction` using `TransactionParser.parse(bytes)` to deserialize (if needed).
    *   Use `transaction.serialize()` to get bytes for sending.
    *   Use `rpcService.sendTransaction(serialized)` (or `sendRawTransaction` if passing bytes directly) to send.

```kotlin
// Example (Conceptual)
val serialized = transaction.serialize()
// If rpcService is Artemis RpcApi:
rpcService.sendRawTransaction(serialized)
```
