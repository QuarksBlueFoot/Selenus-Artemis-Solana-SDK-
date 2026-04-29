package com.selenus.artemis.wallet

import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import com.selenus.artemis.vtx.TxConfig
import com.selenus.artemis.vtx.TxEngine
import com.selenus.artemis.vtx.TxResult

/**
 * WalletSession - Unified signing session for any wallet type.
 *
 * This is the "one interface, any wallet, zero mental overhead" abstraction.
 * Devs never care what wallet type is being used - MWA, Seed Vault, or local
 * keypair all look the same.
 *
 * ```kotlin
 * // Create from keypair (dev mode)
 * val session = WalletSession.local(keypair, txEngine)
 *
 * // Create from WalletAdapter (mobile)
 * val session = WalletSession.fromAdapter(mwaAdapter, txEngine)
 *
 * // Use identically regardless of source:
 * val result = session.send(transferIx)
 * val result = session.sendBatch(listOf(ix1, ix2))
 * val sig = session.signMessage(message)
 * ```
 */
class WalletSession private constructor(
    private val strategy: SignerStrategy,
    private val txEngine: TxEngine?
) {
    /** The public key of the connected wallet. */
    val publicKey: Pubkey get() = strategy.publicKey

    /**
     * Send a single instruction through the transaction pipeline.
     */
    suspend fun send(
        ix: Instruction,
        config: TxConfig = TxConfig()
    ): TxResult {
        requireEngine()
        return executeWithStrategy(listOf(ix), config)
    }

    /**
     * Send multiple instructions as a single transaction.
     */
    suspend fun sendBatch(
        instructions: List<Instruction>,
        config: TxConfig = TxConfig()
    ): TxResult {
        requireEngine()
        return executeWithStrategy(instructions, config)
    }

    /**
     * Get the underlying Signer for use with TxBuilder or other APIs.
     * Throws for adapter-backed wallets where sync signing is not possible.
     */
    fun signer(): Signer = strategy.asSigner()

    /**
     * Sign an arbitrary off-chain message.
     */
    suspend fun signMessage(message: ByteArray): ByteArray {
        return strategy.signMessage(message)
    }

    /**
     * Send SOL to a recipient.
     *
     * @param to Recipient's public key
     * @param lamports Amount in lamports (1 SOL = 1_000_000_000 lamports)
     * @param config Transaction configuration
     */
    suspend fun sendSol(
        to: Pubkey,
        lamports: Long,
        config: TxConfig = TxConfig()
    ): TxResult {
        requireEngine()
        val ix = systemTransfer(publicKey, to, lamports)
        return executeWithStrategy(listOf(ix), config)
    }

    /**
     * Send SPL tokens to a recipient's token account.
     *
     * @param source Source token account
     * @param destination Destination token account
     * @param amount Token amount (in smallest units)
     * @param config Transaction configuration
     */
    suspend fun sendToken(
        source: Pubkey,
        destination: Pubkey,
        amount: Long,
        config: TxConfig = TxConfig()
    ): TxResult {
        requireEngine()
        val ix = tokenTransfer(source, destination, publicKey, amount)
        return executeWithStrategy(listOf(ix), config)
    }

    private suspend fun executeWithStrategy(
        instructions: List<Instruction>,
        config: TxConfig
    ): TxResult {
        val engine = requireEngine()
        return when (strategy) {
            is SignerStrategy.Adapter -> engine.execute(
                instructions,
                strategy.publicKey,
                externalSign = { unsignedTx ->
                    strategy.signTransaction(unsignedTx)
                },
                config
            )
            else -> engine.execute(instructions, strategy.asSigner(), config)
        }
    }

    private fun requireEngine(): TxEngine = requireNotNull(txEngine) {
        "TxEngine is required for send operations. Create WalletSession with a TxEngine."
    }

    companion object {
        /**
         * Create a session from a local keypair (dev mode / server-side).
         */
        fun local(keypair: Keypair, txEngine: TxEngine? = null): WalletSession {
            return WalletSession(SignerStrategy.Local(keypair), txEngine)
        }

        /**
         * Create a session from a WalletAdapter (MWA, Seed Vault, or any adapter).
         */
        fun fromAdapter(adapter: WalletAdapter, txEngine: TxEngine? = null): WalletSession {
            return WalletSession(SignerStrategy.Adapter(adapter), txEngine)
        }

        /**
         * Create a session from a raw Signer.
         */
        fun fromSigner(signer: Signer, txEngine: TxEngine? = null): WalletSession {
            return WalletSession(SignerStrategy.Raw(signer), txEngine)
        }
    }
}

/**
 * Signing strategy - internal abstraction that normalizes different wallet types.
 *
 * Each variant adapts a different signing source into the common `Signer` interface
 * used by TxEngine. This is the strategy pattern that makes WalletSession work.
 */
sealed class SignerStrategy {
    abstract val publicKey: Pubkey

    /** Convert this strategy to a standard Signer for use with TxEngine. */
    abstract fun asSigner(): Signer

    /** Sign an arbitrary off-chain message. */
    abstract suspend fun signMessage(message: ByteArray): ByteArray

    /**
     * Local keypair signer - for dev mode, server-side, and testing.
     */
    class Local(private val keypair: Keypair) : SignerStrategy() {
        override val publicKey: Pubkey = keypair.publicKey

        override fun asSigner(): Signer = keypair

        override suspend fun signMessage(message: ByteArray): ByteArray =
            keypair.sign(message)
    }

    /**
     * WalletAdapter-based signer - wraps MWA, Seed Vault, or any WalletAdapter.
     *
     * This bridges the WalletAdapter interface (async, request-based) to the
     * Signer interface (sync, direct) that TxEngine expects.
     */
    class Adapter(private val adapter: WalletAdapter) : SignerStrategy() {
        override val publicKey: Pubkey = adapter.publicKey

        override fun asSigner(): Signer = object : Signer {
            override val publicKey: Pubkey = adapter.publicKey
            override fun sign(message: ByteArray): ByteArray {
                // WalletAdapter.signMessage is suspend, but Signer.sign is not.
                // For adapter-based signing, use WalletSession.send() which
                // routes through TxEngine's external signing path.
                throw UnsupportedOperationException(
                    "Adapter-based signing requires async flow. Use WalletSession.send() instead."
                )
            }
        }

        override suspend fun signMessage(message: ByteArray): ByteArray =
            adapter.signMessage(message, SignTxRequest(purpose = "signMessage"))

        /** Sign a serialized transaction via the adapter (MWA sign_transactions / Seed Vault). */
        suspend fun signTransaction(transactionBytes: ByteArray): ByteArray =
            adapter.signMessage(transactionBytes, SignTxRequest(purpose = "signTransaction"))
    }

    /**
     * Raw Signer wrapper - for any existing Signer implementation.
     */
    class Raw(private val signer: Signer) : SignerStrategy() {
        override val publicKey: Pubkey = signer.publicKey

        override fun asSigner(): Signer = signer

        override suspend fun signMessage(message: ByteArray): ByteArray =
            signer.sign(message)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Inline instruction helpers (avoids circular dep on artemis-programs)
// ═══════════════════════════════════════════════════════════════════════════════

private val SYSTEM_PROGRAM = Pubkey.fromBase58("11111111111111111111111111111111")
private val TOKEN_PROGRAM = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")

/** SystemProgram.transfer instruction (index 2). */
private fun systemTransfer(from: Pubkey, to: Pubkey, lamports: Long): Instruction {
    val data = ByteArray(12)
    // Instruction index 2 = Transfer (little-endian u32)
    data[0] = 0x02
    // lamports as little-endian u64
    for (i in 0..7) data[i + 4] = ((lamports shr (i * 8)) and 0xFF).toByte()
    return Instruction(
        programId = SYSTEM_PROGRAM,
        accounts = listOf(
            AccountMeta(from, isSigner = true, isWritable = true),
            AccountMeta(to, isSigner = false, isWritable = true)
        ),
        data = data
    )
}

/** TokenProgram.transfer instruction (index 3). */
private fun tokenTransfer(source: Pubkey, destination: Pubkey, owner: Pubkey, amount: Long): Instruction {
    val data = ByteArray(9)
    data[0] = 0x03 // Transfer instruction index
    for (i in 0..7) data[i + 1] = ((amount shr (i * 8)) and 0xFF).toByte()
    return Instruction(
        programId = TOKEN_PROGRAM,
        accounts = listOf(
            AccountMeta(source, isSigner = false, isWritable = true),
            AccountMeta(destination, isSigner = false, isWritable = true),
            AccountMeta(owner, isSigner = true, isWritable = false)
        ),
        data = data
    )
}
