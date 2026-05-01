package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Byte-level fixtures generated with @solana/web3.js 1.98.4.
 *
 * The custom instruction intentionally puts two unsigned writable accounts in
 * non-lexicographic order. web3.js legacy transactions sort equal account
 * groups lexicographically; Artemis must do the same to be byte-compatible.
 */
class Web3JsTransactionParityTest {
  private val blockhash = "11111111111111111111111111111111"
  private val payer = Keypair.fromSeed(ByteArray(32) { (it + 1).toByte() })
  private val accountA = Pubkey(ByteArray(32) { 3 })
  private val accountB = Pubkey(ByteArray(32) { 2 })
  private val programId = Pubkey(ByteArray(32) { 4 })

  @Test
  fun `legacy message bytes match web3js lexicographic account ordering`() {
    val tx = referenceTransaction()
    val message = tx.compileMessage()

    assertEquals(accountB, message.accountKeys[1])
    assertEquals(accountA, message.accountKeys[2])
    assertContentEquals(
      hexToBytes(WEB3JS_1_98_4_LEGACY_MESSAGE_HEX),
      message.serialize()
    )
  }

  @Test
  fun `legacy signed transaction bytes match web3js fixture`() {
    val signed = referenceTransaction().sign(listOf(payer)).serialize()

    assertContentEquals(
      hexToBytes(WEB3JS_1_98_4_LEGACY_TX_HEX),
      signed
    )
  }

  private fun referenceTransaction(): Transaction = Transaction(
    feePayer = payer.publicKey,
    recentBlockhash = blockhash
  ).apply {
    addInstruction(
      Instruction(
        programId = programId,
        accounts = listOf(
          AccountMeta(accountA, isSigner = false, isWritable = true),
          AccountMeta(accountB, isSigner = false, isWritable = true)
        ),
        data = byteArrayOf(9, 8, 7)
      )
    )
  }

  private fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "hex length must be even" }
    return ByteArray(hex.length / 2) { i ->
      hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
  }

  private companion object {
    const val WEB3JS_1_98_4_LEGACY_MESSAGE_HEX =
      "0100010479b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664" +
        "0202020202020202020202020202020202020202020202020202020202020202" +
        "0303030303030303030303030303030303030303030303030303030303030303" +
        "0404040404040404040404040404040404040404040404040404040404040404" +
        "0000000000000000000000000000000000000000000000000000000000000000" +
        "010302020103090807"

    const val WEB3JS_1_98_4_LEGACY_TX_HEX =
      "012d6f3e0ef7a807a1341f12dfb22fa672b6b8e7c7d626c8f18597e134567d6c37" +
        "d86552e18df6eddde85f6a60af555afafc4962a593127c6fd46309a46e07c305" +
        WEB3JS_1_98_4_LEGACY_MESSAGE_HEX
  }
}
