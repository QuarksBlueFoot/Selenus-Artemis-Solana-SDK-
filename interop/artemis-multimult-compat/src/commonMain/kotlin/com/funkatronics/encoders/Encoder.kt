package com.funkatronics.encoders

interface Encoder {
    fun encode(input: ByteArray): ByteArray
    fun encodeToString(input: ByteArray): String
}

interface Decoder {
    fun decode(input: String): ByteArray
    fun decodeToString(input: String): String
}