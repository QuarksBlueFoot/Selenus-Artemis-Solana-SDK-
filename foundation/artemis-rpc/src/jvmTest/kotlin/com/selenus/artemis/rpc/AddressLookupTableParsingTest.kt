package com.selenus.artemis.rpc

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import com.selenus.artemis.runtime.Base58

class AddressLookupTableParsingTest {

    @Test
    fun testParseValidTable() {
        // Construct valid bytes
        // Layout:
        // u32 type (1)
        // u64 deactivation (12345)
        // u64 lastExtended (67890)
        // u8 start (0)
        // u8 hasAuth (1)
        // 32 bytes auth
        // u64 length (2)
        // 32 bytes addr1
        // 32 bytes addr2
        
        val authParams = ByteArray(32) { 1 } // All 1s
        val addr1Params = ByteArray(32) { 2 } // All 2s
        val addr2Params = ByteArray(32) { 3 } // All 3s

        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(1) // type
        buffer.putLong(12345L) // deactivation
        buffer.putLong(67890L) // lastExtended
        buffer.put(0.toByte()) // start index
        buffer.put(1.toByte()) // has authority
        buffer.put(authParams) // authority
        buffer.putLong(2) // length (assuming u64)
        buffer.put(addr1Params)
        buffer.put(addr2Params)
        
        val bytes = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(bytes)

        val result = parseAddressLookupTable(bytes)
        assertNotNull(result)
        assertEquals(12345L, result.deactivationSlot)
        assertEquals(67890L, result.lastExtendedSlot)
        assertEquals(0, result.lastExtendedSlotStartIndex)
        assertEquals(Base58.encode(authParams), result.authority)
        assertEquals(2, result.addresses.size)
        assertEquals(Base58.encode(addr1Params), result.addresses[0])
        assertEquals(Base58.encode(addr2Params), result.addresses[1])
    }
}
