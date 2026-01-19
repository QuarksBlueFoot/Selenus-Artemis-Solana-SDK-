package com.selenus.artemis.runtime

import java.math.BigInteger

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
    return if (Ed25519.onCurve(hash)) null else Pubkey(hash)
  }
}

internal object Ed25519 {
  private val P = BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
  private val D = BigInteger("-121665").multiply(BigInteger("121666").modInverse(P)).mod(P)
  private val TWO = BigInteger("2")

  fun onCurve(pk32: ByteArray): Boolean {
    try {
      val yBytes = pk32.clone()
      yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()
      val y = BigInteger(1, yBytes.reversedArray())

      if (y >= P) return false

      val y2 = y.multiply(y).mod(P)
      val one = BigInteger.ONE
      val num = y2.subtract(one).mod(P)
      val den = D.multiply(y2).add(one).mod(P)

      if (den == BigInteger.ZERO) return false

      val x2 = num.multiply(den.modInverse(P)).mod(P)
      
      if (x2 == BigInteger.ZERO) return true

      // Check if x2 is a quadratic residue
      val exp = P.subtract(one).divide(TWO)
      val check = x2.modPow(exp, P)
      
      return check == one
    } catch (e: Exception) {
      return false
    }
  }
}
