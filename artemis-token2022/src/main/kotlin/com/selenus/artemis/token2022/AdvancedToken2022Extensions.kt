package com.selenus.artemis.token2022

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction

/**
 * Artemis Advanced Token-2022 Extensions
 * 
 * Comprehensive support for Token-2022 extensions beyond basic transfers.
 * Implements the full range of Token Extensions Program features:
 * 
 * - Transfer Fees (royalties on every transfer)
 * - Interest-Bearing Tokens (automatic yield)
 * - Non-Transferable Tokens (soulbound)
 * - Confidential Transfers (private amounts)
 * - Default Account State (frozen by default)
 * - Permanent Delegate (always delegated)
 * - Transfer Hook (custom logic)
 * - Metadata Pointer (on-chain metadata)
 * - Group/Member Pointers (collection support)
 * 
 * Mobile-optimized with efficient serialization.
 */
object AdvancedToken2022Extensions {
    
    private val SYSTEM_PROGRAM = Pubkey.fromBase58("11111111111111111111111111111111")
    private val TOKEN_2022_PROGRAM = Token2022Program.PROGRAM_ID
    
    // ========================================================================
    // Interest-Bearing Tokens
    // ========================================================================
    
    /**
     * Interest-bearing token configuration.
     */
    data class InterestBearingConfig(
        val rateAuthority: Pubkey?,
        val initializationTimestamp: Long,
        val preUpdateAverageRate: Short,
        val lastUpdateTimestamp: Long,
        val currentRate: Short
    ) {
        /**
         * Calculate accumulated interest.
         */
        fun calculateInterest(principal: Long, currentTime: Long): Long {
            if (currentRate == 0.toShort()) return 0
            
            val elapsedSeconds = currentTime / 1000 - lastUpdateTimestamp / 1000
            val elapsedYears = elapsedSeconds.toDouble() / (365.25 * 24 * 60 * 60)
            
            // Rate is in basis points (1/100th of a percent)
            val rateDecimal = currentRate.toDouble() / 10000
            
            // Simple interest: P * r * t
            return (principal * rateDecimal * elapsedYears).toLong()
        }
    }
    
    /**
     * Initialize interest-bearing mint extension.
     */
    fun initializeInterestBearingMint(
        mint: Pubkey,
        rateAuthority: Pubkey?,
        rate: Short
    ): Instruction {
        val data = ByteArray(1 + 1 + 32 + 2)
        var offset = 0
        
        data[offset++] = 33 // InterestBearingMint extension instruction
        
        // Rate authority option
        if (rateAuthority != null) {
            data[offset++] = 1
            System.arraycopy(rateAuthority.bytes, 0, data, offset, 32)
            offset += 32
        } else {
            data[offset++] = 0
            offset += 32
        }
        
        // Initial rate (basis points)
        putI16LE(data, offset, rate)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    /**
     * Update interest rate.
     */
    fun updateInterestRate(
        mint: Pubkey,
        rateAuthority: Pubkey,
        newRate: Short
    ): Instruction {
        val data = ByteArray(3)
        data[0] = 34 // UpdateRate
        putI16LE(data, 1, newRate)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true),
                AccountMeta(rateAuthority, isSigner = true, isWritable = false)
            ),
            data = data
        )
    }
    
    // ========================================================================
    // Non-Transferable Tokens (Soulbound)
    // ========================================================================
    
    /**
     * Initialize non-transferable mint extension.
     * Once set, tokens from this mint cannot be transferred.
     */
    fun initializeNonTransferableMint(mint: Pubkey): Instruction {
        val data = byteArrayOf(35) // NonTransferableAccount extension
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    // ========================================================================
    // Permanent Delegate
    // ========================================================================
    
    /**
     * Initialize permanent delegate extension.
     * The delegate can always transfer or burn tokens.
     */
    fun initializePermanentDelegate(
        mint: Pubkey,
        delegate: Pubkey
    ): Instruction {
        val data = ByteArray(33)
        data[0] = 36 // PermanentDelegate extension
        System.arraycopy(delegate.bytes, 0, data, 1, 32)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    // ========================================================================
    // Default Account State
    // ========================================================================
    
    /**
     * Account states for default account state extension.
     */
    enum class AccountState(val value: Byte) {
        UNINITIALIZED(0),
        INITIALIZED(1),
        FROZEN(2)
    }
    
    /**
     * Initialize default account state extension.
     * New token accounts will be created with this state.
     */
    fun initializeDefaultAccountState(
        mint: Pubkey,
        state: AccountState
    ): Instruction {
        val data = byteArrayOf(37, state.value)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    /**
     * Update default account state.
     */
    fun updateDefaultAccountState(
        mint: Pubkey,
        freezeAuthority: Pubkey,
        state: AccountState
    ): Instruction {
        val data = byteArrayOf(38, state.value)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true),
                AccountMeta(freezeAuthority, isSigner = true, isWritable = false)
            ),
            data = data
        )
    }
    
    // ========================================================================
    // Transfer Hook
    // ========================================================================
    
    /**
     * Transfer hook configuration.
     */
    data class TransferHookConfig(
        val authority: Pubkey?,
        val programId: Pubkey
    )
    
    /**
     * Initialize transfer hook extension.
     * Allows custom program to be invoked on every transfer.
     */
    fun initializeTransferHook(
        mint: Pubkey,
        authority: Pubkey?,
        hookProgramId: Pubkey
    ): Instruction {
        val data = ByteArray(1 + 1 + 32 + 32)
        var offset = 0
        
        data[offset++] = 39 // TransferHook extension
        
        // Authority option
        if (authority != null) {
            data[offset++] = 1
            System.arraycopy(authority.bytes, 0, data, offset, 32)
            offset += 32
        } else {
            data[offset++] = 0
            offset += 32
        }
        
        // Hook program ID
        System.arraycopy(hookProgramId.bytes, 0, data, offset, 32)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    /**
     * Update transfer hook program.
     */
    fun updateTransferHook(
        mint: Pubkey,
        authority: Pubkey,
        newProgramId: Pubkey
    ): Instruction {
        val data = ByteArray(33)
        data[0] = 40
        System.arraycopy(newProgramId.bytes, 0, data, 1, 32)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true),
                AccountMeta(authority, isSigner = true, isWritable = false)
            ),
            data = data
        )
    }
    
    // ========================================================================
    // Metadata Pointer
    // ========================================================================
    
    /**
     * Initialize metadata pointer extension.
     * Points to on-chain metadata account.
     */
    fun initializeMetadataPointer(
        mint: Pubkey,
        authority: Pubkey?,
        metadataAddress: Pubkey
    ): Instruction {
        val data = ByteArray(1 + 1 + 32 + 32)
        var offset = 0
        
        data[offset++] = 41 // MetadataPointer extension
        
        // Authority option
        if (authority != null) {
            data[offset++] = 1
            System.arraycopy(authority.bytes, 0, data, offset, 32)
            offset += 32
        } else {
            data[offset++] = 0
            offset += 32
        }
        
        // Metadata address
        System.arraycopy(metadataAddress.bytes, 0, data, offset, 32)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    /**
     * Update metadata pointer.
     */
    fun updateMetadataPointer(
        mint: Pubkey,
        authority: Pubkey,
        newMetadataAddress: Pubkey
    ): Instruction {
        val data = ByteArray(33)
        data[0] = 42
        System.arraycopy(newMetadataAddress.bytes, 0, data, 1, 32)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true),
                AccountMeta(authority, isSigner = true, isWritable = false)
            ),
            data = data
        )
    }
    
    // ========================================================================
    // Group and Member Pointers (Collections)
    // ========================================================================
    
    /**
     * Initialize group pointer extension.
     * For collection/group NFTs.
     */
    fun initializeGroupPointer(
        mint: Pubkey,
        authority: Pubkey?,
        groupAddress: Pubkey
    ): Instruction {
        val data = ByteArray(1 + 1 + 32 + 32)
        var offset = 0
        
        data[offset++] = 43 // GroupPointer extension
        
        if (authority != null) {
            data[offset++] = 1
            System.arraycopy(authority.bytes, 0, data, offset, 32)
            offset += 32
        } else {
            data[offset++] = 0
            offset += 32
        }
        
        System.arraycopy(groupAddress.bytes, 0, data, offset, 32)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    /**
     * Initialize member pointer extension.
     * For NFTs that belong to a collection.
     */
    fun initializeMemberPointer(
        mint: Pubkey,
        authority: Pubkey?,
        memberAddress: Pubkey
    ): Instruction {
        val data = ByteArray(1 + 1 + 32 + 32)
        var offset = 0
        
        data[offset++] = 44 // MemberPointer extension
        
        if (authority != null) {
            data[offset++] = 1
            System.arraycopy(authority.bytes, 0, data, offset, 32)
            offset += 32
        } else {
            data[offset++] = 0
            offset += 32
        }
        
        System.arraycopy(memberAddress.bytes, 0, data, offset, 32)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    // ========================================================================
    // Confidential Transfers
    // ========================================================================
    
    /**
     * Confidential transfer state.
     */
    enum class ConfidentialTransferAccountState(val value: Byte) {
        UNINITIALIZED(0),
        INITIALIZED(1)
    }
    
    /**
     * Initialize confidential transfer mint.
     */
    fun initializeConfidentialTransferMint(
        mint: Pubkey,
        authority: Pubkey?,
        autoApproveNewAccounts: Boolean,
        auditorElgamalPubkey: ByteArray?
    ): Instruction {
        val hasAuditor = auditorElgamalPubkey != null
        val data = ByteArray(1 + 1 + 32 + 1 + (if (hasAuditor) 33 else 1))
        var offset = 0
        
        data[offset++] = 46 // ConfidentialTransferMint
        
        if (authority != null) {
            data[offset++] = 1
            System.arraycopy(authority.bytes, 0, data, offset, 32)
            offset += 32
        } else {
            data[offset++] = 0
            offset += 32
        }
        
        data[offset++] = if (autoApproveNewAccounts) 1 else 0
        
        if (auditorElgamalPubkey != null) {
            data[offset++] = 1
            System.arraycopy(auditorElgamalPubkey, 0, data, offset, 32)
        } else {
            data[offset] = 0
        }
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    /**
     * Configure confidential transfer account.
     */
    fun configureConfidentialTransferAccount(
        tokenAccount: Pubkey,
        mint: Pubkey,
        owner: Pubkey,
        decryptableZeroBalance: ByteArray, // Encrypted zero balance
        maximumPendingBalanceCreditCounter: Long,
        proofInstructionOffset: Byte
    ): Instruction {
        val data = ByteArray(1 + 64 + 8 + 1) // Simplified
        var offset = 0
        
        data[offset++] = 47 // ConfigureAccount
        
        // Decryptable zero balance (encrypted)
        System.arraycopy(decryptableZeroBalance, 0, data, offset, 
                        minOf(64, decryptableZeroBalance.size))
        offset += 64
        
        putU64LE(data, offset, maximumPendingBalanceCreditCounter)
        offset += 8
        
        data[offset] = proofInstructionOffset
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(tokenAccount, isSigner = false, isWritable = true),
                AccountMeta(mint, isSigner = false, isWritable = false),
                AccountMeta(owner, isSigner = true, isWritable = false)
            ),
            data = data
        )
    }
    
    // ========================================================================
    // CPI Guard
    // ========================================================================
    
    /**
     * Enable CPI guard.
     * Prevents token account from being modified via CPI.
     */
    fun enableCpiGuard(
        tokenAccount: Pubkey,
        owner: Pubkey
    ): Instruction {
        val data = byteArrayOf(48, 1) // Enable
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(tokenAccount, isSigner = false, isWritable = true),
                AccountMeta(owner, isSigner = true, isWritable = false)
            ),
            data = data
        )
    }
    
    /**
     * Disable CPI guard.
     */
    fun disableCpiGuard(
        tokenAccount: Pubkey,
        owner: Pubkey
    ): Instruction {
        val data = byteArrayOf(48, 0) // Disable
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(tokenAccount, isSigner = false, isWritable = true),
                AccountMeta(owner, isSigner = true, isWritable = false)
            ),
            data = data
        )
    }
    
    // ========================================================================
    // Immutable Owner
    // ========================================================================
    
    /**
     * Initialize immutable owner extension.
     * Account owner cannot be changed after this.
     */
    fun initializeImmutableOwner(tokenAccount: Pubkey): Instruction {
        val data = byteArrayOf(49)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(tokenAccount, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    // ========================================================================
    // Close Authority
    // ========================================================================
    
    /**
     * Initialize mint close authority extension.
     * Allows the mint account to be closed.
     */
    fun initializeMintCloseAuthority(
        mint: Pubkey,
        closeAuthority: Pubkey?
    ): Instruction {
        val data = ByteArray(1 + 1 + 32)
        var offset = 0
        
        data[offset++] = 50 // MintCloseAuthority
        
        if (closeAuthority != null) {
            data[offset++] = 1
            System.arraycopy(closeAuthority.bytes, 0, data, offset, 32)
        } else {
            data[offset] = 0
        }
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    // ========================================================================
    // Helper: Create Mint with Extensions
    // ========================================================================
    
    /**
     * Extension types for mint creation.
     */
    sealed class MintExtension {
        data class TransferFee(
            val transferFeeConfigAuthority: Pubkey?,
            val withdrawWithheldAuthority: Pubkey?,
            val transferFeeBasisPoints: Int,
            val maximumFee: Long
        ) : MintExtension()
        
        data class InterestBearing(
            val rateAuthority: Pubkey?,
            val rate: Short
        ) : MintExtension()
        
        object NonTransferable : MintExtension()
        
        data class PermanentDelegate(val delegate: Pubkey) : MintExtension()
        
        data class DefaultAccountState(val state: AccountState) : MintExtension()
        
        data class TransferHook(
            val authority: Pubkey?,
            val programId: Pubkey
        ) : MintExtension()
        
        data class MetadataPointer(
            val authority: Pubkey?,
            val metadataAddress: Pubkey
        ) : MintExtension()
        
        data class MintCloseAuthority(val authority: Pubkey?) : MintExtension()
        
        data class ConfidentialTransfer(
            val authority: Pubkey?,
            val autoApprove: Boolean,
            val auditorPubkey: ByteArray?
        ) : MintExtension()
    }
    
    /**
     * Calculate required space for mint with extensions.
     */
    fun calculateMintSpace(extensions: List<MintExtension>): Int {
        var space = 82 // Base mint size
        
        for (ext in extensions) {
            space += when (ext) {
                is MintExtension.TransferFee -> 108
                is MintExtension.InterestBearing -> 52
                is MintExtension.NonTransferable -> 1
                is MintExtension.PermanentDelegate -> 36
                is MintExtension.DefaultAccountState -> 1
                is MintExtension.TransferHook -> 68
                is MintExtension.MetadataPointer -> 68
                is MintExtension.MintCloseAuthority -> 36
                is MintExtension.ConfidentialTransfer -> 97
            }
        }
        
        return space
    }
    
    /**
     * Prepares all instructions for creating a mint with extensions.
     */
    fun prepareMintWithExtensions(
        mint: Pubkey,
        decimals: Int,
        mintAuthority: Pubkey,
        freezeAuthority: Pubkey?,
        payer: Pubkey,
        extensions: List<MintExtension>
    ): List<Instruction> {
        val instructions = mutableListOf<Instruction>()
        
        val space = calculateMintSpace(extensions)
        val lamports = (space * 10) + 1_000_000L // Rough rent estimate
        
        // Create account
        instructions.add(createAccountInstruction(
            payer = payer,
            newAccount = mint,
            lamports = lamports,
            space = space.toLong(),
            owner = TOKEN_2022_PROGRAM
        ))
        
        // Initialize extensions (must be before InitializeMint)
        for (ext in extensions) {
            val extInstruction = when (ext) {
                is MintExtension.TransferFee -> {
                    initializeTransferFeeConfig(
                        mint, ext.transferFeeConfigAuthority, 
                        ext.withdrawWithheldAuthority,
                        ext.transferFeeBasisPoints, ext.maximumFee
                    )
                }
                is MintExtension.InterestBearing -> {
                    initializeInterestBearingMint(mint, ext.rateAuthority, ext.rate)
                }
                MintExtension.NonTransferable -> {
                    initializeNonTransferableMint(mint)
                }
                is MintExtension.PermanentDelegate -> {
                    initializePermanentDelegate(mint, ext.delegate)
                }
                is MintExtension.DefaultAccountState -> {
                    initializeDefaultAccountState(mint, ext.state)
                }
                is MintExtension.TransferHook -> {
                    initializeTransferHook(mint, ext.authority, ext.programId)
                }
                is MintExtension.MetadataPointer -> {
                    initializeMetadataPointer(mint, ext.authority, ext.metadataAddress)
                }
                is MintExtension.MintCloseAuthority -> {
                    initializeMintCloseAuthority(mint, ext.authority)
                }
                is MintExtension.ConfidentialTransfer -> {
                    initializeConfidentialTransferMint(
                        mint, ext.authority, ext.autoApprove, ext.auditorPubkey
                    )
                }
            }
            instructions.add(extInstruction)
        }
        
        // Initialize mint (last)
        instructions.add(Token2022Program.initializeMint2(
            mint = mint,
            decimals = decimals,
            mintAuthority = mintAuthority,
            freezeAuthority = freezeAuthority
        ))
        
        return instructions
    }
    
    // ========================================================================
    // Internal Helpers
    // ========================================================================
    
    private fun initializeTransferFeeConfig(
        mint: Pubkey,
        transferFeeConfigAuthority: Pubkey?,
        withdrawWithheldAuthority: Pubkey?,
        basisPoints: Int,
        maximumFee: Long
    ): Instruction {
        val data = ByteArray(1 + 1 + 32 + 1 + 32 + 2 + 8)
        var offset = 0
        
        data[offset++] = 27 // InitializeTransferFeeConfig
        
        if (transferFeeConfigAuthority != null) {
            data[offset++] = 1
            System.arraycopy(transferFeeConfigAuthority.bytes, 0, data, offset, 32)
            offset += 32
        } else {
            data[offset++] = 0
            offset += 32
        }
        
        if (withdrawWithheldAuthority != null) {
            data[offset++] = 1
            System.arraycopy(withdrawWithheldAuthority.bytes, 0, data, offset, 32)
            offset += 32
        } else {
            data[offset++] = 0
            offset += 32
        }
        
        putU16LE(data, offset, basisPoints)
        offset += 2
        
        putU64LE(data, offset, maximumFee)
        
        return Instruction(
            programId = TOKEN_2022_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    private fun createAccountInstruction(
        payer: Pubkey,
        newAccount: Pubkey,
        lamports: Long,
        space: Long,
        owner: Pubkey
    ): Instruction {
        val data = ByteArray(52)
        
        // Instruction 0: CreateAccount
        putU32LE(data, 0, 0)
        putU64LE(data, 4, lamports)
        putU64LE(data, 12, space)
        System.arraycopy(owner.bytes, 0, data, 20, 32)
        
        return Instruction(
            programId = SYSTEM_PROGRAM,
            accounts = listOf(
                AccountMeta(payer, isSigner = true, isWritable = true),
                AccountMeta(newAccount, isSigner = true, isWritable = true)
            ),
            data = data
        )
    }
    
    private fun putU64LE(dst: ByteArray, off: Int, v: Long) {
        var x = v
        for (i in 0 until 8) {
            dst[off + i] = (x and 0xff).toByte()
            x = x ushr 8
        }
    }
    
    private fun putU32LE(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v and 0xff).toByte()
        dst[off + 1] = ((v ushr 8) and 0xff).toByte()
        dst[off + 2] = ((v ushr 16) and 0xff).toByte()
        dst[off + 3] = ((v ushr 24) and 0xff).toByte()
    }
    
    private fun putU16LE(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v and 0xff).toByte()
        dst[off + 1] = ((v ushr 8) and 0xff).toByte()
    }
    
    private fun putI16LE(dst: ByteArray, off: Int, v: Short) {
        dst[off] = (v.toInt() and 0xff).toByte()
        dst[off + 1] = ((v.toInt() ushr 8) and 0xff).toByte()
    }
}
