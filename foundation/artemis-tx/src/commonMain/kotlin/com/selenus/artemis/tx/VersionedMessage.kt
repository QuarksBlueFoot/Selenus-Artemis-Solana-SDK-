package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Pubkey

data class MessageAddressTableLookup(
    val accountKey: Pubkey,
    val writableIndexes: ByteArray,
    val readonlyIndexes: ByteArray
) {
    fun serialize(): ByteArray {
        val out = ByteArrayBuilder(
            initialCapacity = 32 + ShortVec.MAX_BYTES + writableIndexes.size + ShortVec.MAX_BYTES + readonlyIndexes.size
        )
        serializeInto(out)
        return out.toByteArray()
    }

    internal fun serializeInto(out: ByteArrayBuilder) {
        out.write(accountKey.bytes)
        out.writeShortVec(writableIndexes.size)
        out.write(writableIndexes)
        out.writeShortVec(readonlyIndexes.size)
        out.write(readonlyIndexes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageAddressTableLookup) return false

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
        val out = ByteArrayBuilder(initialCapacity = estimateSize())
        // Version 0 prefix (0x80)
        out.write(0x80)

        out.write(header.numRequiredSignatures)
        out.write(header.numReadonlySigned)
        out.write(header.numReadonlyUnsigned)

        out.writeShortVec(accountKeys.size)
        for (k in accountKeys) out.write(k.bytes)

        out.write(com.selenus.artemis.runtime.Base58.decode(recentBlockhash))

        out.writeShortVec(instructions.size)
        for (ix in instructions) ix.serializeInto(out)

        out.writeShortVec(addressTableLookups.size)
        for (lookup in addressTableLookups) lookup.serializeInto(out)

        return out.toByteArray()
    }

    private fun estimateSize(): Int {
        var bytes = 1 + 3 // version prefix + header
        bytes += ShortVec.MAX_BYTES + accountKeys.size * 32
        bytes += 32 // recent blockhash
        bytes += ShortVec.MAX_BYTES
        for (ix in instructions) {
            bytes += 1 + ShortVec.MAX_BYTES + ix.accountIndexes.size + ShortVec.MAX_BYTES + ix.data.size
        }
        bytes += ShortVec.MAX_BYTES
        for (lookup in addressTableLookups) {
            bytes += 32 + ShortVec.MAX_BYTES + lookup.writableIndexes.size +
                ShortVec.MAX_BYTES + lookup.readonlyIndexes.size
        }
        return bytes
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
                bytes.copyInto(accountIndexes, 0, offset, offset + numAccounts)
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
