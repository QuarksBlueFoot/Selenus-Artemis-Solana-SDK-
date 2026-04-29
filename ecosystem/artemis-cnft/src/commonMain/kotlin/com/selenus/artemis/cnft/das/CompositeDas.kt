package com.selenus.artemis.cnft.das

import com.selenus.artemis.core.ArtemisEvent
import com.selenus.artemis.core.ArtemisEventBus
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.currentTimeMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * CompositeDas - resilient [ArtemisDas] that composes a primary provider with a fallback.
 *
 * Routes every query to [primary] first. If the primary throws or returns a
 * "not useful" result, automatically re-issues the same call against [fallback].
 * A short-lived failure memo keeps the primary "cooled off" so a burst of calls
 * does not pay the primary timeout on every request - once the primary fails,
 * subsequent calls go straight to the fallback for [cooldownMs] milliseconds.
 *
 * This is the recommended way to wire a DAS client in production:
 *
 * ```kotlin
 * val das: ArtemisDas = CompositeDas(
 *     primary  = HeliusDas(rpcUrl = "https://mainnet.helius-rpc.com/?api-key=$KEY"),
 *     fallback = RpcFallbackDas(rpc)
 * )
 *
 * val nfts = das.assetsByOwner(walletPubkey)
 * ```
 *
 * Most competitor SDKs (sol4k, solana-kt, MSS) have no DAS layer at all, let alone
 * a fallback. When Helius rate-limits or the provider blips, the app keeps working.
 *
 * @param primary       DAS provider queried first (typically [HeliusDas] or equivalent).
 * @param fallback      Provider used when [primary] fails. Typically [RpcFallbackDas].
 * @param cooldownMs    How long to keep the primary "skipped" after a failure.
 * @param onFailover    Optional hook invoked every time the fallback is used.
 *                      Useful for telemetry, banners, or automatic provider rotation.
 */
class CompositeDas(
    private val primary: ArtemisDas,
    private val fallback: ArtemisDas,
    private val cooldownMs: Long = 30_000,
    private val clock: () -> Long = { currentTimeMillis() },
    private val onFailover: (Throwable) -> Unit = {}
) : ArtemisDas {

    private val mutex = Mutex()
    private var primaryCooldownUntil = 0L

    override suspend fun assetsByOwner(
        owner: Pubkey,
        page: Int,
        limit: Int
    ): List<DigitalAsset> = route(
        primaryCall = { primary.assetsByOwner(owner, page, limit) },
        fallbackCall = { fallback.assetsByOwner(owner, page, limit) },
        emptyOk = false
    )

    override suspend fun asset(id: String): DigitalAsset? = route(
        primaryCall = { primary.asset(id) },
        fallbackCall = { fallback.asset(id) },
        emptyOk = false
    )

    override suspend fun assetsByCollection(
        collectionAddress: String,
        page: Int,
        limit: Int
    ): List<DigitalAsset> = route(
        primaryCall = { primary.assetsByCollection(collectionAddress, page, limit) },
        fallbackCall = { fallback.assetsByCollection(collectionAddress, page, limit) },
        emptyOk = false
    )

    /**
     * Signal that the primary is healthy again - clears the cooldown. Apps can call this
     * after a manual retry policy or a provider rotation succeeds.
     */
    suspend fun resetCooldown() {
        mutex.withLock { primaryCooldownUntil = 0 }
    }

    private suspend fun <T> route(
        primaryCall: suspend () -> T,
        fallbackCall: suspend () -> T,
        @Suppress("UNUSED_PARAMETER") emptyOk: Boolean
    ): T {
        val now = clock()
        val skipPrimary = mutex.withLock { now < primaryCooldownUntil }
        if (skipPrimary) return fallbackCall()

        return try {
            primaryCall()
        } catch (e: Throwable) {
            mutex.withLock { primaryCooldownUntil = clock() + cooldownMs }
            runCatching { onFailover(e) }
            ArtemisEventBus.emit(
                ArtemisEvent.Das.ProviderFailover(reason = e.message ?: e::class.simpleName ?: "unknown")
            )
            fallbackCall()
        }
    }
}
