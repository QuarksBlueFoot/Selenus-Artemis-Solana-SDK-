package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import java.io.ByteArrayOutputStream

class VersionedTransaction(
    val message: VersionedMessage,
    val signatures: MutableList<ByteArray> = ArrayList()
) {
    init {
        // Initialize signatures with empty arrays if not provided
        if (signatures.isEmpty()) {
            for (i in 0 until message.header.numRequiredSignatures) {
                signatures.add(ByteArray(64))
            }
        }
    }

    fun sign(signers: List<Signer>) {
        val serializedMessage = message.serialize()
        // The first numRequiredSignatures accounts are the signers
        val signerPubkeys = message.accountKeys.subList(0, message.header.numRequiredSignatures)
        
        for (signer in signers) {
            val index = signerPubkeys.indexOf(signer.publicKey)
            if (index != -1) {
                signatures[index] = signer.sign(serializedMessage)
            }
        }
    }

    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ShortVec.encodeLen(signatures.size))
        for (sig in signatures) out.write(sig)
        out.write(message.serialize())
        return out.toByteArray()
    }
}
