package com.selenus.artemis.vtx

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// Inline compute budget helpers to avoid circular dependency
private object ComputeBudgetHelper {
    private val PROGRAM_ID = Pubkey.fromBase58("ComputeBudget111111111111111111111111111111")

    fun setComputeUnitLimit(units: Int): Instruction {
        require(units > 0) { "units must be > 0" }
        val data = byteArrayOf(0x02) + le32(units)
        return Instruction(PROGRAM_ID, emptyList<AccountMeta>(), data)
    }

    fun setComputeUnitPrice(microLamports: Long): Instruction {
        require(microLamports >= 0L) { "microLamports must be >= 0" }
        val data = byteArrayOf(0x03) + le64(microLamports)
        return Instruction(PROGRAM_ID, emptyList<AccountMeta>(), data)
    }

    private fun le32(v: Int): ByteArray {
        return byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte()
        )
    }

    private fun le64(v: Long): ByteArray {
        return byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte(),
            ((v shr 32) and 0xFF).toByte(),
            ((v shr 40) and 0xFF).toByte(),
            ((v shr 48) and 0xFF).toByte(),
            ((v shr 56) and 0xFF).toByte()
        )
    }
}

/**
 * VersionedTransactionBuilder - Fluent builder for v0 transactions with ALT support
 * 
 * Features:
 * - Automatic address lookup table resolution
 * - Optimal account key placement
 * - Priority fee integration
 * - Transaction size optimization
 * - Batch building for atomic operations
 */
class VersionedTransactionBuilder(
    private val feePayer: Signer,
    private val recentBlockhash: String
) {
    private val instructions = mutableListOf<Instruction>()
    private val addressLookupTables = mutableListOf<AddressLookupTableAccount>()
    private var computeUnitLimit: Int? = null
    private var computeUnitPrice: Long? = null

    /**
     * Add an instruction
     */
    fun addInstruction(instruction: Instruction): VersionedTransactionBuilder {
        instructions.add(instruction)
        return this
    }

    /**
     * Add multiple instructions
     */
    fun addInstructions(vararg instruction: Instruction): VersionedTransactionBuilder {
        instructions.addAll(instruction)
        return this
    }

    /**
     * Add multiple instructions from list
     */
    fun addInstructions(list: List<Instruction>): VersionedTransactionBuilder {
        instructions.addAll(list)
        return this
    }

    /**
     * Use address lookup tables
     */
    fun withLookupTables(vararg tables: AddressLookupTableAccount): VersionedTransactionBuilder {
        addressLookupTables.addAll(tables)
        return this
    }

    /**
     * Set compute budget
     */
    fun withComputeBudget(limit: Int, priceMicroLamports: Long): VersionedTransactionBuilder {
        computeUnitLimit = limit
        computeUnitPrice = priceMicroLamports
        return this
    }

    /**
     * Build the versioned transaction
     */
    fun build(): VersionedTransaction {
        val allInstructions = mutableListOf<Instruction>()

        // Add compute budget instructions first
        computeUnitLimit?.let { limit ->
            allInstructions.add(ComputeBudgetHelper.setComputeUnitLimit(limit))
        }
        computeUnitPrice?.let { price ->
            allInstructions.add(ComputeBudgetHelper.setComputeUnitPrice(price))
        }
        allInstructions.addAll(instructions)

        // Compile to MessageV0 using existing compiler
        val result = V0MessageCompiler.compile(
            feePayer = feePayer,
            recentBlockhash = recentBlockhash,
            instructions = allInstructions,
            addressLookupTables = addressLookupTables
        )

        return VersionedTransaction(result.message)
    }

    /**
     * Build and sign the transaction
     */
    fun buildAndSign(additionalSigners: List<Signer> = emptyList()): VersionedTransaction {
        val tx = build()
        tx.sign(listOf(feePayer) + additionalSigners)
        return tx
    }

    companion object {
        fun builder(feePayer: Signer, recentBlockhash: String): VersionedTransactionBuilder {
            return VersionedTransactionBuilder(feePayer, recentBlockhash)
        }
    }
}

/**
 * TransactionV0Factory - Factory for creating optimized v0 transactions
 * 
 * Features:
 * - Automatic ALT discovery and caching
 * - Size estimation before building
 * - Batch transaction creation
 * - Fee optimization integration
 */
class TransactionV0Factory(
    private val altResolver: AltResolver? = null
) {
    private val altCache = ConcurrentHashMap<Pubkey, AddressLookupTableAccount>()
    private val mutex = Mutex()

    data class BuildContext(
        val feePayer: Signer,
        val blockhash: String,
        val computeUnits: Int = 200_000,
        val priorityFee: Long = 0L,
        val preferredAltKeys: List<Pubkey> = emptyList()
    )

    /**
     * Build a single v0 transaction
     */
    suspend fun build(
        context: BuildContext,
        vararg instructions: Instruction
    ): VersionedTransaction {
        return build(context, instructions.toList())
    }

    /**
     * Build a single v0 transaction from instruction list
     */
    suspend fun build(
        context: BuildContext,
        instructions: List<Instruction>
    ): VersionedTransaction {
        val alts = resolveAltAccounts(context.preferredAltKeys)

        return VersionedTransactionBuilder
            .builder(context.feePayer, context.blockhash)
            .withComputeBudget(context.computeUnits, context.priorityFee)
            .addInstructions(instructions)
            .withLookupTables(*alts.toTypedArray())
            .build()
    }

    /**
     * Build multiple transactions with shared ALTs
     */
    suspend fun buildBatch(
        context: BuildContext,
        instructionBatches: List<List<Instruction>>
    ): List<VersionedTransaction> {
        val alts = resolveAltAccounts(context.preferredAltKeys)

        return instructionBatches.map { instructions ->
            VersionedTransactionBuilder
                .builder(context.feePayer, context.blockhash)
                .withComputeBudget(context.computeUnits, context.priorityFee)
                .addInstructions(instructions)
                .withLookupTables(*alts.toTypedArray())
                .build()
        }
    }

    /**
     * Estimate transaction size before building
     */
    fun estimateSize(
        instructions: List<Instruction>,
        signerCount: Int = 1,
        useAlt: Boolean = true
    ): TransactionSizeEstimate {
        val accountKeys = collectAccountKeys(instructions)
        val programIds = instructions.map { it.programId }.toSet()

        val staticKeyCount = if (useAlt) {
            // Estimate reduction from ALT usage
            (accountKeys.size * 0.3).toInt().coerceAtLeast(signerCount)
        } else {
            accountKeys.size
        }

        val instructionDataSize = instructions.sumOf { it.data.size }
        val estimatedSignatures = signerCount * 64
        val estimatedAccountKeys = staticKeyCount * 32
        val estimatedHeader = 3
        val estimatedBlockhash = 32
        val altLookupOverhead = if (useAlt) 50 else 0

        val estimatedBytes = estimatedSignatures + estimatedAccountKeys + 
            estimatedHeader + estimatedBlockhash + instructionDataSize + altLookupOverhead

        return TransactionSizeEstimate(
            estimatedBytes = estimatedBytes,
            accountKeyCount = accountKeys.size,
            staticKeyCount = staticKeyCount,
            instructionCount = instructions.size,
            fits1232Limit = estimatedBytes <= 1232,
            recommendAlt = accountKeys.size > 20,
            programCount = programIds.size
        )
    }

    private fun collectAccountKeys(instructions: List<Instruction>): Set<Pubkey> {
        return instructions.flatMap { it.accounts.map { m -> m.pubkey } }.toSet()
    }

    private suspend fun resolveAltAccounts(altKeys: List<Pubkey>): List<AddressLookupTableAccount> {
        if (altKeys.isEmpty() || altResolver == null) return emptyList()

        return mutex.withLock {
            altKeys.mapNotNull { key ->
                altCache.getOrPut(key) {
                    altResolver.fetchOne(key) ?: return@mapNotNull null
                }
            }
        }
    }
}

data class TransactionSizeEstimate(
    val estimatedBytes: Int,
    val accountKeyCount: Int,
    val staticKeyCount: Int,
    val instructionCount: Int,
    val fits1232Limit: Boolean,
    val recommendAlt: Boolean,
    val programCount: Int
)

/**
 * TransactionAnalyzer - Analyze transactions for optimization opportunities
 */
object TransactionAnalyzer {
    data class Analysis(
        val signatureCount: Int,
        val accountKeyCount: Int,
        val instructionCount: Int,
        val usesAlt: Boolean,
        val sizeBytes: Int,
        val hints: List<String>
    )

    /**
     * Analyze a versioned transaction
     */
    fun analyze(tx: VersionedTransaction): Analysis {
        val message = tx.message
        val hints = mutableListOf<String>()

        val accountKeyCount = message.staticAccountKeys.size
        val usesAlt = message.addressTableLookups.isNotEmpty()

        if (accountKeyCount > 20 && !usesAlt) {
            hints.add("Consider using Address Lookup Tables to reduce transaction size")
        }

        if (tx.signatures.size > 2) {
            hints.add("Multiple signers detected - ensure all are necessary")
        }

        val sizeBytes = tx.serialize().size
        if (sizeBytes > 1000) {
            hints.add("Transaction approaching size limit (${sizeBytes}/1232 bytes)")
        }

        return Analysis(
            signatureCount = tx.signatures.size,
            accountKeyCount = accountKeyCount,
            instructionCount = message.instructions.size,
            usesAlt = usesAlt,
            sizeBytes = sizeBytes,
            hints = hints
        )
    }

    /**
     * Check if transaction can be sent
     */
    fun canSend(tx: VersionedTransaction): Boolean {
        return try {
            val bytes = tx.serialize()
            bytes.size <= 1232 && tx.signatures.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * BatchTransactionProcessor - Process multiple transactions with retry logic
 */
class BatchTransactionProcessor(
    private val maxConcurrency: Int = 5,
    private val maxRetries: Int = 3,
    private val retryDelayMs: Long = 1000
) {
    sealed class BatchResult<T> {
        data class Success<T>(val results: List<T>) : BatchResult<T>()
        data class PartialSuccess<T>(
            val successResults: List<Pair<Int, T>>,
            val failures: List<Pair<Int, Throwable>>
        ) : BatchResult<T>()
        data class Failure<T>(val error: Throwable) : BatchResult<T>()
    }

    /**
     * Process transactions with retry logic
     */
    suspend fun <T> process(
        transactions: List<VersionedTransaction>,
        processor: suspend (VersionedTransaction) -> T
    ): BatchResult<T> = coroutineScope {
        val semaphore = Semaphore(maxConcurrency)
        val results = ConcurrentHashMap<Int, Result<T>>()
        val counter = AtomicInteger(0)

        val jobs = transactions.mapIndexed { index, tx ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val result = processWithRetry(tx, processor)
                    results[index] = result
                    counter.incrementAndGet()
                }
            }
        }

        try {
            jobs.forEach { it.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@coroutineScope BatchResult.Failure(e)
        }

        val successList = mutableListOf<Pair<Int, T>>()
        val failureList = mutableListOf<Pair<Int, Throwable>>()

        results.forEach { (index, result) ->
            result.fold(
                onSuccess = { successList.add(index to it) },
                onFailure = { failureList.add(index to it) }
            )
        }

        when {
            failureList.isEmpty() -> BatchResult.Success(successList.sortedBy { it.first }.map { it.second })
            successList.isEmpty() -> BatchResult.Failure(
                Exception("All ${transactions.size} transactions failed")
            )
            else -> BatchResult.PartialSuccess(successList, failureList)
        }
    }

    private suspend fun <T> processWithRetry(
        tx: VersionedTransaction,
        processor: suspend (VersionedTransaction) -> T
    ): Result<T> {
        var lastError: Throwable? = null
        
        repeat(maxRetries) { attempt ->
            try {
                return Result.success(processor(tx))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries - 1) {
                    delay(retryDelayMs * (attempt + 1))
                }
            }
        }
        
        return Result.failure(lastError ?: Exception("Unknown error"))
    }

    /**
     * Observe batch progress as a Flow
     */
    fun <T> processAsFlow(
        transactions: List<VersionedTransaction>,
        processor: suspend (VersionedTransaction) -> T
    ): Flow<BatchProgress<T>> = flow {
        val total = transactions.size
        var completed = 0
        var successful = 0
        var failed = 0

        emit(BatchProgress.Started(total))

        transactions.forEachIndexed { index, tx ->
            try {
                val result = processor(tx)
                successful++
                emit(BatchProgress.ItemCompleted(index, Result.success(result)))
            } catch (e: Exception) {
                failed++
                emit(BatchProgress.ItemCompleted(index, Result.failure(e)))
            }
            completed++
            emit(BatchProgress.Progress(completed, total, successful, failed))
        }

        emit(BatchProgress.Completed(successful, failed))
    }
}

sealed class BatchProgress<T> {
    data class Started<T>(val total: Int) : BatchProgress<T>()
    data class Progress<T>(
        val completed: Int,
        val total: Int,
        val successful: Int,
        val failed: Int
    ) : BatchProgress<T>()
    data class ItemCompleted<T>(
        val index: Int,
        val result: Result<T>
    ) : BatchProgress<T>()
    data class Completed<T>(
        val successful: Int,
        val failed: Int
    ) : BatchProgress<T>()
}

/**
 * DSL for building versioned transactions
 */
fun versionedTransaction(
    feePayer: Signer,
    blockhash: String,
    block: VersionedTransactionBuilder.() -> Unit
): VersionedTransaction {
    return VersionedTransactionBuilder(feePayer, blockhash).apply(block).build()
}

/**
 * Extension to sign and serialize
 */
fun VersionedTransaction.signAndSerialize(signers: List<Signer>): ByteArray {
    sign(signers)
    return serialize()
}

/**
 * Extension to get transaction hash (signature)
 */
fun VersionedTransaction.getTransactionHash(): String? {
    return if (signatures.isNotEmpty() && signatures[0].isNotEmpty()) {
        java.util.Base64.getEncoder().encodeToString(signatures[0])
    } else null
}
