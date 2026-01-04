# Public API surfaces

This document lists the intended **public, stable entrypoints** for Artemis. It is a guide for
integrators (including Solana Mobile / Solana Kit) and for maintainers.

Anything not listed here may change more frequently.

## Wallet + sending

- `com.selenus.artemis.wallet.WalletAdapter`
- `com.selenus.artemis.wallet.SendPipeline`

## RPC

- `com.selenus.artemis.rpc.RpcApi`

## Transactions

- `com.selenus.artemis.tx.Transaction` (Legacy)
- `com.selenus.artemis.vtx.VersionedTransaction` (Versioned)
- `com.selenus.artemis.tx.TransactionBuilder` (and related instruction/types)
- `com.selenus.artemis.compute.ComputeBudget` helpers

## Metaplex

- `com.selenus.artemis.metaplex.TokenMetadataInstructions`
- `com.selenus.artemis.mplcore.MplCoreInstructions`
- `com.selenus.artemis.cnft.BubblegumInstructions`

## Candy Machine v3

- `com.selenus.artemis.candymachine.state.CandyMachineStateReader`
- `com.selenus.artemis.candymachine.guards.CandyGuardManifestReader`
- `com.selenus.artemis.candymachine.CandyGuardMintV2Safe`

## Presets

- `com.selenus.artemis.txpresets.TxComposerPresets`
- `com.selenus.artemis.candymachine.presets.CandyMachineMintPresets`
- `com.selenus.artemis.presets.PresetRegistry`