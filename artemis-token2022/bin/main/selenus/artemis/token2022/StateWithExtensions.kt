package selenus.artemis.token2022

/**
 * Minimal Kotlin mirror of SPL Token-2022 `StateWithExtensions` layout rules.
 *
 * Key rule: TLV data begins only after 165 bytes (Account::LEN). For mints (82 bytes),
 * the region between mint base bytes and byte 165 must be all zero padding.
 * Immediately after byte 165 is a 1-byte AccountType, then TLV entries.
 */
object Token2022StateLayout {
  /** SPL Token account base length (Account::LEN). */
  const val BASE_ACCOUNT_LENGTH: Int = 165

  /** SPL Token mint base length (Mint::LEN). */
  const val MINT_BASE_LENGTH: Int = 82

  /** SPL Token holding account base length (Account::LEN). */
  const val ACCOUNT_BASE_LENGTH: Int = 165

  /** Size of AccountType field in the extension region. */
  private const val ACCOUNT_TYPE_LEN: Int = 1

  enum class AccountType(val value: Int) {
    Uninitialized(0),
    Mint(1),
    Account(2);

    companion object {
      fun fromByte(b: Int): AccountType = when (b) {
        0 -> Uninitialized
        1 -> Mint
        2 -> Account
        else -> throw IllegalArgumentException("Unknown Token-2022 AccountType=$b")
      }
    }
  }

  data class ExtensionsSlice(
    val accountType: AccountType,
    /** Raw TLV bytes (starts with u16 type). */
    val tlvData: ByteArray,
  )

  /**
   * Extract account type + TLV bytes from raw account data.
   *
   * @param baseLen 82 for mint, 165 for token account.
   * @return null if there are no extension bytes (data <= baseLen).
   */
  fun extractExtensions(accountData: ByteArray, baseLen: Int): ExtensionsSlice? {
    if (accountData.size <= baseLen) return null

    val rest = accountData.copyOfRange(baseLen, accountData.size)
    if (rest.isEmpty()) return null

    val accountTypeIndex = (BASE_ACCOUNT_LENGTH - baseLen).coerceAtLeast(0)
    val tlvStartIndex = accountTypeIndex + ACCOUNT_TYPE_LEN

    if (rest.size < tlvStartIndex) {
      throw IllegalArgumentException(
        "Invalid Token-2022 data: rest too small for accountType. baseLen=$baseLen rest=${rest.size}"
      )
    }

    // Padding between base bytes and Account::LEN must be all 0.
    for (i in 0 until accountTypeIndex) {
      if (rest[i].toInt() != 0) {
        throw IllegalArgumentException("Invalid Token-2022 padding: non-zero at rest[$i]")
      }
    }

    val accountTypeByte = rest[accountTypeIndex].toInt() and 0xFF
    val tlvData = rest.copyOfRange(tlvStartIndex, rest.size)
    return ExtensionsSlice(
      accountType = AccountType.fromByte(accountTypeByte),
      tlvData = tlvData,
    )
  }
}
