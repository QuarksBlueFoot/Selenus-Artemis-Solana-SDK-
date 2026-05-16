package com.funkatronics.encoders

import com.funkatronics.encoders.error.InvalidInputException
import io.github.funkatronics.multimult.Base58 as GroupPathBase58
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MultimultCompatTest {
    @Test
    fun encodesAndDecodesUpstreamHelloWorldVector() {
        val bytes = "Hello World".encodeToByteArray()

        assertEquals("JxF12TrwUP45BMd", Base58.encodeToString(bytes))
        assertContentEquals("JxF12TrwUP45BMd".encodeToByteArray(), Base58.encode(bytes))
        assertContentEquals(bytes, Base58.decode("JxF12TrwUP45BMd"))
        assertEquals("Hello World", Base58.decodeToString("JxF12TrwUP45BMd"))
    }

    @Test
    fun preservesLeadingZeros() {
        val bytes = ByteArray(3) + "Hello World".encodeToByteArray()

        assertEquals("111JxF12TrwUP45BMd", Base58.encodeToString(bytes))
        assertContentEquals(bytes, Base58.decode("111JxF12TrwUP45BMd"))
    }

    @Test
    fun encodesRfcVectorsAndAllZeros() {
        assertEquals("2NEpo7TZRRrLZSi2U", Base58.encodeToString("Hello World!".encodeToByteArray()))
        assertEquals(
            "USm3fpXnKG5EUBx2ndxBDMPVciP5hGey2Jh4NDv6gmeo1LkMeiKrLJUUBk6Z",
            Base58.encodeToString("The quick brown fox jumps over the lazy dog.".encodeToByteArray())
        )
        assertEquals("11233QC4", Base58.encodeToString(byteArrayOf(0x00, 0x00, 0x28, 0x7f, 0xb4.toByte(), 0xcd.toByte())))
        assertEquals("1111111111", Base58.encodeToString(ByteArray(10)))
        assertContentEquals(ByteArray(10), Base58.decode("1111111111"))
    }

    @Test
    fun mapsInvalidCharactersToUpstreamExceptionShape() {
        val error = assertFailsWith<InvalidInputException.InvalidCharacter> {
            Base58.decode("0OIl")
        }

        assertEquals('0', error.character)
        assertEquals(0, error.position)
    }

    @Test
    fun exposesCompatibilityAliasUnderGroupStylePackage() {
        val bytes = "Hello World".encodeToByteArray()

        assertEquals("JxF12TrwUP45BMd", GroupPathBase58.encodeToString(bytes))
        assertContentEquals(bytes, GroupPathBase58.decode("JxF12TrwUP45BMd"))
    }
}