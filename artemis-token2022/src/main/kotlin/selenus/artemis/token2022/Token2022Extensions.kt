package selenus.artemis.token2022

/** Convenience helpers for decoding SPL Token-2022 extensions. */
object Token2022Extensions {
  data class Decoded(
    val accountType: Token2022StateLayout.AccountType,
    val entries: List<TlvEntry>,
  )

  fun decode(accountData: ByteArray, baseLen: Int): Decoded? {
    val ext = Token2022StateLayout.extractExtensions(accountData, baseLen) ?: return null
    val entries = Token2022Tlv.decode(ext.tlvData)
    return Decoded(ext.accountType, entries)
  }
}
