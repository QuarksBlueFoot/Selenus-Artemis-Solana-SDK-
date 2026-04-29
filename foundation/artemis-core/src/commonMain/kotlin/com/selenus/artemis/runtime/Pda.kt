package com.selenus.artemis.runtime

object Pda {
  private val PDA_MARKER = "ProgramDerivedAddress".encodeToByteArray()
  private const val MAX_SEEDS = 16
  private const val MAX_SEED_LEN = 32

  data class Result(val address: Pubkey, val bump: Int)

  /**
   * Walks bumps from 255 down to 1 (canonical bump search). Bump 0 is
   * skipped to match `solana-program` and `@solana/web3.js`. The
   * canonical PDA is always the first bump in this descending sweep
   * that lands off-curve.
   */
  @JvmStatic fun findProgramAddress(seeds: List<ByteArray>, programId: Pubkey): Result {
    require(seeds.size <= MAX_SEEDS) { "Too many seeds: ${seeds.size} > $MAX_SEEDS" }
    for (bump in 255 downTo 1) {
      val addr = createProgramAddress(seeds + byteArrayOf(bump.toByte()), programId) ?: continue
      return Result(addr, bump)
    }
    throw IllegalStateException("No valid PDA found")
  }

  /**
   * Hashes `seeds || programId || marker` into a candidate address.
   * Returns null when the candidate falls on the ed25519 curve, which
   * matches the on-chain validator's PDA constraint.
   *
   * Hot path: callers invoke this up to 256 times per `findProgramAddress`,
   * so the buffer is sized once and filled with `copyInto` rather than
   * concatenating intermediate arrays.
   */
  @JvmStatic fun createProgramAddress(seeds: List<ByteArray>, programId: Pubkey): Pubkey? {
    require(seeds.size <= MAX_SEEDS) { "Too many seeds: ${seeds.size} > $MAX_SEEDS" }
    require(seeds.all { it.size <= MAX_SEED_LEN }) { "Each seed must be <= $MAX_SEED_LEN bytes" }
    require(programId.bytes.size == 32) { "programId must be 32 bytes" }

    var totalLen = programId.bytes.size + PDA_MARKER.size
    for (s in seeds) totalLen += s.size

    val data = ByteArray(totalLen)
    var off = 0
    for (s in seeds) {
      s.copyInto(data, destinationOffset = off)
      off += s.size
    }
    programId.bytes.copyInto(data, destinationOffset = off); off += programId.bytes.size
    PDA_MARKER.copyInto(data, destinationOffset = off)

    val hash = Crypto.sha256(data)
    return if (PlatformEd25519.isOnCurve(hash)) null else Pubkey(hash)
  }
}
