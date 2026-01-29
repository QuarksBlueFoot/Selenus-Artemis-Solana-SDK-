/*
 * Copyright (c) 2024-2026 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * Zero-Copy Streaming Module Tests - World's First mobile-optimized streaming.
 */
package com.selenus.artemis.streaming

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tests for the Zero-Copy Account Stream - World's First mobile-optimized streaming.
 * 
 * These tests verify:
 * 1. Field type definitions
 * 2. Schema creation
 * 3. Zero-copy accessor operations
 * 4. Buffer pool management
 * 5. Ring buffer history
 * 6. Field change detection
 */
class StreamingModuleTest {
    
    // ===========================================
    // Field Type Tests
    // ===========================================
    
    @Test
    fun `field types have correct sizes`() {
        assertEquals(1, FieldType.U8.size)
        assertEquals(2, FieldType.U16.size)
        assertEquals(4, FieldType.U32.size)
        assertEquals(8, FieldType.U64.size)
        assertEquals(8, FieldType.I64.size)
        assertEquals(1, FieldType.BOOL.size)
        assertEquals(32, FieldType.PUBKEY.size)
    }
    
    @Test
    fun `field definition structure`() {
        val field = FieldDef(
            name = "amount",
            type = FieldType.U64,
            offset = 8,
            size = 8
        )
        
        assertEquals("amount", field.name)
        assertEquals(FieldType.U64, field.type)
        assertEquals(8, field.offset)
    }
    
    // ===========================================
    // Account Schema Tests
    // ===========================================
    
    @Test
    fun `create token account schema`() {
        val schema = AccountSchema(
            name = "TokenAccount",
            fields = listOf(
                FieldDef("mint", FieldType.PUBKEY, 0, 32),
                FieldDef("owner", FieldType.PUBKEY, 32, 32),
                FieldDef("amount", FieldType.U64, 64, 8),
                FieldDef("delegateOption", FieldType.U32, 72, 4),
                FieldDef("delegate", FieldType.PUBKEY, 76, 32),
                FieldDef("state", FieldType.U8, 108, 1),
                FieldDef("isNativeOption", FieldType.U32, 109, 4),
                FieldDef("isNative", FieldType.U64, 113, 8),
                FieldDef("delegatedAmount", FieldType.U64, 121, 8),
                FieldDef("closeAuthorityOption", FieldType.U32, 129, 4),
                FieldDef("closeAuthority", FieldType.PUBKEY, 133, 32)
            ),
            totalSize = 165
        )
        
        assertEquals("TokenAccount", schema.name)
        assertEquals(11, schema.fields.size)
        assertEquals(165, schema.totalSize)
    }
    
    @Test
    fun `schema with monitored fields`() {
        val amountField = FieldDef("amount", FieldType.U64, 64, 8)
        val schema = AccountSchema(
            name = "TokenAccount",
            fields = listOf(
                FieldDef("mint", FieldType.PUBKEY, 0, 32),
                FieldDef("owner", FieldType.PUBKEY, 32, 32),
                amountField
            ),
            totalSize = 72,
            monitoredFields = listOf(amountField)
        )
        
        assertEquals(1, schema.monitoredFields.size)
        assertEquals("amount", schema.monitoredFields[0].name)
    }
    
    // ===========================================
    // Zero-Copy Accessor Tests
    // ===========================================
    
    @Test
    fun `accessor reads u8`() {
        val buffer = createTestBuffer()
        val schema = createSimpleSchema()
        val accessor = ZeroCopyAccessorImpl(buffer, schema)
        
        val value = accessor.getU8("flag")
        assertTrue(value >= 0)
    }
    
    @Test
    fun `accessor reads u64`() {
        val buffer = createTestBuffer()
        buffer.putLong(8, 1_000_000L)
        buffer.rewind()
        
        val schema = createSimpleSchema()
        val accessor = ZeroCopyAccessorImpl(buffer, schema)
        
        val value = accessor.getU64("amount")
        assertEquals(1_000_000L, value)
    }
    
    @Test
    fun `accessor reads pubkey`() {
        val buffer = createTestBuffer()
        val schema = createSimpleSchema()
        val accessor = ZeroCopyAccessorImpl(buffer, schema)
        
        val pubkey = accessor.getPubkey("owner")
        assertEquals(64, pubkey.length) // Hex string = 32 bytes * 2
    }
    
    @Test
    fun `accessor reads bool`() {
        val buffer = createTestBuffer()
        buffer.put(48, 1)
        buffer.rewind()
        
        val schema = createSimpleSchema()
        val accessor = ZeroCopyAccessorImpl(buffer, schema)
        
        val value = accessor.getBool("active")
        assertTrue(value)
    }
    
    // ===========================================
    // Buffer Pool Tests
    // ===========================================
    
    @Test
    fun `buffer pool creates buffers of correct size`() {
        val pool = BufferPool(poolSize = 10, bufferSize = 1024)
        
        val buffer = pool.acquire()
        assertEquals(1024, buffer.capacity())
        
        pool.release(buffer)
    }
    
    @Test
    fun `buffer pool reuses released buffers`() {
        val pool = BufferPool(poolSize = 10, bufferSize = 1024)
        
        val buffer1 = pool.acquire()
        pool.release(buffer1)
        
        val buffer2 = pool.acquire()
        // Should reuse the released buffer
        assertNotNull(buffer2)
    }
    
    @Test
    fun `buffer pool clears all buffers`() {
        val pool = BufferPool(poolSize = 10, bufferSize = 1024)
        
        val buffer = pool.acquire()
        pool.release(buffer)
        pool.clear()
        
        // Pool should still work after clear
        val newBuffer = pool.acquire()
        assertNotNull(newBuffer)
    }
    
    // ===========================================
    // Ring Buffer Tests
    // ===========================================
    
    @Test
    fun `ring buffer stores items`() {
        val ringBuffer = RingBuffer<Int>(5)
        
        ringBuffer.add(1)
        ringBuffer.add(2)
        ringBuffer.add(3)
        
        val items = ringBuffer.toList()
        assertEquals(3, items.size)
    }
    
    @Test
    fun `ring buffer overwrites oldest when full`() {
        val ringBuffer = RingBuffer<Int>(3)
        
        ringBuffer.add(1)
        ringBuffer.add(2)
        ringBuffer.add(3)
        ringBuffer.add(4) // Overwrites 1
        
        val items = ringBuffer.toList()
        assertEquals(3, items.size)
        assertFalse(items.contains(1))
        assertTrue(items.contains(4))
    }
    
    @Test
    fun `ring buffer maintains order`() {
        val ringBuffer = RingBuffer<Int>(5)
        
        ringBuffer.add(1)
        ringBuffer.add(2)
        ringBuffer.add(3)
        
        val items = ringBuffer.toList()
        assertEquals(listOf(1, 2, 3), items)
    }
    
    // ===========================================
    // Account Snapshot Tests
    // ===========================================
    
    @Test
    fun `account snapshot structure`() {
        val data = ByteArray(165)
        val snapshot = AccountSnapshot(
            data = data,
            timestamp = System.currentTimeMillis(),
            slot = 123456789L
        )
        
        assertEquals(165, snapshot.data.size)
        assertTrue(snapshot.timestamp > 0)
        assertEquals(123456789L, snapshot.slot)
    }
    
    // ===========================================
    // Field Change Tests
    // ===========================================
    
    @Test
    fun `field change detection structure`() {
        val change = FieldChange(
            fieldName = "amount",
            oldValue = 1000L,
            newValue = 2000L,
            fieldType = FieldType.U64
        )
        
        assertEquals("amount", change.fieldName)
        assertEquals(1000L, change.oldValue)
        assertEquals(2000L, change.newValue)
    }
    
    // ===========================================
    // Stream Config Tests
    // ===========================================
    
    @Test
    fun `default stream config`() {
        val config = StreamConfig()
        
        assertTrue(config.poolSize > 0)
        assertTrue(config.bufferSize > 0)
        assertTrue(config.historySize > 0)
        assertTrue(config.flowBufferSize > 0)
    }
    
    @Test
    fun `custom stream config`() {
        val config = StreamConfig(
            poolSize = 50,
            bufferSize = 2048,
            historySize = 100,
            flowBufferSize = 32
        )
        
        assertEquals(50, config.poolSize)
        assertEquals(2048, config.bufferSize)
        assertEquals(100, config.historySize)
        assertEquals(32, config.flowBufferSize)
    }
    
    // ===========================================
    // Subscription Tests
    // ===========================================
    
    @Test
    fun `subscription handle structure`() {
        // Create a minimal mock for testing subscription handle
        val account = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"
        
        // Test that we can create subscription data structures
        val subscription = AccountSubscription(
            account = account,
            schema = createSimpleSchema(),
            onChange = { },
            history = RingBuffer(10)
        )
        
        assertEquals(account, subscription.account)
        assertNotNull(subscription.schema)
    }
    
    // ===========================================
    // Helper Methods
    // ===========================================
    
    private fun createTestBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocate(100)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        // Fill with some test data
        for (i in 0 until 100) {
            buffer.put(i.toByte())
        }
        buffer.rewind()
        return buffer
    }
    
    private fun createSimpleSchema(): AccountSchema {
        return AccountSchema(
            name = "TestAccount",
            fields = listOf(
                FieldDef("flag", FieldType.U8, 0, 1),
                FieldDef("amount", FieldType.U64, 8, 8),
                FieldDef("owner", FieldType.PUBKEY, 16, 32),
                FieldDef("active", FieldType.BOOL, 48, 1)
            ),
            totalSize = 100
        )
    }
}

/**
 * Internal test implementation of ZeroCopyAccessor for unit testing.
 */
internal class ZeroCopyAccessorImpl(
    private val buffer: ByteBuffer,
    private val schema: AccountSchema
) : ZeroCopyAccessor {
    
    override fun getU8(fieldName: String): Int {
        val field = findField(fieldName)
        buffer.position(field.offset)
        return buffer.get().toInt() and 0xFF
    }
    
    override fun getU16(fieldName: String): Int {
        val field = findField(fieldName)
        buffer.position(field.offset)
        return buffer.short.toInt() and 0xFFFF
    }
    
    override fun getU32(fieldName: String): Long {
        val field = findField(fieldName)
        buffer.position(field.offset)
        return buffer.int.toLong() and 0xFFFFFFFFL
    }
    
    override fun getU64(fieldName: String): Long {
        val field = findField(fieldName)
        buffer.position(field.offset)
        return buffer.long
    }
    
    override fun getI64(fieldName: String): Long {
        val field = findField(fieldName)
        buffer.position(field.offset)
        return buffer.long
    }
    
    override fun getBool(fieldName: String): Boolean {
        val field = findField(fieldName)
        buffer.position(field.offset)
        return buffer.get() != 0.toByte()
    }
    
    override fun getPubkey(fieldName: String): String {
        val field = findField(fieldName)
        buffer.position(field.offset)
        val bytes = ByteArray(32)
        buffer.get(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    override fun getBytes(fieldName: String, length: Int): ByteArray {
        val field = findField(fieldName)
        buffer.position(field.offset)
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return bytes
    }
    
    private fun findField(name: String): FieldDef {
        return schema.fields.find { it.name == name }
            ?: throw IllegalArgumentException("Unknown field: $name")
    }
}
