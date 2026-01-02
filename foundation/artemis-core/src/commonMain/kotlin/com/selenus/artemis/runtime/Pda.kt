package com.selenus.artemis.runtime

object Pda {
  private val PDA_MARKER = "ProgramDerivedAddress".encodeToByteArray()

  data class Result(val address: Pubkey, val bump: Int)

  @JvmStatic fun findProgramAddress(seeds: List<ByteArray>, programId: Pubkey): Result {
    for (bump in 255 downTo 0) {
      val addr = createProgramAddress(seeds + byteArrayOf(bump.toByte()), programId) ?: continue
      return Result(addr, bump)
    }
    throw IllegalStateException("No valid PDA found")
  }

  @JvmStatic fun createProgramAddress(seeds: List<ByteArray>, programId: Pubkey): Pubkey? {
    require(seeds.all { it.size <= 32 }) { "Each seed must be <= 32 bytes" }
    val data = seeds.fold(ByteArray(0)) { acc, s -> acc + s } + programId.bytes + PDA_MARKER
    val hash = Crypto.sha256(data)
    return if (PlatformEd25519.isOnCurve(hash)) null else Pubkey(hash)
  }
}
