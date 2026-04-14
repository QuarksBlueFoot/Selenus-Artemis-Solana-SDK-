package com.selenus.artemis.wallet.mwa

import android.app.Activity
import android.net.Uri
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.vtx.TxEngine
import com.selenus.artemis.wallet.WalletSession

/**
 * ArtemisMobile — single entry point for Solana mobile apps.
 *
 * Bundles the full Artemis stack into one object: RPC, wallet, transaction engine,
 * and a ready-to-use session. This is the "one import, one line" developer experience.
 *
 * ```kotlin
 * val artemis = ArtemisMobile.create(
 *     activity = this,
 *     identityUri = Uri.parse("https://myapp.com"),
 *     iconPath = "favicon.ico",
 *     identityName = "MyApp"
 * )
 *
 * // Connect to wallet
 * artemis.wallet.connect()
 *
 * // Send SOL in one call
 * val result = artemis.session.sendSol(recipient, 1_000_000_000L)
 * ```
 */
class ArtemisMobile private constructor(
    /** JSON-RPC client for all Solana queries. */
    val rpc: RpcApi,
    /** MWA wallet adapter for signing and sending. */
    val wallet: MwaWalletAdapter,
    /** Transaction execution pipeline with retry, simulation, and confirmation. */
    val txEngine: TxEngine,
    /** Pre-wired session that routes signing through the wallet adapter. */
    val session: WalletSession
) {
    companion object {
        /**
         * Create a fully-wired Artemis mobile stack.
         *
         * @param activity The Activity used for MWA wallet association
         * @param rpcUrl Solana JSON-RPC endpoint
         * @param identityUri Your app's identity URI (shown in wallet approval)
         * @param iconPath Path to your app icon (relative to identityUri)
         * @param identityName Human-readable app name (shown in wallet approval)
         * @param chain Solana chain identifier (default: mainnet)
         */
        fun create(
            activity: Activity,
            identityUri: Uri,
            iconPath: String,
            identityName: String,
            rpcUrl: String = "https://api.mainnet-beta.solana.com",
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
            return ArtemisMobile(rpc, wallet, txEngine, session)
        }
    }
}
