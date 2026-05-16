package com.funkatronics.encoders

import com.funkatronics.encoders.error.InvalidInputException
import com.selenus.artemis.runtime.Base58 as ArtemisBase58

object Base58 : Encoder, Decoder {
    override fun encode(input: ByteArray): ByteArray = Base58BtcEncoder.encode(input)
    override fun encodeToString(input: ByteArray): String = Base58BtcEncoder.encodeToString(input)
    override fun decode(input: String): ByteArray = Base58BtcDecoder.decode(input)
    override fun decodeToString(input: String): String = Base58BtcDecoder.decodeToString(input)
}

object Base58BtcEncoder : Encoder {
    const val ALPHABET = ArtemisBase58.ALPHABET

    override fun encode(input: ByteArray): ByteArray = encodeToString(input).encodeToByteArray()
    override fun encodeToString(input: ByteArray): String = ArtemisBase58.encode(input)
}

object Base58BtcDecoder : Decoder {
    override fun decode(input: String): ByteArray {
        val invalidPosition = input.indexOfFirst { !ArtemisBase58.isValidChar(it) }
        if (invalidPosition >= 0) {
            throw InvalidInputException.InvalidCharacter(input[invalidPosition], invalidPosition)
        }
        return ArtemisBase58.decode(input)
    }

    override fun decodeToString(input: String): String = decode(input).decodeToString()
}