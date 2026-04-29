package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Identity of a dApp asking the wallet to authorize an action.
 *
 * Two identities compare equal iff every component matches; the
 * wallet treats them as separate buckets when enforcing
 * [AuthIssuerConfig.maxOutstandingTokensPerIdentity].
 */
data class Identity(
    val name: String?,
    val uri: Uri?,
    val iconRelativeUri: Uri?
)

/**
 * One auth-token record in the wallet's token store.
 *
 * @property authToken Opaque string the dApp sends back on every
 *   subsequent call. Wallets MUST NOT decode it; it is an opaque
 *   handle into [AuthRepository].
 * @property identity dApp identity bound to the token.
 * @property accounts Accounts the token was issued for.
 * @property chain CAIP-2 chain identifier (e.g. `solana:mainnet`) the
 *   token was issued for. Reauthorize against a different chain MUST
 *   be rejected; upstream walletlib enforces this in `BaseScenario.
 *   doReauthorize`. Older records issued before chain capture may
 *   carry `null`; the dispatcher treats that as "any chain accepted"
 *   for backwards compatibility, but new issuances always populate it.
 * @property scope Wallet-private bytes echoed back to the dApp.
 * @property walletUriBase Endpoint hint for skip-the-chooser flows.
 * @property issuedAtEpochMs `System.currentTimeMillis()` at issuance.
 *   Wall-clock matches upstream walletlib semantics.
 * @property lastUsedAtEpochMs Updated on every successful use; lets
 *   the LRU eviction in [InMemoryAuthRepository] pick the right victim.
 */
data class AuthRecord(
    val authToken: String,
    val identity: Identity,
    val accounts: List<AuthorizedAccount>,
    val chain: String?,
    val scope: ByteArray,
    val walletUriBase: Uri?,
    val issuedAtEpochMs: Long,
    val lastUsedAtEpochMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuthRecord) return false
        return authToken == other.authToken &&
            identity == other.identity &&
            accounts == other.accounts &&
            chain == other.chain &&
            scope.contentEquals(other.scope) &&
            walletUriBase == other.walletUriBase &&
            issuedAtEpochMs == other.issuedAtEpochMs &&
            lastUsedAtEpochMs == other.lastUsedAtEpochMs
    }

    override fun hashCode(): Int {
        var h = authToken.hashCode()
        h = 31 * h + identity.hashCode()
        h = 31 * h + accounts.hashCode()
        h = 31 * h + (chain?.hashCode() ?: 0)
        h = 31 * h + scope.contentHashCode()
        h = 31 * h + (walletUriBase?.hashCode() ?: 0)
        h = 31 * h + issuedAtEpochMs.hashCode()
        h = 31 * h + lastUsedAtEpochMs.hashCode()
        return h
    }
}

/**
 * Wallet-side persistence interface for auth tokens.
 *
 * The default implementation, [InMemoryAuthRepository], is suitable
 * for behavior tests and short-lived wallet processes. Production
 * wallets should plug a SQLite-backed implementation (or any other
 * durable store) so authorizations survive process death.
 *
 * Every method is `suspend` because real implementations will hit
 * disk; the in-memory shim suspends only nominally.
 */
interface AuthRepository {
    /**
     * Open any backing resources (DB connections, secure storage
     * handles). Called by [LocalScenario] / [Scenario] before the
     * first request lands. Default no-op so simple in-memory repos do
     * not need to override; SQLite-backed impls open the database
     * here and avoid an open-on-first-call race.
     *
     * Idempotent: calling twice without an intervening [stop] must be
     * a no-op.
     */
    suspend fun start() {}

    /**
     * Release any backing resources. Called by [Scenario.close]
     * after the dispatch loop has terminated. Default no-op. SQLite
     * impls close the database here. Idempotent.
     */
    suspend fun stop() {}

    /**
     * Issue a fresh auth token bound to [identity] and [accounts]. The
     * implementation must enforce
     * [AuthIssuerConfig.maxOutstandingTokensPerIdentity] by evicting
     * the LRU record when the cap is reached.
     *
     * @param chain CAIP-2 chain identifier, propagated through the
     *   record so reauthorize requests against a different chain can
     *   be rejected (upstream `BaseScenario.doReauthorize` contract).
     */
    suspend fun issue(
        identity: Identity,
        accounts: List<AuthorizedAccount>,
        chain: String?,
        scope: ByteArray,
        walletUri: Uri?
    ): AuthRecord

    /**
     * Look up [authToken]. Returns `null` when the token is unknown
     * or the implementation has decided it has expired. Updates
     * `lastUsedAtEpochMs` on a hit.
     */
    suspend fun lookup(authToken: String): AuthRecord?

    /**
     * Touch [authToken] without changing the bound identity / accounts.
     * Returns the refreshed record, or `null` when the token is gone.
     * Used when handling reauthorize.
     */
    suspend fun reissue(authToken: String): AuthRecord?

    /**
     * Remove [authToken] from the store. Returns `true` when a record
     * was actually removed, `false` when it was already gone. Upstream
     * walletlib's `revoke(AuthRecord)` returns the same boolean so UI
     * code can branch on "nothing to clean up" vs "we just dropped a
     * live record". Idempotent.
     */
    suspend fun revoke(authToken: String): Boolean

    /**
     * Remove every record for [identity]. Returns the count of records
     * that were removed; mirrors upstream's `revoke(IdentityRecord)`
     * boolean (here generalised to a count for richer UI feedback).
     */
    suspend fun revokeAllForIdentity(identity: Identity): Int

    /**
     * Snapshot of every distinct authorized identity. Upstream walletlib
     * exposes this via `getAuthorizedIdentities()`; wallets that render
     * a "manage authorizations" screen iterate it.
     */
    suspend fun getAuthorizedIdentities(): List<Identity> = emptyList()

    /**
     * Live records bound to [identity], in unspecified order. Upstream
     * walletlib exposes this via `getAuthorizations(IdentityRecord)`.
     */
    suspend fun getAuthorizations(identity: Identity): List<AuthRecord> = emptyList()
}

/**
 * Default in-memory implementation. Thread-safe through
 * [ConcurrentHashMap] and [AtomicLong]. Suitable for tests; production
 * wallets should swap in a SQLite-backed implementation that survives
 * process death (intentionally out of scope for this module, wire
 * your own).
 */
class InMemoryAuthRepository(
    private val config: AuthIssuerConfig,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val random: SecureRandom = SecureRandom()
) : AuthRepository {

    private val records = ConcurrentHashMap<String, AuthRecord>()
    private val sequence = AtomicLong()

    override suspend fun issue(
        identity: Identity,
        accounts: List<AuthorizedAccount>,
        chain: String?,
        scope: ByteArray,
        walletUri: Uri?
    ): AuthRecord {
        val now = clock()
        evictExpired(now)
        evictForCap(identity)
        val token = generateToken()
        val record = AuthRecord(
            authToken = token,
            identity = identity,
            accounts = accounts,
            chain = chain,
            scope = scope.copyOf(),
            walletUriBase = walletUri,
            issuedAtEpochMs = now,
            lastUsedAtEpochMs = now
        )
        records[token] = record
        return record
    }

    override suspend fun lookup(authToken: String): AuthRecord? {
        val now = clock()
        val cur = records[authToken] ?: return null
        if (isExpired(cur, now)) {
            records.remove(authToken, cur)
            return null
        }
        val refreshed = cur.copy(lastUsedAtEpochMs = now)
        records[authToken] = refreshed
        return refreshed
    }

    override suspend fun reissue(authToken: String): AuthRecord? {
        val now = clock()
        val cur = records[authToken] ?: return null
        if (isExpired(cur, now)) {
            records.remove(authToken, cur)
            return null
        }
        // Hard upper bound on the original issuance: a token cannot
        // outlive `reauthorizationValidity` past its `issuedAt`,
        // regardless of how often the dApp pings reauthorize.
        if (now - cur.issuedAtEpochMs > config.reauthorizationValidityMs) {
            records.remove(authToken, cur)
            return null
        }
        val refreshed = cur.copy(lastUsedAtEpochMs = now)
        records[authToken] = refreshed
        return refreshed
    }

    override suspend fun revoke(authToken: String): Boolean {
        return records.remove(authToken) != null
    }

    override suspend fun revokeAllForIdentity(identity: Identity): Int {
        var removed = 0
        val it = records.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.identity == identity) {
                it.remove(); removed++
            }
        }
        return removed
    }

    override suspend fun getAuthorizedIdentities(): List<Identity> {
        evictExpired(clock())
        return records.values.map { it.identity }.distinct()
    }

    override suspend fun getAuthorizations(identity: Identity): List<AuthRecord> {
        evictExpired(clock())
        return records.values.filter { it.identity == identity }.toList()
    }

    /**
     * Snapshot of the current record set. Test affordance; not part
     * of the [AuthRepository] contract.
     */
    fun snapshot(): List<AuthRecord> = records.values.toList()

    private fun isExpired(record: AuthRecord, now: Long): Boolean {
        // Expire in two ways: (a) the per-token lifetime since
        // `lastUsedAtEpochMs`, which renews every time the dApp uses
        // the token; (b) the absolute lifetime since `issuedAtEpochMs`.
        // Either condition revokes the token.
        if (now - record.lastUsedAtEpochMs > config.authorizationValidityMs) return true
        if (now - record.issuedAtEpochMs > config.reauthorizationValidityMs) return true
        return false
    }

    private fun evictExpired(now: Long) {
        records.entries.removeIf { isExpired(it.value, now) }
    }

    private fun evictForCap(identity: Identity) {
        val forIdentity = records.values.filter { it.identity == identity }
        val excess = forIdentity.size - (config.maxOutstandingTokensPerIdentity - 1)
        if (excess <= 0) return
        // Drop the LRU N entries to make room for the next issuance.
        forIdentity.sortedBy { it.lastUsedAtEpochMs }
            .take(excess)
            .forEach { records.remove(it.authToken, it) }
    }

    private fun generateToken(): String {
        // 32 bytes from SecureRandom, base64url without padding. Plenty
        // of entropy and url-safe enough that wallets which surface the
        // token to the user (most do not) do not need extra escaping.
        // Sequence appended as a hex tag so two tokens generated in the
        // same millisecond on a flaky RNG remain distinguishable in
        // debug logs without leaking entropy.
        val raw = ByteArray(32)
        random.nextBytes(raw)
        val seq = sequence.incrementAndGet()
        val core = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        return "$core.${seq.toString(16)}"
    }
}
