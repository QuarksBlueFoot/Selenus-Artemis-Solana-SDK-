# artemis-cnft

Bubblegum and compressed NFT utilities for Kotlin apps.

## What you get

- Program IDs (Bubblegum, Account Compression, Log Wrapper)
- PDA helpers (treeConfig, assetId)
- Proof verification helpers
- Instruction builders for mint, transfer, burn, delegate, verify creator, verify collection
- DAS helpers (getAsset, getAssetProof) and parsers
- MarketplaceToolkit for common flows

## Usage

### Transfer a cNFT

1) Fetch proof via DAS

```kotlin
val das = DasClient(rpc)
val asset = das.getAsset(assetId)
val proof = das.getAssetProof(assetId)
```

2) Build proof args and accounts

```kotlin
val proofArgs = DasProofParser.parseProofArgs(asset, proof)
val proofAccounts = DasProofParser.proofAccountsFromProof(proof)
```

3) Build instruction

```kotlin
val ix = BubblegumInstructions.transfer(
  treeConfig = BubblegumPdas.treeConfig(merkleTree),
  merkleTree = merkleTree,
  leafOwner = owner,
  leafDelegate = delegate,
  newLeafOwner = newOwner,
  args = BubblegumArgs.TransferArgs(proofArgs),
  proofAccounts = proofAccounts
)
```

