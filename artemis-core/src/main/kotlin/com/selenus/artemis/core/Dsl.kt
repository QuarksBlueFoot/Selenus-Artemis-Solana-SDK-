package com.selenus.artemis.core

/**
 * Kotlin DSL for building account meta lists.
 * 
 * Provides a type-safe, expressive way to build instruction accounts
 * that's both readable and prevents common mistakes.
 * 
 * Example:
 * ```kotlin
 * val accounts = accounts {
 *     signerWritable(payer)                // Fee payer, writable signer
 *     writable(destinationAccount)         // Token account, writable
 *     readonly(tokenMint)                  // Mint, readonly
 *     program(TOKEN_PROGRAM_ID)            // Program ID
 *     
 *     // Conditional accounts
 *     if (hasDelegate) {
 *         readonly(delegate)
 *     }
 * }
 * ```
 */
@DslMarker
annotation class AccountDsl

/**
 * Account meta representing a single account in an instruction.
 */
data class AccountMeta(
    val pubkey: String,
    val isSigner: Boolean,
    val isWritable: Boolean
) {
    companion object {
        fun signerWritable(pubkey: String) = AccountMeta(pubkey, isSigner = true, isWritable = true)
        fun signer(pubkey: String) = AccountMeta(pubkey, isSigner = true, isWritable = false)
        fun writable(pubkey: String) = AccountMeta(pubkey, isSigner = false, isWritable = true)
        fun readonly(pubkey: String) = AccountMeta(pubkey, isSigner = false, isWritable = false)
    }
}

/**
 * Builder for account lists.
 */
@AccountDsl
class AccountListBuilder {
    private val accounts = mutableListOf<AccountMeta>()
    
    /**
     * Add a signer and writable account (typically fee payer).
     */
    fun signerWritable(pubkey: String) {
        accounts.add(AccountMeta.signerWritable(pubkey))
    }
    
    /**
     * Add a signer-only account (readonly signer).
     */
    fun signer(pubkey: String) {
        accounts.add(AccountMeta.signer(pubkey))
    }
    
    /**
     * Add a writable account (not a signer).
     */
    fun writable(pubkey: String) {
        accounts.add(AccountMeta.writable(pubkey))
    }
    
    /**
     * Add a readonly account (not a signer).
     */
    fun readonly(pubkey: String) {
        accounts.add(AccountMeta.readonly(pubkey))
    }
    
    /**
     * Add a program ID (readonly, not signer).
     */
    fun program(pubkey: String) {
        accounts.add(AccountMeta.readonly(pubkey))
    }
    
    /**
     * Add a custom account meta.
     */
    fun account(pubkey: String, isSigner: Boolean, isWritable: Boolean) {
        accounts.add(AccountMeta(pubkey, isSigner, isWritable))
    }
    
    /**
     * Add an account meta directly.
     */
    fun add(meta: AccountMeta) {
        accounts.add(meta)
    }
    
    /**
     * Add multiple account metas.
     */
    fun addAll(metas: List<AccountMeta>) {
        accounts.addAll(metas)
    }
    
    /**
     * Conditionally add an account.
     */
    inline fun conditionally(condition: Boolean, block: AccountListBuilder.() -> Unit) {
        if (condition) {
            block()
        }
    }
    
    /**
     * Add an optional account (null-safe).
     */
    fun optionalWritable(pubkey: String?) {
        if (pubkey != null) {
            writable(pubkey)
        }
    }
    
    fun optionalReadonly(pubkey: String?) {
        if (pubkey != null) {
            readonly(pubkey)
        }
    }
    
    fun optionalSigner(pubkey: String?) {
        if (pubkey != null) {
            signer(pubkey)
        }
    }
    
    fun build(): List<AccountMeta> = accounts.toList()
}

/**
 * DSL entry point for building account lists.
 */
inline fun accounts(block: AccountListBuilder.() -> Unit): List<AccountMeta> {
    return AccountListBuilder().apply(block).build()
}

/**
 * Instruction builder DSL.
 */
@DslMarker
annotation class InstructionDsl

/**
 * Instruction data.
 */
data class Instruction(
    val programId: String,
    val accounts: List<AccountMeta>,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Instruction) return false
        return programId == other.programId &&
               accounts == other.accounts &&
               data.contentEquals(other.data)
    }
    
    override fun hashCode(): Int {
        var result = programId.hashCode()
        result = 31 * result + accounts.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Builder for instructions.
 */
@InstructionDsl
class InstructionBuilder(
    private val programId: String
) {
    @PublishedApi
    internal var accounts: List<AccountMeta> = emptyList()
    @PublishedApi
    internal var data: ByteArray = ByteArray(0)
    
    /**
     * Set accounts using the DSL.
     */
    fun accounts(block: AccountListBuilder.() -> Unit) {
        accounts = com.selenus.artemis.core.accounts(block)
    }
    
    /**
     * Set accounts directly.
     */
    fun accounts(accountList: List<AccountMeta>) {
        accounts = accountList
    }
    
    /**
     * Set instruction data.
     */
    fun data(bytes: ByteArray) {
        data = bytes
    }
    
    /**
     * Set instruction data using a builder.
     */
    inline fun data(block: InstructionDataBuilder.() -> Unit) {
        data = InstructionDataBuilder().apply(block).build()
    }
    
    fun build(): Instruction = Instruction(programId, accounts, data)
}

/**
 * Builder for instruction data (byte array).
 */
@InstructionDsl
class InstructionDataBuilder {
    private val bytes = mutableListOf<Byte>()
    
    /**
     * Write a single byte.
     */
    fun writeByte(value: Byte) {
        bytes.add(value)
    }
    
    fun writeByte(value: Int) {
        bytes.add(value.toByte())
    }
    
    /**
     * Write bytes directly.
     */
    fun writeBytes(value: ByteArray) {
        bytes.addAll(value.toList())
    }
    
    /**
     * Write a u8.
     */
    fun writeU8(value: Int) {
        bytes.add((value and 0xFF).toByte())
    }
    
    /**
     * Write a u16 (little-endian).
     */
    fun writeU16(value: Int) {
        bytes.add((value and 0xFF).toByte())
        bytes.add(((value shr 8) and 0xFF).toByte())
    }
    
    /**
     * Write a u32 (little-endian).
     */
    fun writeU32(value: Long) {
        bytes.add((value and 0xFF).toByte())
        bytes.add(((value shr 8) and 0xFF).toByte())
        bytes.add(((value shr 16) and 0xFF).toByte())
        bytes.add(((value shr 24) and 0xFF).toByte())
    }
    
    /**
     * Write a u64 (little-endian).
     */
    fun writeU64(value: Long) {
        for (i in 0 until 8) {
            bytes.add(((value shr (i * 8)) and 0xFF).toByte())
        }
    }
    
    /**
     * Write a pubkey (32 bytes).
     */
    fun writePubkey(base58: String) {
        // We need to decode base58 - this is a simplified version
        // In practice, use Base58.decode from artemis-runtime
        throw UnsupportedOperationException("Use writeBytes(Base58.decode(pubkey))")
    }
    
    /**
     * Write a string with length prefix.
     */
    fun writeString(value: String) {
        val stringBytes = value.encodeToByteArray()
        writeU32(stringBytes.size.toLong())
        writeBytes(stringBytes)
    }
    
    /**
     * Write a borsh-style vector (u32 length + elements).
     */
    fun <T> writeVec(items: List<T>, writer: (T) -> Unit) {
        writeU32(items.size.toLong())
        items.forEach(writer)
    }
    
    /**
     * Write an optional value (0 for None, 1 + value for Some).
     */
    fun <T> writeOptional(value: T?, writer: (T) -> Unit) {
        if (value == null) {
            writeByte(0)
        } else {
            writeByte(1)
            writer(value)
        }
    }
    
    fun build(): ByteArray = bytes.toByteArray()
}

/**
 * DSL entry point for building instructions.
 */
inline fun instruction(programId: String, block: InstructionBuilder.() -> Unit): Instruction {
    return InstructionBuilder(programId).apply(block).build()
}

/**
 * Transaction builder DSL.
 */
@DslMarker
annotation class TransactionDsl

/**
 * Builder for transactions.
 */
@TransactionDsl
class TransactionBuilder {
    @PublishedApi
    internal val instructionsList = mutableListOf<Instruction>()
    private var feePayer: String? = null
    private var recentBlockhash: String? = null
    
    /**
     * Set the fee payer.
     */
    fun feePayer(pubkey: String) {
        feePayer = pubkey
    }
    
    /**
     * Set the recent blockhash.
     */
    fun recentBlockhash(blockhash: String) {
        recentBlockhash = blockhash
    }
    
    /**
     * Add an instruction using DSL.
     */
    inline fun instruction(programId: String, block: InstructionBuilder.() -> Unit) {
        instructionsList.add(InstructionBuilder(programId).apply(block).build())
    }
    
    /**
     * Add an instruction directly.
     */
    fun instruction(ix: Instruction) {
        instructionsList.add(ix)
    }
    
    /**
     * Add multiple instructions.
     */
    fun instructions(vararg ixs: Instruction) {
        instructionsList.addAll(ixs)
    }
    
    /**
     * Get the built instructions.
     */
    fun getInstructions(): List<Instruction> = instructionsList.toList()
    
    /**
     * Get the fee payer.
     */
    fun getFeePayer(): String? = feePayer
    
    /**
     * Get the recent blockhash.
     */
    fun getRecentBlockhash(): String? = recentBlockhash
}

/**
 * DSL entry point for building transactions.
 */
inline fun transaction(block: TransactionBuilder.() -> Unit): TransactionBuilder {
    return TransactionBuilder().apply(block)
}
