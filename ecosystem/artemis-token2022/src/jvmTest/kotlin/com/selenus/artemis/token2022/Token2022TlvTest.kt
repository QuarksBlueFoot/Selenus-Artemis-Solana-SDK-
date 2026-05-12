package com.selenus.artemis.token2022

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Token2022TlvTest {

  @Test
  fun `decode stops at Uninitialized`() {
    // type=1, len=3, value=01 02 03 ; type=2, len=1, value=FF ; type=0 => stop
    val tlv = byteArrayOf(
      0x01, 0x00, 0x03, 0x00, 0x01, 0x02, 0x03,
      0x02, 0x00, 0x01, 0x00, 0xFF.toByte(),
      0x00, 0x00, 0x00, 0x00,
    )
    val entries = Token2022Tlv.decode(tlv)
    assertEquals(2, entries.size)
    assertEquals(1u, entries[0].type)
    assertEquals(3, entries[0].length)
    assertEquals(listOf(0x01, 0x02, 0x03), entries[0].value.map { it.toInt() and 0xFF })
    assertEquals(2u, entries[1].type)
    assertEquals(1, entries[1].length)
    assertEquals(0xFF, entries[1].value[0].toInt() and 0xFF)
  }

  @Test
  fun `decodeViews reads values from the original buffer without copying`() {
    val tlv = byteArrayOf(
      0x01, 0x00, 0x03, 0x00, 0x01, 0x02, 0x03,
      0x00, 0x00, 0x00, 0x00,
    )
    val owned = Token2022Tlv.decode(tlv).single()
    val view = Token2022Tlv.decodeViews(tlv).single()

    assertEquals(1u, view.type)
    assertEquals(3, view.length)
    assertEquals(0x02, view.valueAt(1).toInt() and 0xFF)
    assertContentEquals(byteArrayOf(0x01, 0x02, 0x03), view.copyValue())

    tlv[5] = 0x7F
    assertEquals(0x7F, view.valueAt(1).toInt() and 0xFF)
    assertEquals(0x02, owned.value[1].toInt() and 0xFF)
  }

  @Test
  fun `extractExtensions enforces mint padding`() {
    val baseLen = Token2022StateLayout.MINT_BASE_LENGTH
    val paddingLen = Token2022StateLayout.BASE_ACCOUNT_LENGTH - baseLen

    val tlv = byteArrayOf(0x01, 0x00, 0x00, 0x00) // type=1 len=0
    val ok = ByteArray(baseLen + paddingLen + 1 + tlv.size)
    // AccountType = Mint
    ok[Token2022StateLayout.BASE_ACCOUNT_LENGTH] = 0x01
    tlv.copyInto(ok, Token2022StateLayout.BASE_ACCOUNT_LENGTH + 1)

    val decoded = Token2022Extensions.decode(ok, baseLen)!!
    assertEquals(Token2022StateLayout.AccountType.Mint, decoded.accountType)
    assertEquals(1, decoded.entries.size)
    assertEquals(1u, decoded.entries[0].type)

    val bad = ok.copyOf()
    // break padding byte (between base and 165)
    bad[baseLen] = 0x01
    assertFailsWith<IllegalArgumentException> {
      Token2022Extensions.decode(bad, baseLen)
    }
  }

  @Test
  fun `decodeView walks the original account buffer`() {
    val baseLen = Token2022StateLayout.MINT_BASE_LENGTH
    val paddingLen = Token2022StateLayout.BASE_ACCOUNT_LENGTH - baseLen
    val tlv = byteArrayOf(0x01, 0x00, 0x02, 0x00, 0x0A, 0x0B)
    val data = ByteArray(baseLen + paddingLen + 1 + tlv.size)
    data[Token2022StateLayout.BASE_ACCOUNT_LENGTH] = 0x01
    tlv.copyInto(data, Token2022StateLayout.BASE_ACCOUNT_LENGTH + 1)

    val decoded = Token2022Extensions.decodeView(data, baseLen)!!
    val view = decoded.entries.single()
    assertEquals(Token2022StateLayout.AccountType.Mint, decoded.accountType)
    assertEquals(0x0B, view.valueAt(1).toInt() and 0xFF)

    data[Token2022StateLayout.BASE_ACCOUNT_LENGTH + 1 + 5] = 0x55
    assertEquals(0x55, view.valueAt(1).toInt() and 0xFF)
  }
}
