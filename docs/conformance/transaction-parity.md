# Transaction parity

Solana transaction compatibility is byte-sensitive. This page records the parity gates required before stronger transaction-engine claims.

## Current coverage

| Area | Status | Evidence |
|---|---|---|
| Legacy transaction serialization | Verified for committed fixtures | `artemis-tx` tests and web3.js byte fixtures |
| Versioned transaction v0 | Verified for committed fixtures | `artemis-vtx` tests and ALT parse/serialize coverage |
| Account ordering | Verified for committed fixtures | legacy and v0 fixture tests |
| Address lookup table instructions | Verified | `artemis-programs` builders and v0 tests |
| System/SPL/Token-2022/ATA/Memo/Compute Budget builders | Verified | program and compat tests |
| Retry/simulation pipeline | Verified for local behavior | `TxEngine`, blockhash cache, retry tests |

## Required byte-parity cases

- Legacy message with equal account groups sorted lexicographically by pubkey bytes.
- v0 message preserving first-seen order inside equal account groups.
- Address lookup table writable and readonly index ordering.
- Multi-signer signature slot ordering.
- Partial signing with wallet fee payer and external fee payer.
- Durable nonce advance/rollback edge cases.
- Compute budget instruction injection before user instructions.
- Duplicate signature and already-processed retry handling.

## Devnet/local-validator gates

- `getLatestBlockhash` -> build -> simulate -> send -> confirm.
- SOL transfer.
- SPL token transfer.
- Token-2022 transfer with extension-aware account handling.
- ALT transaction with lookup table account.
- WebSocket signature subscription confirmation.
- Blockhash expiration retry with fresh blockhash.

## Rule for new fixtures

Every transaction fixture should store both the semantic inputs and the expected serialized bytes. Do not change account ordering rules without adding a byte fixture that explains whether it follows legacy web3.js ordering or v0 first-seen ordering.