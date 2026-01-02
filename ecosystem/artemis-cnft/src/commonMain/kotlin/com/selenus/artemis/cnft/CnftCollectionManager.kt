package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Crypto
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.currentTimeMillis
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * CnftCollectionManager - Manage compressed NFT collections efficiently
 * 
 * Features:
 * - Batch minting with concurrency control
 * - Collection indexing and caching
 * - Merkle proof management
 * - Asset metadata handling
 * - Transfer batch operations
 */
class CnftCollectionManager(
    private val scope: CoroutineScope,
    private val config: Config = Config()
) {
    data class Config(
        val maxConcurrentMints: Int = 5,
        val maxConcurrentTransfers: Int = 10,
        val cacheSize: Int = 1000,
        val proofCacheTtlMs: Long = 60_000L,
        val enableMetadataCache: Boolean = true
    )

    /**
     * Compressed NFT asset representation
     */
    data class CnftAsset(
        val id: String,
        val owner: Pubkey,
        val delegate: Pubkey?,
        val merkleTree: Pubkey,
        val leafIndex: Long,
        val dataHash: ByteArray,
        val creatorHash: ByteArray,
        val nonce: Long,
        val metadata: CnftMetadata?,
        val proofPath: List<ByteArray>?,
        val lastRefreshMs: Long = currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CnftAsset) return false
            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()
    }

    /**
     * Asset metadata
     */
    data class CnftMetadata(
        val name: String,
        val symbol: String,
        val uri: String,
        val sellerFeeBasisPoints: Int,
        val creators: List<Creator>,
        val collection: CollectionInfo?,
        val uses: Uses?,
        val attributes: List<Attribute>
    )

    data class Creator(
        val address: Pubkey,
        val verified: Boolean,
        val share: Int
    )

    data class CollectionInfo(
        val verified: Boolean,
        val key: Pubkey
    )

    data class Uses(
        val useMethod: UseMethod,
        val remaining: Long,
        val total: Long
    )

    enum class UseMethod {
        BURN,
        MULTIPLE,
        SINGLE
    }

    data class Attribute(
        val traitType: String,
        val value: String
    )

    /**
     * Mint request for batch minting
     */
    data class MintRequest(
        val recipient: Pubkey,
        val metadata: CnftMetadata,
        val nonce: Long? = null,
        val delegate: Pubkey? = null
    )

    /**
     * Mint result
     */
    sealed class MintResult {
        data class Success(
            val asset: CnftAsset,
            val signature: String,
            val slot: Long
        ) : MintResult()

        data class Failure(
            val request: MintRequest,
            val error: String,
            val retryable: Boolean
        ) : MintResult()
    }

    /**
     * Transfer request
     */
    data class TransferRequest(
        val assetId: String,
        val from: Pubkey,
        val to: Pubkey,
        val delegate: Pubkey? = null
    )

    /**
     * Transfer result
     */
    sealed class TransferResult {
        data class Success(
            val assetId: String,
            val signature: String,
            val newOwner: Pubkey
        ) : TransferResult()

        data class Failure(
            val request: TransferRequest,
            val error: String,
            val retryable: Boolean
        ) : TransferResult()
    }

    // Caches
    private val assetCache = mutableMapOf<String, CnftAsset>()
    private val proofCache = mutableMapOf<String, CachedProof>()
    private val collectionCache = mutableMapOf<Pubkey, CollectionStats>()
    private val mutex = Mutex()

    private data class CachedProof(
        val proof: List<ByteArray>,
        val cachedAtMs: Long
    )

    // Collection statistics
    data class CollectionStats(
        val merkleTree: Pubkey,
        val totalMinted: Long,
        val authority: Pubkey?,
        val maxDepth: Int,
        val maxBufferSize: Int,
        val canopyDepth: Int,
        val lastRefreshMs: Long
    )

    // Observable streams
    private val _mintProgress = MutableSharedFlow<MintProgress>(extraBufferCapacity = 64)
    val mintProgress: SharedFlow<MintProgress> = _mintProgress.asSharedFlow()

    private val _transferProgress = MutableSharedFlow<TransferProgress>(extraBufferCapacity = 64)
    val transferProgress: SharedFlow<TransferProgress> = _transferProgress.asSharedFlow()

    data class MintProgress(
        val completed: Int,
        val total: Int,
        val currentAsset: CnftAsset?,
        val errors: Int
    )

    data class TransferProgress(
        val completed: Int,
        val total: Int,
        val currentAssetId: String?,
        val errors: Int
    )

    // Semaphores for concurrency control
    private val mintSemaphore = Semaphore(config.maxConcurrentMints)
    private val transferSemaphore = Semaphore(config.maxConcurrentTransfers)

    /**
     * Batch mint cNFTs
     */
    suspend fun batchMint(
        merkleTree: Pubkey,
        authority: Pubkey,
        requests: List<MintRequest>,
        minter: suspend (MintRequest, Pubkey, Pubkey) -> MintResult
    ): BatchMintResult = coroutineScope {
        val results = mutableListOf<MintResult>()
        val counterMutex = Mutex()
        var completedCount = 0
        var errorsCount = 0

        val jobs = requests.map { request ->
            async {
                mintSemaphore.withPermit {
                    val result = try {
                        minter(request, merkleTree, authority)
                    } catch (e: Exception) {
                        MintResult.Failure(request, e.message ?: "Unknown error", true)
                    }

                    when (result) {
                        is MintResult.Success -> {
                            counterMutex.withLock { completedCount++ }
                            cacheAsset(result.asset)
                        }
                        is MintResult.Failure -> {
                            counterMutex.withLock { errorsCount++ }
                        }
                    }

                    _mintProgress.tryEmit(
                        MintProgress(
                            completed = counterMutex.withLock { completedCount },
                            total = requests.size,
                            currentAsset = (result as? MintResult.Success)?.asset,
                            errors = counterMutex.withLock { errorsCount }
                        )
                    )

                    result
                }
            }
        }

        results.addAll(jobs.awaitAll())

        BatchMintResult(
            totalRequested = requests.size,
            successful = results.filterIsInstance<MintResult.Success>(),
            failed = results.filterIsInstance<MintResult.Failure>()
        )
    }

    data class BatchMintResult(
        val totalRequested: Int,
        val successful: List<MintResult.Success>,
        val failed: List<MintResult.Failure>
    ) {
        val successCount: Int get() = successful.size
        val failureCount: Int get() = failed.size
        val successRate: Float get() = successCount.toFloat() / totalRequested
    }

    /**
     * Batch transfer cNFTs
     */
    suspend fun batchTransfer(
        requests: List<TransferRequest>,
        transferrer: suspend (TransferRequest, List<ByteArray>?) -> TransferResult
    ): BatchTransferResult = coroutineScope {
        val results = mutableListOf<TransferResult>()
        val counterMutex = Mutex()
        var completedCount = 0
        var errorsCount = 0

        val jobs = requests.map { request ->
            async {
                transferSemaphore.withPermit {
                    // Get proof from cache or fetch
                    val proof = getProof(request.assetId)

                    val result = try {
                        transferrer(request, proof)
                    } catch (e: Exception) {
                        TransferResult.Failure(request, e.message ?: "Unknown error", true)
                    }

                    when (result) {
                        is TransferResult.Success -> {
                            counterMutex.withLock { completedCount++ }
                            // Invalidate cache for transferred asset
                            invalidateAsset(request.assetId)
                        }
                        is TransferResult.Failure -> {
                            counterMutex.withLock { errorsCount++ }
                        }
                    }

                    _transferProgress.tryEmit(
                        TransferProgress(
                            completed = counterMutex.withLock { completedCount },
                            total = requests.size,
                            currentAssetId = request.assetId,
                            errors = counterMutex.withLock { errorsCount }
                        )
                    )

                    result
                }
            }
        }

        results.addAll(jobs.awaitAll())

        BatchTransferResult(
            totalRequested = requests.size,
            successful = results.filterIsInstance<TransferResult.Success>(),
            failed = results.filterIsInstance<TransferResult.Failure>()
        )
    }

    data class BatchTransferResult(
        val totalRequested: Int,
        val successful: List<TransferResult.Success>,
        val failed: List<TransferResult.Failure>
    ) {
        val successCount: Int get() = successful.size
        val failureCount: Int get() = failed.size
        val successRate: Float get() = successCount.toFloat() / totalRequested
    }

    /**
     * Get asset from cache or null
     */
    fun getCachedAsset(assetId: String): CnftAsset? = assetCache[assetId]

    /**
     * Cache an asset
     */
    suspend fun cacheAsset(asset: CnftAsset) = mutex.withLock {
        if (assetCache.size >= config.cacheSize) {
            // Remove oldest entries
            val oldest = assetCache.entries
                .sortedBy { it.value.lastRefreshMs }
                .take(config.cacheSize / 4)
            oldest.forEach { assetCache.remove(it.key) }
        }
        assetCache[asset.id] = asset
    }

    /**
     * Cache multiple assets
     */
    suspend fun cacheAssets(assets: List<CnftAsset>) = mutex.withLock {
        assets.forEach { asset ->
            if (assetCache.size >= config.cacheSize) {
                val oldest = assetCache.entries
                    .sortedBy { it.value.lastRefreshMs }
                    .take(1)
                oldest.forEach { assetCache.remove(it.key) }
            }
            assetCache[asset.id] = asset
        }
    }

    /**
     * Invalidate cached asset
     */
    suspend fun invalidateAsset(assetId: String): Unit = mutex.withLock {
        assetCache.remove(assetId)
        proofCache.remove(assetId)
    }

    /**
     * Cache a merkle proof
     */
    suspend fun cacheProof(assetId: String, proof: List<ByteArray>): Unit = mutex.withLock {
        proofCache[assetId] = CachedProof(proof, currentTimeMillis())
    }

    /**
     * Get cached proof if valid
     */
    fun getProof(assetId: String): List<ByteArray>? {
        val cached = proofCache[assetId] ?: return null
        val now = currentTimeMillis()
        if (now - cached.cachedAtMs > config.proofCacheTtlMs) {
            proofCache.remove(assetId)
            return null
        }
        return cached.proof
    }

    /**
     * Cache collection statistics
     */
    suspend fun cacheCollectionStats(stats: CollectionStats) = mutex.withLock {
        collectionCache[stats.merkleTree] = stats
    }

    /**
     * Get collection statistics
     */
    fun getCollectionStats(merkleTree: Pubkey): CollectionStats? = collectionCache[merkleTree]

    /**
     * Get all cached assets for an owner
     */
    fun getAssetsByOwner(owner: Pubkey): List<CnftAsset> {
        return assetCache.values.filter { it.owner == owner }
    }

    /**
     * Get all cached assets in a collection
     */
    fun getAssetsByCollection(merkleTree: Pubkey): List<CnftAsset> {
        return assetCache.values.filter { it.merkleTree == merkleTree }
    }

    /**
     * Clear all caches
     */
    suspend fun clearCaches() = mutex.withLock {
        assetCache.clear()
        proofCache.clear()
        collectionCache.clear()
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            assetCount = assetCache.size,
            proofCount = proofCache.size,
            collectionCount = collectionCache.size,
            estimatedMemoryBytes = estimateMemoryUsage()
        )
    }

    data class CacheStats(
        val assetCount: Int,
        val proofCount: Int,
        val collectionCount: Int,
        val estimatedMemoryBytes: Long
    )

    private fun estimateMemoryUsage(): Long {
        // Rough estimation
        val assetSize = assetCache.size * 512L  // ~512 bytes per asset
        val proofSize = proofCache.size * 1024L  // ~1KB per proof (32 nodes * 32 bytes)
        val collectionSize = collectionCache.size * 128L
        return assetSize + proofSize + collectionSize
    }
}

/**
 * CnftProofBuilder - Build Merkle proofs for cNFT operations
 */
object CnftProofBuilder {

    /**
     * Build leaf hash from asset data
     */
    fun buildLeafHash(
        owner: Pubkey,
        delegate: Pubkey?,
        dataHash: ByteArray,
        creatorHash: ByteArray,
        nonce: Long,
        leafIndex: Long
    ): ByteArray {
        return Crypto.sha256(
            byteArrayOf(0x01.toByte()),
            owner.bytes,
            (delegate ?: owner).bytes,
            dataHash,
            creatorHash,
            u64ToBytes(nonce),
            u64ToBytes(leafIndex)
        )
    }

    /**
     * Verify a merkle proof
     */
    fun verifyProof(
        leafHash: ByteArray,
        proof: List<ByteArray>,
        root: ByteArray,
        leafIndex: Long
    ): Boolean {
        var currentHash = leafHash.clone()
        var index = leafIndex

        for (node in proof) {
            currentHash = if (index and 1L == 0L) {
                // Leaf is on left
                hashPair(currentHash, node)
            } else {
                // Leaf is on right
                hashPair(node, currentHash)
            }
            index = index shr 1
        }

        return currentHash.contentEquals(root)
    }

    /**
     * Build data hash from metadata
     */
    fun buildDataHash(
        name: String,
        symbol: String,
        uri: String,
        sellerFeeBasisPoints: Int,
        primarySaleHappened: Boolean,
        isMutable: Boolean
    ): ByteArray {
        return Crypto.sha256(
            name.encodeToByteArray(),
            symbol.encodeToByteArray(),
            uri.encodeToByteArray(),
            u16ToBytes(sellerFeeBasisPoints),
            byteArrayOf(if (primarySaleHappened) 1.toByte() else 0.toByte()),
            byteArrayOf(if (isMutable) 1.toByte() else 0.toByte())
        )
    }

    /**
     * Build creator hash from creator list
     */
    fun buildCreatorHash(creators: List<Pair<Pubkey, Int>>): ByteArray {
        val parts = mutableListOf<ByteArray>()
        for ((address, share) in creators) {
            parts.add(address.bytes)
            parts.add(byteArrayOf(share.toByte()))
        }
        return Crypto.sha256(*parts.toTypedArray())
    }

    private fun hashPair(left: ByteArray, right: ByteArray): ByteArray {
        return Crypto.sha256(left, right)
    }

    private fun u64ToBytes(value: Long): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 32) and 0xFF).toByte(),
            ((value shr 40) and 0xFF).toByte(),
            ((value shr 48) and 0xFF).toByte(),
            ((value shr 56) and 0xFF).toByte()
        )
    }

    private fun u16ToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }
}
