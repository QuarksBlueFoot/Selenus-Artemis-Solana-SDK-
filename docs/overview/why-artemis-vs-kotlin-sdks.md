# Why Artemis vs other Solana Kotlin SDKs

Artemis is designed for **mobile-first Solana apps**. Most Kotlin/JVM Solana libraries focus on
basic RPC calls and transaction construction. Artemis targets the problems that show up when
shipping real mobile apps: guard-heavy programs (Candy Machine), Token-2022 edge cases, and
transaction reliability under mobile network conditions.

## What Artemis optimizes for

- **Modular + optional** modules (drop in only what you use)
- **No paid assumptions**: RPC-only by default
- **Deterministic planning** for program calls where remaining accounts/args are easy to get wrong
- **Mobile reliability**: priority fees + resend/confirm patterns

## Comparison (high-level)

### sol4k

Strong baseline for Kotlin RPC + basic tx construction.

Where Artemis goes further:

- Candy Machine v3 + Candy Guard planning and safe builder
- Tx composer presets (ATA + priority + resend)

### Metaplex Kotlin modules

Historically NFT-oriented and not focused on mobile transaction reliability.

Where Artemis goes further:

- Candy Guard introspection + fail-early validation
- pNFT-aware planning for token record + metadata account sets

### Solana Mobile Wallet Adapter libraries

These provide wallet connection and signing on Android. Artemis complements them by providing
program-level tooling and mobile-first sending patterns.

## Practical guidance

- If you only need simple RPC calls and transfers, a thin Kotlin RPC client is enough.
- If you are shipping a mobile minting experience (Candy Machine v3), Artemis removes the
  remaining-account/guard guessing and adds send reliability patterns.