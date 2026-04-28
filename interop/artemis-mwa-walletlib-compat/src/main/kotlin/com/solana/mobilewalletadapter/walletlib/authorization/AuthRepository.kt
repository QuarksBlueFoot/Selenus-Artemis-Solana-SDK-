/*
 * Drop-in source compatibility for com.solana.mobilewalletadapter.walletlib.authorization.
 *
 * Upstream's `AuthRepository` is a Java interface with a SQLite-backed
 * implementation. Most apps that consume the upstream walletlib pass a
 * pre-built repo through to the scenario; this shim mirrors the
 * interface (start/stop/issue/fromAuthToken/toAuthToken/reissue/revoke
 * etc.) so existing code keeps compiling.
 *
 * Compat-side records are immutable POJOs that translate to/from the
 * Artemis [com.selenus.artemis.wallet.mwa.walletlib.AuthRecord].
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.walletlib.authorization

import android.net.Uri
import com.solana.mobilewalletadapter.walletlib.scenario.AuthorizedAccount as CompatAuthorizedAccount
import com.selenus.artemis.wallet.mwa.walletlib.AuthRecord as ArtemisAuthRecord
import com.selenus.artemis.wallet.mwa.walletlib.AuthRepository as ArtemisAuthRepository
import com.selenus.artemis.wallet.mwa.walletlib.AuthorizedAccount as ArtemisAuthorizedAccount
import com.selenus.artemis.wallet.mwa.walletlib.Identity as ArtemisIdentity
import kotlinx.coroutines.runBlocking

/**
 * Identity record. Upstream `IdentityRecord` carries a generated id +
 * the dApp's name / uri / icon. We expose the same shape; the id is
 * synthesised from the identity content hash so consumers can use it
 * as a stable map key without us needing a real DB.
 */
data class IdentityRecord(
    @JvmField val id: Int,
    @JvmField val name: String?,
    @JvmField val uri: Uri?,
    @JvmField val relativeIconUri: Uri?
) {
    internal fun toArtemis(): ArtemisIdentity = ArtemisIdentity(
        name = name,
        uri = uri,
        iconRelativeUri = relativeIconUri
    )

    companion object {
        @JvmStatic
        internal fun fromArtemis(art: ArtemisIdentity): IdentityRecord = IdentityRecord(
            id = synthesiseId(art),
            name = art.name,
            uri = art.uri,
            relativeIconUri = art.iconRelativeUri
        )

        private fun synthesiseId(art: ArtemisIdentity): Int {
            // Stable deterministic id from the identity content. Not a
            // DB id; consumers that use it must not assume densely
            // packed integers, only equality.
            var h = 17
            h = 31 * h + (art.name?.hashCode() ?: 0)
            h = 31 * h + (art.uri?.hashCode() ?: 0)
            h = 31 * h + (art.iconRelativeUri?.hashCode() ?: 0)
            return h
        }
    }
}

/**
 * One-account record. Upstream's [AccountRecord] mirrors the upstream
 * `AccountRecord`'s shape, including the wallet-stored display fields.
 */
data class AccountRecord(
    @JvmField val id: Int,
    @JvmField val publicKeyRaw: ByteArray,
    @JvmField val accountLabel: String?,
    @JvmField val displayAddress: String?,
    @JvmField val displayAddressFormat: String?,
    @JvmField val accountIcon: Uri?,
    @JvmField val chains: Array<String>?,
    @JvmField val features: Array<String>?
) {
    internal fun toArtemis(): ArtemisAuthorizedAccount = ArtemisAuthorizedAccount(
        publicKey = publicKeyRaw,
        accountLabel = accountLabel,
        displayAddress = displayAddress,
        displayAddressFormat = displayAddressFormat,
        accountIcon = accountIcon,
        chains = chains?.toList() ?: emptyList(),
        features = features?.toList() ?: emptyList()
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccountRecord) return false
        return id == other.id &&
            publicKeyRaw.contentEquals(other.publicKeyRaw) &&
            accountLabel == other.accountLabel &&
            displayAddress == other.displayAddress &&
            displayAddressFormat == other.displayAddressFormat &&
            accountIcon == other.accountIcon &&
            (chains?.toList() ?: emptyList<String>()) == (other.chains?.toList() ?: emptyList<String>()) &&
            (features?.toList() ?: emptyList<String>()) == (other.features?.toList() ?: emptyList<String>())
    }

    override fun hashCode(): Int {
        var h = id
        h = 31 * h + publicKeyRaw.contentHashCode()
        h = 31 * h + (accountLabel?.hashCode() ?: 0)
        h = 31 * h + (displayAddress?.hashCode() ?: 0)
        h = 31 * h + (displayAddressFormat?.hashCode() ?: 0)
        h = 31 * h + (accountIcon?.hashCode() ?: 0)
        h = 31 * h + (chains?.toList()?.hashCode() ?: 0)
        h = 31 * h + (features?.toList()?.hashCode() ?: 0)
        return h
    }

    companion object {
        @JvmStatic
        internal fun fromArtemis(art: ArtemisAuthorizedAccount): AccountRecord = AccountRecord(
            id = art.publicKey.contentHashCode(),
            publicKeyRaw = art.publicKey,
            accountLabel = art.accountLabel,
            displayAddress = art.displayAddress,
            displayAddressFormat = art.displayAddressFormat,
            accountIcon = art.accountIcon,
            chains = art.chains.toTypedArray().takeIf { it.isNotEmpty() },
            features = art.features.toTypedArray().takeIf { it.isNotEmpty() }
        )
    }
}

/**
 * Auth-token record. Mirrors upstream `AuthRecord`; upstream's
 * [issued] / [accessed] are wall-clock millis identical to ours.
 */
data class AuthRecord(
    @JvmField val authToken: String,
    @JvmField val identity: IdentityRecord,
    @JvmField val accounts: Array<AccountRecord>,
    @JvmField val cluster: String?,
    @JvmField val chain: String?,
    @JvmField val scope: ByteArray,
    @JvmField val walletUriBase: Uri?,
    @JvmField val issued: Long,
    @JvmField val accessed: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuthRecord) return false
        return authToken == other.authToken &&
            identity == other.identity &&
            accounts.contentEquals(other.accounts) &&
            cluster == other.cluster &&
            chain == other.chain &&
            scope.contentEquals(other.scope) &&
            walletUriBase == other.walletUriBase &&
            issued == other.issued &&
            accessed == other.accessed
    }

    override fun hashCode(): Int {
        var h = authToken.hashCode()
        h = 31 * h + identity.hashCode()
        h = 31 * h + accounts.contentHashCode()
        h = 31 * h + (cluster?.hashCode() ?: 0)
        h = 31 * h + (chain?.hashCode() ?: 0)
        h = 31 * h + scope.contentHashCode()
        h = 31 * h + (walletUriBase?.hashCode() ?: 0)
        h = 31 * h + issued.hashCode()
        h = 31 * h + accessed.hashCode()
        return h
    }

    companion object {
        @JvmStatic
        internal fun fromArtemis(art: ArtemisAuthRecord): AuthRecord = AuthRecord(
            authToken = art.authToken,
            identity = IdentityRecord.fromArtemis(art.identity),
            accounts = art.accounts.map { AccountRecord.fromArtemis(it) }.toTypedArray(),
            cluster = art.chain?.let {
                com.selenus.artemis.wallet.mwa.walletlib.ProtocolContract.clusterForChain(it) ?: it
            },
            chain = art.chain,
            scope = art.scope,
            walletUriBase = art.walletUriBase,
            issued = art.issuedAtEpochMs,
            accessed = art.lastUsedAtEpochMs
        )
    }
}

/**
 * Wallet-side persistence interface. Mirrors upstream walletlib's
 * `AuthRepository` Java interface. Methods are blocking (Java-friendly)
 * and bridge into the Artemis suspend [ArtemisAuthRepository] when the
 * implementation passes through to it.
 *
 * Custom Java implementations of this interface keep working: the
 * scenario calls them via the [AuthIssuerToArtemisRepositoryAdapter].
 */
interface AuthRepository {
    fun start() {}
    fun stop() {}

    fun issue(
        name: String?,
        uri: Uri?,
        relativeIconUri: Uri?,
        accounts: Array<CompatAuthorizedAccount>,
        cluster: String?,
        walletUriBase: Uri?,
        scope: ByteArray
    ): AuthRecord

    fun fromAuthToken(authToken: String): AuthRecord?
    fun toAuthToken(authRecord: AuthRecord): String = authRecord.authToken

    fun reissue(authRecord: AuthRecord): AuthRecord?

    fun revoke(authRecord: AuthRecord): Boolean
    fun revoke(identityRecord: IdentityRecord): Boolean

    fun getAuthorizedIdentities(): Array<IdentityRecord> = emptyArray()
    fun getAuthorizations(identityRecord: IdentityRecord): Array<AuthRecord> = emptyArray()

    /** Internal escape hatch the scenario uses to bridge to Artemis. */
    fun asArtemis(): ArtemisAuthRepository
}

/**
 * Default in-memory implementation. Wraps the Artemis
 * [com.selenus.artemis.wallet.mwa.walletlib.InMemoryAuthRepository] so
 * upstream-shaped calls (`fromAuthToken`, `revoke(AuthRecord)`, ...)
 * read and write the same store the dispatcher uses.
 */
class InMemoryAuthRepository(
    issuerConfig: AuthIssuerConfig
) : AuthRepository {
    private val backing = com.selenus.artemis.wallet.mwa.walletlib.InMemoryAuthRepository(
        issuerConfig.toArtemis()
    )

    override fun start() = runBlocking { backing.start() }
    override fun stop() = runBlocking { backing.stop() }

    override fun issue(
        name: String?,
        uri: Uri?,
        relativeIconUri: Uri?,
        accounts: Array<CompatAuthorizedAccount>,
        cluster: String?,
        walletUriBase: Uri?,
        scope: ByteArray
    ): AuthRecord = runBlocking {
        val record = backing.issue(
            identity = ArtemisIdentity(name, uri, relativeIconUri),
            accounts = accounts.map { it.toArtemis() },
            chain = cluster,
            scope = scope,
            walletUri = walletUriBase
        )
        AuthRecord.fromArtemis(record)
    }

    override fun fromAuthToken(authToken: String): AuthRecord? = runBlocking {
        backing.lookup(authToken)?.let { AuthRecord.fromArtemis(it) }
    }

    override fun reissue(authRecord: AuthRecord): AuthRecord? = runBlocking {
        backing.reissue(authRecord.authToken)?.let { AuthRecord.fromArtemis(it) }
    }

    override fun revoke(authRecord: AuthRecord): Boolean = runBlocking {
        backing.revoke(authRecord.authToken)
    }

    override fun revoke(identityRecord: IdentityRecord): Boolean = runBlocking {
        backing.revokeAllForIdentity(identityRecord.toArtemis()) > 0
    }

    override fun getAuthorizedIdentities(): Array<IdentityRecord> = runBlocking {
        backing.getAuthorizedIdentities()
            .map { IdentityRecord.fromArtemis(it) }
            .toTypedArray()
    }

    override fun getAuthorizations(identityRecord: IdentityRecord): Array<AuthRecord> = runBlocking {
        backing.getAuthorizations(identityRecord.toArtemis())
            .map { AuthRecord.fromArtemis(it) }
            .toTypedArray()
    }

    override fun asArtemis(): ArtemisAuthRepository = backing
}

/**
 * Adapter that lets a Java-shaped [AuthRepository] participate in the
 * Artemis dispatcher's `suspend` interface. Used for custom Java
 * repository implementations that don't already wrap an Artemis
 * backing — calls bridge through the blocking compat methods on the
 * dispatcher's IO context so they don't strand a coroutine.
 */
internal class AuthIssuerToArtemisRepositoryAdapter(
    private val compat: AuthRepository
) : ArtemisAuthRepository {

    override suspend fun start() = runOnIo { compat.start() }
    override suspend fun stop() = runOnIo { compat.stop() }

    override suspend fun issue(
        identity: ArtemisIdentity,
        accounts: List<ArtemisAuthorizedAccount>,
        chain: String?,
        scope: ByteArray,
        walletUri: Uri?
    ): ArtemisAuthRecord = runOnIo {
        val compatAccounts = accounts.map { acc ->
            CompatAuthorizedAccount(
                publicKey = acc.publicKey,
                accountLabel = acc.accountLabel,
                displayAddress = acc.displayAddress,
                displayAddressFormat = acc.displayAddressFormat,
                accountIcon = acc.accountIcon,
                chains = acc.chains.toTypedArray().takeIf { it.isNotEmpty() },
                features = acc.features.toTypedArray().takeIf { it.isNotEmpty() }
            )
        }.toTypedArray()
        val rec = compat.issue(
            name = identity.name,
            uri = identity.uri,
            relativeIconUri = identity.iconRelativeUri,
            accounts = compatAccounts,
            cluster = chain,
            walletUriBase = walletUri,
            scope = scope
        )
        toArtemis(rec)
    }

    override suspend fun lookup(authToken: String): ArtemisAuthRecord? = runOnIo {
        compat.fromAuthToken(authToken)?.let { toArtemis(it) }
    }

    override suspend fun reissue(authToken: String): ArtemisAuthRecord? = runOnIo {
        val record = compat.fromAuthToken(authToken) ?: return@runOnIo null
        compat.reissue(record)?.let { toArtemis(it) }
    }

    override suspend fun revoke(authToken: String): Boolean = runOnIo {
        val record = compat.fromAuthToken(authToken) ?: return@runOnIo false
        compat.revoke(record)
    }

    override suspend fun revokeAllForIdentity(identity: ArtemisIdentity): Int = runOnIo {
        val identityRecord = IdentityRecord.fromArtemis(identity)
        if (compat.revoke(identityRecord)) 1 else 0
    }

    override suspend fun getAuthorizedIdentities(): List<ArtemisIdentity> = runOnIo {
        compat.getAuthorizedIdentities().map { it.toArtemis() }
    }

    override suspend fun getAuthorizations(identity: ArtemisIdentity): List<ArtemisAuthRecord> = runOnIo {
        compat.getAuthorizations(IdentityRecord.fromArtemis(identity)).map { toArtemis(it) }
    }

    private fun toArtemis(rec: AuthRecord): ArtemisAuthRecord = ArtemisAuthRecord(
        authToken = rec.authToken,
        identity = rec.identity.toArtemis(),
        accounts = rec.accounts.map { it.toArtemis() },
        chain = rec.chain,
        scope = rec.scope,
        walletUriBase = rec.walletUriBase,
        issuedAtEpochMs = rec.issued,
        lastUsedAtEpochMs = rec.accessed
    )

    private suspend fun <T> runOnIo(block: () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
}

/**
 * Bridge a compat [AuthRepository] into an Artemis suspend repo. The
 * default in-memory impl returns its backing Artemis repo directly;
 * custom Java impls go through the runtime adapter.
 */
internal fun AuthRepository.toArtemisAdapter(): ArtemisAuthRepository =
    if (this is InMemoryAuthRepository) this.asArtemis()
    else AuthIssuerToArtemisRepositoryAdapter(this)
