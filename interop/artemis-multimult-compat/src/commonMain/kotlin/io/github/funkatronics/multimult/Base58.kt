package io.github.funkatronics.multimult

import com.funkatronics.encoders.Decoder
import com.funkatronics.encoders.Encoder

typealias MultimultEncoder = Encoder
typealias MultimultDecoder = Decoder

object Base58 : Encoder, Decoder {
    override fun encode(input: ByteArray): ByteArray = com.funkatronics.encoders.Base58.encode(input)
    override fun encodeToString(input: ByteArray): String = com.funkatronics.encoders.Base58.encodeToString(input)
    override fun decode(input: String): ByteArray = com.funkatronics.encoders.Base58.decode(input)
    override fun decodeToString(input: String): String = com.funkatronics.encoders.Base58.decodeToString(input)
}