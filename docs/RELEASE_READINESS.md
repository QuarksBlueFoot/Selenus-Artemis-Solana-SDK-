# Release readiness

Artemis ships against two targets. Being ready for one does not mean ready for
the other, and each target carries its own checklist. Every box below is
enforced by CI. The `release-gate` job in [.github/workflows/publish.yml](../.github/workflows/publish.yml)
blocks the Maven and NPM publish until the suite goes green.

## Release labels

| Label | Meaning | Gate |
| --- | --- | --- |
| **Preview** | Works for early adopters, primitive crypto tests pass, CI coverage incomplete | build + primitive unit tests |
| **Beta / RC** | Artemis-native is publishable end to end; compat surface is partial but honest | P0 items below |
| **GA / Replacement-ready** | Artemis-native and drop-in compat both proven by CI | P0 + P1 + P2 items below |

Every public claim (README, PARITY_MATRIX, release notes) maps to one of those
labels. "Drop-in replacement for Solana Mobile Stack" is a GA claim and
requires every box below to be ticked.

---

## Artemis-native ready (Beta / RC)

P0 items. Must be green in CI before tagging any serious release.

- [x] **Publish workflow path.** `advanced/artemis-react-native` resolves in
  the NPM publish job. A dry run of the workflow finds `package.json`.
- [x] **Nightly fails loudly.** `|| true` is gone from the devnet step. A
  failing integration run turns the nightly red; reports still upload for
  diagnosis.
- [x] **Compat API-surface gate.** `release-gate` runs `./gradlew dumpApi`
  and `git diff --exit-code -- 'interop/*/api/*.api'`. Accidental surface
  drift fails the job. Intentional changes have to land the updated `.api`
  snapshots in the same PR.
- [x] **Seed Vault conformance.** CI runs
  `:artemis-seed-vault:testDebugUnitTest` on every relevant PR and on the
  release gate. Failures block merge and publish.
- [x] **MWA walletlib-2.0 behavior gate.** CI runs
  `:artemis-wallet-mwa-android:testDebugUnitTest --tests '*BehaviorTest*'`.
  The suite covers authorize with auth_token / addresses / features / SIWS,
  clone_authorization, getCapabilities, signAndSendTransactions, sign-only
  fallback with and without an injected broadcaster, reauthorize, session
  teardown, expiry, and invalid-payload error propagation.

---

## SMS drop-in ready (GA / Replacement-ready)

P1 items. Required before claiming "drop-in replacement for Solana Mobile Stack".

- [x] **No Base58 association-token fallback.**
  `LocalAssociationIntentCreator` and `LocalAssociationScenario` both use
  base64url-no-padding, matching the MWA spec. Enforced by
  `MwaCompatParityTest.createAssociationUri uses base64url encoding` and
  `.fallback path encodes association key as base64url`.
- [x] **No bridge-dependent unsupported paths.**
  `MobileWalletAdapterClient` core methods throw a typed
  `SessionNotReadyException` with remediation text rather than an opaque
  `UnsupportedOperationException`. `MobileWalletAdapterSession` generates
  a real P-256 keypair on construction and exposes the uncompressed
  public key instead of an empty placeholder. Enforced by
  `MwaCompatParityTest.core methods throw SessionNotReadyException when no bridge`
  and `.MobileWalletAdapterSession exposes real association public key`.
- [x] **Seed Vault contract split finished.**
  `SeedVaultManager` is a facade. Every RPC verb routes through
  `SeedVaultContractClient` and then through
  `SeedVaultAccountProvider` / `SeedVaultSigningProvider`. Raw IPC, typed
  lookup, and signing each live in their own testable seam. See
  [ADR 0001](adr/0001-engine-architecture.md) for the full layering.
- [x] **Pending Seed Vault requests fail on disconnect.**
  `SeedVaultManager` tracks in-flight continuations in a
  `ConcurrentHashMap`. `onServiceDisconnected` and the binder
  `DeathRecipient` both call `failAllPending`, which raises a typed
  `SeedVaultException.ServiceUnavailable` on every pending call. No
  in-flight request hangs past binder death. Covered by
  `SeedVaultProviderDisconnectTest`.
- [x] **Session-style crypto moved out of Seed Vault.** X25519 ECDH plus
  HKDF-SHA256 now live in
  `com.selenus.artemis.wallet.mwa.protocol.MwaSessionCrypto`. The Seed
  Vault module carries only custody-relevant crypto (signature-derived
  keys, AES-256-GCM envelope, HMAC). Enforced by `MwaSessionCryptoTest`.

P2 items. Required for GA.

- [x] **Native MWA E2E behavior suite passes.** Covered by the behavior
  gate above. 15 tests, all green.
- [x] **Compat runtime parity suite passes.** `MwaCompatParityTest`
  exercises every behavior the audit flagged as a compat gap. 10 tests,
  all green.
- [x] **Release-readiness gate enforced.** This document plus the
  `release-gate` job in the publish workflow.

---

## What "publishable" means for a given release

The release manager answers in order:

1. Is every P0 item checked? If not, the only label the release may use is
   **Preview**.
2. Is every P1 item checked? If not, the release is **Beta / RC**. Publish
   the Artemis-native surface, mark compat modules as partial in the
   changelog.
3. Is every P2 item checked? If not, do not claim "drop-in". The release is
   still publishable as **Beta / RC**.
4. All boxes ticked? **GA / Replacement-ready**. The changelog may claim
   drop-in compatibility for the scope listed in
   [PARITY_MATRIX.md](PARITY_MATRIX.md).

Every claim in the release notes has to point at an evidence source: a test
name, an ADR, a parity-matrix row, or a reference to the release-gate run
that backed the claim.

---

## Running the gate locally

The same suite CI runs is callable from a workstation:

```bash
./scripts/verify.sh
```

The script mirrors the `release-gate` job line for line. A clean local run
is the prerequisite for tagging a release candidate.
