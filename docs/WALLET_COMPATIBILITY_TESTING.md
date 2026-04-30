# Wallet compatibility testing

MWA gives Artemis protocol compatibility in theory. Real wallet testing proves compatibility in production conditions.

This document defines the minimum wallet evidence needed before claiming broad Solana Mobile wallet readiness for a release.

## Test layers

| Layer | Purpose | Required coverage |
| --- | --- | --- |
| Official MWA reference wallet / fake wallet | Protocol baseline | Authorize, reauthorize, sign messages, sign transactions, sign-and-send fallback, disconnect |
| Real Android wallets | Wallet-specific behavior | Phantom, Backpack, Solflare, plus a Solana Mobile device wallet when available |
| React Native harness | App-framework behavior | Connect, sign, send, reject, reconnect, foreground/background transitions |
| Seed Vault device checks | Custody integration | Supported device access checks, auth-token parsing, signing request result handling |

## Required flows

Run these on devnet unless a flow requires mainnet data.

### Connection

- Connect wallet.
- Show selected public key.
- Disconnect.
- Reconnect after app restart.
- Reconnect after wallet app force-close.

### Signing

- Sign message.
- Sign transaction.
- Sign multiple transactions.
- Sign and send transaction when wallet supports it.
- Fall back to sign-only plus RPC broadcast when wallet does not support sign-and-send.

### Transactions

- Send SOL transfer.
- Send SPL token transfer.
- Simulate transaction before signing when the flow supports it.
- Confirm submitted signature.

### Edge cases

- User rejects authorization.
- User rejects signing.
- Wallet closes mid-session.
- Session expires before the next operation.
- Multiple rapid requests are submitted.
- App backgrounds while wallet UI is open.
- Wallet returns a transport error or malformed response.

## Result matrix template

| Wallet | Version | Device / OS | Connect | Sign message | Sign tx | Send tx | Edge cases | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Phantom | TBD | TBD | TBD | TBD | TBD | TBD | TBD | Record deeplink or RESULT_CANCELED behavior if observed |
| Backpack | TBD | TBD | TBD | TBD | TBD | TBD | TBD | Record wallet-specific timing issues |
| Solflare | TBD | TBD | TBD | TBD | TBD | TBD | TBD | Record wallet-specific errors |
| MWA reference wallet | TBD | TBD | TBD | TBD | TBD | TBD | TBD | Protocol baseline |
| Solana Mobile device wallet | TBD | TBD | TBD | TBD | TBD | TBD | TBD | Seed Vault-backed path where available |

Use `PASS`, `FAIL`, `WARN`, or `N/A`. A `WARN` must include a reproduction note and an owner for follow-up.

## In-repo automated coverage

Automated tests do not replace real wallet testing, but they prevent regressions in the protocol and compatibility layers.

- Native MWA behavior: [../mobile/artemis-wallet-mwa-android/src/test/kotlin/com/selenus/artemis/wallet/mwa/MwaWalletAdapterBehaviorTest.kt](../mobile/artemis-wallet-mwa-android/src/test/kotlin/com/selenus/artemis/wallet/mwa/MwaWalletAdapterBehaviorTest.kt)
- Wallet conformance cases: [../mobile/artemis-wallet-mwa-android/src/test/kotlin/com/selenus/artemis/wallet/mwa/MwaWalletConformanceTest.kt](../mobile/artemis-wallet-mwa-android/src/test/kotlin/com/selenus/artemis/wallet/mwa/MwaWalletConformanceTest.kt)
- Multi-account session cases: [../mobile/artemis-wallet-mwa-android/src/test/kotlin/com/selenus/artemis/wallet/mwa/MwaMultiAccountSessionTest.kt](../mobile/artemis-wallet-mwa-android/src/test/kotlin/com/selenus/artemis/wallet/mwa/MwaMultiAccountSessionTest.kt)
- Compat client behavior: [../interop/artemis-mwa-clientlib-compat/src/test/kotlin/com/solana/mobilewalletadapter/clientlib/MwaCompatParityTest.kt](../interop/artemis-mwa-clientlib-compat/src/test/kotlin/com/solana/mobilewalletadapter/clientlib/MwaCompatParityTest.kt)
- Compat result shapes: [../interop/artemis-mwa-compat/src/test/kotlin/com/solana/mobilewalletadapter/clientlib/MwaCompatResultsTest.kt](../interop/artemis-mwa-compat/src/test/kotlin/com/solana/mobilewalletadapter/clientlib/MwaCompatResultsTest.kt)
- Wallet-side compat behavior: [../interop/artemis-mwa-walletlib-compat/src/test/kotlin/com/solana/mobilewalletadapter/walletlib/MwaWalletlibCompatParityTest.kt](../interop/artemis-mwa-walletlib-compat/src/test/kotlin/com/solana/mobilewalletadapter/walletlib/MwaWalletlibCompatParityTest.kt)

## Pass criteria

A release can claim wallet compatibility for a wallet only when:

1. The required flows pass on a physical Android device.
2. The wallet version and device OS are recorded.
3. Every `WARN` has a documented limitation and workaround.
4. Every `FAIL` blocks the claim for that wallet.
5. Automated MWA behavior and compat tests are green for the release commit.

## Known risk areas to watch

- Wallets may close or cancel the Android activity result before the signing UI appears.
- Some wallets differ in timing around deep links and foreground/background transitions.
- MWA sessions can close after a callback; retries must open a new compliant session.
- Batch signing and sign-and-send support are optional wallet capabilities.
- React Native app lifecycle can race with wallet return intents.
