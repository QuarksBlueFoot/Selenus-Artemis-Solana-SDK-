# Parity matrix

Feature-by-feature honest status of Artemis against the Kotlin/Android Solana SDK ecosystem. Each status is gated on what the tests in the repo actually exercise.

## Legend

- **Verified**: implementation shipped and exercised by a test that would fail if the feature broke.
- **In Progress**: implementation shipped, more work scheduled; treat as a preview surface.
- **Partial**: feature works for the documented happy path; known edge cases or upstream behaviours are not covered yet.
- **Experimental**: surface exists; behaviour may shift before 1.0 of that module.
- **Planned**: on the roadmap, no code yet.
- **N/A**: not applicable to that SDK.

## Release labels

Two orthogonal labels appear throughout the docs:

- `Artemis-native ready`: safe to adopt using Artemis APIs directly (`ArtemisMobile.create()`, `WalletSession`, `TxEngine`, `RpcApi`). Everything `Verified` here qualifies.
- `SMS-client-compat ready`: safe to use the `interop/artemis-*-compat` shims for source-compatible migration from the listed Solana Mobile client libraries. Requires both the native path and the compat behavior tests to pass. Today this label applies to `Verified` items in the Wallet/Mobile and RPC rows below; the rest is `Partial` or `In Progress`. This label does not mean Artemis replaces MWA, Seed Vault, or the Solana Mobile platform.

## Core primitives

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| PublicKey type | Yes | Yes | via solana-kmp | via solana-kmp | `Pubkey` | Verified |
| Keypair generation | Yes | Yes | N/A | N/A | `Keypair` | Verified |
| Ed25519 signing | Yes | Yes | N/A | N/A | `Crypto` | Verified |
| Base58 encode/decode | Yes | Yes | N/A | N/A | `Base58` | Verified |
| Base64 encode/decode | Partial | Yes | N/A | N/A | `PlatformBase64` | Verified |
| PDA derivation | Yes | No | N/A | via solana-kmp | `Pda.find()` | Verified |
| HD key derivation | No | No | N/A | N/A | `Bip32.deriveKeypair()` + `SolanaDerivation` | Verified (RFC + golden vectors) |
| X25519 ECDH | No | No | N/A | No | `SeedVaultCrypto.deriveX25519SharedSecret` | Verified (symmetric + context-separation tests) |
| HKDF-SHA256 | No | No | N/A | N/A | `HkdfSha256.derive` | Verified (RFC 5869 Appendix A test cases) |
| AES-128-GCM session cipher | No | No | N/A | N/A | `Aes128Gcm` | Verified (round-trip + tamper-reject) |
| P-256 ECDH + ECDSA P1363 | No | No | Yes (inside walletlib) | N/A | `EcP256` | Verified (round-trip + DER/P1363 tests) |

## Transaction model

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| Legacy transactions | Yes | Yes | N/A | N/A | `Transaction` | Verified |
| Versioned transactions (v0) | Partial | Partial | N/A | N/A | `VersionedTransaction` | Verified |
| Address lookup tables | Partial | No | N/A | N/A | `VersionedMessage.addressTableLookups` | Verified (parse + serialize round-trip) |
| Durable nonce support | No | No | N/A | N/A | `DurableNonce` | Partial (build + send covered; rollback edge cases not fuzzed) |
| Transaction serialization | Yes | Yes | N/A | N/A | Yes | Verified (internal round-trip + initial `web3.js` 1.98.4 byte fixtures; named-SDK equivalence remains fixture-scoped) |
| Multi-signer support | Yes | Yes | N/A | N/A | `SolanaSigner.signTransaction` | Verified (index-by-pubkey lookup) |

## RPC client

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| JSON-RPC methods (typed `suspend fun`) | ~20 | ~15 | via solana-kmp | via solana-kmp | 92 typed methods declared on `RpcApi` (113 funs total including helpers / overloads) | Verified |
| Typed response wrappers | Some | Minimal | N/A | N/A | `*Typed` helpers | Verified |
| Batch requests | No | No | N/A | N/A | `callBatch` + `callBatchTyped` | Verified |
| Per-item batch error surface | No | No | N/A | N/A | `BatchItemResult.Ok` / `.Err(code,message,data)` | Verified |
| Commitment config | Yes | Yes | N/A | N/A | Yes | Verified |
| Endpoint failover | No | No | N/A | N/A | `RpcEndpointPool` | Verified |
| Circuit breaker (three-state, `Closed` / `Open` / `HalfOpen`, observable `StateFlow`, configurable threshold + cooldown + half-open success threshold) | No | No | N/A | N/A | `CircuitBreaker` (standalone, wrappable around any suspend block) | Verified |
| Blockhash cache | No | No | N/A | N/A | `BlockhashCache` | Verified |
| Retry classification (sockets, TLS, EOF, timeouts) | No | No | N/A | N/A | `shouldRetry` typed branches | Verified |

## WebSocket

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| Account subscriptions | No | No | N/A | N/A | Yes | Verified |
| Program subscriptions | No | No | N/A | N/A | Yes | Verified |
| Signature subscriptions | No | No | N/A | N/A | Yes | Verified |
| Slot subscriptions | No | No | N/A | N/A | Yes | In Progress |
| Real transport pings (OkHttp `pingInterval`) | N/A | N/A | N/A | N/A | Yes | Verified |
| Reconnect only on `onOpen` success | N/A | N/A | N/A | N/A | Yes | Verified |
| Deterministic resubscribe | N/A | N/A | N/A | N/A | Yes | Verified |
| HTTP polling fallback (acct/sig/prog) | N/A | N/A | N/A | N/A | Yes | Partial (logs-subscribe has no HTTP equivalent; surfaces a typed `logsPollingUnavailable` event) |
| Collector-job retention on reconnect | N/A | N/A | N/A | N/A | Yes | Verified |
| Typed `ConnectionState` StateFlow | No | No | N/A | N/A | `ConnectionState` | Verified |
| Endpoint rotation | No | No | N/A | N/A | Yes | Verified |

## Wallet / Mobile

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| Wallet abstraction | No | No | No (raw protocol) | No | `WalletSession` | Verified |
| MWA 2.0 client (authorize/addresses/features/auth_token) | No | No | Yes | No | `artemis-wallet-mwa-android` | Verified |
| MWA auth lifecycle (unified `authorize(auth_token=...)`) | No | No | Yes | No | `WalletSessionManager` | Verified |
| `withWallet { }` retry narrowed to session-expiry sentinels | No | No | No | No | `SessionExpiredException` | Verified |
| Sign transactions (MWA 2.0 optional) | N/A | N/A | Yes | N/A | `MwaWalletAdapter.signMessages` | Verified |
| Sign and send transactions | N/A | N/A | Yes | N/A | Yes | Verified |
| Sign-and-send wallet-unsupported fallback (`SignedButNotBroadcast` + injectable `RpcBroadcaster`) | N/A | N/A | N/A | N/A | Yes | Verified |
| SIWS (Sign In With Solana) payload round-trip (incl. `resources`) | N/A | N/A | Partial | N/A | Yes | Verified |
| Keystore-backed auth token (AES-256-GCM, fail-closed) | No | No | Partial | No | `KeystoreEncryptedAuthTokenStore` | Verified |
| HMAC session secret persisted for reauthorize after process death | No | No | No | No | `SessionManager.installPersistedSecret` | In Progress (requires caller-side Keystore wiring) |
| MWA session sequencing (atomic counters, fail-on-close) | No | No | Yes | No | `MwaSession` | Verified |
| Local WebSocket server hardened (loopback bind, origin allow-list, ping/pong, oversized-frame reject, fragmentation) | N/A | N/A | Yes | N/A | `MwaWebSocketServer` | Verified (loopback + origin reject + oversized frame reject + ping/pong covered by `MwaWebSocketServerTest`) |
| Chain-gated reauthorize (token issued for `solana:mainnet` rejected against `solana:devnet`) | N/A | N/A | Yes (`BaseScenario.doReauthorize`) | N/A | `WalletMwaServer.handleAuthorizeAsReauthorize` + `handleReauthorize` enforce `record.chain == requestedChain` | Verified (covered by `WalletCorrectnessTest`) |
| Wallet-driven `DeauthorizedEvent.complete()` (server awaits the wallet's UI cleanup before replying success; bounded 30s timeout) | N/A | N/A | Yes | N/A | `WalletMwaServer.handleDeauthorize` | Verified |
| `sign_messages` address-set check (every requested address must be in the active authorization's account list) | N/A | N/A | Yes | N/A | `WalletMwaServer.handleSignMessages` | Verified |
| `AuthRepository.start()` / `stop()` lifecycle hooks called by `LocalScenario` on session establish/close (SQLite-backed impls open the DB here) | N/A | N/A | Yes | N/A | `AuthRepository` interface + `LocalScenario` wiring | Verified |
| `get_capabilities` emits the spec-correct unified `max_payloads_per_request` field alongside the legacy `max_transactions_per_request` / `max_messages_per_request` for MWA 1.x compat | N/A | N/A | Yes | N/A | `WalletMwaServer.handleGetCapabilities` | Verified |
| HELLO_RSP frame shape gated on negotiated protocol version (LEGACY frame is `Qw` only; V1 frame is `Qw` followed by an encrypted SessionProperties envelope) | N/A | N/A | Yes | N/A | `WalletSideHandshake.perform` | Verified |
| Low-power-mode gate on `noConnectionWarningTimeoutMs` (warning only fires when `PowerManager.isPowerSaveMode()` returns true) | N/A | N/A | Yes | N/A | `LocalScenario` + `DevicePowerConfigProvider` | Verified |
| MWA wallet conformance detector (normalizes Phantom/Solflare/Seeker quirks from upstream #958, #1146, #1331, #1458) | No | No | No | No | `MwaWalletConformance` + `KnownWallet` + `ConformanceReport` | Verified (covered by `MwaWalletConformanceTest`) |
| Spec-first MWA error taxonomy with typed recovery hints (closes upstream #314) | No | No | No | No | `MwaError` (sealed) + `Recovery` enum | Verified (covered by `MwaErrorTest`) |
| SIWS validator with canonical message rendering + ed25519 verification + replay check (closes upstream #193, #1331) | No | No | Partial | No | `MwaSiwsValidator` + `SiwsVerification` | Verified (covered by `MwaSiwsValidatorTest`) |
| First-class multi-account session wrapper (closes upstream #438, open 2+ years) | No | No | No | No | `MwaMultiAccountSession` + `ResolvedAccount` | Verified (covered by `MwaMultiAccountSessionTest`) |
| WebView / PWA / TWA environment detector with routing hints (closes upstream #1082, #1323, #1364) | No | No | No | No | `MwaEnvironmentDetector` | Verified (covered by `MwaEnvironmentDetectorTest`) |
| Keystore-backed auth-token store with AES-256-GCM `[12-byte IV][ciphertext+tag]` wire format and fail-closed on tamper | No | No | Partial | No | `KeystoreEncryptedAuthTokenStore` + `InMemoryAuthTokenStore` | Verified (covered by `AuthTokenStoreTest`; AES round-trip + tamper-reject + IV uniqueness) |
| Seed Vault (system-service custody; secure EE) | No | No | Separate SDK | No | `artemis-seed-vault` | Partial (device required for full behaviour; wrapper Verified, service is upstream) |
| Seed Vault strict contract/provider split | N/A | N/A | N/A | N/A | `SeedVaultContractClient` + `*Provider` | Verified |
| Seed Vault provider trust checks (platform signature / allowlist) | N/A | N/A | N/A | N/A | `SeedVaultCheck.isTrustedProvider` | Verified |
| Seed Vault IPC timeouts + strict auth-token parsing | N/A | N/A | N/A | N/A | `parseAuthTokenStrict` + `withTimeout(30s)` | Verified |
| Local keypair signing | Yes | Yes | N/A | N/A | `WalletSession.Local` | Verified |
| React Native MWA wrapper | No | No | Yes | No | `advanced/artemis-react-native` (TypeScript + Android bridge; ships outside the Gradle build, distributed via npm) | Partial (Android-only; RN platform / `readyState` detection Verified by the TS test runner under `advanced/artemis-react-native/`) |

## MWA compat (drop-in path)

| Capability | Artemis module | Status |
|---|---|---|
| `com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter` (ktx) | `interop/artemis-mwa-compat` | Verified |
| `MobileWalletAdapterClient` low-level client with live `SessionBridge` | `interop/artemis-mwa-clientlib-compat` | Verified (no `UnsupportedOperationException` in the happy path) |
| `LocalAssociationScenario` with real P-256 keypair, loopback port reservation, base64url association token | `interop/artemis-mwa-clientlib-compat` | Verified |
| Nested result types (`AuthorizationResult`, `SignPayloadsResult`, `SignMessagesResult`, `SignAndSendTransactionsResult`) | `interop/artemis-mwa-clientlib-compat` | Verified |
| `LocalAdapterOperations` routes through live `MwaSessionBridge` | `interop/artemis-mwa-compat` | Verified |
| API-diff snapshot (`dumpApi` + `verifyApiSnapshots` Gradle tasks) | `interop/artemis-mwa-*/api/*.api` | Verified |

## MWA walletlib compat (drop-in, wallet side)

| Capability | Artemis module | Status |
|---|---|---|
| `com.solana.mobilewalletadapter.walletlib.association.AssociationUri.parse` (+ `parseOrNull`, `createScenario(Context, ...)` factory) | `interop/artemis-mwa-walletlib-compat` | Verified |
| `LocalAssociationScenario` with `startAsync(): CompletableFuture<String>` and lifecycle callbacks | `interop/artemis-mwa-walletlib-compat` | Verified |
| `RemoteWebSocketServerScenario` FQN reachable; runtime stub completes exceptionally with `UnsupportedOperationException` until reflector lands | `interop/artemis-mwa-walletlib-compat` | Partial |
| `MobileWalletAdapterServer` typed exceptions: `RequestDeclinedException`, `InvalidPayloadsException`, `NotSubmittedException`, `TooManyPayloadsException`, `AuthorizationNotValidException`, `ChainNotSupportedException`, deprecated `ClusterNotSupportedException` alias | `interop/artemis-mwa-walletlib-compat` | Verified |
| `MobileWalletAdapterSession` FQN exposed | `interop/artemis-mwa-walletlib-compat` | Verified |
| `JsonRpc20Server` with canonical error codes + envelope helpers | `interop/artemis-mwa-walletlib-compat` | Verified |
| `walletlib.authorization.AuthRepository` interface (start/stop/issue/fromAuthToken/toAuthToken/reissue/revoke/getAuthorizedIdentities/getAuthorizations) + `InMemoryAuthRepository` + `AuthRecord` + `IdentityRecord` + `AccountRecord` | `interop/artemis-mwa-walletlib-compat` | Verified |
| `BaseScenarioRequest` / `VerifiableIdentityRequest` / `SignPayloadsRequest` marker interfaces | `interop/artemis-mwa-walletlib-compat` | Verified |
| `WalletIconProvider` + `DefaultWalletIconProvider` | `interop/artemis-mwa-walletlib-compat` | Verified |
| Deprecated MWA-1.x request aliases: `cluster` getter on `AuthorizeRequest` / `ReauthorizeRequest`, `completeWithClusterNotSupported`, `completeWithAuthorize(ByteArray, …)` overload, `publicKey` getter on Sign* requests | `interop/artemis-mwa-walletlib-compat` | Verified |

## Seed Vault compat

| Capability | Artemis module | Status |
|---|---|---|
| `com.solanamobile.seedvault.Wallet` static surface | `interop/artemis-seedvault-compat` | Verified (every upstream static; `AuthTokenGuard` for the invalid-token edge) |
| `WalletContractV1` constants | `interop/artemis-seedvault-compat` | Verified (every public const; IntDef + `@Purpose`) |
| `SeedVault.isAvailable` / `AccessType` | `interop/artemis-seedvault-compat` | Verified |
| AIDL `ISeedVaultService` 9-method shape | `mobile/artemis-seed-vault/src/main/aidl` | Verified (reconciled with internal Kotlin proxy) |

## solana-kmp compat (drop-in path)

| Capability | Artemis module | Status |
|---|---|---|
| `foundation.metaplex.solanapublickeys.PublicKey` (both ctors + findProgramAddress / createProgramAddress) | `interop/artemis-solana-kmp-compat` | Verified |
| `PUBLIC_KEY_LENGTH`, `defaultPublicKey()`, `HasPublicKey` | `interop/artemis-solana-kmp-compat` | Verified |
| `foundation.metaplex.base58.Base58` object + `encodeToBase58String` / `decodeBase58` / checksum variants | `interop/artemis-solana-kmp-compat` | Verified |
| `foundation.metaplex.amount.Amount` data class + `Lamports` / `SOL` factories | `interop/artemis-solana-kmp-compat` | Verified |
| `lamports` / `sol` / `createAmount` / `createAmountFromDecimals` / `percentAmount` / `tokenAmount` | `interop/artemis-solana-kmp-compat` | Verified |
| Amount arithmetic (`addAmounts`, `subtractAmounts`, `multiplyAmount`, `divideAmount`, `absoluteAmount`) | `interop/artemis-solana-kmp-compat` | Verified |
| Amount comparison + predicates (`compareAmounts`, `isEqualToAmount`, `isZero/Positive/NegativeAmount`, `sameAmounts`, `isAmount`) | `interop/artemis-solana-kmp-compat` | Verified |
| Amount assertions + formatting (`assertAmount`, `assertSolAmount`, `amountToString`, `amountToNumber`, `displayAmount`) | `interop/artemis-solana-kmp-compat` | Verified |
| `Commitment` enum, `Encoding` enum | `interop/artemis-solana-kmp-compat` | Verified |
| `Cluster` sealed class (`MainnetBeta`, `Devnet`, `Testnet`, `Localnet`, `Custom`) + `resolveClusterFromEndpoint` | `interop/artemis-solana-kmp-compat` | Verified |
| `RpcGetAccountInfoConfiguration`, `RpcGetMultipleAccountsConfiguration`, `RpcGetProgramAccountsConfiguration`, `RpcGetLatestBlockhashConfiguration`, `RpcGetSlotConfiguration`, `RpcGetBalanceConfiguration`, `RpcRequestAirdropConfiguration`, `RpcSendTransactionConfiguration` | `interop/artemis-solana-kmp-compat` | Verified |
| `RpcDataFilter` sealed (`Size`, `Memcmp`), `MemcmpFilter`, `RpcDataSlice` | `interop/artemis-solana-kmp-compat` | Verified |
| `BlockhashWithExpiryBlockHeight(blockhash, lastValidBlockHeight)` | `interop/artemis-solana-kmp-compat` | Verified |
| `RpcInterface.getProgramAccounts` + `AccountInfoWithPublicKey` | `interop/artemis-solana-kmp-compat` | Verified |
| `Transaction` surface (addInstruction / add / setRecentBlockHash / sign / partialSign / addSignature / verifySignatures / compileMessage / serializeMessage / serialize(SerializeConfig)) | `interop/artemis-solana-kmp-compat` | Verified |
| `SerializeConfig(requireAllSignatures, verifySignatures)` | `interop/artemis-solana-kmp-compat` | Verified |
| `Message` interface + `SolanaMessage` (isAccountSigner / isAccountWritable / isProgramId / programIds / nonProgramIds / serialize / setFeePayer) | `interop/artemis-solana-kmp-compat` | Verified |
| `MessageHeader(numRequiredSignatures, numReadonlySignedAccounts, numReadonlyUnsignedAccounts)` with `toByteArray` | `interop/artemis-solana-kmp-compat` | Verified |
| `CompiledInstruction`, `SignaturePubkeyPair`, `NonceInformation`, `Shortvec.encodeLength/decodeLength` | `interop/artemis-solana-kmp-compat` | Verified |
| `foundation.metaplex.solana.programs.SystemProgram` (`transfer`, `createAccount`, `PROGRAM_ID`) | `interop/artemis-solana-kmp-compat` | Verified |
| `foundation.metaplex.solana.programs.MemoProgram.writeUtf8` | `interop/artemis-solana-kmp-compat` | Verified |
| `Keypair` (generate / publicKey / secretKey / sign), `SolanaEddsa.sign/verify/publicKeyFromSeed` | `interop/artemis-solana-kmp-compat` | Verified |
| `RPC(rpcUrl)` with `asArtemis()` escape hatch | `interop/artemis-solana-kmp-compat` | Verified |
| `ReadApiInterface` + `ReadApiDecorator` (getAsset, getAssetsByOwner, getAssetsByGroup, getAssetProof) | `interop/artemis-solana-kmp-compat` | Verified |

## Sol4k compat (drop-in path)

| Capability | Artemis module | Status |
|---|---|---|
| `org.sol4k.Connection` (22+ methods: getBalance, getLatestBlockhash, getAccountInfo, getMultipleAccounts, sendTransaction, simulateTransaction, getFeeForMessage, getSignaturesForAddress, getRecentPrioritizationFees, getEpochInfo, getVersion, getIdentity, getHealth, getTransactionCount, requestAirdrop, getMinimumBalanceForRentExemption, getTokenAccountBalance, getTokenSupply, isBlockhashValid) | `interop/artemis-sol4k-compat` | Verified |
| `org.sol4k.PublicKey` with `findProgramAddress` + `findProgramDerivedAddress` (ATA helper) | `interop/artemis-sol4k-compat` | Verified |
| `org.sol4k.Keypair` (generate, fromSecretKey, sign, publicKey, secret) | `interop/artemis-sol4k-compat` | Verified |
| `Transaction` (sign, addSignature, serialize, `from(String)`) | `interop/artemis-sol4k-compat` | Verified |
| `VersionedTransaction` (sign, addSignature, serialize, `calculateFee(lamportsPerSignature)`, `from(String)`) | `interop/artemis-sol4k-compat` | Verified |
| `TransactionMessage` (newMessage, deserialize, withNewBlockhash, serialize) | `interop/artemis-sol4k-compat` | Verified |
| Instruction builders (`TransferInstruction`, `SplTransferInstruction`, open `TokenTransferInstruction` base, `Token2022TransferInstruction`, `CreateAssociatedTokenAccountInstruction`, `CreateAssociatedToken2022AccountInstruction`, `SetComputeUnitLimitInstruction`, `SetComputeUnitPriceInstruction`) | `interop/artemis-sol4k-compat` | Verified |
| `AccountMeta` companion factories (`signerAndWritable`, `writable`, `signer`, `readonly`) | `interop/artemis-sol4k-compat` | Verified |
| `PublicKey.findProgramAddress` + `findProgramDerivedAddress(holder, mint, programId = TOKEN_PROGRAM_ID)` (Token-2022 ATA derivation supported) | `interop/artemis-sol4k-compat` | Verified |
| `PublicKey.readPubkey(bytes, offset)` + `PublicKey.valueOf(base58)` | `interop/artemis-sol4k-compat` | Verified |
| `ProgramDerivedAddress(address, nonce)` (matches upstream field name; deprecated `publicKey` alias preserved) | `interop/artemis-sol4k-compat` | Verified |
| `Constants` (SYSTEM_PROGRAM, TOKEN_PROGRAM_ID, TOKEN_2022_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID, COMPUTE_BUDGET_PROGRAM_ID, SYSVAR_RENT_ADDRESS, PUBLIC_KEY_LENGTH, SIGNATURE_LENGTH) | `interop/artemis-sol4k-compat` | Verified |
| `Base58`, `Binary` (uint32/int64/uint16/encodeLength/decodeLength), `Convert` (lamport/sol/micro-lamport) | `interop/artemis-sol4k-compat` | Verified |
| `Commitment` enum, `RpcUrl` enum, `Health` enum, API types (`AccountInfo`, `Blockhash`, `TokenAccountBalance`, `TokenAmount`, `TransactionSignature`, `TransactionSimulation`, `PrioritizationFee`, `Version`, `EpochInfo`) | `interop/artemis-sol4k-compat` | Verified |
| `RpcException` as `data class RpcException(code: Int, message: String, rawResponse: String)` with destructuring + `.copy()`; `SerializationException` as `data class SerializationException(message: String)`. Matches upstream sol4k 0.7.0 shape exactly. | `interop/artemis-sol4k-compat` | Verified |

## Metaplex Android compat (drop-in path)

| Capability | Artemis module | Status |
|---|---|---|
| `com.metaplex.lib.Metaplex` entry point with `connection`, `identityDriver`, `nft`, `tokens`, `das`, `candyMachinesV2`, `candyMachines` modules | `interop/artemis-metaplex-android-compat` | Verified |
| `NftModule` (findByMint, findAllByOwner, findAllByMintList; findAllByCreator and findAllByUpdateAuthority degrade to empty when no DAS is available) | `interop/artemis-metaplex-android-compat` | Partial |
| `TokensModule.findByMint` | `interop/artemis-metaplex-android-compat` | Verified |
| `DasModule` (assetsByOwner, asset) | `interop/artemis-metaplex-android-compat` | Verified |
| Token Metadata instruction builders (createMetadataAccountV3, createMasterEditionV3, updateMetadataAccountV2, signMetadata, verifyCollection, unverifyCollection, setAndVerifyCollection, verifySizedCollectionItem, approveCollectionAuthority, revokeCollectionAuthority) | `compatibility/artemis-nft-compat` | Verified |
| pNFT support (token record PDA, TokenRecordParser, collection authority record PDA, CollectionAuthorityRecordParser) | `compatibility/artemis-nft-compat` | Verified |

## Common programs

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| System Program | Yes | Yes | N/A | N/A | 12 instructions | Verified |
| SPL Token | Partial | Partial | N/A | N/A | 10 builders | Verified |
| Associated Token | Partial | Partial | N/A | N/A | Yes | Verified |
| Compute Budget | No | No | N/A | N/A | Yes | Verified |
| Stake Program | No | No | N/A | N/A | 5 instructions | Verified |
| Memo Program | No | Partial | N/A | N/A | Yes | Verified |
| Address Lookup Table program | No | No | N/A | N/A | Yes | Partial (create + extend covered; close + freeze not yet) |

## Token-2022

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| Transfer fees | No | No | N/A | No | Yes | Verified |
| Interest bearing | No | No | N/A | No | Yes | Verified |
| Permanent delegate | No | No | N/A | No | Yes | Verified |
| Default account state | No | No | N/A | No | Yes | Verified |
| Transfer hook | No | No | N/A | No | Yes | In Progress |
| Metadata pointer | No | No | N/A | No | Yes | Verified |
| Confidential transfers | No | No | N/A | No | Yes | Experimental |
| CPI guard | No | No | N/A | No | Yes | Verified |
| Immutable owner | No | No | N/A | No | Yes | Verified |
| Mint close authority | No | No | N/A | No | Yes | Verified |
| TLV decoding | No | No | N/A | No | Yes | Verified |

## NFT / Metaplex

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| Token Metadata read | No | No | N/A | Yes | Yes | Verified |
| Token Metadata write | No | No | N/A | Yes | Yes | Partial (mint / update covered; burn / verify edge cases pending) |
| Editions | No | No | N/A | Yes | Yes | Partial |
| Collections | No | No | N/A | Yes | Yes | Verified |
| MPL Core | No | No | N/A | Partial | Yes | Partial |
| Compressed NFTs (Bubblegum) | No | No | N/A | Partial | `artemis-cnft` | Verified |
| cNFT transfer via MarketplaceEngine | No | No | N/A | No | `MarketplaceEngine` | Verified |
| Candy Machine v3 | No | No | N/A | No | `artemis-candy-machine` | In Progress |
| DAS by-owner / by-collection / single-asset | No | No | N/A | No | `ArtemisDas` / `HeliusDas` | Verified |
| DAS RPC fallback | No | No | N/A | No | `RpcFallbackDas` | Verified |
| DAS primary + fallback router with 30s cooldown | No | No | N/A | No | `CompositeDas` | Verified |
| Marketplace preflight (ownership, ATA, frozen) | No | No | N/A | No | `MarketplacePreflight` | Verified |
| Standalone ATA ensurer | No | No | N/A | No | `AtaEnsurer` | Verified |

## Developer tooling

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| Transaction simulation | No | No | N/A | N/A | `TxEngine` simulate stage | Verified |
| Compute estimation | No | No | N/A | N/A | `artemis-compute` | Verified |
| Priority fee helpers | No | No | N/A | N/A | Yes | Verified |
| Error decoding | Minimal | No | N/A | N/A | `artemis-errors` | Partial |
| Transaction engine (pipeline) | No | No | N/A | N/A | `TxEngine` | Verified |
| Retry pipeline with classified errors | No | No | N/A | N/A | `RetryPipeline` | Verified |
| Blockhash cache with TTL | No | No | N/A | N/A | `BlockhashCache` | Verified |
| Framework event bus | No | No | N/A | N/A | `ArtemisEventBus` | Verified |
| Compat API-diff snapshot | No | No | N/A | N/A | `./gradlew dumpApi` | Verified |

## Artemis-only capabilities

These are features no other Kotlin Solana SDK provides; none are required for the SMS-client-compat path.

| Capability | Module | Status |
|---|---|---|
| Typed WebSocket subscriptions (`RealtimeEngine`) | `artemis-ws` | Verified |
| DAS queries (Helius / RPC standard) | `artemis-cnft` | Verified |
| cNFT transfer with proof resolution (`MarketplaceEngine`) | `artemis-cnft` | Verified |
| Full mobile stack wiring (`ArtemisMobile.create()`) | `artemis-wallet-mwa-android` | Verified |
| Anchor IDL client | `artemis-anchor` | Partial |
| Jupiter DEX integration | `artemis-jupiter` | Partial |
| Solana Actions / Blinks | `artemis-actions` | In Progress |
| Privacy toolkit | `artemis-privacy` | Experimental |
| Zero-copy streaming | `artemis-streaming` | Experimental |
| Transaction batching | `artemis-batch` | In Progress |
| Offline queue | `artemis-offline` | In Progress |
| Portfolio tracking | `artemis-portfolio` | In Progress |
| Gaming primitives (session keys, VRF, state proofs) | `artemis-gaming` | Experimental |
| DePIN attestation | `artemis-depin` | Experimental |
| NLP transactions | `artemis-nlp` | Experimental |
| Intent decoding | `artemis-intent` | Verified |
| Universal program client | `artemis-universal` | Experimental |
| MWA wallet conformance detector (normalizes known Phantom/Solflare/Seeker quirks from upstream #958, #1146, #1331, #1458) | `artemis-wallet-mwa-android` (`MwaWalletConformance`, `KnownWallet`, `ConformanceReport`); test: `MwaWalletConformanceTest` | Verified |
| Spec-first MWA error taxonomy with typed recovery hints (closes upstream #314) | `artemis-wallet-mwa-android` (`MwaError`, `Recovery`); test: `MwaErrorTest` | Verified |
| SIWS validator with canonical message rendering + ed25519 verification + replay check (closes upstream #193, #1331) | `artemis-wallet-mwa-android` (`MwaSiwsValidator`, `SiwsVerification`); test: `MwaSiwsValidatorTest` | Verified |
| First-class multi-account session wrapper (closes upstream #438, open 2+ years) | `artemis-wallet-mwa-android` (`MwaMultiAccountSession`, `ResolvedAccount`); test: `MwaMultiAccountSessionTest` | Verified |
| WebView / PWA / TWA environment detector with routing hints (closes upstream #1082, #1323, #1364) | `artemis-wallet-mwa-android` (`MwaEnvironmentDetector`); test: `MwaEnvironmentDetectorTest` | Verified |
| Three-state circuit breaker (`CircuitBreaker`) wrappable around any suspend block, observable via `StateFlow`, configurable threshold/cooldown/half-open success threshold | `artemis-rpc` (`CircuitBreaker`, `CircuitBreakerOpenException`); test: `CircuitBreakerTest` | Verified |
