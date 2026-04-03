# Dependency Rules

These rules are not guidelines. They are constraints. Violating them breaks the architecture.
They are enforced at build time by `./gradlew checkDependencyRings`.

## The rule

Higher rings may depend on lower rings. Lower rings never depend on higher rings.

```
Ring 1 (Foundation) --> nothing above
Ring 2 (Mobile)     --> Ring 1 only
Ring 3 (Ecosystem)  --> Ring 1, selected Ring 2 abstractions
Ring 4 (Advanced)   --> Ring 1, 2, 3
Ring 5 (Compat)     --> Ring 1, 3 (NOT Ring 4)
Ring 6 (Interop)    --> Ring 1, 2, 3 (NOT Ring 4 or 5)
Testing/Samples     --> anything
```

## Rule A: Foundation is self-contained

Foundation modules depend only on other Foundation modules.

Allowed:
- `artemis-rpc` depends on `artemis-core`
- `artemis-tx` depends on `artemis-core`
- `artemis-vtx` depends on `artemis-tx`, `artemis-core`

Forbidden:
- `artemis-core` depends on `artemis-metaplex`
- `artemis-rpc` depends on `artemis-privacy`
- `artemis-tx` depends on `artemis-jupiter`

If you find a Foundation module importing from a higher ring, that is a bug. Fix it.

## Rule B: Mobile stays lean

Mobile modules depend on Foundation only.

Allowed:
- `artemis-wallet` depends on `artemis-core`, `artemis-tx`
- `artemis-wallet-mwa-android` depends on `artemis-wallet`, `artemis-core`

Forbidden:
- `artemis-wallet` depends on `artemis-jupiter`
- `artemis-seed-vault` depends on `artemis-privacy`

The point of this ring is that a mobile team can adopt Artemis with just `foundation/` + `mobile/` and nothing else.

## Rule C: Ecosystem depends on Foundation

Ecosystem modules depend on Foundation. They may reference Mobile signing interfaces when needed (e.g., wallet signing in Jupiter swap flows). They should not depend on Advanced modules.

Allowed:
- `artemis-jupiter` depends on `artemis-core`, `artemis-rpc`, `artemis-tx`
- `artemis-anchor` depends on `artemis-core`, `artemis-programs`

Forbidden:
- `artemis-metaplex` depends on `artemis-nlp`
- `artemis-token2022` depends on `artemis-simulation`

## Rule D: Advanced can reach down, not up

Advanced modules may depend on Foundation, Mobile, and Ecosystem. They must not create dependencies that flow back into the core adoption path.

Allowed:
- `artemis-privacy` depends on `artemis-core`, `artemis-tx`
- `artemis-simulation` depends on `artemis-rpc`, `artemis-tx`

Forbidden:
- `artemis-core` depends on `artemis-simulation`
- `artemis-wallet` depends on `artemis-gaming`

## Rule E: Compat bridges legacy patterns

Compat modules provide backward-compatible APIs and presets. They depend on Foundation and Ecosystem. They must not depend on Advanced.

Modules:
- `artemis-discriminators`, `artemis-nft-compat`, `artemis-tx-presets`,
  `artemis-candy-machine-presets`, `artemis-presets`

Allowed:
- `artemis-nft-compat` depends on `artemis-core`, `artemis-metaplex`
- `artemis-candy-machine-presets` depends on `artemis-candy-machine`

Forbidden:
- `artemis-nft-compat` depends on `artemis-simulation`
- `artemis-discriminators` depends on `artemis-gaming`

## Rule F: Interop provides drop-in shims

Interop modules let existing code that imports SolanaMobile SDK packages (`com.solanamobile.*`, `com.solana.mobilewalletadapter.*`) compile against Artemis without changing imports. They depend on Foundation, Mobile, and Ecosystem. They must not depend on Advanced or Compat.

Modules:
- `artemis-seedvault-compat` — shims `com.solanamobile.seedvault.Wallet` and `WalletContractV1`
- `artemis-mwa-compat` — shims `com.solana.mobilewalletadapter.clientlib.MobileWalletAdapterCompat`

Allowed:
- `artemis-seedvault-compat` depends on `artemis-seed-vault`, `artemis-core`
- `artemis-mwa-compat` depends on `artemis-wallet-mwa-android`, `artemis-wallet`, `artemis-core`

Forbidden:
- `artemis-seedvault-compat` depends on `artemis-privacy`
- `artemis-mwa-compat` depends on `artemis-nft-compat`

## Rule G: Testing and Samples depend on everything

Both `testing/` and `samples/` can import from any ring. No other module should depend on testing or samples. If a sample becomes architectural glue, refactor it out.

## How to verify

Run the automated ring check:

```bash
./gradlew checkDependencyRings
```

This task inspects every module's `implementation` and `api` dependencies and flags any violation. It runs during CI and must pass before merging.

You can also grep for imports manually as a sanity check:

```bash
# Foundation modules should not import from ecosystem/advanced/mobile
grep -r "import.*artemis\.\(jupiter\|metaplex\|privacy\|gaming\)" foundation/
# Should return nothing

# Mobile modules should not import from ecosystem/advanced
grep -r "import.*artemis\.\(jupiter\|metaplex\|privacy\|gaming\|nlp\)" mobile/
# Should return nothing
```

If either returns results, you have a dependency violation. Fix it before merging.

## Why this matters

Without these boundaries:
- Teams cannot adopt the core SDK without pulling in experimental features
- Breaking changes in advanced modules cascade into foundation
- Build times grow because everything depends on everything
- The SDK starts feeling like a monolith instead of a modular toolkit

The rings keep Artemis adoptable. Respect them.
