# artemis-replay

Deterministic replay helpers for Solana games and interactive apps.

## What it is

- A small recorder for frame metadata
- Optional signature and blockhash attachment
- A loader for replay sessions

Execution is app-specific because different games have different state models.

## Usage

```kotlin
val rec = ReplayRecorder()
rec.recordFrame(frame.createdAtMs, frame.instructions, meta = mapOf("match" to matchId))

// after send
rec.attachSignature(index = 0, signature = sig, recentBlockhash = bh)
rec.writeTo(File("replay.json"))

val session = ReplayPlayer().load(File("replay.json"))
```
