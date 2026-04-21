# Release readiness

Artemis ships against two distinct targets. "Ready" for one is not "ready" for
the other, and each one owns its own checklist. Every item below is enforced
by CI where possible — the `release-gate` job in [.github/workflows/publish.yml](../.github/workflows/publish.yml)
blocks the Maven + NPM publish until the suite goes green.

## Release labels

| Label | Meaning | Gate |
| --- | --- | --- |
| **Preview** | Works for early adopters, primitive crypto tests pass, CI coverage incomplete | build + primitive unit tests |
| **Beta / RC** | Artemis-native is publishable end-to-end; compat is partial but honest | P0 items below |
| **GA / Replacement-ready** | Artemis-native and drop-in compat both proven by CI | P0 + P1 + P2 items below |

Every public claim (README, PARITY_MATRIX, release notes) must map to a label
above. "Drop-in replacement for Solana Mobile Stack" is a **GA** claim and
requires every box below to be ticked.

---

## Artemis-native ready (Beta / RC)

P0 items — must be green in CI before tagging any serious release:

- [x] **Publish workflow path** — `advanced/artemis-react-native` resolves in
  the NPM publish job. A dry-run invocation finds `package.json`.
- [x] **Nightly fails loudly** — `||  true` is gone from the devnet step. A
  failing integration run makes the nightly red; reports still upload for
  diagnosis.
- [x] **Compat API-surface gate** — `release-gate` runs `./gradlew dumpApi`
  and `git diff --exit-code -- 'interop/*/api/*.api'`. Accidental surface
  drift fails the job; intentional changes must land the updated `.api`
  snapshots in the same PR.
- [x] **Seed Vault conformance** — CI runs
  `:artemis-seed-vault:testDebugUnitTest` on every relevant PR and the
  release gate. Failures block merge and publish.
- [x] **MWA walletlib-2.0 behavior gate** — CI runs
  `:artemis-wallet-mwa-android:testDebugUnitTest --tests '*BehaviorTest*'`.
  The suite covers authorize with auth_token / addresses / features / SIWS,
  clone_authorization, getCapabilities, signAndSendTransactions,
  sign-only fallback (with and without an injected broadcaster),
  reauthorize, and session teardown.

---

## SMS drop-in ready (GA / Replacement-ready)

P1 items — required before claiming "drop-in replacement for Solana Mobile Stack":

- [x] **No Base58 association-token fallback** —
  `LocalAssociationIntentCreator` and `LocalAssociationScenario` both use
  base64url-no-padding, matching the spec. Enforced by
  `MwaCompatParityTest.createAssociationUri uses base64url encoding` and
  `.fallback path encodes association key as base64url`.
- [x] **No bridge-dependent unsupported paths** —
  `MobileWalletAdapterClient` core methods throw a typed
  `SessionNotReadyException` with remediation text rather than opaque
  `UnsupportedOperationException`. `MobileWalletAdapterSession` generates
  a real P-256 keypair on construction and exposes the uncompressed
  public key; no more "empty byte array" placeholder. Enforced by
  `MwaCompatParityTest.core methods throw SessionNotReadyException when no bridge`
  and `.MobileWalletAdapterSession exposes real association public key`.
- [x] **Seed Vault contract split finished** —
  `SeedVaultManager` is a facade; all RPC verbs route through
  `SeedVaultContractClient` and through
  `SeedVaultAccountProvider` / `SeedVaultSigningProvider`. Raw IPC,
  typed lookup, and signing live in three separately testable seams.
  See [ADR 0001](adr/0001-engine-architecture.md) for the full layering.
- [x] **Pending Seed Vault requests fail on disconnect** —
  `SeedVaultManager` tracks in-flight continuations in a
  `ConcurrentHashMap`. `onServiceDisconnected` and the binder
  `DeathRecipient` both call `failAllPending` with a typed
  `SeedVaultException.ServiceUnavailable`, so no in-flight call hangs
  past binder death.
- [x] **Session-style crypto moved out of Seed Vault** — X25519 ECDH +
  HKDF-SHA256 primitives now live in
  `com.selenus.artemis.wallet.mwa.protocol.MwaSessionCrypto`. The Seed
  Vault module only contains custody/provider-relevant crypto
  (signature-derived keys, AES-256-GCM envelope, HMAC). Enforced by
  `MwaSessionCryptoTest`.

P2 items — must be green for GA:

- [x] **Native MWA E2E behavior suite passes** — covered by the MWA
  behavior gate above.
- [x] **Compat runtime parity suite passes** — `MwaCompatParityTest`
  exercises every behavior the audit called out as a compat gap.
- [x] **Release-readiness gate enforced** — this document plus the
  `release-gate` job in the publish workflow.

---

## What "publishable" means for a given release

The release manager answers the questions in order:

1. Is every P0 item above checked? If not, the only label the release may use
   is **Preview**.
2. Is every P1 item checked? If not, the release is **Beta / RC** — publish
   Artemis-native surface, mark compat modules as partial in the changelog.
3. Is every P2 item checked? If not, do not claim "drop-in". The release is
   still publishable as **Beta / RC**.
4. All boxes ticked? **GA / Replacement-ready** — the changelog may claim
   drop-in compatibility for the scope listed in
   [PARITY_MATRIX.md](PARITY_MATRIX.md).

Every claim in the release notes must point at an evidence source: a test
name, an ADR, a parity-matrix row, or a reference to the release-gate run
that backed the claim.

---

## Running the gate locally

The same suite CI runs is callable from a workstation via:

```bash
./scripts/verify.sh
```

The script mirrors the `release-gate` job line-for-line. A clean local run
is the prerequisite for tagging a release candidate.
