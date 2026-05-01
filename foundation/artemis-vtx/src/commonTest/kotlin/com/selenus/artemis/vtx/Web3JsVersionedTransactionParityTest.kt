package com.selenus.artemis.vtx

import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Byte-level v0 fixtures generated with @solana/web3.js 1.98.4. */
class Web3JsVersionedTransactionParityTest {
  private val blockhash = "11111111111111111111111111111111"
  private val payer = Keypair.fromSeed(ByteArray(32) { (it + 1).toByte() })
  private val accountA = Pubkey(ByteArray(32) { 3 })
  private val accountB = Pubkey(ByteArray(32) { 2 })
  private val programId = Pubkey(ByteArray(32) { 4 })

  @Test
  fun `v0 message bytes match web3js first-seen account ordering`() {
    val compiled = V0MessageCompiler.compile(payer, blockhash, listOf(referenceInstruction()))

    assertEquals(accountA, compiled.message.staticAccountKeys[1])
    assertEquals(accountB, compiled.message.staticAccountKeys[2])
    assertContentEquals(
      hexToBytes(WEB3JS_1_98_4_V0_MESSAGE_HEX),
      compiled.message.serialize()
    )
  }

  @Test
  fun `v0 signed transaction bytes match web3js fixture`() {
    val tx = V0MessageCompiler.compileAndSign(
      feePayer = payer,
      additionalSigners = emptyList(),
      recentBlockhash = blockhash,
      instructions = listOf(referenceInstruction())
    )

    assertContentEquals(
      hexToBytes(WEB3JS_1_98_4_V0_TX_HEX),
      tx.serialize()
    )
  }

  private fun referenceInstruction(): Instruction = Instruction(
    programId = programId,
    accounts = listOf(
      AccountMeta(accountA, isSigner = false, isWritable = true),
      AccountMeta(accountB, isSigner = false, isWritable = true)
    ),
    data = byteArrayOf(9, 8, 7)
  )

  private fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "hex length must be even" }
    return ByteArray(hex.length / 2) { i ->
      hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
  }

  private companion object {
    const val WEB3JS_1_98_4_V0_MESSAGE_HEX =
      "800100010479b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664" +
        "0303030303030303030303030303030303030303030303030303030303030303" +
        "0202020202020202020202020202020202020202020202020202020202020202" +
        "0404040404040404040404040404040404040404040404040404040404040404" +
        "0000000000000000000000000000000000000000000000000000000000000000" +
        "01030201020309080700"

    const val WEB3JS_1_98_4_V0_TX_HEX =
      "01847742859319478fa833f7ecf9a7df0ab2e01ec7867ecef904904f4664cfc9db824" +
        "d076eabdf01cb884a030651935d05c8085cf442543b695eb48021fe3f190e" +
        WEB3JS_1_98_4_V0_MESSAGE_HEX
  }
}
