package com.selenus.artemis.vtx

import com.selenus.artemis.core.ArtemisEvent
import com.selenus.artemis.core.ArtemisEventBus
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.rpc.BlockhashCache
import com.selenus.artemis.rpc.TransactionSimulationResult
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import kotlinx.coroutines.delay

/**
 * TxEngine — production-grade transaction execution pipeline.
 *
 * This is Artemis's core differentiator. Transactions are processed through
 * a staged pipeline: prepare → simulate → sign → send → confirm, with automatic
 * blockhash management, simulation safety, and retry logic.
 *
 * ```kotlin
 * val engine = TxEngine(rpc)
 *
 * // Fluent builder API
 * val result = engine.builder()
 *     .add(transferIx)
 *     .add(memoIx)
 *     .feePayer(wallet)
 *     .config { retries = 3; awaitConfirmation = true }
 *     .send(signer)
 *
 * // Direct execution
 * val result = engine.execute(listOf(ix), signer, feePayer.publicKey)
 * ```
 *
 * Design goal: "Devs should never think about blockhash manually again."
 */
class TxEngine(
    val rpc: RpcApi,
    private val blockhashCache: BlockhashCache? = null,
    private val defaultConfig: TxConfig = TxConfig()
) {

    /**
     * Create a fluent transaction builder.
     */
    fun builder(): TxBuilder = TxBuilder(this)

    /**
     * Execute a transaction through the full pipeline.
     */
    suspend fun execute(
        instructions: List<Instruction>,
        signers: List<Signer>,
        feePayer: Pubkey = signers.first().publicKey,
        config: TxConfig = defaultConfig,
        lookupTables: List<AddressLookupTableAccount> = emptyList()
    ): TxResult {
        val ctx = TxContext(
            instructions = instructions,
            signers = signers,
            feePayer = feePayer,
            config = config,
            lookupTables = lookupTables
        )

        return TxPipeline(this).run(ctx)
    }

    /**
     * Convenience: execute with a single signer (most common case).
     */
    suspend fun execute(
        instructions: List<Instruction>,
        signer: Signer,
        config: TxConfig = defaultConfig
    ): TxResult = execute(instructions, listOf(signer), signer.publicKey, config)

    /**
     * Execute a transaction with an external (async) signer.
     *
     * Used by WalletSession when the signing source is a WalletAdapter (MWA, Seed Vault)
     * where signing is suspend-based rather than synchronous.
     *
     * The callback receives the serialized unsigned transaction bytes (with zero-filled
     * signature slots) and must return the fully signed serialized transaction bytes.
     * This matches the MWA sign_transactions and Seed Vault signTransactions protocols.
     *
     * @param instructions Transaction instructions
     * @param feePayer Fee payer's public key
     * @param externalSign Suspend function that signs a serialized transaction and returns signed bytes
     * @param config Transaction configuration
     * @param lookupTables Address lookup tables for v0 transactions
     */
    suspend fun execute(
        instructions: List<Instruction>,
        feePayer: Pubkey,
        externalSign: suspend (unsignedTxBytes: ByteArray) -> ByteArray,
        config: TxConfig = defaultConfig,
        lookupTables: List<AddressLookupTableAccount> = emptyList()
    ): TxResult {
        val ctx = TxContext(
            instructions = instructions,
            signers = emptyList(),
            feePayer = feePayer,
            config = config,
            lookupTables = lookupTables,
            externalSign = externalSign
        )
        return TxPipeline(this).run(ctx)
    }

    /**
     * Get a fresh blockhash, using cache if available.
     */
    internal suspend fun getBlockhash(commitment: String = "finalized"): String {
        blockhashCache?.let { cache ->
            return cache.getBlockhash()
        }
        return rpc.getLatestBlockhash(commitment).blockhash
    }

    /**
     * Simulate a transaction and return typed result.
     */
    internal suspend fun simulate(txBase64: String): TransactionSimulationResult {
        return rpc.simulateTransactionTyped(txBase64, replaceRecentBlockhash = true)
    }

    /**
     * Send a raw transaction.
     */
    internal suspend fun send(txBase64: String, skipPreflight: Boolean): String {
        return rpc.sendTransaction(txBase64, skipPreflight = skipPreflight)
    }

    /**
     * Confirm a transaction signature.
     */
    internal suspend fun confirm(
        signature: String,
        maxAttempts: Int = 30,
        sleepMs: Long = 500
    ): Boolean {
        return rpc.confirmTransaction(signature, maxAttempts, sleepMs)
    }

    /**
     * Estimate the fee for a set of instructions.
     *
     * Builds a temporary transaction message, then calls getFeeForMessage.
     * Returns the fee in lamports, or null if estimation fails.
     */
    suspend fun estimateFee(
        instructions: List<Instruction>,
        feePayer: Pubkey,
        commitment: String = "processed"
    ): Long? {
        return try {
            val blockhash = getBlockhash(commitment)
            val builder = VersionedTransactionBuilder(
                object : Signer {
                    override val publicKey: Pubkey = feePayer
                    override fun sign(message: ByteArray): ByteArray = ByteArray(64)
                },
                blockhash
            ).addInstructions(instructions)
            val tx = builder.build()
            val base64 = tx.toBase64()
            rpc.getFeeForMessage(base64, commitment)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Get recommended priority fee based on recent network activity.
     *
     * @param accounts Account addresses involved in the transaction
     * @return Median priority fee in micro-lamports, or 0 if unavailable
     */
    suspend fun getRecommendedPriorityFee(accounts: List<String>? = null): Long {
        return try {
            val fees = rpc.getRecentPrioritizationFeesTyped(accounts)
            if (fees.isEmpty()) return 0L
            val sorted = fees.map { it.prioritizationFee }.sorted()
            sorted[sorted.size / 2] // median
        } catch (_: Throwable) {
            0L
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TxConfig — Transaction execution configuration
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Configuration for transaction execution.
 *
 * ```kotlin
 * val config = TxConfig(
 *     simulate = true,           // Simulate before send
 *     retries = 3,               // Retry on failure
 *     awaitConfirmation = true,  // Wait for confirmation
 *     computeUnitLimit = 200_000 // Set compute budget
 * )
 * ```
 */
data class TxConfig(
    /** If true, simulate the transaction before sending. Catches errors early. */
    val simulate: Boolean = true,
    /** If true, abort on simulation failure. */
    val requireSimulationSuccess: Boolean = true,
    /** If true, wait for transaction confirmation after send. */
    val awaitConfirmation: Boolean = false,
    /** Number of retries on blockhash expiry or RPC failures. */
    val retries: Int = 2,
    /** Skip RPC preflight checks (faster but less safe). */
    val skipPreflight: Boolean = false,
    /** Compute unit limit (null = don't set). */
    val computeUnitLimit: Int? = null,
    /** Priority fee in micro-lamports (null = don't set). */
    val computeUnitPrice: Long? = null,
    /** Commitment level for blockhash. */
    val commitment: String = "finalized",
    /** Max confirmation attempts. */
    val confirmMaxAttempts: Int = 30,
    /** Delay between confirmation polls (ms). */
    val confirmSleepMs: Long = 500,
    /**
     * Durable nonce account pubkey. When set, the pipeline uses the nonce value
     * as the blockhash and prepends an AdvanceNonceAccount instruction.
     * This makes transactions survive past the normal ~60s blockhash window.
     */
    val durableNonce: Pubkey? = null,
    /** Authority for the durable nonce account (required if durableNonce is set). */
    val nonceAuthority: Pubkey? = null
)

// ═══════════════════════════════════════════════════════════════════════════════
// TxContext — Mutable state machine for the pipeline
// ═══════════════════════════════════════════════════════════════════════════════

internal data class TxContext(
    val instructions: List<Instruction>,
    val signers: List<Signer>,
    val feePayer: Pubkey,
    val config: TxConfig,
    val lookupTables: List<AddressLookupTableAccount> = emptyList(),
    val externalSign: (suspend (ByteArray) -> ByteArray)? = null,

    var blockhash: String? = null,
    var transaction: VersionedTransaction? = null,
    var txBase64: String? = null,
    var signature: String? = null,
    var simulationResult: TransactionSimulationResult? = null,
    var logs: List<String>? = null,
    var confirmed: Boolean = false,
    var attempts: Int = 0
)

// ═══════════════════════════════════════════════════════════════════════════════
// TxResult — Sealed result hierarchy
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Result of a transaction execution.
 *
 * ```kotlin
 * when (val result = engine.execute(ixs, signer)) {
 *     is TxResult.Success -> println("Sig: ${result.signature}")
 *     is TxResult.SimulationFailed -> println("Sim error: ${result.logs}")
 *     is TxResult.SendFailed -> println("Send error: ${result.error}")
 *     is TxResult.ConfirmationFailed -> println("Unconfirmed: ${result.signature}")
 * }
 * ```
 */
sealed class TxResult {
    /** Transaction confirmed successfully. */
    data class Success(
        val signature: String,
        val attempts: Int,
        val simulationLogs: List<String>? = null
    ) : TxResult()

    /** Pre-send simulation detected an error. Transaction was NOT sent. */
    data class SimulationFailed(
        val error: String?,
        val logs: List<String>?,
        val unitsConsumed: Long? = null
    ) : TxResult()

    /** Transaction send failed after all retries. */
    data class SendFailed(
        val error: Throwable,
        val attempts: Int
    ) : TxResult()

    /** Transaction sent but confirmation timed out or failed. */
    data class ConfirmationFailed(
        val signature: String,
        val attempts: Int
    ) : TxResult()

    /** Whether the transaction was successfully sent and (optionally) confirmed. */
    val isSuccess: Boolean get() = this is Success

    /** Get signature if available (Success or ConfirmationFailed). */
    val signatureOrNull: String? get() = when (this) {
        is Success -> signature
        is ConfirmationFailed -> signature
        else -> null
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TxPipeline — Staged execution pipeline
// ═══════════════════════════════════════════════════════════════════════════════

internal class TxPipeline(private val engine: TxEngine) {

    suspend fun run(ctx: TxContext): TxResult {
        var lastError: Throwable? = null

        for (attempt in 1..ctx.config.retries + 1) {
            ctx.attempts = attempt
            try {
                prepare(ctx)
                if (ctx.config.simulate) {
                    val simResult = simulate(ctx)
                    if (simResult != null) {
                        ArtemisEventBus.emit(
                            ArtemisEvent.Tx.Failed(
                                signature = null,
                                message = "simulation failed: ${(simResult as? TxResult.SimulationFailed)?.error ?: "unknown"}"
                            )
                        )
                        return simResult
                    }
                }
                sign(ctx)
                send(ctx)
                ArtemisEventBus.emit(ArtemisEvent.Tx.Sent(signature = ctx.signature!!))
                if (ctx.config.awaitConfirmation) {
                    confirm(ctx)
                    ArtemisEventBus.emit(ArtemisEvent.Tx.Confirmed(signature = ctx.signature!!))
                }
                return TxResult.Success(
                    signature = ctx.signature!!,
                    attempts = attempt,
                    simulationLogs = ctx.logs
                )
            } catch (e: Throwable) {
                lastError = e
                if (attempt <= ctx.config.retries && isRetryable(e)) {
                    ArtemisEventBus.emit(
                        ArtemisEvent.Tx.Retrying(
                            signature = ctx.signature,
                            attempt = attempt,
                            reason = e.message ?: e::class.simpleName ?: "unknown"
                        )
                    )
                    delay(300L * attempt)
                    continue
                }
                break
            }
        }

        ArtemisEventBus.emit(
            ArtemisEvent.Tx.Failed(
                signature = ctx.signature,
                message = lastError?.message ?: "transaction failed"
            )
        )
        return TxResult.SendFailed(
            error = lastError ?: RuntimeException("Transaction failed"),
            attempts = ctx.attempts
        )
    }

    private suspend fun prepare(ctx: TxContext) {
        val allInstructions = mutableListOf<Instruction>()

        // Inject compute budget instructions if configured
        ctx.config.computeUnitLimit?.let { limit ->
            allInstructions.add(computeSetUnitLimit(limit))
        }
        ctx.config.computeUnitPrice?.let { price ->
            allInstructions.add(computeSetUnitPrice(price))
        }

        // Handle durable nonce: use nonce value as blockhash and prepend AdvanceNonce ix
        val nonceAccount = ctx.config.durableNonce
        if (nonceAccount != null) {
            val authority = ctx.config.nonceAuthority
                ?: throw IllegalArgumentException("nonceAuthority is required when using durableNonce")
            ctx.blockhash = fetchNonceValue(nonceAccount)
            allInstructions.add(advanceNonceInstruction(nonceAccount, authority))
        } else {
            ctx.blockhash = engine.getBlockhash(ctx.config.commitment)
        }

        allInstructions.addAll(ctx.instructions)

        // Use actual signer if available, otherwise dummy for building only (external signing)
        val buildSigner = if (ctx.signers.isNotEmpty()) {
            ctx.signers.first()
        } else {
            object : Signer {
                override val publicKey: Pubkey = ctx.feePayer
                override fun sign(message: ByteArray): ByteArray = ByteArray(64)
            }
        }

        val builder = VersionedTransactionBuilder(buildSigner, ctx.blockhash!!)
            .addInstructions(allInstructions)

        if (ctx.lookupTables.isNotEmpty()) {
            builder.withLookupTables(*ctx.lookupTables.toTypedArray())
        }

        ctx.transaction = builder.build()
    }

    private suspend fun simulate(ctx: TxContext): TxResult? {
        val tx = ctx.transaction!!
        // Serialize for simulation (may have zero signatures, use replaceRecentBlockhash)
        val base64 = tx.toBase64()
        val result = engine.simulate(base64)
        ctx.simulationResult = result
        ctx.logs = result.logs

        if (result.isError && ctx.config.requireSimulationSuccess) {
            return TxResult.SimulationFailed(
                error = result.err?.toString(),
                logs = result.logs,
                unitsConsumed = result.unitsConsumed
            )
        }
        return null
    }

    private suspend fun sign(ctx: TxContext) {
        if (ctx.externalSign != null) {
            // External/async signing path (WalletAdapter: MWA, Seed Vault)
            // Serialize unsigned tx with zero-filled signature slots
            val tx = ctx.transaction!!
            val numRequired = tx.message.header.numRequiredSignatures
            val msgBytes = tx.message.serialize()
            val buf = com.selenus.artemis.tx.ByteArrayBuilder()
            buf.write(com.selenus.artemis.tx.ShortVec.encodeLen(numRequired))
            repeat(numRequired) { buf.write(ByteArray(64)) }
            buf.write(msgBytes)
            val unsignedBytes = buf.toByteArray()

            // Pass to external signer — returns fully signed tx bytes
            val signedBytes = ctx.externalSign.invoke(unsignedBytes)

            // Deserialize the signed transaction
            ctx.transaction = VersionedTransaction.deserialize(signedBytes)
        } else {
            // Standard sync signing path
            ctx.transaction!!.sign(ctx.signers)
        }
        ctx.txBase64 = ctx.transaction!!.toBase64()
    }

    private suspend fun send(ctx: TxContext) {
        ctx.signature = engine.send(ctx.txBase64!!, ctx.config.skipPreflight)
    }

    private suspend fun confirm(ctx: TxContext) {
        val ok = engine.confirm(
            ctx.signature!!,
            ctx.config.confirmMaxAttempts,
            ctx.config.confirmSleepMs
        )
        ctx.confirmed = ok
        if (!ok) {
            throw ConfirmationTimeoutException(ctx.signature!!)
        }
    }

    private fun isRetryable(e: Throwable): Boolean {
        val msg = e.message?.lowercase() ?: return false
        return msg.contains("blockhash") ||
               msg.contains("timeout") ||
               msg.contains("connection") ||
               msg.contains("429") ||
               e is ConfirmationTimeoutException
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Compute budget instruction helpers (inline to avoid circular dep)
    // ═══════════════════════════════════════════════════════════════════════

    private val COMPUTE_BUDGET_PROGRAM = Pubkey.fromBase58("ComputeBudget111111111111111111111111111111")

    private fun computeSetUnitLimit(units: Int): Instruction {
        val data = ByteArray(5)
        data[0] = 0x02
        data[1] = (units and 0xFF).toByte()
        data[2] = ((units shr 8) and 0xFF).toByte()
        data[3] = ((units shr 16) and 0xFF).toByte()
        data[4] = ((units shr 24) and 0xFF).toByte()
        return Instruction(COMPUTE_BUDGET_PROGRAM, emptyList(), data)
    }

    private fun computeSetUnitPrice(microLamports: Long): Instruction {
        val data = ByteArray(9)
        data[0] = 0x03
        for (i in 0..7) data[i + 1] = ((microLamports shr (i * 8)) and 0xFF).toByte()
        return Instruction(COMPUTE_BUDGET_PROGRAM, emptyList(), data)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Durable nonce helpers (inline to avoid circular dep on artemis-programs)
    // ═══════════════════════════════════════════════════════════════════════

    private val SYSTEM_PROGRAM = Pubkey.fromBase58("11111111111111111111111111111111")
    private val SYSVAR_RECENT_BLOCKHASHES = Pubkey.fromBase58("SysvarRecentB1teleading1111111111111111111")// deprecated but required by AdvanceNonce

    /**
     * Fetch the stored nonce value from a nonce account.
     * Nonce account layout: 4 version + 4 state + 32 authority + 32 nonce_hash = 72 bytes
     * The nonce value (base58-encoded blockhash) is at offset 40.
     */
    private suspend fun fetchNonceValue(nonceAccount: Pubkey): String {
        val data = engine.rpc.getAccountInfoBase64(nonceAccount.toBase58())
            ?: throw IllegalStateException("Nonce account ${nonceAccount.toBase58()} not found")
        if (data.size < 72) {
            throw IllegalStateException("Nonce account data too small (${data.size} bytes, expected >= 72)")
        }
        // Extract 32 bytes at offset 40, encode as base58
        val nonceBytes = data.copyOfRange(40, 72)
        return com.selenus.artemis.runtime.Base58.encode(nonceBytes)
    }

    /**
     * Build an AdvanceNonceAccount instruction.
     * Instruction index 4 in SystemProgram, with 3 accounts:
     *   [0] nonce account (writable)
     *   [1] RecentBlockhashes sysvar
     *   [2] nonce authority (signer)
     */
    private fun advanceNonceInstruction(nonceAccount: Pubkey, authority: Pubkey): Instruction {
        val data = ByteArray(4)
        data[0] = 0x04 // AdvanceNonceAccount instruction index
        return Instruction(
            programId = SYSTEM_PROGRAM,
            accounts = listOf(
                AccountMeta(nonceAccount, isSigner = false, isWritable = true),
                AccountMeta(SYSVAR_RECENT_BLOCKHASHES, isSigner = false, isWritable = false),
                AccountMeta(authority, isSigner = true, isWritable = false)
            ),
            data = data
        )
    }
}

internal class ConfirmationTimeoutException(val signature: String) :
    RuntimeException("Transaction $signature was not confirmed in time")

// ═══════════════════════════════════════════════════════════════════════════════
// TxBuilder — Fluent developer-facing API
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Fluent transaction builder. This is the developer-facing API.
 *
 * ```kotlin
 * engine.builder()
 *     .add(SystemProgram.transfer(from, to, lamports))
 *     .add(MemoProgram.memo("hello"))
 *     .feePayer(wallet)
 *     .config { retries = 3 }
 *     .send(signer)
 * ```
 */
class TxBuilder internal constructor(private val engine: TxEngine) {
    private val instructions = mutableListOf<Instruction>()
    private var config = TxConfig()
    private var feePayerPubkey: Pubkey? = null
    private val signerList = mutableListOf<Signer>()
    private val lookupTables = mutableListOf<AddressLookupTableAccount>()

    /** Add a single instruction. */
    fun add(ix: Instruction): TxBuilder {
        instructions.add(ix)
        return this
    }

    /** Add multiple instructions. */
    fun add(vararg ixs: Instruction): TxBuilder {
        instructions.addAll(ixs)
        return this
    }

    /** Add a list of instructions. */
    fun addAll(ixs: List<Instruction>): TxBuilder {
        instructions.addAll(ixs)
        return this
    }

    /** Set the fee payer explicitly. */
    fun feePayer(signer: Signer): TxBuilder {
        feePayerPubkey = signer.publicKey
        if (signer !in signerList) signerList.add(0, signer)
        return this
    }

    /** Set the fee payer by pubkey (signer must still be added separately). */
    fun feePayer(pubkey: Pubkey): TxBuilder {
        feePayerPubkey = pubkey
        return this
    }

    /** Add a signer (for multisig / partial signing). */
    fun sign(signer: Signer): TxBuilder {
        if (signer !in signerList) signerList.add(signer)
        return this
    }

    /** Configure transaction execution parameters. */
    fun config(block: TxConfigBuilder.() -> Unit): TxBuilder {
        val builder = TxConfigBuilder(config)
        builder.block()
        config = builder.build()
        return this
    }

    /** Add address lookup tables for v0 transaction optimization. */
    fun withLookupTables(vararg tables: AddressLookupTableAccount): TxBuilder {
        lookupTables.addAll(tables)
        return this
    }

    /**
     * Execute the transaction through the pipeline.
     * If no signer has been added yet, uses the provided signer as both fee payer and signer.
     */
    suspend fun send(signer: Signer): TxResult {
        if (signer !in signerList) signerList.add(signer)
        val feePayer = feePayerPubkey ?: signer.publicKey
        return engine.execute(instructions, signerList, feePayer, config, lookupTables)
    }

    /**
     * Execute using previously added signers (via [sign] or [feePayer]).
     */
    suspend fun send(): TxResult {
        require(signerList.isNotEmpty()) { "At least one signer is required. Use send(signer) or call sign(signer) first." }
        val feePayer = feePayerPubkey ?: signerList.first().publicKey
        return engine.execute(instructions, signerList, feePayer, config, lookupTables)
    }
}

/**
 * Mutable builder for [TxConfig] — used inside [TxBuilder.config] blocks.
 */
class TxConfigBuilder internal constructor(base: TxConfig) {
    var simulate: Boolean = base.simulate
    var requireSimulationSuccess: Boolean = base.requireSimulationSuccess
    var awaitConfirmation: Boolean = base.awaitConfirmation
    var retries: Int = base.retries
    var skipPreflight: Boolean = base.skipPreflight
    var computeUnitLimit: Int? = base.computeUnitLimit
    var computeUnitPrice: Long? = base.computeUnitPrice
    var commitment: String = base.commitment
    var confirmMaxAttempts: Int = base.confirmMaxAttempts
    var confirmSleepMs: Long = base.confirmSleepMs
    var durableNonce: Pubkey? = base.durableNonce
    var nonceAuthority: Pubkey? = base.nonceAuthority

    internal fun build(): TxConfig = TxConfig(
        simulate = simulate,
        requireSimulationSuccess = requireSimulationSuccess,
        awaitConfirmation = awaitConfirmation,
        retries = retries,
        skipPreflight = skipPreflight,
        computeUnitLimit = computeUnitLimit,
        computeUnitPrice = computeUnitPrice,
        commitment = commitment,
        confirmMaxAttempts = confirmMaxAttempts,
        confirmSleepMs = confirmSleepMs,
        durableNonce = durableNonce,
        nonceAuthority = nonceAuthority
    )
}
