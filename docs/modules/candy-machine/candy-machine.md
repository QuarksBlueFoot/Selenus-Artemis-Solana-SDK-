# Candy Machine helpers

Artemis includes an optional Candy Machine module that targets the modern Metaplex
Candy Machine v3 stack:

- Candy Machine Core program
- Candy Guard program

## What we support today

- Program id constants
- PDA helpers (Candy Machine authority PDA)
- Candy Guard `mint_v2` instruction builder for the common mobile flow

The `mint_v2` builder currently focuses on the typical SOL payment mint path.
If your guard requires additional accounts (for example, allow-list proofs), provide them via
`remainingAccounts`.

## Intelligence layer

If you want guard introspection, deterministic account planning, and safe error messages, see:

- `docs/candy-machine-intelligence.md`

## Mint preset

If you want a one-call mobile mint that includes optional ATA creation, priority fees, and resend/confirm,
see:

- `docs/candy-machine-mint-presets.md`

## Quick example

```kotlin
val ix = CandyGuardMintV2.build(
  args = CandyGuardMintV2.Args(group = null),
  accounts = CandyGuardMintV2.Accounts(
    candyGuard = candyGuard,
    candyMachine = candyMachine,
    payer = payer,
    minter = minter,
    nftMint = newNftMint,
    nftMetadata = nftMetadata,
    nftMasterEdition = nftMasterEdition,
    collectionDelegateRecord = collectionDelegateRecord,
    collectionMint = collectionMint,
    collectionMetadata = collectionMetadata,
    collectionMasterEdition = collectionMasterEdition,
    collectionUpdateAuthority = collectionUpdateAuthority,
  )
)
```

## Notes

- The account order used by Artemis matches the Anchor generated `MintV2` accounts struct.
- If you are minting pNFTs, provide `token` and `tokenRecord`.
