/*
 * MwaWalletConformance
 *
 * Inspects an [MwaCapabilities] reply (returned by the wallet during `authorize`
 * / `get_capabilities`) and flags known third-party deviations so apps can
 * apply targeted workarounds or surface an informed warning to the user.
 *
 * Background:
 *
 * The MWA 2.0 specification is deliberately narrow, but every shipped wallet
 * has a slightly different interpretation. Issues repeatedly filed against
 * `solana-mobile/mobile-wallet-adapter` show the same quirks over and over:
 *
 *   - Phantom treats `minContextSlot` as required on `signAndSendTransactions`
 *     even though the spec marks it optional (upstream #1146, still open).
 *   - Phantom and Solflare have historically signed a transaction against a
 *     different cluster than the one they claimed in `get_capabilities`
 *     (upstream #958, 2+ years open).
 *   - Seeker stock wallet times out the local WebSocket on Trusted Web
 *     Activity launches, dismissing the bottom sheet without telling the app
 *     (upstream #1458).
 *   - Some wallet builds advertise `solana:signIn` support but only accept a
 *     byte-array address in the SIWS payload, failing when the deprecated
 *     string form is used (upstream #1331, RN-specific variant).
 *
 * `MwaWalletConformance` is Artemis's opinionated normalization layer: it
 * returns a [ConformanceReport] the app can use to set sensible defaults
 * before calling the adapter. It does not modify wallet behavior; it only
 * gives the caller the information to work around the wallet's behavior.
 *
 * Intentionally Artemis-native and not exposed in the compat modules.
 */
package com.selenus.artemis.wallet.mwa

import com.selenus.artemis.wallet.mwa.protocol.MwaCapabilities

/**
 * Outcome of inspecting a wallet's advertised capabilities.
 *
 * All fields are non-null and deterministic. Apps can `when { report.<flag> }`
 * to drive per-wallet behavior without maintaining their own workaround table.
 */
data class ConformanceReport(
    /**
     * The wallet identifier Artemis matched, or [KnownWallet.UNKNOWN] when the
     * capabilities payload does not fingerprint against any known shape. Not
     * an authoritative identity; some wallets do not advertise their name.
     */
    val knownWallet: KnownWallet,

    /**
     * When true, always pass `minContextSlot` on sign-and-send calls, even when
     * the app would not otherwise set one. Phantom rejects the call without it
     * on some versions even though the spec marks it optional.
     */
    val requireMinContextSlotOnSignAndSend: Boolean,

    /**
     * When true, compare the wallet's reported chain against the app's target
     * chain before every sign call and warn the user if they diverge. Catches
     * cross-cluster signing (upstream #958) which Artemis cannot prevent at the
     * protocol layer.
     */
    val verifyChainBeforeSign: Boolean,

    /**
     * Some wallets close the bottom-sheet Activity without ever opening the
     * local WebSocket, leaving `connect()` hanging. When this is true, apps
     * should wrap the connect call in [MwaDialogTimeoutHandler] with a tight
     * timeout (10s is the spec recommendation) and treat a timeout as
     * "wallet not installed or not responding" rather than "connection broken".
     */
    val needsTightConnectTimeout: Boolean,

    /**
     * When true, SIWS payload construction must use the byte-array `address`
     * form rather than the deprecated string form; some wallets crash on the
     * string form during JSON round-trip.
     */
    val requireBytesAddressInSiws: Boolean,

    /**
     * When true, wrap `signAndSendTransactions` and `signTransactions` in a
     * batch retry loop (already available via [MwaBatchRetry]) because the
     * wallet occasionally drops a response under load. Default recommended
     * attempts = 2.
     */
    val useBatchRetryOnSign: Boolean,

    /**
     * Human-readable annotations the caller can surface in a debug overlay or
     * log line. One entry per quirk detected.
     */
    val notes: List<String>,
) {
    /** No workaround is needed. Wallet matches the spec. */
    val isStrictlyCompliant: Boolean get() =
        !requireMinContextSlotOnSignAndSend &&
            !verifyChainBeforeSign &&
            !needsTightConnectTimeout &&
            !requireBytesAddressInSiws &&
            !useBatchRetryOnSign
}

/**
 * Known wallet fingerprints. The set is intentionally small; [UNKNOWN] is the
 * right bucket for anything Artemis has not been tested against.
 */
enum class KnownWallet {
    PHANTOM,
    SOLFLARE,
    BACKPACK,
    SEEKER_STOCK,
    SAGA_STOCK,
    UNKNOWN,
}

/**
 * Inspect an [MwaCapabilities] bag and produce a conformance report.
 *
 * The matcher does not rely on wallet-name strings (most wallets do not send
 * one). Instead it fingerprints by feature-set shape, protocol version, and
 * a tiny set of known deviations.
 */
object MwaWalletConformance {

    fun inspect(capabilities: MwaCapabilities): ConformanceReport {
        val features = capabilities.allFeatures()
        val wallet = fingerprint(capabilities, features)
        val notes = mutableListOf<String>()
        var minContextSlotFix = false
        var chainVerifyFix = false
        var tightTimeoutFix = false
        var bytesAddressFix = false
        var batchRetryFix = false

        when (wallet) {
            KnownWallet.PHANTOM -> {
                minContextSlotFix = true
                chainVerifyFix = true
                batchRetryFix = true
                notes += "Phantom: passing minContextSlot on every signAndSendTransactions call (upstream #1146)."
                notes += "Phantom: verifying wallet chain before signing (upstream #958)."
            }
            KnownWallet.SOLFLARE -> {
                chainVerifyFix = true
                batchRetryFix = true
                bytesAddressFix = true
                notes += "Solflare: verifying wallet chain before signing (upstream #958)."
                notes += "Solflare: SIWS payload uses byte-array address form (upstream #1331)."
            }
            KnownWallet.SEEKER_STOCK -> {
                tightTimeoutFix = true
                notes += "Seeker stock wallet: tight connect timeout to detect silent TWA dismiss (upstream #1458)."
            }
            KnownWallet.SAGA_STOCK -> {
                // Saga stock wallet ships the reference implementation and is
                // strictly compliant today. Kept as a distinct bucket so
                // regressions surface clearly.
                notes += "Saga stock wallet: no known deviations."
            }
            KnownWallet.BACKPACK -> {
                chainVerifyFix = true
                notes += "Backpack: verifying wallet chain before signing (general hardening)."
            }
            KnownWallet.UNKNOWN -> {
                // Conservative defaults: when fingerprinting fails, enable
                // the cheap workarounds that never hurt compliant wallets.
                minContextSlotFix = true
                chainVerifyFix = true
                notes += "Unknown wallet fingerprint: enabling conservative workarounds."
            }
        }

        return ConformanceReport(
            knownWallet = wallet,
            requireMinContextSlotOnSignAndSend = minContextSlotFix,
            verifyChainBeforeSign = chainVerifyFix,
            needsTightConnectTimeout = tightTimeoutFix,
            requireBytesAddressInSiws = bytesAddressFix,
            useBatchRetryOnSign = batchRetryFix,
            notes = notes.toList(),
        )
    }

    /**
     * Best-effort fingerprint from the capabilities payload alone. The wallet
     * does not self-identify in the spec; Artemis matches the small set of
     * deviations each shipped wallet is known for. Expand as new wallets ship.
     */
    private fun fingerprint(cap: MwaCapabilities, features: Set<String>): KnownWallet {
        val maxTx = cap.maxTransactionsPerRequest ?: 0

        // Saga stock: supports every feature in the MWA 2.0 feature set and
        // caps at 20+ transactions. Artemis treats the Saga reference wallet
        // as the strict compliance baseline. Evaluate first so the broader
        // "includes sign_in" heuristic below does not claim it.
        if (maxTx >= 20 &&
            cap.supportsCloneAuth() &&
            cap.supportsSignIn() &&
            cap.supportsSignAndSend()
        ) return KnownWallet.SAGA_STOCK

        // Phantom: advertises both sign_and_send and sign_transactions, caps
        // transactions-per-request at 10 today, and supports sign-in.
        if (maxTx == 10 &&
            cap.supportsSignAndSend() &&
            cap.supportsSignTransactions() &&
            cap.supportsSignIn()
        ) return KnownWallet.PHANTOM

        // Solflare: pre-2.0 bug bucket. Caps transactions at 6-8, advertises
        // sign-in but not clone-authorization.
        if (maxTx in 6..8 &&
            cap.supportsSignIn() &&
            !cap.supportsCloneAuth()
        ) return KnownWallet.SOLFLARE

        // Backpack: caps at 5 transactions, does not advertise sign-in yet.
        if (maxTx == 5 &&
            !cap.supportsSignIn()
        ) return KnownWallet.BACKPACK

        // Seeker stock: advertises exactly the MWA 2.0 minimum set; the
        // combination of sign_and_send + legacy transactions and no
        // clone-authorization fingerprints it on current firmware.
        if (cap.supportsSignAndSend() &&
            cap.supportsLegacyTransactions() &&
            !cap.supportsCloneAuth() &&
            cap.supportsSignTransactions()
        ) return KnownWallet.SEEKER_STOCK

        return KnownWallet.UNKNOWN
    }
}
