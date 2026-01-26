package com.selenus.artemis.intent

/**
 * Registry of known Solana programs with their instruction decoders.
 * 
 * This is the core of Artemis's Transaction Intent Protocol - the first
 * Solana SDK to provide comprehensive instruction decoding for mobile apps.
 */
object ProgramRegistry {
    
    // Well-known program IDs (Base58 strings for easy comparison)
    const val SYSTEM_PROGRAM = "11111111111111111111111111111111"
    const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    const val TOKEN_2022_PROGRAM = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
    const val ASSOCIATED_TOKEN_PROGRAM = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
    const val COMPUTE_BUDGET_PROGRAM = "ComputeBudget111111111111111111111111111111"
    const val MEMO_PROGRAM = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"
    const val MEMO_V2_PROGRAM = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"
    const val MEMO_PROGRAM_V1 = "Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo"
    const val STAKE_PROGRAM = "Stake11111111111111111111111111111111111111"
    const val VOTE_PROGRAM = "Vote111111111111111111111111111111111111111"
    const val BPF_LOADER = "BPFLoaderUpgradeab1e11111111111111111111111"
    
    // Metaplex programs
    const val TOKEN_METADATA_PROGRAM = "metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s"
    const val CANDY_MACHINE_V3 = "Guard1JwRhJkVH6XZhzoYxeBVQe872VH6QggF4BWmS9g"
    const val MPL_CORE = "CoREENxT6tW1HoK8ypY1SxRMZTcVPm7R94rH4PZNhX7d"
    
    // DeFi programs (commonly used)
    const val JUPITER_V6 = "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4"
    const val RAYDIUM_AMM = "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8"
    const val ORCA_WHIRLPOOL = "whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc"
    
    // Known malicious or suspicious programs (for warnings)
    private val suspiciousPrograms = mutableSetOf<String>()
    
    private val decoders = mutableMapOf<String, InstructionDecoder>()
    private val programNames = mutableMapOf<String, String>()
    
    init {
        // Register built-in decoders
        register(SYSTEM_PROGRAM, "System Program", SystemProgramDecoder)
        register(TOKEN_PROGRAM, "SPL Token", TokenProgramDecoder)
        register(TOKEN_2022_PROGRAM, "Token-2022", Token2022ProgramDecoder)
        register(ASSOCIATED_TOKEN_PROGRAM, "Associated Token", AssociatedTokenDecoder)
        register(COMPUTE_BUDGET_PROGRAM, "Compute Budget", ComputeBudgetDecoder)
        register(MEMO_PROGRAM, "Memo", MemoProgramDecoder)
        register(MEMO_PROGRAM_V1, "Memo v1", MemoProgramDecoder)
        register(STAKE_PROGRAM, "Stake", StakeProgramDecoder)
        
        // Register names for known programs without full decoders
        programNames[TOKEN_METADATA_PROGRAM] = "Token Metadata"
        programNames[MPL_CORE] = "MPL Core"
        programNames[JUPITER_V6] = "Jupiter v6"
        programNames[RAYDIUM_AMM] = "Raydium AMM"
        programNames[ORCA_WHIRLPOOL] = "Orca Whirlpool"
        programNames[VOTE_PROGRAM] = "Vote Program"
        programNames[BPF_LOADER] = "BPF Loader"
        programNames[CANDY_MACHINE_V3] = "Candy Machine v3"
    }
    
    /**
     * Register a custom program decoder.
     */
    fun register(programId: String, name: String, decoder: InstructionDecoder) {
        decoders[programId] = decoder
        programNames[programId] = name
    }
    
    /**
     * Get the decoder for a program.
     */
    fun getDecoder(programId: String): InstructionDecoder? = decoders[programId]
    
    /**
     * Get the human-readable name for a program.
     */
    fun getProgramName(programId: String): String {
        return programNames[programId] ?: "Unknown (${programId.take(8)}...)"
    }
    
    /**
     * Check if a program has a registered decoder.
     */
    fun hasDecoder(programId: String): Boolean = decoders.containsKey(programId)
    
    /**
     * Get a short display name for a public key.
     */
    fun getDisplayName(pubkey: String): String {
        return programNames[pubkey] ?: "${pubkey.take(4)}...${pubkey.takeLast(4)}"
    }
    
    /**
     * Check if an address is a known program.
     */
    fun isKnownProgram(pubkey: String): Boolean = programNames.containsKey(pubkey)
    
    /**
     * Mark a program as suspicious (for community-reported scams).
     */
    fun markSuspicious(programId: String) {
        suspiciousPrograms.add(programId)
    }
    
    /**
     * Check if a program has been marked as suspicious.
     */
    fun isSuspicious(programId: String): Boolean = suspiciousPrograms.contains(programId)
    
    /**
     * Get all registered program IDs.
     */
    fun getRegisteredPrograms(): Set<String> = programNames.keys.toSet()
}

/**
 * Interface for program-specific instruction decoders.
 */
interface InstructionDecoder {
    /**
     * Decode an instruction into a human-readable intent.
     * 
     * @param programId The program being called (Base58 string)
     * @param accounts The account pubkeys passed to the instruction (Base58 strings)
     * @param data The instruction data bytes
     * @param instructionIndex Index of this instruction in the transaction
     * @return Decoded intent, or null if decoding failed
     */
    fun decode(
        programId: String,
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent?
}
