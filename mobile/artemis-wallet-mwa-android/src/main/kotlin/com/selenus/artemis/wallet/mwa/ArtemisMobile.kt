package com.selenus.artemis.wallet.mwa

import android.app.Activity
import android.net.Uri
import com.selenus.artemis.cnft.MarketplaceEngine
import com.selenus.artemis.cnft.das.ArtemisDas
import com.selenus.artemis.cnft.das.HeliusDas
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.vtx.TxEngine
import com.selenus.artemis.wallet.WalletSession
import com.selenus.artemis.wallet.WalletSessionManager
import com.selenus.artemis.ws.RealtimeEngine

/**
 * ArtemisMobile — single entry point for Solana mobile apps.
 *
 * Bundles the full Artemis v68 stack into one object: RPC, wallet, transaction engine,
 * realtime subscriptions, DAS (NFT assets), and marketplace. Zero boilerplate.
 *
 * ```kotlin
 * val artemis = ArtemisMobile.create(
 *     activity     = this,
 *     identityUri  = Uri.parse("https://myapp.com"),
 *     iconPath     = "https://myapp.com/favicon.ico",  // must be absolute HTTPS URI
 *     identityName = "MyApp",
 *     rpcUrl       = "https://mainnet.helius-rpc.com/?api-key=<KEY>",
 *     wsUrl        = "wss://atlas-mainnet.helius-rpc.com/?api-key=<KEY>"
 * )
 *
 * // Gate all wallet ops through sessionManager — handles connect, retry, events
 * val sig = artemis.sessionManager.withWallet { session ->
 *     session.sendSol(recipient, 1_000_000_000L)
 * }
 *
 * // React to wallet lifecycle events
 * artemis.sessionManager.onDisconnect { showConnectButton() }
 * artemis.sessionManager.onSessionExpired { println("Reconnecting...") }
 *
 * // Watch account balance in real time
 * artemis.realtime.connect()
 * artemis.realtime.subscribeAccount(artemis.session.publicKey.toBase58()) { info ->
 *     println("lamports: ${info.lamports}")
 * }
 *
 * // Rotate to backup endpoint on failure
 * artemis.realtime.reconnect()
 *
 * // Fetch all NFTs
 * val nfts = artemis.das?.assetsByOwner(artemis.session.publicKey)
 * ```
 */
class ArtemisMobile private constructor(
    /** JSON-RPC client for all Solana queries and transaction submission. */
    val rpc: RpcApi,
    /** MWA wallet adapter for connect / sign / send. */
    val wallet: MwaWalletAdapter,
    /** Transaction execution pipeline: blockhash, simulation, retry, confirmation. */
    val txEngine: TxEngine,
    /** Pre-wired session that routes signing through the wallet adapter. */
    val session: WalletSession,
    /** Managed session lifecycle: lazy connect, invalidate, withWallet {}, and event callbacks. */
    val sessionManager: WalletSessionManager,
    /** WebSocket subscription engine for real-time account and signature events. */
    val realtime: RealtimeEngine,
    /** Digital Asset Standard query layer (optional; requires a DAS-compatible endpoint). */
    val das: ArtemisDas?,
    /** High-level NFT/marketplace helpers. */
    val marketplace: MarketplaceEngine
) {
    companion object {

        /**
         * Create a fully-wired Artemis mobile stack.
         *
         * @param activity      The Activity used for MWA wallet association
         * @param identityUri   Your app's identity URI (shown in wallet approval dialog)
         * @param iconPath      Path or full URL to your app icon
         * @param identityName  Human-readable app name (shown in wallet approval dialog)
         * @param rpcUrl        Solana JSON-RPC HTTP endpoint
         * @param wsUrl         Solana WebSocket endpoint (defaults to wss based on rpcUrl)
         * @param dasUrl        DAS-compatible RPC URL for NFT queries (null = no DAS)
         * @param chain         Solana chain identifier (default: mainnet)
         */
        fun create(
            activity: Activity,
            identityUri: Uri,
            iconPath: String,
            identityName: String,
            rpcUrl: String = "https://api.mainnet-beta.solana.com",
            wsUrl: String = rpcUrl.replace("https://", "wss://").replace("http://", "ws://"),
            dasUrl: String? = null,
            chain: String = "solana:mainnet"
        ): ArtemisMobile {
            val client = JsonRpcClient(rpcUrl)
            val rpc = RpcApi(client)
            val txEngine = TxEngine(rpc)
            val wallet = MwaWalletAdapter(
                activity = activity,
                identityUri = identityUri,
                iconPath = iconPath,
                identityName = identityName,
                chain = chain
            )
            val session = WalletSession.fromAdapter(wallet, txEngine)
            val sessionManager = WalletSessionManager {
                wallet.connect()
                WalletSession.fromAdapter(wallet, txEngine)
            }
            val realtime = RealtimeEngine(endpoints = listOf(wsUrl))
            val das: ArtemisDas? = dasUrl?.let { HeliusDas(it) }
            val marketplace = MarketplaceEngine(rpc, txEngine, das)
            return ArtemisMobile(rpc, wallet, txEngine, session, sessionManager, realtime, das, marketplace)
        }
    }
}

