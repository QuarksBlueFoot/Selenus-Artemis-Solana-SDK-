package com.selenus.artemis.runtime

object Pda {
  private val PDA_MARKER = "ProgramDerivedAddress".encodeToByteArray()

  data class Result(val address: Pubkey, val bump: Int)

  fun findProgramAddress(seeds: List<ByteArray>, programId: Pubkey): Result {
    for (bump in 255 downTo 0) {
      val addr = createProgramAddress(seeds + byteArrayOf(bump.toByte()), programId) ?: continue
      return Result(addr, bump)
    }
    throw IllegalStateException("No valid PDA found")
  }

  fun createProgramAddress(seeds: List<ByteArray>, programId: Pubkey): Pubkey? {
    require(seeds.all { it.size <= 32 }) { "Each seed must be <= 32 bytes" }
    val data = seeds.fold(ByteArray(0)) { acc, s -> acc + s } + programId.bytes + PDA_MARKER
    val hash = Crypto.sha256(data)
    // On Solana, the resulting 32 bytes must NOT be on ed25519 curve.
    // We use a conservative check: attempt to treat as public key and reject if it looks valid on curve.
    // Full curve check is heavy; for PDA derivation this heuristic is acceptable for SDK purposes.
    // To be safe, we reject a known on-curve detection using BouncyCastle's decoder.
    return if (Ed25519.onCurve(hash)) null else Pubkey(hash)
  }
}

internal object Ed25519 {
  fun onCurve(pk32: ByteArray): Boolean {
    // Minimal on-curve test using BouncyCastle
    return try {
      org.bouncycastle.math.ec.rfc8032.Ed25519.validatePublicKeyFull(pk32, 0)
      true
    } catch (_: Throwable) {
      false
    }
  }
}
