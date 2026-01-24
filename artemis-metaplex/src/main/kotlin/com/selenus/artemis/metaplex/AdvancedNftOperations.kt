package com.selenus.artemis.metaplex

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import java.security.MessageDigest

/**
 * Artemis Advanced NFT Operations
 * 
 * Provides advanced NFT operations not commonly found in other SDKs:
 * - Batch minting and updates
 * - Dynamic metadata
 * - Collection management
 * - Royalty enforcement helpers
 * - Off-chain metadata caching
 * 
 * Designed for mobile-first NFT applications.
 */
object AdvancedNftOperations {
    
    private val SYSTEM_PROGRAM = Pubkey.fromBase58("11111111111111111111111111111111")
    private val TOKEN_PROGRAM = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    private val ASSOCIATED_TOKEN_PROGRAM = Pubkey.fromBase58("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
    private val METAPLEX_PROGRAM = Pubkey.fromBase58("metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s")
    
    // ========================================================================
    // Batch Operations
    // ========================================================================
    
    /**
     * Batch NFT mint configuration.
     */
    data class BatchMintConfig(
        val mints: List<MintConfig>,
        val collection: Pubkey?,
        val payer: Pubkey,
        val updateAuthority: Pubkey,
        val verifyCollection: Boolean = true
    ) {
        data class MintConfig(
            val name: String,
            val symbol: String,
            val uri: String,
            val sellerFeeBasisPoints: Int,
            val creators: List<TokenMetadataInstructions.Creator>?,
            val recipient: Pubkey
        )
    }
    
    /**
     * Result of a batch mint operation.
     */
    data class BatchMintResult(
        val mints: List<Pubkey>,
        val metadatas: List<Pubkey>,
        val instructions: List<Instruction>,
        val estimatedFee: Long
    )
    
    /**
     * Prepares instructions for batch minting NFTs.
     * 
     * Optimizes for transaction packing by grouping related instructions.
     * Returns multiple transaction sets if the batch is too large for one tx.
     */
    fun prepareBatchMint(config: BatchMintConfig): List<BatchMintResult> {
        val results = mutableListOf<BatchMintResult>()
        
        // Max ~4 mints per transaction due to compute limits
        val batchSize = 4
        
        config.mints.chunked(batchSize).forEach { batch ->
            val mints = mutableListOf<Pubkey>()
            val metadatas = mutableListOf<Pubkey>()
            val instructions = mutableListOf<Instruction>()
            
            for (mintConfig in batch) {
                // Generate mint keypair PDA
                val mintKeypair = generateMintAddress(
                    mintConfig.name,
                    config.payer
                )
                mints.add(mintKeypair)
                
                // Derive metadata PDA
                val metadataPda = MetadataPdas.metadataPda(mintKeypair)
                metadatas.add(metadataPda)
                
                // Derive ATA for recipient
                val ata = deriveAta(mintConfig.recipient, mintKeypair)
                
                // Create mint account
                instructions.add(createMintAccountInstruction(
                    mint = mintKeypair,
                    payer = config.payer
                ))
                
                // Create ATA
                instructions.add(createAtaInstruction(
                    ata = ata,
                    owner = mintConfig.recipient,
                    mint = mintKeypair,
                    payer = config.payer
                ))
                
                // Initialize mint
                instructions.add(initializeMintInstruction(
                    mint = mintKeypair,
                    mintAuthority = config.updateAuthority
                ))
                
                // Create metadata
                instructions.add(TokenMetadataInstructions.createMetadataAccountV3(
                    metadata = metadataPda,
                    mint = mintKeypair,
                    mintAuthority = config.updateAuthority,
                    payer = config.payer,
                    updateAuthority = config.updateAuthority,
                    data = TokenMetadataInstructions.DataV2(
                        name = mintConfig.name,
                        symbol = mintConfig.symbol,
                        uri = mintConfig.uri,
                        sellerFeeBasisPoints = mintConfig.sellerFeeBasisPoints,
                        creators = mintConfig.creators,
                        collection = config.collection?.let { 
                            TokenMetadataInstructions.Collection(false, it) 
                        }
                    )
                ))
                
                // Mint to recipient
                instructions.add(mintToInstruction(
                    mint = mintKeypair,
                    destination = ata,
                    authority = config.updateAuthority,
                    amount = 1
                ))
                
                // Verify collection if needed
                if (config.collection != null && config.verifyCollection) {
                    instructions.add(verifyCollectionInstruction(
                        metadata = metadataPda,
                        collectionAuthority = config.updateAuthority,
                        payer = config.payer,
                        collectionMint = config.collection,
                        collection = MetadataPdas.metadataPda(config.collection),
                        collectionMasterEdition = MetadataPdas.masterEditionPda(config.collection)
                    ))
                }
            }
            
            results.add(BatchMintResult(
                mints = mints,
                metadatas = metadatas,
                instructions = instructions,
                estimatedFee = batch.size * 10_000_000L // ~0.01 SOL per mint
            ))
        }
        
        return results
    }
    
    // ========================================================================
    // Dynamic Metadata
    // ========================================================================
    
    /**
     * Dynamic metadata that can change based on on-chain state.
     */
    data class DynamicMetadata(
        val baseUri: String,
        val stateHash: ByteArray,
        val traits: Map<String, DynamicTrait>
    ) {
        /**
         * A trait that can change dynamically.
         */
        data class DynamicTrait(
            val name: String,
            val value: String,
            val valueHash: ByteArray,
            val lastUpdated: Long
        )
        
        /**
         * Compute the current URI including state hash.
         */
        fun currentUri(): String {
            val stateHex = stateHash.toHexString().take(16)
            return "$baseUri?state=$stateHex"
        }
        
        /**
         * Update a trait value.
         */
        fun updateTrait(name: String, newValue: String): DynamicMetadata {
            val valueHash = sha256(newValue.toByteArray())
            val newTraits = traits.toMutableMap()
            newTraits[name] = DynamicTrait(
                name = name,
                value = newValue,
                valueHash = valueHash,
                lastUpdated = System.currentTimeMillis()
            )
            
            // Recompute state hash
            val newStateHash = computeStateHash(newTraits)
            
            return copy(traits = newTraits, stateHash = newStateHash)
        }
        
        private fun computeStateHash(traits: Map<String, DynamicTrait>): ByteArray {
            val sorted = traits.entries.sortedBy { it.key }
            val combined = sorted.flatMap { 
                it.value.valueHash.toList() 
            }.toByteArray()
            return sha256(combined)
        }
    }
    
    /**
     * Update metadata URI to reflect new dynamic state.
     */
    fun updateDynamicMetadata(
        metadata: Pubkey,
        updateAuthority: Pubkey,
        dynamicMetadata: DynamicMetadata
    ): Instruction {
        return TokenMetadataInstructions.updateMetadataAccountV2(
            metadata = metadata,
            updateAuthority = updateAuthority,
            data = TokenMetadataInstructions.DataV2(
                name = "",  // Keep existing
                symbol = "", // Keep existing
                uri = dynamicMetadata.currentUri(),
                sellerFeeBasisPoints = 0 // Keep existing
            )
        )
    }
    
    // ========================================================================
    // Collection Management
    // ========================================================================
    
    /**
     * Collection configuration.
     */
    data class CollectionConfig(
        val name: String,
        val symbol: String,
        val uri: String,
        val sellerFeeBasisPoints: Int,
        val maxSupply: Long?,
        val creators: List<TokenMetadataInstructions.Creator>
    )
    
    /**
     * Collection statistics.
     */
    data class CollectionStats(
        val mint: Pubkey,
        val totalMinted: Long,
        val maxSupply: Long?,
        val floorPrice: Long?,
        val uniqueHolders: Int,
        val verifiedCount: Long
    )
    
    /**
     * Creates a collection NFT.
     */
    fun createCollection(
        config: CollectionConfig,
        payer: Pubkey,
        updateAuthority: Pubkey,
        collectionMint: Pubkey
    ): List<Instruction> {
        val instructions = mutableListOf<Instruction>()
        
        val metadataPda = MetadataPdas.metadataPda(collectionMint)
        val masterEditionPda = MetadataPdas.masterEditionPda(collectionMint)
        val ata = deriveAta(updateAuthority, collectionMint)
        
        // Create mint account
        instructions.add(createMintAccountInstruction(
            mint = collectionMint,
            payer = payer
        ))
        
        // Create ATA
        instructions.add(createAtaInstruction(
            ata = ata,
            owner = updateAuthority,
            mint = collectionMint,
            payer = payer
        ))
        
        // Initialize mint
        instructions.add(initializeMintInstruction(
            mint = collectionMint,
            mintAuthority = updateAuthority
        ))
        
        // Create metadata
        instructions.add(TokenMetadataInstructions.createMetadataAccountV3(
            metadata = metadataPda,
            mint = collectionMint,
            mintAuthority = updateAuthority,
            payer = payer,
            updateAuthority = updateAuthority,
            data = TokenMetadataInstructions.DataV2(
                name = config.name,
                symbol = config.symbol,
                uri = config.uri,
                sellerFeeBasisPoints = config.sellerFeeBasisPoints,
                creators = config.creators,
                collection = null,
                uses = null
            ),
            collectionDetails = true
        ))
        
        // Mint collection NFT to authority
        instructions.add(mintToInstruction(
            mint = collectionMint,
            destination = ata,
            authority = updateAuthority,
            amount = 1
        ))
        
        // Create master edition (makes it non-fungible)
        instructions.add(createMasterEditionInstruction(
            masterEdition = masterEditionPda,
            mint = collectionMint,
            updateAuthority = updateAuthority,
            mintAuthority = updateAuthority,
            payer = payer,
            metadata = metadataPda,
            maxSupply = config.maxSupply
        ))
        
        return instructions
    }
    
    /**
     * Sets and verifies collection for an NFT.
     */
    fun setAndVerifyCollection(
        metadata: Pubkey,
        collectionAuthority: Pubkey,
        payer: Pubkey,
        updateAuthority: Pubkey,
        collectionMint: Pubkey
    ): Instruction {
        val collectionMetadata = MetadataPdas.metadataPda(collectionMint)
        val collectionMasterEdition = MetadataPdas.masterEditionPda(collectionMint)
        
        val body = java.io.ByteArrayOutputStream()
        body.write(25) // SetAndVerifyCollection instruction
        
        return Instruction(
            programId = METAPLEX_PROGRAM,
            accounts = listOf(
                AccountMeta(metadata, isSigner = false, isWritable = true),
                AccountMeta(collectionAuthority, isSigner = true, isWritable = false),
                AccountMeta(payer, isSigner = true, isWritable = true),
                AccountMeta(updateAuthority, isSigner = false, isWritable = false),
                AccountMeta(collectionMint, isSigner = false, isWritable = false),
                AccountMeta(collectionMetadata, isSigner = false, isWritable = false),
                AccountMeta(collectionMasterEdition, isSigner = false, isWritable = false)
            ),
            data = body.toByteArray()
        )
    }
    
    /**
     * Unverifies collection from an NFT.
     */
    fun unverifyCollection(
        metadata: Pubkey,
        collectionAuthority: Pubkey,
        collectionMint: Pubkey
    ): Instruction {
        val collectionMetadata = MetadataPdas.metadataPda(collectionMint)
        
        val body = java.io.ByteArrayOutputStream()
        body.write(22) // UnverifyCollection instruction
        
        return Instruction(
            programId = METAPLEX_PROGRAM,
            accounts = listOf(
                AccountMeta(metadata, isSigner = false, isWritable = true),
                AccountMeta(collectionAuthority, isSigner = true, isWritable = false),
                AccountMeta(collectionMint, isSigner = false, isWritable = false),
                AccountMeta(collectionMetadata, isSigner = false, isWritable = false)
            ),
            data = body.toByteArray()
        )
    }
    
    // ========================================================================
    // Royalty Enforcement
    // ========================================================================
    
    /**
     * Royalty configuration.
     */
    data class RoyaltyConfig(
        val sellerFeeBasisPoints: Int, // 0-10000 (0-100%)
        val creators: List<TokenMetadataInstructions.Creator>
    ) {
        init {
            require(sellerFeeBasisPoints in 0..10000) {
                "Seller fee must be 0-10000 basis points"
            }
            require(creators.sumOf { it.share } == 100) {
                "Creator shares must sum to 100"
            }
        }
        
        /**
         * Calculate royalty amount for a sale.
         */
        fun calculateRoyalty(salePrice: Long): Long {
            return salePrice * sellerFeeBasisPoints / 10000
        }
        
        /**
         * Calculate each creator's share of royalties.
         */
        fun calculateCreatorShares(salePrice: Long): Map<Pubkey, Long> {
            val totalRoyalty = calculateRoyalty(salePrice)
            return creators.associate { creator ->
                creator.address to (totalRoyalty * creator.share / 100)
            }
        }
    }
    
    /**
     * Prepares royalty payment instructions.
     */
    fun prepareRoyaltyPayment(
        royaltyConfig: RoyaltyConfig,
        salePrice: Long,
        payer: Pubkey
    ): List<Instruction> {
        val shares = royaltyConfig.calculateCreatorShares(salePrice)
        
        return shares.filter { it.value > 0 }.map { (creator, amount) ->
            transferSolInstruction(
                from = payer,
                to = creator,
                lamports = amount
            )
        }
    }
    
    // ========================================================================
    // Metadata Caching
    // ========================================================================
    
    /**
     * Cached metadata entry.
     */
    data class CachedMetadata(
        val mint: Pubkey,
        val metadata: MetadataData,
        val offchainData: OffchainMetadata?,
        val fetchedAt: Long,
        val expiresAt: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    }
    
    /**
     * Off-chain metadata from URI.
     */
    data class OffchainMetadata(
        val name: String,
        val description: String?,
        val image: String?,
        val animationUrl: String?,
        val externalUrl: String?,
        val attributes: List<Attribute>
    ) {
        data class Attribute(
            val traitType: String,
            val value: String,
            val displayType: String?
        )
    }
    
    /**
     * Simple in-memory metadata cache.
     */
    class MetadataCache(
        private val maxSize: Int = 1000,
        private val ttlMs: Long = 5 * 60 * 1000 // 5 minutes
    ) {
        private val cache = LinkedHashMap<Pubkey, CachedMetadata>(
            maxSize + 1, 0.75f, true
        )
        
        @Synchronized
        fun get(mint: Pubkey): CachedMetadata? {
            val cached = cache[mint] ?: return null
            return if (cached.isExpired()) {
                cache.remove(mint)
                null
            } else {
                cached
            }
        }
        
        @Synchronized
        fun put(mint: Pubkey, metadata: MetadataData, offchain: OffchainMetadata? = null) {
            val now = System.currentTimeMillis()
            cache[mint] = CachedMetadata(
                mint = mint,
                metadata = metadata,
                offchainData = offchain,
                fetchedAt = now,
                expiresAt = now + ttlMs
            )
            
            // Evict oldest if over capacity
            while (cache.size > maxSize) {
                val oldest = cache.entries.first()
                cache.remove(oldest.key)
            }
        }
        
        @Synchronized
        fun invalidate(mint: Pubkey) {
            cache.remove(mint)
        }
        
        @Synchronized
        fun clear() {
            cache.clear()
        }
    }
    
    // ========================================================================
    // Internal Helpers
    // ========================================================================
    
    private fun generateMintAddress(name: String, payer: Pubkey): Pubkey {
        // In production, use actual keypair generation
        val seed = sha256(name.toByteArray() + payer.bytes + System.nanoTime().toByteArray())
        return Pubkey(seed)
    }
    
    private fun deriveAta(owner: Pubkey, mint: Pubkey): Pubkey {
        // Simplified ATA derivation
        val seeds = owner.bytes + TOKEN_PROGRAM.bytes + mint.bytes
        return Pubkey(sha256(seeds + ASSOCIATED_TOKEN_PROGRAM.bytes))
    }
    
    private fun createMintAccountInstruction(mint: Pubkey, payer: Pubkey): Instruction {
        // System program create account for mint
        val space = 82L
        val lamports = 1_461_600L // Rent exempt for mint
        
        val data = ByteArray(52)
        // Instruction 0: CreateAccount
        writeU32LE(data, 0, 0)
        writeU64LE(data, 4, lamports)
        writeU64LE(data, 12, space)
        System.arraycopy(TOKEN_PROGRAM.bytes, 0, data, 20, 32)
        
        return Instruction(
            programId = SYSTEM_PROGRAM,
            accounts = listOf(
                AccountMeta(payer, isSigner = true, isWritable = true),
                AccountMeta(mint, isSigner = true, isWritable = true)
            ),
            data = data
        )
    }
    
    private fun createAtaInstruction(
        ata: Pubkey,
        owner: Pubkey,
        mint: Pubkey,
        payer: Pubkey
    ): Instruction {
        return Instruction(
            programId = ASSOCIATED_TOKEN_PROGRAM,
            accounts = listOf(
                AccountMeta(payer, isSigner = true, isWritable = true),
                AccountMeta(ata, isSigner = false, isWritable = true),
                AccountMeta(owner, isSigner = false, isWritable = false),
                AccountMeta(mint, isSigner = false, isWritable = false),
                AccountMeta(SYSTEM_PROGRAM, isSigner = false, isWritable = false),
                AccountMeta(TOKEN_PROGRAM, isSigner = false, isWritable = false)
            ),
            data = ByteArray(0)
        )
    }
    
    private fun initializeMintInstruction(mint: Pubkey, mintAuthority: Pubkey): Instruction {
        val data = ByteArray(35)
        data[0] = 0 // InitializeMint
        data[1] = 0 // Decimals = 0 for NFT
        System.arraycopy(mintAuthority.bytes, 0, data, 2, 32)
        data[34] = 0 // No freeze authority option
        
        return Instruction(
            programId = TOKEN_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true),
                AccountMeta(Pubkey.fromBase58("SysvarRent111111111111111111111111111111111"), 
                           isSigner = false, isWritable = false)
            ),
            data = data
        )
    }
    
    private fun mintToInstruction(
        mint: Pubkey,
        destination: Pubkey,
        authority: Pubkey,
        amount: Long
    ): Instruction {
        val data = ByteArray(9)
        data[0] = 7 // MintTo
        writeU64LE(data, 1, amount)
        
        return Instruction(
            programId = TOKEN_PROGRAM,
            accounts = listOf(
                AccountMeta(mint, isSigner = false, isWritable = true),
                AccountMeta(destination, isSigner = false, isWritable = true),
                AccountMeta(authority, isSigner = true, isWritable = false)
            ),
            data = data
        )
    }
    
    private fun verifyCollectionInstruction(
        metadata: Pubkey,
        collectionAuthority: Pubkey,
        payer: Pubkey,
        collectionMint: Pubkey,
        collection: Pubkey,
        collectionMasterEdition: Pubkey
    ): Instruction {
        val body = java.io.ByteArrayOutputStream()
        body.write(18) // VerifyCollection
        
        return Instruction(
            programId = METAPLEX_PROGRAM,
            accounts = listOf(
                AccountMeta(metadata, isSigner = false, isWritable = true),
                AccountMeta(collectionAuthority, isSigner = true, isWritable = false),
                AccountMeta(payer, isSigner = true, isWritable = true),
                AccountMeta(collectionMint, isSigner = false, isWritable = false),
                AccountMeta(collection, isSigner = false, isWritable = false),
                AccountMeta(collectionMasterEdition, isSigner = false, isWritable = false)
            ),
            data = body.toByteArray()
        )
    }
    
    private fun createMasterEditionInstruction(
        masterEdition: Pubkey,
        mint: Pubkey,
        updateAuthority: Pubkey,
        mintAuthority: Pubkey,
        payer: Pubkey,
        metadata: Pubkey,
        maxSupply: Long?
    ): Instruction {
        val body = java.io.ByteArrayOutputStream()
        body.write(17) // CreateMasterEditionV3
        
        if (maxSupply != null) {
            body.write(1)
            val supplyBytes = ByteArray(8)
            writeU64LE(supplyBytes, 0, maxSupply)
            body.write(supplyBytes)
        } else {
            body.write(0)
        }
        
        return Instruction(
            programId = METAPLEX_PROGRAM,
            accounts = listOf(
                AccountMeta(masterEdition, isSigner = false, isWritable = true),
                AccountMeta(mint, isSigner = false, isWritable = true),
                AccountMeta(updateAuthority, isSigner = true, isWritable = false),
                AccountMeta(mintAuthority, isSigner = true, isWritable = false),
                AccountMeta(payer, isSigner = true, isWritable = true),
                AccountMeta(metadata, isSigner = false, isWritable = false),
                AccountMeta(TOKEN_PROGRAM, isSigner = false, isWritable = false),
                AccountMeta(SYSTEM_PROGRAM, isSigner = false, isWritable = false),
                AccountMeta(Pubkey.fromBase58("SysvarRent111111111111111111111111111111111"), 
                           isSigner = false, isWritable = false)
            ),
            data = body.toByteArray()
        )
    }
    
    private fun transferSolInstruction(from: Pubkey, to: Pubkey, lamports: Long): Instruction {
        val data = ByteArray(12)
        writeU32LE(data, 0, 2) // Transfer instruction
        writeU64LE(data, 4, lamports)
        
        return Instruction(
            programId = SYSTEM_PROGRAM,
            accounts = listOf(
                AccountMeta(from, isSigner = true, isWritable = true),
                AccountMeta(to, isSigner = false, isWritable = true)
            ),
            data = data
        )
    }
    
    private fun sha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
    
    private fun writeU32LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
    
    private fun writeU64LE(buffer: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) {
            buffer[offset + i] = ((value shr (i * 8)) and 0xFF).toByte()
        }
    }
    
    private fun Long.toByteArray(): ByteArray {
        return ByteArray(8) { i -> ((this shr (i * 8)) and 0xFF).toByte() }
    }
    
    private fun List<Byte>.toByteArray(): ByteArray {
        return ByteArray(this.size) { this[it] }
    }
    
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
