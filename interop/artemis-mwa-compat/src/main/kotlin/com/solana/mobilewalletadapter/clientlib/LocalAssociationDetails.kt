/*
 * Drop-in source compatibility with
 * `com.solana.mobilewalletadapter.clientlib.LocalAssociationDetails`.
 *
 * Upstream ships `AssociationDetails(uriPrefix, port, session)` plus an
 * extension factory `LocalAssociationScenario.associationDetails(uri)`. This
 * shim preserves both. Artemis's underlying scenario does not expose a raw
 * `MobileWalletAdapterSession` object (sessions live inside the Artemis MWA
 * client), so the `session` field holds a typealiased Any-backed placeholder
 * that callers only read back; upstream code paths that pattern-match on it
 * are extremely rare.
 */
package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario

/** Placeholder session handle. Replaced by Artemis internals when needed. */
typealias MobileWalletAdapterSession = Any

/**
 * Captures the details needed to launch the local-association intent.
 *
 * Matches upstream `AssociationDetails(uriPrefix, port, session)`.
 */
data class AssociationDetails(
    val uriPrefix: Uri? = null,
    val port: Int,
    val session: MobileWalletAdapterSession
)

/**
 * Extension that extracts details from a [LocalAssociationScenario].
 *
 * Matches upstream's factory extension so apps that call
 * `scenario.associationDetails(uri)` continue to compile unchanged.
 */
fun LocalAssociationScenario.associationDetails(uri: Uri? = null): AssociationDetails =
    AssociationDetails(
        uriPrefix = uri,
        port = getPort(),
        session = this
    )
