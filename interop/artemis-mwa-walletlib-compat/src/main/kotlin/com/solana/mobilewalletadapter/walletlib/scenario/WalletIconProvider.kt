/*
 * Drop-in source compatibility for upstream walletlib's
 * WalletIconProvider / DefaultWalletIconProvider.
 *
 * Wallets resolve a dApp's icon URI through this seam so they can
 * proxy fetches through their own cache, retry policy, or trust
 * checks. The Artemis path delegates icon-fetching to the wallet's
 * UI layer; this module supplies the FQN + a no-op default so
 * upstream code that injects a custom provider keeps compiling.
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.walletlib.scenario

import android.net.Uri

/**
 * Resolves a dApp icon URI to a fully-qualified URI the wallet can
 * fetch. Implementations may rewrite paths, attach trust headers,
 * inject a CDN, or return null when the URI is on a blocklist.
 */
interface WalletIconProvider {
    /**
     * @param identityUri Canonical URL of the dApp.
     * @param iconRelativeUri Path relative to [identityUri] pointing
     *   at the icon. May be null when the dApp omitted it.
     * @return Fully-qualified absolute URI to fetch, or null when the
     *   wallet should render its own placeholder.
     */
    fun resolve(identityUri: Uri?, iconRelativeUri: Uri?): Uri?
}

/**
 * Default provider that resolves [iconRelativeUri] against
 * [identityUri] using `Uri.withAppendedPath` semantics. Returns null
 * when either input is missing.
 */
class DefaultWalletIconProvider : WalletIconProvider {
    override fun resolve(identityUri: Uri?, iconRelativeUri: Uri?): Uri? {
        if (identityUri == null || iconRelativeUri == null) return null
        // Already absolute. pass through.
        if (iconRelativeUri.isAbsolute) return iconRelativeUri
        return identityUri.buildUpon()
            .encodedPath(iconRelativeUri.encodedPath)
            .build()
    }
}
