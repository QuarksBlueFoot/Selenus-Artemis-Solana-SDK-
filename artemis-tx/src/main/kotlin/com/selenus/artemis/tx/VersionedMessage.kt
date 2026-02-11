package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Pubkey
import java.io.ByteArrayOutputStream

data class MessageAddressTableLookup(
    val accountKey: Pubkey,
    val writableIndexes: ByteArray,
    val readonlyIndexes: ByteArray
) {
    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(accountKey.bytes)
        out.write(ShortVec.encodeLen(writableIndexes.size))
        out.write(writableIndexes)
        out.write(ShortVec.encodeLen(readonlyIndexes.size))
        out.write(readonlyIndexes)
        return out.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageAddressTableLookup

        if (accountKey != other.accountKey) return false
        if (!writableIndexes.contentEquals(other.writableIndexes)) return false
        if (!readonlyIndexes.contentEquals(other.readonlyIndexes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = accountKey.hashCode()
        result = 31 * result + writableIndexes.contentHashCode()
        result = 31 * result + readonlyIndexes.contentHashCode()
        return result
    }
}

data class VersionedMessage(
    val header: MessageHeader,
    val accountKeys: List<Pubkey>,
    val recentBlockhash: String,
    val instructions: List<CompiledInstruction>,
    val addressTableLookups: List<MessageAddressTableLookup>
) {
    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        // Version 0 prefix (0x80)
        out.write(0x80)
        
        out.write(byteArrayOf(
            header.numRequiredSignatures.toByte(),
            header.numReadonlySigned.toByte(),
            header.numReadonlyUnsigned.toByte()
        ))
        
        out.write(ShortVec.encodeLen(accountKeys.size))
        for (k in accountKeys) out.write(k.bytes)
        
        out.write(com.selenus.artemis.runtime.Base58.decode(recentBlockhash))
        
        out.write(ShortVec.encodeLen(instructions.size))
        for (ix in instructions) out.write(ix.serialize())
        
        out.write(ShortVec.encodeLen(addressTableLookups.size))
        for (lookup in addressTableLookups) out.write(lookup.serialize())
        
        return out.toByteArray()
    }

    companion object {
        /**
         * Deserialize a VersionedMessage from wire bytes.
         *
         * Expects the first byte to be 0x80 (version 0 prefix).
         */
        fun deserialize(bytes: ByteArray): VersionedMessage {
            var offset = 0

            // Version prefix
            val prefix = bytes[offset].toInt() and 0xFF
            require(prefix and 0x80 != 0) { "Not a versioned message (prefix=0x${prefix.toString(16)})" }
            offset++

            // Header
            val numRequiredSignatures = bytes[offset++].toInt() and 0xFF
            val numReadonlySigned = bytes[offset++].toInt() and 0xFF
            val numReadonlyUnsigned = bytes[offset++].toInt() and 0xFF
            val header = MessageHeader(numRequiredSignatures, numReadonlySigned, numReadonlyUnsigned)

            // Account keys
            val (numKeys, keyLenBytes) = ShortVec.decodeLen(bytes, offset)
            offset += keyLenBytes
            val accountKeys = ArrayList<Pubkey>(numKeys)
            for (i in 0 until numKeys) {
                accountKeys.add(Pubkey(bytes.copyOfRange(offset, offset + 32)))
                offset += 32
            }

            // Recent blockhash (32 bytes)
            val blockhashBytes = bytes.copyOfRange(offset, offset + 32)
            val recentBlockhash = com.selenus.artemis.runtime.Base58.encode(blockhashBytes)
            offset += 32

            // Instructions
            val (numIx, ixLenBytes) = ShortVec.decodeLen(bytes, offset)
            offset += ixLenBytes
            val instructions = ArrayList<CompiledInstruction>(numIx)
            for (i in 0 until numIx) {
                val programIdIndex = bytes[offset++].toInt() and 0xFF
                val (numAccounts, accLenBytes) = ShortVec.decodeLen(bytes, offset)
                offset += accLenBytes
                val accountIndexes = ByteArray(numAccounts)
                System.arraycopy(bytes, offset, accountIndexes, 0, numAccounts)
                offset += numAccounts
                val (dataLen, dataLenBytes) = ShortVec.decodeLen(bytes, offset)
                offset += dataLenBytes
                val data = bytes.copyOfRange(offset, offset + dataLen)
                offset += dataLen
                instructions.add(CompiledInstruction(programIdIndex, accountIndexes, data))
            }

            // Address table lookups
            val (numLookups, lookupLenBytes) = ShortVec.decodeLen(bytes, offset)
            offset += lookupLenBytes
            val lookups = ArrayList<MessageAddressTableLookup>(numLookups)
            for (i in 0 until numLookups) {
                val accountKey = Pubkey(bytes.copyOfRange(offset, offset + 32))
                offset += 32
                val (numWritable, wLenBytes) = ShortVec.decodeLen(bytes, offset)
                offset += wLenBytes
                val writableIndexes = bytes.copyOfRange(offset, offset + numWritable)
                offset += numWritable
                val (numReadonly, rLenBytes) = ShortVec.decodeLen(bytes, offset)
                offset += rLenBytes
                val readonlyIndexes = bytes.copyOfRange(offset, offset + numReadonly)
                offset += numReadonly
                lookups.add(MessageAddressTableLookup(accountKey, writableIndexes, readonlyIndexes))
            }

            return VersionedMessage(header, accountKeys, recentBlockhash, instructions, lookups)
        }
    }
}
