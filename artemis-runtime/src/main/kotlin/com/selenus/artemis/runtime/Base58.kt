package com.selenus.artemis.runtime

object Base58 {
  private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
  private val INDEXES = IntArray(128) { -1 }.also { arr ->
    for (i in ALPHABET.indices) arr[ALPHABET[i].code] = i
  }

  fun encode(input: ByteArray): String {
    if (input.isEmpty()) return ""
    var zeros = 0
    while (zeros < input.size && input[zeros].toInt() == 0) zeros++

    val encoded = CharArray(input.size * 2)
    var outLen = 0
    val copy = input.copyOf()
    var startAt = zeros
    while (startAt < copy.size) {
      var mod = 0
      for (i in startAt until copy.size) {
        val num = (copy[i].toInt() and 0xFF)
        val temp = mod * 256 + num
        copy[i] = (temp / 58).toByte()
        mod = temp % 58
      }
      encoded[outLen++] = ALPHABET[mod]
      while (startAt < copy.size && copy[startAt].toInt() == 0) startAt++
    }
    for (i in 0 until zeros) encoded[outLen++] = ALPHABET[0]
    return encoded.copyOfRange(0, outLen).reversed().joinToString("")
  }

  fun decode(input: String): ByteArray {
    if (input.isEmpty()) return ByteArray(0)
    val input58 = ByteArray(input.length)
    for (i in input.indices) {
      val c = input[i].code
      require(c < 128) { "Non-ASCII Base58 character" }
      val digit = INDEXES[c]
      require(digit >= 0) { "Invalid Base58 character: ${input[i]}" }
      input58[i] = digit.toByte()
    }
    var zeros = 0
    while (zeros < input58.size && input58[zeros].toInt() == 0) zeros++

    val decoded = ByteArray(input.length)
    var outLen = 0
    var startAt = zeros
    while (startAt < input58.size) {
      var mod = 0
      for (i in startAt until input58.size) {
        val num = input58[i].toInt() and 0xFF
        val temp = mod * 58 + num
        input58[i] = (temp / 256).toByte()
        mod = temp % 256
      }
      decoded[outLen++] = mod.toByte()
      while (startAt < input58.size && input58[startAt].toInt() == 0) startAt++
    }
    val result = ByteArray(zeros + outLen)
    // leading zeros
    // decoded is little-endian in [0..outLen)
    for (i in 0 until outLen) result[zeros + i] = decoded[outLen - 1 - i]
    return result
  }
}
