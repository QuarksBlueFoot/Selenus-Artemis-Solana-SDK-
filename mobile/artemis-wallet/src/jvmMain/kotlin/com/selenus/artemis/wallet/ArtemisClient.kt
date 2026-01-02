package com.selenus.artemis.wallet

import com.selenus.artemis.rpc.BlockhashCache
import com.selenus.artemis.rpc.Commitment
import com.selenus.artemis.rpc.Connection
import com.selenus.artemis.rpc.RpcEndpointPool
import com.selenus.artemis.rpc.RpcRouter
import com.selenus.artemis.rpc.SolanaCluster
import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.vtx.TxBuilder
import com.selenus.artemis.vtx.TxConfig
import com.selenus.artemis.vtx.TxEngine

/**
 * ArtemisClient — unified SDK entrypoint.
 *
 * ONE canonical entry point for the entire Artemis SDK. Creates and wires
 * together Connection, TxEngine, BlockhashCache, and WalletSession so
 * developers never manually construct the object graph.
 *
 * ```kotlin
 * val artemis = ArtemisClient {
 *     rpc = "https://api.devnet.solana.com"
 *     commitment = Commitment.CONFIRMED
 *     blockhashCaching = true
 * }
 *
 * // Access typed RPC
 * val balance = artemis.rpc().getBalance(pubkey)
 *
 * // Build and send transactions
 * val result = artemis.tx()
 *     .add(transferIx)
 *     .send(signer)
 *
 * // Unified wallet session
 * val session = artemis.wallet(keypair)
 * val result = session.send(transferIx)
 * ```
 */
class ArtemisClient private constructor(builder: Builder) {
    private val connection: Connection = builder.buildConnection()
    private val blockhashCache: BlockhashCache? = if (builder.blockhashCaching) BlockhashCache(connection) else null
    private val txEngine: TxEngine = TxEngine(connection, blockhashCache, builder.defaultTxConfig)

    /** Access the underlying RPC connection for typed queries. */
    fun rpc(): Connection = connection

    /** Create a fluent transaction builder wired to this client's TxEngine. */
    fun tx(): TxBuilder = txEngine.builder()

    /** Access the TxEngine directly for advanced usage. */
    fun engine(): TxEngine = txEngine

    /** Create a WalletSession from a local keypair. */
    fun wallet(keypair: Keypair): WalletSession = WalletSession.local(keypair, txEngine)

    /** Create a WalletSession from a WalletAdapter (MWA, Seed Vault, etc). */
    fun wallet(adapter: WalletAdapter): WalletSession = WalletSession.fromAdapter(adapter, txEngine)

    /** Create a WalletSession from a raw Signer. */
    fun wallet(signer: Signer): WalletSession = WalletSession.fromSigner(signer, txEngine)

    /**
     * Request an airdrop (devnet/testnet only).
     *
     * @param pubkey Recipient public key
     * @param lamports Amount in lamports (1 SOL = 1_000_000_000)
     * @return Transaction signature
     */
    suspend fun airdrop(pubkey: String, lamports: Long = 1_000_000_000L): String {
        return connection.requestAirdrop(pubkey, lamports, connection.defaultCommitment)
    }

    /**
     * Request an airdrop and wait for confirmation (devnet/testnet only).
     *
     * @param pubkey Recipient public key
     * @param lamports Amount in lamports (1 SOL = 1_000_000_000)
     * @return Confirmed transaction signature
     */
    suspend fun airdropAndConfirm(pubkey: String, lamports: Long = 1_000_000_000L): String {
        return connection.requestAirdropAndConfirm(pubkey, lamports, connection.defaultCommitment.value)
    }

    /**
     * Request an airdrop for a Pubkey.
     */
    suspend fun airdrop(pubkey: com.selenus.artemis.runtime.Pubkey, lamports: Long = 1_000_000_000L): String {
        return airdrop(pubkey.toBase58(), lamports)
    }

    /**
     * Request an airdrop and confirm for a Pubkey.
     */
    suspend fun airdropAndConfirm(pubkey: com.selenus.artemis.runtime.Pubkey, lamports: Long = 1_000_000_000L): String {
        return airdropAndConfirm(pubkey.toBase58(), lamports)
    }

    /** Shut down background resources (blockhash cache refresh, etc). */
    fun close() {
        blockhashCache?.close()
    }

    class Builder internal constructor() {
        /** RPC endpoint URL. Mutually exclusive with [cluster], [pool], [router]. */
        var rpc: String? = null

        /** Solana cluster preset. Mutually exclusive with [rpc], [pool], [router]. */
        var cluster: SolanaCluster? = null

        /** RPC endpoint pool for failover. Mutually exclusive with [rpc], [cluster], [router]. */
        var pool: RpcEndpointPool? = null

        /** Smart router for method-based routing. Mutually exclusive with [rpc], [cluster], [pool]. */
        var router: RpcRouter? = null

        /** Default commitment level. */
        var commitment: Commitment = Commitment.FINALIZED

        /** Enable automatic blockhash caching (recommended for production). */
        var blockhashCaching: Boolean = true

        /** Default transaction execution configuration. */
        var defaultTxConfig: TxConfig = TxConfig()

        /** Configure default transaction settings via DSL block. */
        fun txConfig(block: TxConfigDsl.() -> Unit) {
            val dsl = TxConfigDsl()
            dsl.block()
            defaultTxConfig = dsl.build()
        }

        internal fun buildConnection(): Connection {
            return when {
                rpc != null -> Connection(rpc!!, commitment)
                cluster != null -> Connection(cluster!!, commitment)
                pool != null -> Connection(pool!!, commitment)
                router != null -> Connection(router!!, commitment)
                else -> throw IllegalStateException(
                    "ArtemisClient requires an RPC endpoint. Set one of: rpc, cluster, pool, or router."
                )
            }
        }
    }

    class TxConfigDsl internal constructor() {
        var simulate: Boolean = true
        var retries: Int = 2
        var awaitConfirmation: Boolean = false
        var skipPreflight: Boolean = false
        var computeUnitLimit: Int? = null
        var computeUnitPrice: Long? = null

        internal fun build(): TxConfig = TxConfig(
            simulate = simulate,
            retries = retries,
            awaitConfirmation = awaitConfirmation,
            skipPreflight = skipPreflight,
            computeUnitLimit = computeUnitLimit,
            computeUnitPrice = computeUnitPrice
        )
    }

    companion object {
        /** Create an ArtemisClient with a DSL builder. */
        operator fun invoke(block: Builder.() -> Unit): ArtemisClient {
            val builder = Builder()
            builder.block()
            return ArtemisClient(builder)
        }

        /** Quick devnet client for testing. */
        fun devnet(commitment: Commitment = Commitment.CONFIRMED): ArtemisClient {
            return ArtemisClient {
                cluster = SolanaCluster.DEVNET
                this.commitment = commitment
            }
        }

        /** Quick mainnet client. */
        fun mainnet(endpoint: String, commitment: Commitment = Commitment.FINALIZED): ArtemisClient {
            return ArtemisClient {
                rpc = endpoint
                this.commitment = commitment
            }
        }
    }
}
