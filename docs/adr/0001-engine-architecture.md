# ADR 0001: Engine architecture and layer rules

Status: Accepted  (2026-04-20)

## Context

The audit in `First, Artemis's runtime-compatibility s.md` called out that
Artemis's compat modules were accumulating protocol and lifecycle logic that
really belonged to the native Artemis layer. Compat was becoming a second
source of truth: two places parsed the MWA authorize response, two places
decided when a session had expired, two places converted JSON-RPC results
into typed objects. That is the same divergence the SMS `walletlib` /
`clientlib` split has produced in every major version bump.

We want one owner per protocol-facing action and compat that is a thin
facade.

## Decision

Artemis code sits in exactly four layers. Every file in the repo is
assignable to exactly one of them.

### 1. Engine

An engine owns a protocol contract end to end. It is the only place where a
protocol decision is made.

| Engine | What it owns | Primary module |
|---|---|---|
| `SessionEngine` | Wallet session lifecycle (idle, associating, authorized, active, expired, closed). | `mobile/artemis-wallet/WalletSessionManager.kt` |
| `AuthEngine` | authorize / reauthorize / deauthorize flows. Normalises `authToken`-driven reauth vs legacy. | `mobile/artemis-wallet-mwa-android/protocol/MwaClient.kt` |
| `SecureTransport` | Local MWA WebSocket, ping/pong, frame cap, fragmentation, origin policy, close semantics. | `mobile/artemis-wallet-mwa-android/protocol/MwaWebSocketServer.kt`, `MwaSession.kt` |
| `SigningEngine` | Routing transaction + message signing through wallet or local key store. Fallback semantics when sign-and-send is unsupported. | `mobile/artemis-wallet-mwa-android/MwaWalletAdapter.kt` |
| `CapabilityEngine` | Truthful capability model from the wallet's `get_capabilities` response. No optimistic defaults. | `mobile/artemis-wallet-mwa-android/protocol/MwaRpc.kt` + `mobile/artemis-wallet/WalletCapabilities.kt` |
| `SecurePersistenceEngine` | Keystore-backed auth tokens, HMAC secrets, session state. Fail-closed on any init failure. | `mobile/artemis-wallet-mwa-android/AuthTokenStore.kt`, `MwaSessionPersistence.kt`, `mobile/artemis-wallet/SessionManager.kt` |
| `RpcEngine` | JSON-RPC calls, batch semantics, retry classification, endpoint selection. | `foundation/artemis-rpc/RpcApi.kt` + `JsonRpcClient.kt` |
| `RealtimeEngine` | WebSocket subscriptions, reconnect accounting, collector-job retention, state flow. | `foundation/artemis-ws/RealtimeEngine.kt` + `SolanaWsClient.kt` |

### 2. Provider / Contract

A provider is a single-purpose interface that an engine (or a user's code)
talks to. It isolates IPC, external services, or hardware-backed custody.

- `SeedVaultContractClient`, `SeedVaultAccountProvider`, `SeedVaultSigningProvider` (Phase 3.1)
- `RpcBroadcaster` (injection point for Artemis or upstream RPC submission)
- `ArtemisDas` / `HeliusDas` / `RpcFallbackDas` / `CompositeDas`

Providers own no protocol decisions. They call a well-defined external
contract and return typed data. If a new runtime (e.g. an MPC signer,
hardware wallet, emulator) needs to plug in, it implements the provider
interface.

### 3. Adapter

An adapter is a native Artemis class that an app imports directly. It
wires one or more engines + providers into a convenient object.

Examples:

- `ArtemisMobile` (wires `SessionEngine` + `RpcEngine` + `RealtimeEngine` + `SigningEngine` into one object).
- `MwaWalletAdapter` (wires `AuthEngine` + `SigningEngine` + `CapabilityEngine` + `SecureTransport`).
- `WalletSession` (wires `SessionEngine` + `TxEngine` + whichever signing provider is active).

Adapters are allowed to do orchestration (call engine A then engine B, pass
A's result to B) but they do NOT re-implement engine logic.

### 4. Compat

Compat modules live under `interop/artemis-*-compat`. They re-export
upstream public APIs (`com.solana.*`, `com.solanamobile.*`, `org.sol4k.*`)
at the same fully qualified names. Every method on a compat class is a
facade call into an Artemis engine or adapter.

Compat rules:

- Compat MUST NOT own business logic.
- Compat MUST NOT make protocol decisions.
- Compat MUST NOT catch + re-throw to hide errors; it re-types exceptions
  into the upstream exception hierarchy and lets them propagate.
- Compat MUST preserve every externally visible field from the engine
  response (accounts, chains, features, wallet metadata, SIWS result). The
  audit specifically called out synthesised reduced results as a bug.

## Consequences

- Adding a new MWA RPC method is a two-file change (engine + compat
  facade) and not a three-way divergence.
- Tests target engines directly. A test of `signMessagesDetached` should
  not need the compat layer to run.
- API-diff snapshots (`./gradlew dumpApi`) are run against compat only.
  They would be noise against engine code, which we expect to evolve
  freely.
- Every PR answers six questions in the description:
  1. What public contract is being satisfied?
  2. Which engine owns the logic?
  3. Is this file facade or implementation?
  4. What is original about the design?
  5. What does it improve?
  6. Which public spec / doc behaviour does it match?

## Originality principle

> We are implementing the Solana Mobile contract, not copying the Solana
> Mobile code.

Every engine listed above is an original implementation. Artemis matches
the public protocol / public API shape; the machinery is Artemis-owned.
Specifically:

- `MwaSession` uses an `AtomicInteger`-based send/recv counter and a scope
  that `close()` cancels. The SMS reference session uses a different
  concurrency model.
- `SolanaWsClient` uses a `CompletableDeferred<Unit>` open signal so the
  reconnect loop only advances after the socket has actually opened. SMS
  callers don't expose this.
- `SeedVaultContractClient` is a fresh split that upstream SDKs don't
  provide; it lets non-device test doubles plug into the full Artemis
  flow without stubbing binder transactions.
- `CompositeDas` adds a 30-second cooldown on the primary after failure,
  so a burst of calls doesn't pay the primary timeout repeatedly. No
  equivalent exists in Helius's reference client.

## References

- `docs/PARITY_MATRIX.md`: per-capability status map.
- `docs/ADOPTING_ARTEMIS_WITH_SOLANA_MOBILE.md`: both migration tracks and Solana Mobile boundary language.
- `docs/DEPENDENCY_RULES.md`: ring hierarchy that prevents compat from
  depending on advanced modules.
- MWA 2.0 migration notes (upstream): authorize-with-auth-token,
  deprecated reauthorize, mandatory sign-and-send for wallets.
