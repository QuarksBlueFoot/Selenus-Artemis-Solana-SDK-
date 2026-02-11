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

    /**
     * Add an externally-produced signature for a specific public key.
     *
     * Matches sol4k VersionedTransaction.addSignature() for parity.
     *
     * @param publicKey The signer's public key
     * @param signature The 64-byte Ed25519 signature
     */
    fun addSignature(publicKey: Pubkey, signature: ByteArray) {
        require(signature.size == 64) { "Signature must be 64 bytes" }
        val signerPubkeys = message.accountKeys.subList(0, message.header.numRequiredSignatures)
        val index = signerPubkeys.indexOf(publicKey)
        require(index != -1) { "Public key not found in transaction signers" }
        signatures[index] = signature
    }

    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ShortVec.encodeLen(signatures.size))
        for (sig in signatures) out.write(sig)
        out.write(message.serialize())
        return out.toByteArray()
    }

    fun toBase64(): String = java.util.Base64.getEncoder().encodeToString(serialize())

    companion object {
        /**
         * Deserialize a versioned transaction from wire bytes.
         *
         * @param bytes The serialized transaction bytes
         * @return The deserialized VersionedTransaction
         */
        fun from(bytes: ByteArray): VersionedTransaction {
            var offset = 0

            // Decode number of signatures
            val (numSigs, sigLenBytes) = ShortVec.decodeLen(bytes)
            offset += sigLenBytes

            val sigs = ArrayList<ByteArray>(numSigs)
            for (i in 0 until numSigs) {
                sigs.add(bytes.copyOfRange(offset, offset + 64))
                offset += 64
            }

            // Remaining bytes are the message
            val messageBytes = bytes.copyOfRange(offset, bytes.size)
            val message = VersionedMessage.deserialize(messageBytes)

            return VersionedTransaction(message, sigs)
        }

        /**
         * Deserialize from a Base64-encoded string.
         */
        fun fromBase64(base64: String): VersionedTransaction {
            val bytes = java.util.Base64.getDecoder().decode(base64)
            return from(bytes)
        }
    }
}
