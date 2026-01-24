package com.selenus.artemis.gaming

import com.selenus.artemis.runtime.Pubkey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Artemis Gaming Reward Distribution System
 * 
 * Provides flexible, fair, and gas-efficient reward distribution for games.
 * Supports various distribution strategies from winner-take-all to complex
 * tournament payout structures.
 * 
 * Features:
 * - Multiple payout strategies (linear, exponential, custom)
 * - Merkle-based claim system for gas efficiency
 * - Anti-sybil protections
 * - Streamed rewards for ongoing games
 * - NFT reward integration
 * 
 * Use Cases:
 * - Tournament prizes
 * - Daily/weekly rewards
 * - Achievement unlocks
 * - Leaderboard payouts
 * - Play-to-earn distributions
 * 
 * NOT available in standard Solana SDKs.
 */
object RewardDistribution {
    
    private val random = SecureRandom()
    private const val DOMAIN_SEPARATOR = "artemis:rewards:v1"
    
    // ========================================================================
    // Payout Strategies
    // ========================================================================
    
    /**
     * Interface for payout calculation strategies.
     */
    interface PayoutStrategy {
        /**
         * Calculate payouts for ranked participants.
         * 
         * @param totalPrize The total prize pool
         * @param participants Number of participants
         * @param paidPlaces Number of places that receive payouts
         * @return Map of rank (1-indexed) to payout amount
         */
        fun calculate(
            totalPrize: Long,
            participants: Int,
            paidPlaces: Int
        ): Map<Int, Long>
    }
    
    /**
     * Winner-takes-all strategy.
     */
    object WinnerTakesAll : PayoutStrategy {
        override fun calculate(totalPrize: Long, participants: Int, paidPlaces: Int): Map<Int, Long> {
            return mapOf(1 to totalPrize)
        }
    }
    
    /**
     * Equal distribution among top N.
     */
    class EqualSplit(private val topN: Int) : PayoutStrategy {
        override fun calculate(totalPrize: Long, participants: Int, paidPlaces: Int): Map<Int, Long> {
            val actualPaid = minOf(topN, participants, paidPlaces)
            val perPlayer = totalPrize / actualPaid
            val remainder = totalPrize % actualPaid
            
            return (1..actualPaid).associate { rank ->
                // First place gets any remainder
                rank to (perPlayer + if (rank == 1) remainder else 0)
            }
        }
    }
    
    /**
     * Linear decay: each position gets less than the previous.
     */
    class LinearDecay(
        private val topN: Int,
        private val decayFactor: Double = 0.9
    ) : PayoutStrategy {
        override fun calculate(totalPrize: Long, participants: Int, paidPlaces: Int): Map<Int, Long> {
            val actualPaid = minOf(topN, participants, paidPlaces)
            
            // Calculate weights: 1, decay, decay^2, ...
            var totalWeight = 0.0
            val weights = mutableListOf<Double>()
            var weight = 1.0
            
            for (i in 0 until actualPaid) {
                weights.add(weight)
                totalWeight += weight
                weight *= decayFactor
            }
            
            // Distribute based on weights
            return weights.mapIndexed { index, w ->
                (index + 1) to (totalPrize * w / totalWeight).toLong()
            }.toMap()
        }
    }
    
    /**
     * Exponential payout (top heavy).
     */
    class ExponentialPayout(
        private val topN: Int,
        private val base: Double = 2.0
    ) : PayoutStrategy {
        override fun calculate(totalPrize: Long, participants: Int, paidPlaces: Int): Map<Int, Long> {
            val actualPaid = minOf(topN, participants, paidPlaces)
            
            // Weights: base^(n-1), base^(n-2), ..., base^0
            val weights: List<Double> = (0 until actualPaid).map { i: Int -> 
                Math.pow(base, (actualPaid - 1 - i).toDouble()) 
            }
            val totalWeight: Double = weights.sum()
            
            return weights.mapIndexed { index: Int, w: Double ->
                (index + 1) to (totalPrize * w / totalWeight).toLong()
            }.toMap()
        }
    }
    
    /**
     * Poker tournament style (steeper curve at top).
     */
    object PokerStyle : PayoutStrategy {
        // Standard poker payout percentages for various field sizes
        private val payoutTables = mapOf(
            // 2-6 players
            6 to listOf(0.65, 0.35),
            // 7-12 players
            12 to listOf(0.50, 0.30, 0.20),
            // 13-27 players
            27 to listOf(0.40, 0.25, 0.18, 0.10, 0.07),
            // 28-45 players
            45 to listOf(0.35, 0.22, 0.15, 0.10, 0.08, 0.06, 0.04),
            // 46+ players
            1000 to listOf(0.30, 0.20, 0.14, 0.10, 0.08, 0.06, 0.05, 0.04, 0.03)
        )
        
        override fun calculate(totalPrize: Long, participants: Int, paidPlaces: Int): Map<Int, Long> {
            val table = payoutTables.entries
                .filter { it.key >= participants }
                .minByOrNull { it.key }?.value
                ?: payoutTables[1000]!!
            
            val actualPaid = minOf(table.size, participants, paidPlaces)
            
            var distributed = 0L
            return (1..actualPaid).associate { rank ->
                val payout = if (rank == actualPaid) {
                    // Last paid position gets remainder
                    totalPrize - distributed
                } else {
                    (totalPrize * table[rank - 1]).toLong()
                }
                distributed += payout
                rank to payout
            }
        }
    }
    
    /**
     * Custom percentage-based payouts.
     */
    class CustomPercentages(
        private val percentages: List<Double> // Must sum to 1.0
    ) : PayoutStrategy {
        init {
            require(percentages.isNotEmpty()) { "Need at least one payout percentage" }
            require(kotlin.math.abs(percentages.sum() - 1.0) < 0.001) {
                "Percentages must sum to 1.0"
            }
        }
        
        override fun calculate(totalPrize: Long, participants: Int, paidPlaces: Int): Map<Int, Long> {
            val actualPaid = minOf(percentages.size, participants, paidPlaces)
            
            var distributed = 0L
            return (1..actualPaid).associate { rank ->
                val payout = if (rank == actualPaid) {
                    totalPrize - distributed
                } else {
                    (totalPrize * percentages[rank - 1]).toLong()
                }
                distributed += payout
                rank to payout
            }
        }
    }
    
    // ========================================================================
    // Reward Claims
    // ========================================================================
    
    /**
     * A reward claim for a player.
     */
    data class RewardClaim(
        val player: Pubkey,
        val amount: Long,
        val tokenMint: Pubkey?,    // null for SOL
        val gameId: ByteArray,
        val rank: Int,
        val timestamp: Long,
        val nonce: Long
    ) {
        /**
         * Compute the leaf hash for Merkle tree.
         */
        fun computeLeaf(): ByteArray {
            val tokenBytes = tokenMint?.bytes ?: ByteArray(32)
            return sha256(
                DOMAIN_SEPARATOR.toByteArray() +
                "claim".toByteArray() +
                player.bytes +
                amount.toByteArray() +
                tokenBytes +
                gameId +
                rank.toByteArray() +
                timestamp.toByteArray() +
                nonce.toByteArray()
            )
        }
        
        /**
         * Serialize for on-chain claiming.
         */
        fun serialize(): ByteArray {
            val hasToken = if (tokenMint != null) 1 else 0
            val size = 32 + 8 + 1 + (if (hasToken == 1) 32 else 0) + 32 + 4 + 8 + 8
            val buffer = ByteArray(size)
            var offset = 0
            
            System.arraycopy(player.bytes, 0, buffer, offset, 32)
            offset += 32
            
            writeLongLE(buffer, offset, amount)
            offset += 8
            
            buffer[offset++] = hasToken.toByte()
            if (tokenMint != null) {
                System.arraycopy(tokenMint.bytes, 0, buffer, offset, 32)
                offset += 32
            }
            
            System.arraycopy(gameId, 0, buffer, offset, 32)
            offset += 32
            
            writeIntLE(buffer, offset, rank)
            offset += 4
            
            writeLongLE(buffer, offset, timestamp)
            offset += 8
            
            writeLongLE(buffer, offset, nonce)
            
            return buffer
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RewardClaim) return false
            return player == other.player && 
                   gameId.contentEquals(other.gameId) && 
                   nonce == other.nonce
        }
        
        override fun hashCode(): Int = player.hashCode() + gameId.contentHashCode()
    }
    
    /**
     * A Merkle tree of reward claims.
     */
    data class RewardTree(
        val root: ByteArray,
        val claims: List<RewardClaim>,
        val proofs: Map<Pubkey, List<ByteArray>>
    ) {
        /**
         * Get the proof for a specific player.
         */
        fun getProof(player: Pubkey): List<ByteArray>? = proofs[player]
        
        /**
         * Verify a claim against the root.
         */
        fun verifyClaim(claim: RewardClaim, proof: List<ByteArray>): Boolean {
            return MerkleDistributor.verify(proof, root, claim.computeLeaf())
        }
    }
    
    /**
     * Build a Merkle tree from reward claims.
     */
    fun buildRewardTree(claims: List<RewardClaim>): RewardTree {
        if (claims.isEmpty()) {
            return RewardTree(ByteArray(32), emptyList(), emptyMap())
        }
        
        val leaves = claims.map { it.computeLeaf() }
        val root = buildMerkleRoot(leaves)
        val proofs = claims.mapIndexed { index, claim ->
            claim.player to generateMerkleProof(leaves, index)
        }.toMap()
        
        return RewardTree(root, claims, proofs)
    }
    
    // ========================================================================
    // Streamed Rewards
    // ========================================================================
    
    /**
     * A streamed reward that vests over time.
     */
    data class StreamedReward(
        val player: Pubkey,
        val totalAmount: Long,
        val tokenMint: Pubkey?,
        val startTime: Long,
        val endTime: Long,
        val claimedAmount: Long = 0
    ) {
        /**
         * Calculate the currently claimable amount.
         */
        fun claimableAmount(currentTime: Long = System.currentTimeMillis()): Long {
            if (currentTime <= startTime) return 0
            if (currentTime >= endTime) return totalAmount - claimedAmount
            
            val elapsed = currentTime - startTime
            val duration = endTime - startTime
            val vested = (totalAmount * elapsed / duration)
            
            return maxOf(0, vested - claimedAmount)
        }
        
        /**
         * Claim available rewards.
         */
        fun claim(amount: Long): StreamedReward {
            require(amount <= claimableAmount()) { "Insufficient claimable amount" }
            return copy(claimedAmount = claimedAmount + amount)
        }
        
        /**
         * Check if the stream is complete.
         */
        fun isComplete(): Boolean = claimedAmount >= totalAmount
        
        /**
         * Serialize for on-chain storage.
         */
        fun serialize(): ByteArray {
            val hasToken = if (tokenMint != null) 1 else 0
            val size = 32 + 8 + 1 + (if (hasToken == 1) 32 else 0) + 8 + 8 + 8
            val buffer = ByteArray(size)
            var offset = 0
            
            System.arraycopy(player.bytes, 0, buffer, offset, 32)
            offset += 32
            
            writeLongLE(buffer, offset, totalAmount)
            offset += 8
            
            buffer[offset++] = hasToken.toByte()
            if (tokenMint != null) {
                System.arraycopy(tokenMint.bytes, 0, buffer, offset, 32)
                offset += 32
            }
            
            writeLongLE(buffer, offset, startTime)
            offset += 8
            
            writeLongLE(buffer, offset, endTime)
            offset += 8
            
            writeLongLE(buffer, offset, claimedAmount)
            
            return buffer
        }
    }
    
    /**
     * Creates streamed rewards from a payout.
     */
    fun createStreamedRewards(
        payouts: Map<Pubkey, Long>,
        tokenMint: Pubkey?,
        vestingDurationMs: Long
    ): List<StreamedReward> {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + vestingDurationMs
        
        return payouts.map { (player, amount) ->
            StreamedReward(
                player = player,
                totalAmount = amount,
                tokenMint = tokenMint,
                startTime = startTime,
                endTime = endTime
            )
        }
    }
    
    // ========================================================================
    // NFT Rewards
    // ========================================================================
    
    /**
     * An NFT reward for game achievements.
     */
    data class NftReward(
        val player: Pubkey,
        val nftMint: Pubkey?,       // null if not yet minted
        val collectionMint: Pubkey,
        val metadata: NftMetadata,
        val unlocked: Boolean = false
    ) {
        data class NftMetadata(
            val name: String,
            val symbol: String,
            val uri: String,
            val attributes: Map<String, String>
        )
        
        /**
         * Serialize metadata for on-chain creation.
         */
        fun serializeMetadata(): ByteArray {
            val nameBytes = metadata.name.toByteArray()
            val symbolBytes = metadata.symbol.toByteArray()
            val uriBytes = metadata.uri.toByteArray()
            
            val size = 4 + nameBytes.size + 4 + symbolBytes.size + 4 + uriBytes.size
            val buffer = ByteArray(size)
            var offset = 0
            
            writeIntLE(buffer, offset, nameBytes.size)
            offset += 4
            System.arraycopy(nameBytes, 0, buffer, offset, nameBytes.size)
            offset += nameBytes.size
            
            writeIntLE(buffer, offset, symbolBytes.size)
            offset += 4
            System.arraycopy(symbolBytes, 0, buffer, offset, symbolBytes.size)
            offset += symbolBytes.size
            
            writeIntLE(buffer, offset, uriBytes.size)
            offset += 4
            System.arraycopy(uriBytes, 0, buffer, offset, uriBytes.size)
            
            return buffer
        }
    }
    
    /**
     * Achievement-based NFT unlocks.
     */
    data class Achievement(
        val id: String,
        val name: String,
        val description: String,
        val condition: AchievementCondition,
        val nftReward: NftReward.NftMetadata?
    ) {
        interface AchievementCondition {
            fun check(playerStats: Map<String, Long>): Boolean
        }
        
        class ThresholdCondition(
            private val stat: String,
            private val threshold: Long
        ) : AchievementCondition {
            override fun check(playerStats: Map<String, Long>): Boolean {
                return (playerStats[stat] ?: 0) >= threshold
            }
        }
        
        class MultiCondition(
            private val conditions: List<AchievementCondition>,
            private val requireAll: Boolean = true
        ) : AchievementCondition {
            override fun check(playerStats: Map<String, Long>): Boolean {
                return if (requireAll) {
                    conditions.all { it.check(playerStats) }
                } else {
                    conditions.any { it.check(playerStats) }
                }
            }
        }
    }
    
    // ========================================================================
    // Anti-Sybil Protection
    // ========================================================================
    
    /**
     * Checks for potential sybil attacks in reward distribution.
     */
    data class SybilCheck(
        val player: Pubkey,
        val score: Double,         // 0-1, higher = more suspicious
        val flags: Set<SybilFlag>
    ) {
        enum class SybilFlag {
            NEW_ACCOUNT,           // Account created recently
            LOW_ACTIVITY,          // Little on-chain history
            CLUSTER_DETECTED,      // Part of connected wallet cluster
            SAME_IP,               // Multiple accounts from same IP
            TIMING_CORRELATION,    // Actions correlated with other accounts
            FUNDING_SOURCE         // Funded by known sybil source
        }
    }
    
    /**
     * Perform basic sybil analysis on participants.
     */
    fun analyzeSybilRisk(
        participants: List<Pubkey>,
        accountAges: Map<Pubkey, Long>,
        transactionCounts: Map<Pubkey, Int>
    ): List<SybilCheck> {
        val minAge = 7 * 24 * 60 * 60 * 1000L // 7 days
        val minTxCount = 10
        
        return participants.map { player ->
            val flags = mutableSetOf<SybilCheck.SybilFlag>()
            var score = 0.0
            
            val age = accountAges[player] ?: 0
            if (age < minAge) {
                flags.add(SybilCheck.SybilFlag.NEW_ACCOUNT)
                score += 0.3
            }
            
            val txCount = transactionCounts[player] ?: 0
            if (txCount < minTxCount) {
                flags.add(SybilCheck.SybilFlag.LOW_ACTIVITY)
                score += 0.2
            }
            
            SybilCheck(player, minOf(1.0, score), flags)
        }
    }
    
    // ========================================================================
    // Internal Helpers
    // ========================================================================
    
    private fun buildMerkleRoot(leaves: List<ByteArray>): ByteArray {
        if (leaves.isEmpty()) return ByteArray(32)
        if (leaves.size == 1) return leaves[0]
        
        val nextLevel = mutableListOf<ByteArray>()
        for (i in leaves.indices step 2) {
            val left = leaves[i]
            val right = if (i + 1 < leaves.size) leaves[i + 1] else left
            
            val combined = if (compare(left, right) <= 0) {
                sha256(left + right)
            } else {
                sha256(right + left)
            }
            nextLevel.add(combined)
        }
        
        return buildMerkleRoot(nextLevel)
    }
    
    private fun generateMerkleProof(leaves: List<ByteArray>, index: Int): List<ByteArray> {
        if (leaves.size <= 1) return emptyList()
        
        val proof = mutableListOf<ByteArray>()
        var currentLeaves = leaves
        var currentIndex = index
        
        while (currentLeaves.size > 1) {
            val siblingIndex = if (currentIndex % 2 == 0) currentIndex + 1 else currentIndex - 1
            if (siblingIndex < currentLeaves.size) {
                proof.add(currentLeaves[siblingIndex])
            }
            
            val nextLevel = mutableListOf<ByteArray>()
            for (i in currentLeaves.indices step 2) {
                val left = currentLeaves[i]
                val right = if (i + 1 < currentLeaves.size) currentLeaves[i + 1] else left
                
                val combined = if (compare(left, right) <= 0) {
                    sha256(left + right)
                } else {
                    sha256(right + left)
                }
                nextLevel.add(combined)
            }
            
            currentLeaves = nextLevel
            currentIndex /= 2
        }
        
        return proof
    }
    
    private fun compare(a: ByteArray, b: ByteArray): Int {
        for (i in a.indices) {
            val av = a[i].toInt() and 0xFF
            val bv = b[i].toInt() and 0xFF
            if (av != bv) return av - bv
        }
        return 0
    }
    
    private fun sha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
    
    private fun Long.toByteArray(): ByteArray {
        return ByteArray(8) { i -> ((this shr (i * 8)) and 0xFF).toByte() }
    }
    
    private fun Int.toByteArray(): ByteArray {
        return ByteArray(4) { i -> ((this shr (i * 8)) and 0xFF).toByte() }
    }
    
    private fun writeLongLE(buffer: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) {
            buffer[offset + i] = ((value shr (i * 8)) and 0xFF).toByte()
        }
    }
    
    private fun writeIntLE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
    
    private fun String.toByteArray(): ByteArray = this.encodeToByteArray()
    
    /**
     * Distributes rewards for ranked players.
     */
    fun distributeToPlayers(
        rankedPlayers: List<Pair<Pubkey, Long>>, // (pubkey, score)
        totalPrize: Long,
        strategy: PayoutStrategy,
        gameId: String,
        tokenMint: Pubkey? = null
    ): List<RewardClaim> {
        val sorted = rankedPlayers.sortedByDescending { it.second }
        val payouts = strategy.calculate(totalPrize, sorted.size, sorted.size)
        
        return sorted.mapIndexedNotNull { index: Int, (pubkey, _) ->
            val rank = index + 1
            val amount = payouts[rank] ?: return@mapIndexedNotNull null
            
            RewardClaim(
                player = pubkey,
                amount = amount,
                tokenMint = tokenMint,
                gameId = gameId.encodeToByteArray(),
                rank = rank,
                timestamp = System.currentTimeMillis(),
                nonce = System.nanoTime()
            )
        }
    }
}
