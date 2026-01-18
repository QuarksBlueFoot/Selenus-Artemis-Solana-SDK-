package selenus.artemis.token2022

/**
 * SPL Token-2022 TLV entries are encoded as:
 * - type:   u16 (little-endian)
 * - length: u16 (little-endian)
 * - value:  [length] bytes
 *
 * Parsing behavior matches the upstream reference logic:
 * - Stop when we hit ExtensionType.Uninitialized (0)
 * - Stop when there aren't enough bytes left to read the next type
 */
data class TlvEntry(
  val type: UShort,
  val length: Int,
  val value: ByteArray,
  val offset: Int,
)

object Token2022Tlv {
  private const val HEADER_LEN = 4

  fun decode(tlvData: ByteArray): List<TlvEntry> {
    val out = ArrayList<TlvEntry>(8)
    var i = 0
    while (i < tlvData.size) {
      // If we can't read the next type, we're done.
      if (tlvData.size - i < 2) break
      if (tlvData.size - i < HEADER_LEN) {
        throw IllegalArgumentException("Malformed TLV: truncated header at offset=$i")
      }

      val type = readU16LE(tlvData, i)
      if (type == 0) break // ExtensionType.Uninitialized

      val len = readU16LE(tlvData, i + 2)
      val valueStart = i + HEADER_LEN
      val valueEnd = valueStart + len
      if (valueEnd > tlvData.size) {
        throw IllegalArgumentException(
          "Malformed TLV: value overruns buffer at offset=$i (len=$len, size=${tlvData.size})"
        )
      }
      out += TlvEntry(
        type = type.toUShort(),
        length = len,
        value = tlvData.copyOfRange(valueStart, valueEnd),
        offset = i,
      )
      i = valueEnd
    }
    return out
  }

  fun readU16LE(b: ByteArray, off: Int): Int {
    return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
  }
}
