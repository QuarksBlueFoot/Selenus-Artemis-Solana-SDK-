# SolanaKT compatibility map

Artemis is designed to be a drop-in upgrade path for projects that previously used older Kotlin Solana SDKs.

This page documents common method parity.

## RPC

| SolanaKT style | Artemis |
|---|---|
| getLatestBlockhash | RpcApi.getLatestBlockhash |
| getBalance | RpcApi.getBalance |
| getAccountInfo | RpcApi.getAccountInfo / getAccountInfoBase64 |
| getMultipleAccounts | RpcApi.getMultipleAccounts |
| getProgramAccounts | RpcApi.getProgramAccounts |
| getTokenAccountsByOwner | RpcApi.getTokenAccountsByOwner |
| simulateTransaction | RpcApi.simulateTransaction |
| sendTransaction | RpcApi.sendTransaction / sendRawTransaction |
| getSignatureStatuses | RpcApi.getSignatureStatuses |
| getTransaction | RpcApi.getTransaction |
| getRecentPrioritizationFees | RpcApi.getRecentPrioritizationFees |
| getFeeForMessage | RpcApi.getFeeForMessage |

## Class Mapping

| Concept | SolanaKT / Web3.js | Artemis |
|---|---|---|
| Public Key | `PublicKey` | `Pubkey` |
| Private Key / Signer | `Account` / `Keypair` | `Keypair` / `Signer` |
| Instruction | `TransactionInstruction` | `Instruction` |
| Legacy Transaction | `Transaction` | `Transaction` |
| Versioned Transaction | `VersionedTransaction` | `VersionedTransaction` |
| Account Meta | `AccountMeta` | `AccountMeta` |

## Networking (OkHttp and Ktor)

- OkHttp default: JsonRpcClient(endpoint)
- Ktor parity: JsonRpcClient(endpoint, transport = HttpTransports.ktorBridge {  })

## Notes

- Artemis focuses on mobile and game reliability (compute tuning and wallet re-sign flow).
- Core SDK modules remain free and do not require hosted services.

## Program accounts filters
Use RpcFilters.memcmp and RpcFilters.dataSize to build filters for getProgramAccounts.
