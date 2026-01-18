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
}
