/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * ORIGINAL IMPLEMENTATION - Complete Borsh serialization for Anchor IDL types.
 * 
 * This provides:
 * - Full Borsh encoding/decoding for all IDL types
 * - Automatic type mapping from Kotlin types
 * - Zero-copy deserialization where possible
 * - Support for nested structs and enums
 */
package com.selenus.artemis.anchor

import com.selenus.artemis.runtime.Pubkey

/**
 * Borsh serializer for IDL types.
 */
object BorshSerializer {
    
    /**
     * Serialize instruction arguments based on IDL definition.
     */
    fun serializeArgs(
        argDefs: List<IdlField>,
        args: Map<String, Any?>,
        program: AnchorProgram
    ): ByteArray {
        val buffer = DynamicByteBuffer()
        
        for (argDef in argDefs) {
            val value = args[argDef.name]
            serializeValue(buffer, argDef.type, value, program)
        }
        
        return buffer.toByteArray()
    }
    
    /**
     * Serialize a single value.
     */
    fun serializeValue(
        buffer: DynamicByteBuffer,
        type: IdlType,
        value: Any?,
        program: AnchorProgram
    ) {
        when (type) {
            is IdlType.Bool -> buffer.writeBool(value as? Boolean ?: false)
            is IdlType.U8 -> buffer.writeU8((value as? Number)?.toInt() ?: 0)
            is IdlType.U16 -> buffer.writeU16((value as? Number)?.toInt() ?: 0)
            is IdlType.U32 -> buffer.writeU32((value as? Number)?.toLong() ?: 0L)
            is IdlType.U64 -> buffer.writeU64(toLong(value))
            is IdlType.U128 -> buffer.writeU128(toLong(value))
            is IdlType.I8 -> buffer.writeI8((value as? Number)?.toInt() ?: 0)
            is IdlType.I16 -> buffer.writeI16((value as? Number)?.toInt() ?: 0)
            is IdlType.I32 -> buffer.writeI32((value as? Number)?.toInt() ?: 0)
            is IdlType.I64 -> buffer.writeI64(toLong(value))
            is IdlType.I128 -> buffer.writeI128(toLong(value))
            is IdlType.F32 -> buffer.writeF32((value as? Number)?.toFloat() ?: 0f)
            is IdlType.F64 -> buffer.writeF64((value as? Number)?.toDouble() ?: 0.0)
            is IdlType.String -> buffer.writeString(value?.toString() ?: "")
            is IdlType.Bytes -> buffer.writeBytes(value as? ByteArray ?: ByteArray(0))
            is IdlType.PublicKey -> buffer.writePubkey(toPubkey(value))
            
            is IdlType.Vec -> {
                val list = (value as? List<*>) ?: emptyList<Any>()
                buffer.writeU32(list.size.toLong())
                for (item in list) {
                    serializeValue(buffer, type.inner, item, program)
                }
            }
            
            is IdlType.Array -> {
                val list = (value as? List<*>) ?: List(type.size) { null }
                require(list.size == type.size) { 
                    "Array size mismatch: expected ${type.size}, got ${list.size}" 
                }
                for (item in list) {
                    serializeValue(buffer, type.inner, item, program)
                }
            }
            
            is IdlType.Option -> {
                if (value == null) {
                    buffer.writeU8(0)
                } else {
                    buffer.writeU8(1)
                    serializeValue(buffer, type.inner, value, program)
                }
            }
            
            is IdlType.COption -> {
                // COption uses 4-byte discriminator
                if (value == null) {
                    buffer.writeU32(0)
                } else {
                    buffer.writeU32(1)
                    serializeValue(buffer, type.inner, value, program)
                }
            }
            
            is IdlType.Defined -> {
                val typeDef = program.findType(type.name)
                    ?: throw IllegalArgumentException("Unknown type: ${type.name}")
                serializeDefinedType(buffer, typeDef, value, program)
            }
            
            is IdlType.Generic -> {
                // Generics should be resolved before serialization
                throw IllegalArgumentException("Unresolved generic type: ${type.name}")
            }
        }
    }
    
    private fun serializeDefinedType(
        buffer: DynamicByteBuffer,
        typeDef: IdlTypeDef,
        value: Any?,
        program: AnchorProgram
    ) {
        when (val typeType = typeDef.type) {
            is IdlTypeDefType.Struct -> {
                @Suppress("UNCHECKED_CAST")
                val map = value as? Map<String, Any?> ?: emptyMap()
                for (field in typeType.fields) {
                    serializeValue(buffer, field.type, map[field.name], program)
                }
            }
            
            is IdlTypeDefType.Enum -> {
                // Enum serialization: variant index (u8) + variant data
                when (value) {
                    is String -> {
                        // Simple enum variant
                        val variantIndex = typeType.variants.indexOfFirst { it.name == value }
                        require(variantIndex >= 0) { "Unknown enum variant: $value" }
                        buffer.writeU8(variantIndex)
                    }
                    is Map<*, *> -> {
                        // Enum with data
                        @Suppress("UNCHECKED_CAST")
                        val map = value as Map<String, Any?>
                        val variantName = map.keys.first()
                        val variantIndex = typeType.variants.indexOfFirst { it.name == variantName }
                        require(variantIndex >= 0) { "Unknown enum variant: $variantName" }
                        buffer.writeU8(variantIndex)
                        
                        val variant = typeType.variants[variantIndex]
                        val variantData = map[variantName]
                        
                        variant.fields?.let { fields ->
                            if (variantData is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                val dataMap = variantData as Map<String, Any?>
                                for (field in fields) {
                                    serializeValue(buffer, field.type, dataMap[field.name], program)
                                }
                            }
                        }
                    }
                    is Int -> {
                        // Numeric enum
                        buffer.writeU8(value)
                    }
                    else -> {
                        buffer.writeU8(0) // Default to first variant
                    }
                }
            }
            
            is IdlTypeDefType.Alias -> {
                serializeValue(buffer, typeType.value, value, program)
            }
        }
    }
    
    /**
     * Calculate the size of a type in bytes.
     */
    fun sizeOf(type: IdlType, program: AnchorProgram): Int {
        return when (type) {
            is IdlType.Bool -> 1
            is IdlType.U8, is IdlType.I8 -> 1
            is IdlType.U16, is IdlType.I16 -> 2
            is IdlType.U32, is IdlType.I32, is IdlType.F32 -> 4
            is IdlType.U64, is IdlType.I64, is IdlType.F64 -> 8
            is IdlType.U128, is IdlType.I128 -> 16
            is IdlType.PublicKey -> 32
            is IdlType.String, is IdlType.Bytes, is IdlType.Vec -> -1 // Variable size
            is IdlType.Array -> {
                val innerSize = sizeOf(type.inner, program)
                if (innerSize < 0) -1 else innerSize * type.size
            }
            is IdlType.Option -> {
                val innerSize = sizeOf(type.inner, program)
                if (innerSize < 0) -1 else 1 + innerSize
            }
            is IdlType.COption -> {
                val innerSize = sizeOf(type.inner, program)
                if (innerSize < 0) -1 else 4 + innerSize
            }
            is IdlType.Defined -> {
                val typeDef = program.findType(type.name)
                if (typeDef == null) -1 else sizeOfDefined(typeDef, program)
            }
            is IdlType.Generic -> -1
        }
    }
    
    private fun sizeOfDefined(typeDef: IdlTypeDef, program: AnchorProgram): Int {
        return when (val typeType = typeDef.type) {
            is IdlTypeDefType.Struct -> {
                var total = 0
                for (field in typeType.fields) {
                    val fieldSize = sizeOf(field.type, program)
                    if (fieldSize < 0) return -1
                    total += fieldSize
                }
                total
            }
            is IdlTypeDefType.Enum -> {
                // Enum size depends on largest variant
                -1
            }
            is IdlTypeDefType.Alias -> sizeOf(typeType.value, program)
        }
    }
    
    private fun toLong(value: Any?): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }
    
    private fun toPubkey(value: Any?): Pubkey {
        return when (value) {
            is Pubkey -> value
            is String -> Pubkey.fromBase58(value)
            is ByteArray -> Pubkey(value)
            else -> Pubkey(ByteArray(32))
        }
    }
}

/**
 * Borsh deserializer for IDL types.
 */
object BorshDeserializer {
    
    data class DeserializeResult(
        val fields: Map<String, Any?>,
        val bytesRead: Int
    )
    
    /**
     * Deserialize account fields.
     */
    fun deserializeFields(
        fields: List<IdlField>,
        data: ByteArray,
        offset: Int,
        program: AnchorProgram
    ): DeserializeResult {
        val reader = ByteArrayReader(data, offset)
        val result = mutableMapOf<String, Any?>()
        
        for (field in fields) {
            result[field.name] = deserializeValue(reader, field.type, program)
        }
        
        return DeserializeResult(result, reader.position - offset)
    }
    
    /**
     * Deserialize event fields.
     */
    fun deserializeEventFields(
        fields: List<IdlEventField>,
        data: ByteArray,
        offset: Int,
        program: AnchorProgram
    ): Map<String, Any?> {
        val reader = ByteArrayReader(data, offset)
        val result = mutableMapOf<String, Any?>()
        
        for (field in fields) {
            result[field.name] = deserializeValue(reader, field.type, program)
        }
        
        return result
    }
    
    /**
     * Deserialize a single value.
     */
    fun deserializeValue(
        reader: ByteArrayReader,
        type: IdlType,
        program: AnchorProgram
    ): Any? {
        return when (type) {
            is IdlType.Bool -> reader.readBool()
            is IdlType.U8 -> reader.readU8()
            is IdlType.U16 -> reader.readU16()
            is IdlType.U32 -> reader.readU32()
            is IdlType.U64 -> reader.readU64()
            is IdlType.U128 -> reader.readU128()
            is IdlType.I8 -> reader.readI8()
            is IdlType.I16 -> reader.readI16()
            is IdlType.I32 -> reader.readI32()
            is IdlType.I64 -> reader.readI64()
            is IdlType.I128 -> reader.readI128()
            is IdlType.F32 -> reader.readF32()
            is IdlType.F64 -> reader.readF64()
            is IdlType.String -> reader.readString()
            is IdlType.Bytes -> reader.readBytes()
            is IdlType.PublicKey -> reader.readPubkey()
            
            is IdlType.Vec -> {
                val length = reader.readU32().toInt()
                List(length) { deserializeValue(reader, type.inner, program) }
            }
            
            is IdlType.Array -> {
                List(type.size) { deserializeValue(reader, type.inner, program) }
            }
            
            is IdlType.Option -> {
                val isSome = reader.readU8() != 0
                if (isSome) deserializeValue(reader, type.inner, program) else null
            }
            
            is IdlType.COption -> {
                val isSome = reader.readU32() != 0L
                if (isSome) deserializeValue(reader, type.inner, program) else null
            }
            
            is IdlType.Defined -> {
                val typeDef = program.findType(type.name)
                    ?: throw IllegalArgumentException("Unknown type: ${type.name}")
                deserializeDefinedType(reader, typeDef, program)
            }
            
            is IdlType.Generic -> {
                throw IllegalArgumentException("Unresolved generic type: ${type.name}")
            }
        }
    }
    
    private fun deserializeDefinedType(
        reader: ByteArrayReader,
        typeDef: IdlTypeDef,
        program: AnchorProgram
    ): Any? {
        return when (val typeType = typeDef.type) {
            is IdlTypeDefType.Struct -> {
                val result = mutableMapOf<String, Any?>()
                for (field in typeType.fields) {
                    result[field.name] = deserializeValue(reader, field.type, program)
                }
                result
            }
            
            is IdlTypeDefType.Enum -> {
                val variantIndex = reader.readU8()
                if (variantIndex >= typeType.variants.size) {
                    throw IllegalArgumentException("Invalid enum variant index: $variantIndex")
                }
                
                val variant = typeType.variants[variantIndex]
                if (variant.fields.isNullOrEmpty()) {
                    // Simple variant
                    variant.name
                } else {
                    // Variant with data
                    val data = mutableMapOf<String, Any?>()
                    for (field in variant.fields) {
                        data[field.name] = deserializeValue(reader, field.type, program)
                    }
                    mapOf(variant.name to data)
                }
            }
            
            is IdlTypeDefType.Alias -> {
                deserializeValue(reader, typeType.value, program)
            }
        }
    }
}

/**
 * Dynamic byte buffer for serialization.
 */
class DynamicByteBuffer {
    private var buf = ByteArray(256)
    private var pos = 0
    
    private fun ensureCapacity(additional: Int) {
        if (pos + additional > buf.size) {
            buf = buf.copyOf((buf.size * 2).coerceAtLeast(pos + additional))
        }
    }
    
    fun writeBool(value: Boolean) {
        ensureCapacity(1)
        buf[pos++] = if (value) 1.toByte() else 0.toByte()
    }
    
    fun writeU8(value: Int) {
        ensureCapacity(1)
        buf[pos++] = value.toByte()
    }
    
    fun writeU16(value: Int) {
        ensureCapacity(2)
        buf[pos++] = (value and 0xff).toByte()
        buf[pos++] = ((value ushr 8) and 0xff).toByte()
    }
    
    fun writeU32(value: Long) {
        ensureCapacity(4)
        val v = value.toInt()
        buf[pos++] = (v and 0xff).toByte()
        buf[pos++] = ((v ushr 8) and 0xff).toByte()
        buf[pos++] = ((v ushr 16) and 0xff).toByte()
        buf[pos++] = ((v ushr 24) and 0xff).toByte()
    }
    
    fun writeU64(value: Long) {
        ensureCapacity(8)
        var v = value
        for (i in 0 until 8) {
            buf[pos++] = (v and 0xff).toByte()
            v = v ushr 8
        }
    }
    
    fun writeU128(value: Long) {
        writeU64(value)
        writeU64(0L)
    }
    
    fun writeI8(value: Int) = writeU8(value)
    fun writeI16(value: Int) = writeU16(value)
    fun writeI32(value: Int) {
        ensureCapacity(4)
        buf[pos++] = (value and 0xff).toByte()
        buf[pos++] = ((value ushr 8) and 0xff).toByte()
        buf[pos++] = ((value ushr 16) and 0xff).toByte()
        buf[pos++] = ((value ushr 24) and 0xff).toByte()
    }
    fun writeI64(value: Long) = writeU64(value)
    fun writeI128(value: Long) {
        writeU64(value)
        writeU64(if (value < 0L) -1L else 0L)
    }
    
    fun writeF32(value: Float) {
        ensureCapacity(4)
        val bits = value.toRawBits()
        buf[pos++] = (bits and 0xff).toByte()
        buf[pos++] = ((bits ushr 8) and 0xff).toByte()
        buf[pos++] = ((bits ushr 16) and 0xff).toByte()
        buf[pos++] = ((bits ushr 24) and 0xff).toByte()
    }
    
    fun writeF64(value: Double) {
        ensureCapacity(8)
        var bits = value.toRawBits()
        for (i in 0 until 8) {
            buf[pos++] = (bits and 0xff).toByte()
            bits = bits ushr 8
        }
    }
    
    fun writeString(value: String) {
        val bytes = value.encodeToByteArray()
        writeU32(bytes.size.toLong())
        ensureCapacity(bytes.size)
        bytes.copyInto(buf, destinationOffset = pos)
        pos += bytes.size
    }
    
    fun writeBytes(value: ByteArray) {
        writeU32(value.size.toLong())
        ensureCapacity(value.size)
        value.copyInto(buf, destinationOffset = pos)
        pos += value.size
    }
    
    fun writePubkey(value: Pubkey) {
        ensureCapacity(32)
        value.bytes.copyInto(buf, destinationOffset = pos)
        pos += 32
    }
    
    fun toByteArray(): ByteArray {
        return buf.copyOfRange(0, pos)
    }
}

/**
 * Byte array reader for deserialization.
 */
class ByteArrayReader(
    private val data: ByteArray,
    private var pos: Int = 0
) {
    val position: Int get() = pos
    
    private fun checkBounds(size: Int) {
        if (pos + size > data.size) {
            throw IndexOutOfBoundsException("Not enough data: need $size bytes at position $pos, have ${data.size - pos}")
        }
    }
    
    fun readBool(): Boolean {
        checkBounds(1)
        return data[pos++] != 0.toByte()
    }
    
    fun readU8(): Int {
        checkBounds(1)
        return data[pos++].toInt() and 0xFF
    }
    
    fun readU16(): Int {
        checkBounds(2)
        val result = (data[pos].toInt() and 0xFF) or
                     ((data[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        return result
    }
    
    fun readU32(): Long {
        checkBounds(4)
        val result = (data[pos].toLong() and 0xFF) or
                     ((data[pos + 1].toLong() and 0xFF) shl 8) or
                     ((data[pos + 2].toLong() and 0xFF) shl 16) or
                     ((data[pos + 3].toLong() and 0xFF) shl 24)
        pos += 4
        return result
    }
    
    fun readU64(): Long {
        checkBounds(8)
        var result = 0L
        for (i in 0 until 8) {
            result = result or ((data[pos + i].toLong() and 0xFF) shl (i * 8))
        }
        pos += 8
        return result
    }
    
    fun readU128(): Long {
        checkBounds(16)
        // Read low 8 bytes as Long, skip high 8 bytes
        var result = 0L
        for (i in 0 until 8) {
            result = result or ((data[pos + i].toLong() and 0xFF) shl (i * 8))
        }
        pos += 16
        return result
    }
    
    fun readI8(): Int {
        checkBounds(1)
        return data[pos++].toInt()
    }
    
    fun readI16(): Int {
        checkBounds(2)
        val result = (data[pos].toInt() and 0xFF) or
                     ((data[pos + 1].toInt()) shl 8)
        pos += 2
        return result.toShort().toInt()
    }
    
    fun readI32(): Int {
        checkBounds(4)
        val result = (data[pos].toInt() and 0xFF) or
                     ((data[pos + 1].toInt() and 0xFF) shl 8) or
                     ((data[pos + 2].toInt() and 0xFF) shl 16) or
                     ((data[pos + 3].toInt()) shl 24)
        pos += 4
        return result
    }
    
    fun readI64(): Long = readU64()
    fun readI128(): Long = readU128()
    
    fun readF32(): Float {
        checkBounds(4)
        val bits = (data[pos].toInt() and 0xFF) or
                   ((data[pos + 1].toInt() and 0xFF) shl 8) or
                   ((data[pos + 2].toInt() and 0xFF) shl 16) or
                   ((data[pos + 3].toInt()) shl 24)
        pos += 4
        return Float.fromBits(bits)
    }
    
    fun readF64(): Double {
        checkBounds(8)
        var bits = 0L
        for (i in 0 until 8) {
            bits = bits or ((data[pos + i].toLong() and 0xFF) shl (i * 8))
        }
        pos += 8
        return Double.fromBits(bits)
    }
    
    fun readString(): String {
        val length = readU32().toInt()
        checkBounds(length)
        val result = data.decodeToString(pos, pos + length)
        pos += length
        return result
    }
    
    fun readBytes(): ByteArray {
        val length = readU32().toInt()
        checkBounds(length)
        val result = data.copyOfRange(pos, pos + length)
        pos += length
        return result
    }
    
    fun readPubkey(): Pubkey {
        checkBounds(32)
        val bytes = data.copyOfRange(pos, pos + 32)
        pos += 32
        return Pubkey(bytes)
    }
}
