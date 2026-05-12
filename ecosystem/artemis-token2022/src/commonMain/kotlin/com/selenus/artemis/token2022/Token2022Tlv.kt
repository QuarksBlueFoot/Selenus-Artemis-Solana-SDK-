package com.selenus.artemis.token2022

/**
 * Token2022Tlv - TLV decoder for SPL Token-2022 extensions.
 * 
 * SPL Token-2022 stores extension data in TLV (Type-Length-Value) format.
 * This decoder parses the raw account data to extract extension information.
 * 
 * Usage:
 * ```kotlin
 * import com.selenus.artemis.token2022.Token2022Tlv
 * 
 * val accountData = rpc.getAccountInfoBase64(mintAddress)
 * val entries = Token2022Tlv.decode(accountData)
 * 
 * entries.forEach { entry ->
 *     when (entry.type.toInt()) {
 *         1 -> println("Transfer Fee Config")
 *         2 -> println("Transfer Fee Amount")
 *         3 -> println("Mint Close Authority")
 *         // etc.
 *     }
 * }
 * ```
 */
object Token2022Tlv {
    private const val HEADER_LEN = 4
    
    /**
     * TLV entry parsed from account data.
     */
    data class TlvEntry(
        val type: UShort,
        val length: Int,
        val value: ByteArray,
        val offset: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TlvEntry) return false
            return type == other.type && offset == other.offset
        }
        
        override fun hashCode(): Int = type.hashCode() + offset
    }

    /** Zero-copy TLV entry view backed by the original account buffer. */
    class TlvEntryView internal constructor(
        val type: UShort,
        val length: Int,
        private val source: ByteArray,
        val valueOffset: Int,
        val offset: Int
    ) {
        fun valueAt(index: Int): Byte {
            require(index in 0 until length) { "value index out of range: $index" }
            return source[valueOffset + index]
        }

        fun copyValue(): ByteArray = source.copyOfRange(valueOffset, valueOffset + length)

        fun copyValueInto(destination: ByteArray, destinationOffset: Int = 0) {
            require(destinationOffset >= 0 && destinationOffset + length <= destination.size) {
                "destination too small for TLV value"
            }
            source.copyInto(destination, destinationOffset, valueOffset, valueOffset + length)
        }

        fun toOwnedEntry(): TlvEntry = TlvEntry(
            type = type,
            length = length,
            value = copyValue(),
            offset = offset
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TlvEntryView) return false
            return type == other.type && offset == other.offset
        }

        override fun hashCode(): Int = type.hashCode() + offset
    }
    
    /**
     * Extension type constants.
     */
    object ExtensionType {
        const val UNINITIALIZED: Int = 0
        const val TRANSFER_FEE_CONFIG: Int = 1
        const val TRANSFER_FEE_AMOUNT: Int = 2
        const val MINT_CLOSE_AUTHORITY: Int = 3
        const val CONFIDENTIAL_TRANSFER_MINT: Int = 4
        const val CONFIDENTIAL_TRANSFER_ACCOUNT: Int = 5
        const val DEFAULT_ACCOUNT_STATE: Int = 6
        const val IMMUTABLE_OWNER: Int = 7
        const val MEMO_TRANSFER: Int = 8
        const val NON_TRANSFERABLE: Int = 9
        const val INTEREST_BEARING_CONFIG: Int = 10
        const val CPI_GUARD: Int = 11
        const val PERMANENT_DELEGATE: Int = 12
        const val NON_TRANSFERABLE_ACCOUNT: Int = 13
        const val TRANSFER_HOOK: Int = 14
        const val TRANSFER_HOOK_ACCOUNT: Int = 15
        const val METADATA_POINTER: Int = 16
        const val TOKEN_METADATA: Int = 17
        const val GROUP_POINTER: Int = 18
        const val GROUP_MEMBER_POINTER: Int = 19
    }
    
    /**
     * Decode TLV entries from raw account data.
     * 
     * @param tlvData Raw bytes from after the base mint/account structure
     * @return List of TLV entries found
     */
    fun decode(tlvData: ByteArray): List<TlvEntry> = decodeViews(tlvData).map { it.toOwnedEntry() }

    /** Decode TLV entries without copying each value payload. */
    fun decodeViews(tlvData: ByteArray): List<TlvEntryView> = decodeViews(tlvData, 0, tlvData.size)

    fun decodeViews(source: ByteArray, startOffset: Int, length: Int): List<TlvEntryView> {
        require(startOffset >= 0 && length >= 0 && startOffset + length <= source.size) {
            "TLV range out of bounds"
        }
        val out = ArrayList<TlvEntryView>(8)
        var i = startOffset
        val endOffset = startOffset + length
        
        while (i < endOffset) {
            // Stop when there are not enough bytes for the next type tag
            if (endOffset - i < 2) break
            if (endOffset - i < HEADER_LEN) {
                throw IllegalArgumentException("Malformed TLV: truncated header at offset=${i - startOffset}")
            }
            
            val type = readU16LE(source, i)
            if (type == 0) break // ExtensionType.Uninitialized
            
            val len = readU16LE(source, i + 2)
            val valueStart = i + HEADER_LEN
            val valueEnd = valueStart + len
            
            if (valueEnd > endOffset) {
                throw IllegalArgumentException(
                    "Malformed TLV: value overruns buffer at offset=${i - startOffset} (len=$len, size=$length)"
                )
            }
            
            out += TlvEntryView(
                type = type.toUShort(),
                length = len,
                source = source,
                valueOffset = valueStart,
                offset = i - startOffset
            )
            i = valueEnd
        }
        return out
    }
    
    /**
     * Find entries of a specific type.
     */
    fun findByType(entries: List<TlvEntry>, extensionType: Int): List<TlvEntry> {
        return entries.filter { it.type.toInt() == extensionType }
    }

    fun findViewByType(entries: List<TlvEntryView>, extensionType: Int): List<TlvEntryView> {
        return entries.filter { it.type.toInt() == extensionType }
    }
    
    /**
     * Check if a specific extension is present.
     */
    fun hasExtension(entries: List<TlvEntry>, extensionType: Int): Boolean {
        return entries.any { it.type.toInt() == extensionType }
    }
    
    /**
     * Get extension type name for debugging.
     */
    fun getTypeName(type: Int): String {
        return when (type) {
            ExtensionType.UNINITIALIZED -> "Uninitialized"
            ExtensionType.TRANSFER_FEE_CONFIG -> "TransferFeeConfig"
            ExtensionType.TRANSFER_FEE_AMOUNT -> "TransferFeeAmount"
            ExtensionType.MINT_CLOSE_AUTHORITY -> "MintCloseAuthority"
            ExtensionType.CONFIDENTIAL_TRANSFER_MINT -> "ConfidentialTransferMint"
            ExtensionType.CONFIDENTIAL_TRANSFER_ACCOUNT -> "ConfidentialTransferAccount"
            ExtensionType.DEFAULT_ACCOUNT_STATE -> "DefaultAccountState"
            ExtensionType.IMMUTABLE_OWNER -> "ImmutableOwner"
            ExtensionType.MEMO_TRANSFER -> "MemoTransfer"
            ExtensionType.NON_TRANSFERABLE -> "NonTransferable"
            ExtensionType.INTEREST_BEARING_CONFIG -> "InterestBearingConfig"
            ExtensionType.CPI_GUARD -> "CpiGuard"
            ExtensionType.PERMANENT_DELEGATE -> "PermanentDelegate"
            ExtensionType.NON_TRANSFERABLE_ACCOUNT -> "NonTransferableAccount"
            ExtensionType.TRANSFER_HOOK -> "TransferHook"
            ExtensionType.TRANSFER_HOOK_ACCOUNT -> "TransferHookAccount"
            ExtensionType.METADATA_POINTER -> "MetadataPointer"
            ExtensionType.TOKEN_METADATA -> "TokenMetadata"
            ExtensionType.GROUP_POINTER -> "GroupPointer"
            ExtensionType.GROUP_MEMBER_POINTER -> "GroupMemberPointer"
            else -> "Unknown($type)"
        }
    }
    
    private fun readU16LE(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }
}
