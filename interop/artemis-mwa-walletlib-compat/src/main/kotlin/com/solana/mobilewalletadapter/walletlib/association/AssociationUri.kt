/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.walletlib.association.
 *
 * Upstream exposes `AssociationUri` as an abstract base with two
 * concrete subclasses: `LocalAssociationUri` and `RemoteAssociationUri`.
 * The shim mirrors the same package + class names and adapts every
 * field to the Artemis sealed
 * [com.selenus.artemis.wallet.mwa.walletlib.AssociationUri].
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.walletlib.association

import android.content.Context
import android.net.Uri
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import com.solana.mobilewalletadapter.walletlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.walletlib.scenario.RemoteWebSocketServerScenario
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario
import com.selenus.artemis.wallet.mwa.walletlib.AssociationUri as ArtemisAssociationUri
import com.selenus.artemis.wallet.mwa.walletlib.MwaAssociationException

/**
 * Abstract association-URI base. Two concrete subclasses mirror the
 * upstream walletlib package shape.
 */
abstract class AssociationUri internal constructor(
    @JvmField val uri: Uri,
    @JvmField val associationPublicKey: ByteArray
) {
    /**
     * Build a [Scenario] for this URI. Upstream's primary entry point;
     * the wallet's MWA Activity calls this and start()s the result.
     * Subclasses override to pick the right scenario flavour.
     */
    abstract fun createScenario(
        context: Context,
        config: MobileWalletAdapterConfig,
        authIssuerConfig: AuthIssuerConfig,
        callbacks: Scenario.Callbacks
    ): Scenario

    companion object {
        /**
         * Parse a `solana-wallet:` URI. Returns the matching subclass;
         * throws a typed exception that wraps the underlying Artemis
         * [MwaAssociationException] so consumers can catch the exact
         * error path the upstream walletlib uses.
         */
        @JvmStatic
        fun parse(uri: Uri): AssociationUri {
            val artemis = ArtemisAssociationUri.parse(uri)
            return when (artemis) {
                is ArtemisAssociationUri.Local -> LocalAssociationUri(uri, artemis)
                is ArtemisAssociationUri.Remote -> RemoteAssociationUri(uri, artemis)
            }
        }

        /**
         * Permissive parser that returns null on malformed input. Mirrors
         * the upstream factory's `@Nullable` contract so Java callers
         * that swallow the parse failure (ifelse pattern in the
         * upstream walletlib activity) keep their existing branching.
         */
        @JvmStatic
        fun parseOrNull(uri: Uri): AssociationUri? = try {
            parse(uri)
        } catch (_: MwaAssociationException) {
            null
        } catch (_: Throwable) {
            null
        }
    }
}

class LocalAssociationUri internal constructor(
    uri: Uri,
    internal val artemis: ArtemisAssociationUri.Local
) : AssociationUri(uri, artemis.associationPublicKey) {
    @JvmField val port: Int = artemis.port

    /**
     * Expose the artemis core seal type so callers that already opted
     * into the Artemis API can pull it out without re-parsing.
     */
    fun toArtemis(): ArtemisAssociationUri.Local = artemis

    override fun createScenario(
        context: Context,
        config: MobileWalletAdapterConfig,
        authIssuerConfig: AuthIssuerConfig,
        callbacks: Scenario.Callbacks
    ): Scenario = LocalAssociationScenario(
        context = context,
        associationUri = this,
        config = config,
        authIssuerConfig = authIssuerConfig,
        callbacks = callbacks
    )
}

class RemoteAssociationUri internal constructor(
    uri: Uri,
    internal val artemis: ArtemisAssociationUri.Remote
) : AssociationUri(uri, artemis.associationPublicKey) {
    @JvmField val reflectorAuthority: String = artemis.reflectorAuthority
    @JvmField val reflectorId: ByteArray = artemis.reflectorId

    fun toArtemis(): ArtemisAssociationUri.Remote = artemis

    override fun createScenario(
        context: Context,
        config: MobileWalletAdapterConfig,
        authIssuerConfig: AuthIssuerConfig,
        callbacks: Scenario.Callbacks
    ): Scenario = RemoteWebSocketServerScenario(
        associationUri = this,
        config = config,
        authIssuerConfig = authIssuerConfig,
        callbacks = callbacks
    )
}
