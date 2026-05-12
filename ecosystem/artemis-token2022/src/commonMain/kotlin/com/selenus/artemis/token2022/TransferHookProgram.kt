package com.selenus.artemis.token2022

import com.selenus.artemis.runtime.Pda
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.ByteArrayBuilder
import com.selenus.artemis.tx.Instruction

/**
 * Builders for the SPL transfer-hook program interface.
 *
 * These instructions target a hook program, not the Token-2022 program itself.
 * Token-2022 calls the hook program during checked transfers, and clients use
 * the validation PDA to discover the extra accounts required by that hook.
 */
object TransferHookProgram {
    const val NAMESPACE: String = "spl-transfer-hook-interface"
    const val EXTRA_ACCOUNT_METAS_SEED: String = "extra-account-metas"

    private val SYSTEM_PROGRAM = Pubkey.fromBase58("11111111111111111111111111111111")
    private val EXECUTE_DISCRIMINATOR = byteArrayOf(0x69, 0x25, 0x65, 0xC5.toByte(), 0x4B, 0xFB.toByte(), 0x66, 0x1A)
    private val INITIALIZE_EXTRA_ACCOUNT_METAS_DISCRIMINATOR = byteArrayOf(0x2B, 0x22, 0x0D, 0x31, 0xA7.toByte(), 0x58, 0xEB.toByte(), 0xEB.toByte())
    private val UPDATE_EXTRA_ACCOUNT_METAS_DISCRIMINATOR = byteArrayOf(0x9D.toByte(), 0x69, 0x2A, 0x92.toByte(), 0x66, 0x55, 0xF1.toByte(), 0xAE.toByte())

    data class ValidationAccountAddress(val address: Pubkey, val bump: Int)

    sealed class Seed {
        abstract fun packedLength(): Int
        internal abstract fun writeInto(destination: ByteArray, offset: Int): Int

        class Literal(bytes: ByteArray) : Seed() {
            val bytes: ByteArray = bytes.copyOf()
            override fun packedLength(): Int = 2 + bytes.size
            override fun writeInto(destination: ByteArray, offset: Int): Int {
                require(bytes.size <= 30) { "literal seed must fit in the 32-byte seed config" }
                destination[offset] = 1
                destination[offset + 1] = bytes.size.toByte()
                bytes.copyInto(destination, offset + 2)
                return packedLength()
            }
        }

        data class InstructionData(val index: Int, val length: Int) : Seed() {
            override fun packedLength(): Int = 3
            override fun writeInto(destination: ByteArray, offset: Int): Int {
                destination[offset] = 2
                destination[offset + 1] = checkedU8(index, "instruction-data index")
                destination[offset + 2] = checkedU8(length, "instruction-data length")
                return packedLength()
            }
        }

        data class AccountKey(val index: Int) : Seed() {
            override fun packedLength(): Int = 2
            override fun writeInto(destination: ByteArray, offset: Int): Int {
                destination[offset] = 3
                destination[offset + 1] = checkedU8(index, "account-key index")
                return packedLength()
            }
        }

        data class AccountData(val accountIndex: Int, val dataIndex: Int, val length: Int) : Seed() {
            override fun packedLength(): Int = 4
            override fun writeInto(destination: ByteArray, offset: Int): Int {
                destination[offset] = 4
                destination[offset + 1] = checkedU8(accountIndex, "account-data account index")
                destination[offset + 2] = checkedU8(dataIndex, "account-data data index")
                destination[offset + 3] = checkedU8(length, "account-data length")
                return packedLength()
            }
        }
    }

    sealed class PubkeyDataSource {
        abstract fun packedLength(): Int
        internal abstract fun writeInto(destination: ByteArray, offset: Int): Int

        data class InstructionData(val index: Int) : PubkeyDataSource() {
            override fun packedLength(): Int = 2
            override fun writeInto(destination: ByteArray, offset: Int): Int {
                destination[offset] = 1
                destination[offset + 1] = checkedU8(index, "pubkey instruction-data index")
                return packedLength()
            }
        }

        data class AccountData(val accountIndex: Int, val dataIndex: Int) : PubkeyDataSource() {
            override fun packedLength(): Int = 3
            override fun writeInto(destination: ByteArray, offset: Int): Int {
                destination[offset] = 2
                destination[offset + 1] = checkedU8(accountIndex, "pubkey account-data account index")
                destination[offset + 2] = checkedU8(dataIndex, "pubkey account-data data index")
                return packedLength()
            }
        }
    }

    data class ExtraAccountMeta(
        val discriminator: Int,
        val addressConfig: ByteArray,
        val isSigner: Boolean,
        val isWritable: Boolean
    ) {
        init {
            require(discriminator in 0..255) { "discriminator must fit in u8" }
            require(addressConfig.size == EXTRA_ACCOUNT_META_ADDRESS_CONFIG_SIZE) {
                "addressConfig must be $EXTRA_ACCOUNT_META_ADDRESS_CONFIG_SIZE bytes"
            }
        }

        fun toBytes(): ByteArray {
            val out = ByteArray(EXTRA_ACCOUNT_META_SIZE)
            out[0] = discriminator.toByte()
            addressConfig.copyInto(out, destinationOffset = 1)
            out[33] = if (isSigner) 1 else 0
            out[34] = if (isWritable) 1 else 0
            return out
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ExtraAccountMeta) return false
            return discriminator == other.discriminator &&
                addressConfig.contentEquals(other.addressConfig) &&
                isSigner == other.isSigner &&
                isWritable == other.isWritable
        }

        override fun hashCode(): Int =
            31 * (31 * discriminator + addressConfig.contentHashCode()) +
                31 * isSigner.hashCode() + isWritable.hashCode()

        companion object {
            fun static(pubkey: Pubkey, isSigner: Boolean = false, isWritable: Boolean = false): ExtraAccountMeta =
                ExtraAccountMeta(0, pubkey.bytes.copyOf(), isSigner, isWritable)

            fun pda(seeds: List<Seed>, isSigner: Boolean = false, isWritable: Boolean = false): ExtraAccountMeta =
                ExtraAccountMeta(1, packSeeds(seeds), isSigner, isWritable)

            fun pubkeyData(
                source: PubkeyDataSource,
                isSigner: Boolean = false,
                isWritable: Boolean = false
            ): ExtraAccountMeta = ExtraAccountMeta(2, packPubkeyDataSource(source), isSigner, isWritable)

            fun externalPda(
                programIndex: Int,
                seeds: List<Seed>,
                isSigner: Boolean = false,
                isWritable: Boolean = false
            ): ExtraAccountMeta {
                require(programIndex in 0..127) { "external PDA program index must fit in 7 bits" }
                return ExtraAccountMeta(0x80 + programIndex, packSeeds(seeds), isSigner, isWritable)
            }
        }
    }

    class ExtraAccountMetaView internal constructor(
        private val source: ByteArray,
        private val offset: Int
    ) {
        val discriminator: Int get() = source[offset].toInt() and 0xFF
        val isSigner: Boolean get() = source[offset + 33].toInt() != 0
        val isWritable: Boolean get() = source[offset + 34].toInt() != 0

        fun addressConfigByteAt(index: Int): Byte {
            require(index in 0 until EXTRA_ACCOUNT_META_ADDRESS_CONFIG_SIZE) { "address config index out of range: $index" }
            return source[offset + 1 + index]
        }

        fun copyAddressConfig(): ByteArray = source.copyOfRange(offset + 1, offset + 33)

        fun toOwnedMeta(): ExtraAccountMeta = ExtraAccountMeta(
            discriminator = discriminator,
            addressConfig = copyAddressConfig(),
            isSigner = isSigner,
            isWritable = isWritable
        )
    }

    class ExtraAccountMetaListView internal constructor(
        private val source: ByteArray,
        private val listOffset: Int,
        val count: Int
    ) {
        operator fun get(index: Int): ExtraAccountMetaView {
            require(index in 0 until count) { "extra account meta index out of range: $index" }
            return ExtraAccountMetaView(source, listOffset + 4 + index * EXTRA_ACCOUNT_META_SIZE)
        }

        fun toOwnedList(): List<ExtraAccountMeta> = List(count) { get(it).toOwnedMeta() }
    }

    fun extraAccountMetasAddress(mint: Pubkey, hookProgramId: Pubkey): Pubkey =
        extraAccountMetasAddressWithBump(mint, hookProgramId).address

    fun extraAccountMetasAddressWithBump(mint: Pubkey, hookProgramId: Pubkey): ValidationAccountAddress {
        val result = Pda.findProgramAddress(
            seeds = listOf(EXTRA_ACCOUNT_METAS_SEED.encodeToByteArray(), mint.bytes),
            programId = hookProgramId
        )
        return ValidationAccountAddress(result.address, result.bump)
    }

    fun execute(
        hookProgramId: Pubkey,
        source: Pubkey,
        mint: Pubkey,
        destination: Pubkey,
        authority: Pubkey,
        amount: Long,
        validationAccount: Pubkey = extraAccountMetasAddress(mint, hookProgramId)
    ): Instruction = Instruction(
        programId = hookProgramId,
        accounts = baseExecuteAccounts(source, mint, destination, authority) +
            AccountMeta(validationAccount, isSigner = false, isWritable = false),
        data = executeData(amount)
    )

    fun executeWithExtraAccountMetas(
        hookProgramId: Pubkey,
        source: Pubkey,
        mint: Pubkey,
        destination: Pubkey,
        authority: Pubkey,
        amount: Long,
        validationAccount: Pubkey = extraAccountMetasAddress(mint, hookProgramId),
        additionalAccounts: List<AccountMeta> = emptyList()
    ): Instruction = Instruction(
        programId = hookProgramId,
        accounts = baseExecuteAccounts(source, mint, destination, authority) +
            AccountMeta(validationAccount, isSigner = false, isWritable = false) + additionalAccounts,
        data = executeData(amount)
    )

    fun extraAccountsForTransfer(
        hookProgramId: Pubkey,
        mint: Pubkey,
        resolvedExtraAccounts: List<AccountMeta> = emptyList(),
        validationAccount: Pubkey = extraAccountMetasAddress(mint, hookProgramId)
    ): List<AccountMeta> = resolvedExtraAccounts + listOf(
        AccountMeta(hookProgramId, isSigner = false, isWritable = false),
        AccountMeta(validationAccount, isSigner = false, isWritable = false)
    )

    fun initializeExtraAccountMetaList(
        hookProgramId: Pubkey,
        mint: Pubkey,
        authority: Pubkey,
        extraAccountMetas: List<ExtraAccountMeta>,
        validationAccount: Pubkey = extraAccountMetasAddress(mint, hookProgramId)
    ): Instruction = Instruction(
        programId = hookProgramId,
        accounts = listOf(
            AccountMeta(validationAccount, isSigner = false, isWritable = true),
            AccountMeta(mint, isSigner = false, isWritable = false),
            AccountMeta(authority, isSigner = true, isWritable = false),
            AccountMeta(SYSTEM_PROGRAM, isSigner = false, isWritable = false)
        ),
        data = discriminatorListData(INITIALIZE_EXTRA_ACCOUNT_METAS_DISCRIMINATOR, extraAccountMetas)
    )

    fun updateExtraAccountMetaList(
        hookProgramId: Pubkey,
        mint: Pubkey,
        authority: Pubkey,
        extraAccountMetas: List<ExtraAccountMeta>,
        validationAccount: Pubkey = extraAccountMetasAddress(mint, hookProgramId)
    ): Instruction = Instruction(
        programId = hookProgramId,
        accounts = listOf(
            AccountMeta(validationAccount, isSigner = false, isWritable = true),
            AccountMeta(mint, isSigner = false, isWritable = false),
            AccountMeta(authority, isSigner = true, isWritable = false)
        ),
        data = discriminatorListData(UPDATE_EXTRA_ACCOUNT_METAS_DISCRIMINATOR, extraAccountMetas)
    )

    fun decodeExtraAccountMetaListView(source: ByteArray, startOffset: Int = 0, length: Int = source.size - startOffset): ExtraAccountMetaListView {
        require(startOffset >= 0 && length >= 4 && startOffset + length <= source.size) {
            "extra account meta list range out of bounds"
        }
        val count = readU32LE(source, startOffset)
        val expectedLength = 4 + count * EXTRA_ACCOUNT_META_SIZE
        require(expectedLength <= length) { "extra account meta list is truncated" }
        return ExtraAccountMetaListView(source, startOffset, count)
    }

    fun packExtraAccountMetaList(extraAccountMetas: List<ExtraAccountMeta>): ByteArray =
        listData(extraAccountMetas)

    private fun baseExecuteAccounts(source: Pubkey, mint: Pubkey, destination: Pubkey, authority: Pubkey): List<AccountMeta> =
        listOf(
            AccountMeta(source, isSigner = false, isWritable = false),
            AccountMeta(mint, isSigner = false, isWritable = false),
            AccountMeta(destination, isSigner = false, isWritable = false),
            AccountMeta(authority, isSigner = false, isWritable = false)
        )

    private fun executeData(amount: Long): ByteArray = ByteArrayBuilder(16)
        .write(EXECUTE_DISCRIMINATOR)
        .putLongLE(amount)
        .toByteArray()

    private fun discriminatorListData(discriminator: ByteArray, extraAccountMetas: List<ExtraAccountMeta>): ByteArray =
        ByteArrayBuilder(discriminator.size + 4 + extraAccountMetas.size * EXTRA_ACCOUNT_META_SIZE)
            .write(discriminator)
            .write(listData(extraAccountMetas))
            .toByteArray()

    private fun listData(extraAccountMetas: List<ExtraAccountMeta>): ByteArray {
        val builder = ByteArrayBuilder(4 + extraAccountMetas.size * EXTRA_ACCOUNT_META_SIZE)
        builder.putIntLE(extraAccountMetas.size)
        extraAccountMetas.forEach { builder.write(it.toBytes()) }
        return builder.toByteArray()
    }

    private fun packSeeds(seeds: List<Seed>): ByteArray {
        val config = ByteArray(EXTRA_ACCOUNT_META_ADDRESS_CONFIG_SIZE)
        var offset = 0
        for (seed in seeds) {
            val seedLength = seed.packedLength()
            require(offset + seedLength <= config.size) { "seed configuration exceeds 32 bytes" }
            offset += seed.writeInto(config, offset)
        }
        return config
    }

    private fun packPubkeyDataSource(source: PubkeyDataSource): ByteArray {
        val config = ByteArray(EXTRA_ACCOUNT_META_ADDRESS_CONFIG_SIZE)
        source.writeInto(config, 0)
        return config
    }

    private fun readU32LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun checkedU8(value: Int, label: String): Byte {
        require(value in 0..255) { "$label must fit in u8" }
        return value.toByte()
    }

    private const val EXTRA_ACCOUNT_META_ADDRESS_CONFIG_SIZE = 32
    private const val EXTRA_ACCOUNT_META_SIZE = 35
}