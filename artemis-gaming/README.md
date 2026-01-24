# artemis-gaming

**Production-ready Solana gaming utilities for Kotlin and Android**

> ⭐ **v1.5.0**: Now includes Verifiable Randomness, Game State Proofs, and Reward Distribution

Artemis Gaming provides everything modern mobile games need for Solana integration:
- 🎲 **Verifiable Randomness**: Provably fair RNG with VRF and commit-reveal
- 🏆 **Reward Distribution**: 4 payout strategies with Merkle claims
- 🎮 **Game State Proofs**: Cryptographic state verification and fraud proofs
- ⚡ **ArcanaFlow**: Low-latency frame batching for real-time games
- 💰 **Priority Fee Oracle**: Adaptive compute pricing
- 🔑 **Session Keys**: Ephemeral keys for high-frequency actions

Full ArcanaFlow guide: see `docs/arcanaflow.md`

## New in v1.5.0

### 🎲 Verifiable Randomness

Provably fair randomness for on-chain gaming that cannot be manipulated by players or servers.

```kotlin
import com.selenus.artemis.gaming.VerifiableRandomness

// Commit-reveal scheme (player contributes entropy)
val (commitment, secret) = VerifiableRandomness.commit()
// Submit commitment on-chain...
// Later, reveal to prove fairness
val reveal = VerifiableRandomness.Reveal(
    value = secret.copyOfRange(0, 32),
    salt = secret.copyOfRange(32, 48),
    commitment = commitment
)
val isValid = reveal.verify()  // true

// VRF (Verifiable Random Function)
val vrfOutput = VerifiableRandomness.vrfGenerate(
    privateKey = gameServerKey,
    message = "game-round-42".toByteArray()
)
// Anyone can verify this output came from your server
val verified = VerifiableRandomness.vrfVerify(
    publicKey = gameServerPubkey.bytes,
    message = "game-round-42".toByteArray(),
    vrfOutput = vrfOutput
)

// Multi-party random beacon (combine entropy from multiple sources)
val beacon = VerifiableRandomness.RandomBeacon(
    epoch = roundNumber,
    contributions = listOf(
        Base58.encode(player1Entropy),
        Base58.encode(player2Entropy),
        Base58.encode(serverEntropy)
    )
)
val finalSeed = beacon.finalize()  // Deterministic, fair seed
```

**Use cases**: Card shuffling, loot boxes, matchmaking, battle outcomes

### 🏆 Reward Distribution

Flexible payout strategies with gas-efficient Merkle claims.

```kotlin
import com.selenus.artemis.gaming.RewardDistribution

val totalPool = 10_000_000L  // 0.01 SOL

// 1. Winner Takes All
val wta = RewardDistribution.PayoutStrategy.WinnerTakesAll()
val payouts1 = wta.calculate(totalPool, 1)

// 2. Linear Decay (common for tournaments)
val linear = RewardDistribution.PayoutStrategy.LinearDecay(topN = 3)
val payouts2 = linear.calculate(totalPool, 3)
// #1: 50%, #2: 33%, #3: 17%

// 3. Exponential Payout (heavier top-weighting)
val exp = RewardDistribution.PayoutStrategy.ExponentialPayout(
    topN = 3,
    decayFactor = 0.5
)
val payouts3 = exp.calculate(totalPool, 3)

// 4. Poker-style (50%/30%/20% for 10 players)
val poker = RewardDistribution.PayoutStrategy.PokerStyle(players = 10)
val payouts4 = poker.calculate(totalPool, 3)

// Build Merkle claim tree for gas-efficient distribution
val winners = listOf(player1Pubkey, player2Pubkey, player3Pubkey)
val claims = winners.zip(payouts2).map { (pubkey, amount) ->
    RewardDistribution.RewardClaim(
        recipient = pubkey,
        amount = amount,
        metadata = mapOf("rank" to "top3")
    )
}

val rewardTree = RewardDistribution.RewardTree.build(claims)
val proof = rewardTree.generateProof(player1Pubkey)
val isValid = rewardTree.verifyClaim(player1Pubkey, payouts2[0], proof)
```

**Use cases**: Tournaments, battle royales, leaderboards, esports payouts

### 🎮 Game State Proofs

Cryptographic verification of game state transitions for off-chain gaming.

```kotlin
import com.selenus.artemis.gaming.GameStateProofs

// Create initial game state
val initialState = GameStateProofs.GameState(
    stateHash = ByteArray(32),
    nonce = 0,
    timestamp = System.currentTimeMillis(),
    players = listOf(player1, player2),
    data = mapOf(
        "player1_hp" to byteArrayOf(100),
        "player2_hp" to byteArrayOf(100),
        "turn" to byteArrayOf(1)
    )
)

// Update state (off-chain)
val newState = initialState.update("player1_hp", byteArrayOf(90))

// Create proof of state transition
val action = GameStateProofs.GameAction(
    actionType = GameStateProofs.ActionType.ATTACK,
    actor = player2,
    target = player1,
    value = 10,
    payload = ByteArray(0)
)

val transition = GameStateProofs.StateTransition(
    fromState = initialState.stateHash,
    toState = newState.stateHash,
    action = action,
    proof = ByteArray(64),
    timestamp = System.currentTimeMillis()
)

// Verify transition is valid
val isValid = GameStateProofs.verifyTransition(initialState, newState, transition)

// Build Merkle tree of state for efficient verification
val stateTree = GameStateProofs.buildStateTree(newState)
val merkleProof = GameStateProofs.generateMerkleProof(stateTree, "player1_hp")
```

**Use cases**: Turn-based strategy, card games, state channels, dispute resolution

## Original Features

### Compute presets

```kotlin
val ixs = ComputeBudgetPresets.preset(ComputeBudgetPresets.Tier.COMPETITIVE)
```

### Session keys

```kotlin
val session = SessionKeys.new()
val sessionPubkey = session.pubkey
```

### ArcanaFlow

A batching and frame emission helper. You can sign and send each frame as a v0 tx.

```kotlin
val lane = ArcanaFlow(scope)
lane.start()

lane.enqueue(gameActionIx)

scope.launch {
  lane.frames.collect { frame ->
    // build v0 tx with ALT and send
  }
}
```


### ALT session builder

Build stable ALT proposals from ArcanaFlow frames.

```kotlin
val cache = AltSessionCache()
val builder = AltSessionBuilder(maxAddresses = 256)

scope.launch {
  lane.frames.collect { frame ->
    val proposal = ArcanaFlowTxHelper.collectForAltPlanning(frame, cache, builder)
    // proposal.addresses is a deterministic list you can feed into your ALT creation flow
  }
}
```


### ALT execution bundles

Build create and extend instruction bundles for lookup tables.

```kotlin
val proposal = builder.propose()
val plan = AltSessionExecutor.createAndExtend(
  authority = authorityPubkey,
  payer = payerPubkey,
  recentSlot = recentSlot,
  addresses = proposal.addresses,
  extendChunk = 20
)
// plan.instructions can be sent as one or more transactions
```


### Frame transaction planning

Compose compute budget, priority fees, and lookup table hints for each ArcanaFlow frame.

```kotlin
val composer = ArcanaFlowFrameComposer(
  programId = gameProgramId,
  tier = ComputeBudgetPresets.Tier.COMPETITIVE,
  oracle = oracle
)

val txPlan = composer.compose(
  frame = frame,
  knownLookupTables = listOf(gameLutAddress)
)

// Use AltToolkit (or your pipeline) to compile and sign v0 with txPlan.lookupTableAddresses
```

## Architecture

All gaming features follow modern 2026 Android patterns:
- ✅ Kotlin Coroutines for async operations
- ✅ Flow for reactive streams
- ✅ Mobile-optimized (no heavy cryptographic operations)
- ✅ Production-tested for real-time games
- ✅ Compatible with Jetpack Compose

## License

Apache-2.0 - See root LICENSE file
## Lookup table tx scheduling

Split create and extend plans into multiple transactions.

```kotlin
val plan = AltSessionExecutor.createAndExtend(
  authority = authorityPubkey,
  payer = payerPubkey,
  recentSlot = recentSlot,
  addresses = proposal.addresses
)

val batches = AltTxScheduler.scheduleCreatePlan(plan, maxExtendIxsPerTx = 2)

for (b in batches) {
  // sign + send tx using b.instructions
}
```


## Compile v0 transactions for frames

Turn a FrameTxPlan into a signed v0 VersionedTransaction using auto fetched lookup tables.

```kotlin
val tx = ArcanaFlowV0Compiler.compileFrame(
  rpc = rpc,
  feePayer = feePayer,
  additionalSigners = listOf(sessionSigner),
  recentBlockhash = blockhash,
  plan = txPlan
)
```
