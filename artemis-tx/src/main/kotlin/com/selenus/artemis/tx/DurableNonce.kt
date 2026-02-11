package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Base58

/**
 * Artemis Durable Nonce Transaction Support
 * 
 * Implements durable transaction nonces for offline signing scenarios
 * where transactions need to remain valid beyond the ~2 minute blockhash window.
 * 
 * The Solana Mobile SDK lacks native durable nonce support, requiring apps
 * to manually handle nonce accounts. Artemis provides:
 * 
 * - Nonce account creation and management
 * - Automatic nonce instruction insertion
 * - Nonce advancement detection
 * - Offline signing workflows
 */
object DurableNonce {
    
    /** System Program ID */
    val SYSTEM_PROGRAM_ID = Pubkey.fromBase58("11111111111111111111111111111111")
    
    /** Minimum rent-exempt balance for nonce account (as of 2024) */
    const val NONCE_ACCOUNT_SIZE = 80L
    
    /**
     * System instruction discriminators.
     */
    private object SystemInstruction {
        const val CREATE_ACCOUNT = 0
        const val ADVANCE_NONCE_ACCOUNT = 4
        const val WITHDRAW_NONCE_ACCOUNT = 5
        const val INITIALIZE_NONCE_ACCOUNT = 6
        const val AUTHORIZE_NONCE_ACCOUNT = 7
    }
    
    /**
     * Nonce account state.
     */
    enum class NonceState {
        UNINITIALIZED,
        INITIALIZED
    }
    
    /**
     * Represents a nonce account's data.
     */
    data class NonceAccountData(
        val state: NonceState,
        val authority: Pubkey,
        val nonce: String, // The durable nonce (blockhash to use)
        val feeCalculator: Long? = null // lamports per signature
    )
    
    /**
     * Configuration for a durable nonce transaction.
     */
    data class NonceConfig(
        val nonceAccount: Pubkey,
        val nonceAuthority: Pubkey,
        val nonce: String // The current nonce value to use as blockhash
    )
    
    // ========================================================================
    // Instruction Builders
    // ========================================================================
    
    /**
     * Creates an AdvanceNonceAccount instruction.
     * This MUST be the first instruction in a durable nonce transaction.
     */
    fun advanceNonceInstruction(
        nonceAccount: Pubkey,
        nonceAuthority: Pubkey
    ): Instruction {
        // Recent blockhashes sysvar
        val recentBlockhashesSysvar = Pubkey.fromBase58("SysvarRecentB1ockhashes11111111111111111111")
        
        return Instruction(
            programId = SYSTEM_PROGRAM_ID,
            accounts = listOf(
                AccountMeta(nonceAccount, isSigner = false, isWritable = true),
                AccountMeta(recentBlockhashesSysvar, isSigner = false, isWritable = false),
                AccountMeta(nonceAuthority, isSigner = true, isWritable = false)
            ),
            data = byteArrayOf(SystemInstruction.ADVANCE_NONCE_ACCOUNT.toByte())
        )
    }
    
    /**
     * Creates instructions to create and initialize a nonce account.
     */
    fun createNonceAccountInstructions(
        fromPubkey: Pubkey,
        nonceAccount: Pubkey,
        authority: Pubkey,
        lamports: Long
    ): List<Instruction> {
        val createAccountIx = createAccountInstruction(
            fromPubkey = fromPubkey,
            newAccountPubkey = nonceAccount,
            lamports = lamports,
            space = NONCE_ACCOUNT_SIZE,
            owner = SYSTEM_PROGRAM_ID
        )
        
        val initializeNonceIx = initializeNonceInstruction(
            nonceAccount = nonceAccount,
            authority = authority
        )
        
        return listOf(createAccountIx, initializeNonceIx)
    }
    
    /**
     * Creates a CreateAccount instruction.
     */
    private fun createAccountInstruction(
        fromPubkey: Pubkey,
        newAccountPubkey: Pubkey,
        lamports: Long,
        space: Long,
        owner: Pubkey
    ): Instruction {
        val data = ByteArray(4 + 8 + 8 + 32)
        var offset = 0
        
        // Instruction type (0 = CreateAccount)
        data[offset++] = SystemInstruction.CREATE_ACCOUNT.toByte()
        offset += 3 // padding
        
        // Lamports (u64 LE)
        for (i in 0 until 8) {
            data[offset + i] = ((lamports shr (i * 8)) and 0xFF).toByte()
        }
        offset += 8
        
        // Space (u64 LE)
        for (i in 0 until 8) {
            data[offset + i] = ((space shr (i * 8)) and 0xFF).toByte()
        }
        offset += 8
        
        // Owner pubkey
        System.arraycopy(owner.bytes, 0, data, offset, 32)
        
        return Instruction(
            programId = SYSTEM_PROGRAM_ID,
            accounts = listOf(
                AccountMeta(fromPubkey, isSigner = true, isWritable = true),
                AccountMeta(newAccountPubkey, isSigner = true, isWritable = true)
            ),
            data = data
        )
    }
    
    /**
     * Creates an InitializeNonceAccount instruction.
     */
    private fun initializeNonceInstruction(
        nonceAccount: Pubkey,
        authority: Pubkey
    ): Instruction {
        // Recent blockhashes sysvar
        val recentBlockhashesSysvar = Pubkey.fromBase58("SysvarRecentB1ockhashes11111111111111111111")
        // Rent sysvar
        val rentSysvar = Pubkey.fromBase58("SysvarRent111111111111111111111111111111111")
        
        val data = ByteArray(4 + 32)
        data[0] = SystemInstruction.INITIALIZE_NONCE_ACCOUNT.toByte()
        System.arraycopy(authority.bytes, 0, data, 4, 32)
        
        return Instruction(
            programId = SYSTEM_PROGRAM_ID,
            accounts = listOf(
                AccountMeta(nonceAccount, isSigner = false, isWritable = true),
                AccountMeta(recentBlockhashesSysvar, isSigner = false, isWritable = false),
                AccountMeta(rentSysvar, isSigner = false, isWritable = false)
            ),
            data = data
        )
    }
    
    /**
     * Creates a WithdrawNonceAccount instruction.
     */
    fun withdrawNonceInstruction(
        nonceAccount: Pubkey,
        authority: Pubkey,
        toPubkey: Pubkey,
        lamports: Long
    ): Instruction {
        val recentBlockhashesSysvar = Pubkey.fromBase58("SysvarRecentB1ockhashes11111111111111111111")
        val rentSysvar = Pubkey.fromBase58("SysvarRent111111111111111111111111111111111")
        
        val data = ByteArray(4 + 8)
        data[0] = SystemInstruction.WITHDRAW_NONCE_ACCOUNT.toByte()
        for (i in 0 until 8) {
            data[4 + i] = ((lamports shr (i * 8)) and 0xFF).toByte()
        }
        
        return Instruction(
            programId = SYSTEM_PROGRAM_ID,
            accounts = listOf(
                AccountMeta(nonceAccount, isSigner = false, isWritable = true),
                AccountMeta(toPubkey, isSigner = false, isWritable = true),
                AccountMeta(recentBlockhashesSysvar, isSigner = false, isWritable = false),
                AccountMeta(rentSysvar, isSigner = false, isWritable = false),
                AccountMeta(authority, isSigner = true, isWritable = false)
            ),
            data = data
        )
    }
    
    /**
     * Creates an AuthorizeNonceAccount instruction.
     */
    fun authorizeNonceInstruction(
        nonceAccount: Pubkey,
        currentAuthority: Pubkey,
        newAuthority: Pubkey
    ): Instruction {
        val data = ByteArray(4 + 32)
        data[0] = SystemInstruction.AUTHORIZE_NONCE_ACCOUNT.toByte()
        System.arraycopy(newAuthority.bytes, 0, data, 4, 32)
        
        return Instruction(
            programId = SYSTEM_PROGRAM_ID,
            accounts = listOf(
                AccountMeta(nonceAccount, isSigner = false, isWritable = true),
                AccountMeta(currentAuthority, isSigner = true, isWritable = false)
            ),
            data = data
        )
    }
    
    // ========================================================================
    // Transaction Helpers
    // ========================================================================
    
    /**
     * Prepends the AdvanceNonce instruction to make a transaction durable.
     * The transaction's blockhash should be set to the nonce value.
     */
    fun makeDurable(
        instructions: List<Instruction>,
        nonceConfig: NonceConfig
    ): List<Instruction> {
        val advanceIx = advanceNonceInstruction(
            nonceAccount = nonceConfig.nonceAccount,
            nonceAuthority = nonceConfig.nonceAuthority
        )
        return listOf(advanceIx) + instructions
    }
    
    /**
     * Checks if a transaction uses a durable nonce.
     */
    fun isDurableNonceTransaction(instructions: List<Instruction>): Boolean {
        if (instructions.isEmpty()) return false
        val first = instructions.first()
        
        return first.programId == SYSTEM_PROGRAM_ID &&
               first.data.isNotEmpty() &&
               first.data[0] == SystemInstruction.ADVANCE_NONCE_ACCOUNT.toByte()
    }
    
    /**
     * Extracts nonce account from a durable transaction's first instruction.
     */
    fun extractNonceAccount(instructions: List<Instruction>): Pubkey? {
        if (!isDurableNonceTransaction(instructions)) return null
        return instructions.first().accounts.firstOrNull()?.pubkey
    }
    
    // ========================================================================
    // Nonce Account Parsing
    // ========================================================================
    
    /**
     * Parses nonce account data from account bytes.
     */
    fun parseNonceAccount(data: ByteArray): NonceAccountData? {
        if (data.size < 80) return null
        
        return try {
            // Version/state at offset 0-4
            val version = readU32LE(data, 0)
            if (version == 0L) {
                return NonceAccountData(
                    state = NonceState.UNINITIALIZED,
                    authority = Pubkey(ByteArray(32)),
                    nonce = ""
                )
            }
            
            // State = 1 means initialized
            // Authority at offset 4-36
            val authority = Pubkey(data.copyOfRange(4, 36))
            
            // Nonce (blockhash) at offset 36-68
            val nonceBytes = data.copyOfRange(36, 68)
            val nonce = com.selenus.artemis.runtime.Base58.encode(nonceBytes)
            
            // Fee calculator at offset 68-76 (lamports per signature)
            val feeCalculator = readU64LE(data, 68)
            
            NonceAccountData(
                state = NonceState.INITIALIZED,
                authority = authority,
                nonce = nonce,
                feeCalculator = feeCalculator
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun readU32LE(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF)) or
               ((data[offset + 1].toLong() and 0xFF) shl 8) or
               ((data[offset + 2].toLong() and 0xFF) shl 16) or
               ((data[offset + 3].toLong() and 0xFF) shl 24)
    }
    
    private fun readU64LE(data: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = result or ((data[offset + i].toLong() and 0xFF) shl (i * 8))
        }
        return result
    }
}

// Extension function removed - Message uses CompiledInstruction which is a different format.
// Use DurableNonce.makeDurable() with uncompiled Instructions before message compilation.
