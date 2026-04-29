package com.selenus.artemis.wallet.mwa

import com.selenus.artemis.wallet.mwa.protocol.MwaCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour tests for [MwaWalletConformance]. Pins the fingerprint matrix
 * (Phantom / Solflare / Backpack / Seeker / Saga) and the workaround flags
 * each known wallet warrants. A regression in the fingerprint heuristics
 * makes one of these tests fail.
 */
class MwaWalletConformanceTest {

    @Test
    fun `phantom fingerprint sets minContextSlot and chain verify and batch retry`() {
        // Phantom: maxTx=10 + sign-and-send + sign-transactions + sign-in.
        val caps = MwaCapabilities(
            maxTransactionsPerRequest = 10,
            features = listOf(
                MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS,
                MwaCapabilities.FEATURE_SIGN_TRANSACTIONS,
                MwaCapabilities.FEATURE_SIGN_IN_WITH_SOLANA
            )
        )

        val report = MwaWalletConformance.inspect(caps)

        assertEquals(KnownWallet.PHANTOM, report.knownWallet)
        assertTrue(
            "Phantom needs minContextSlot on signAndSend (#1146)",
            report.requireMinContextSlotOnSignAndSend
        )
        assertTrue("Phantom needs chain verification (#958)", report.verifyChainBeforeSign)
        assertTrue("Phantom benefits from batch retry on sign", report.useBatchRetryOnSign)
        assertFalse(report.needsTightConnectTimeout)
        assertFalse(report.requireBytesAddressInSiws)
        assertFalse("Phantom is not strictly compliant", report.isStrictlyCompliant)
        assertTrue(
            "notes mention upstream issues",
            report.notes.any { it.contains("#1146") } && report.notes.any { it.contains("#958") }
        )
    }

    @Test
    fun `saga stock fingerprint is strictly compliant`() {
        // Saga stock: maxTx>=20, every feature including clone-auth + sign-in.
        val caps = MwaCapabilities(
            maxTransactionsPerRequest = 20,
            features = listOf(
                MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS,
                MwaCapabilities.FEATURE_SIGN_TRANSACTIONS,
                MwaCapabilities.FEATURE_SIGN_IN_WITH_SOLANA,
                MwaCapabilities.FEATURE_CLONE_AUTHORIZATION
            )
        )

        val report = MwaWalletConformance.inspect(caps)

        assertEquals(KnownWallet.SAGA_STOCK, report.knownWallet)
        assertTrue("Saga stock is strictly compliant", report.isStrictlyCompliant)
        assertFalse(report.requireMinContextSlotOnSignAndSend)
        assertFalse(report.verifyChainBeforeSign)
        assertFalse(report.needsTightConnectTimeout)
        assertFalse(report.requireBytesAddressInSiws)
        assertFalse(report.useBatchRetryOnSign)
        assertEquals(1, report.notes.size)
    }

    @Test
    fun `solflare fingerprint requires bytes address in SIWS`() {
        // Solflare: maxTx=7, sign-in but no clone-auth.
        val caps = MwaCapabilities(
            maxTransactionsPerRequest = 7,
            features = listOf(
                MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS,
                MwaCapabilities.FEATURE_SIGN_IN_WITH_SOLANA
            )
        )

        val report = MwaWalletConformance.inspect(caps)

        assertEquals(KnownWallet.SOLFLARE, report.knownWallet)
        assertTrue("Solflare needs bytes address (#1331)", report.requireBytesAddressInSiws)
        assertTrue(report.verifyChainBeforeSign)
        assertTrue(report.useBatchRetryOnSign)
        assertFalse(report.requireMinContextSlotOnSignAndSend)
        assertFalse(report.needsTightConnectTimeout)
        assertTrue(
            "notes mention #1331",
            report.notes.any { it.contains("#1331") }
        )
    }

    @Test
    fun `backpack fingerprint matches max-tx-five with no sign-in`() {
        // Backpack: maxTx=5, no sign-in.
        val caps = MwaCapabilities(
            maxTransactionsPerRequest = 5,
            features = listOf(MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS)
        )

        val report = MwaWalletConformance.inspect(caps)

        assertEquals(KnownWallet.BACKPACK, report.knownWallet)
        assertTrue(report.verifyChainBeforeSign)
        assertFalse(report.requireMinContextSlotOnSignAndSend)
        assertFalse(report.requireBytesAddressInSiws)
        assertFalse(report.useBatchRetryOnSign)
        assertFalse(report.needsTightConnectTimeout)
    }

    @Test
    fun `unknown fingerprint enables conservative workarounds`() {
        // Empty/odd capabilities: matches no rule. Conservative defaults.
        val caps = MwaCapabilities()

        val report = MwaWalletConformance.inspect(caps)

        assertEquals(KnownWallet.UNKNOWN, report.knownWallet)
        // Conservative defaults: cheap workarounds that never hurt compliant wallets.
        assertTrue(report.requireMinContextSlotOnSignAndSend)
        assertTrue(report.verifyChainBeforeSign)
        assertFalse(report.useBatchRetryOnSign)
        assertFalse(report.requireBytesAddressInSiws)
        assertFalse(report.needsTightConnectTimeout)
        assertNotNull(report.notes.firstOrNull())
    }

    @Test
    fun `seeker stock gets tight connect timeout for silent TWA dismiss`() {
        // Seeker stock: sign-and-send + sign-transactions + legacy, no clone-auth.
        // maxTx is deliberately not 5, 7, 8, 10, or >=20 so prior buckets don't claim it.
        val caps = MwaCapabilities(
            maxTransactionsPerRequest = 3,
            features = listOf(
                MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS,
                MwaCapabilities.FEATURE_SIGN_TRANSACTIONS
            )
            // supportedTransactionVersions == null -> legacy implicitly supported
        )

        val report = MwaWalletConformance.inspect(caps)

        assertEquals(KnownWallet.SEEKER_STOCK, report.knownWallet)
        assertTrue(
            "Seeker stock needs tight connect timeout (#1458)",
            report.needsTightConnectTimeout
        )
        assertFalse(report.requireMinContextSlotOnSignAndSend)
        assertFalse(report.verifyChainBeforeSign)
        assertTrue(
            "notes mention upstream #1458",
            report.notes.any { it.contains("#1458") }
        )
    }

    @Test
    fun `isStrictlyCompliant is false whenever any workaround is on`() {
        // Build a synthetic Phantom-shaped report and make sure the derived
        // flag reflects the workaround set.
        val phantomCaps = MwaCapabilities(
            maxTransactionsPerRequest = 10,
            features = listOf(
                MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS,
                MwaCapabilities.FEATURE_SIGN_TRANSACTIONS,
                MwaCapabilities.FEATURE_SIGN_IN_WITH_SOLANA
            )
        )
        val report = MwaWalletConformance.inspect(phantomCaps)
        assertFalse(report.isStrictlyCompliant)

        val sagaReport = MwaWalletConformance.inspect(
            MwaCapabilities(
                maxTransactionsPerRequest = 25,
                features = listOf(
                    MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS,
                    MwaCapabilities.FEATURE_SIGN_TRANSACTIONS,
                    MwaCapabilities.FEATURE_SIGN_IN_WITH_SOLANA,
                    MwaCapabilities.FEATURE_CLONE_AUTHORIZATION
                )
            )
        )
        assertTrue(sagaReport.isStrictlyCompliant)
    }

    @Test
    fun `notes are non-empty for every fingerprint bucket`() {
        // The dev-overlay surface contract: every fingerprint bucket emits at
        // least one human-readable note that the caller can show in a debug UI.
        val cases = listOf(
            MwaCapabilities(
                maxTransactionsPerRequest = 25,
                features = listOf(
                    MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS,
                    MwaCapabilities.FEATURE_SIGN_TRANSACTIONS,
                    MwaCapabilities.FEATURE_SIGN_IN_WITH_SOLANA,
                    MwaCapabilities.FEATURE_CLONE_AUTHORIZATION
                )
            ),
            MwaCapabilities(
                maxTransactionsPerRequest = 10,
                features = listOf(
                    MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS,
                    MwaCapabilities.FEATURE_SIGN_TRANSACTIONS,
                    MwaCapabilities.FEATURE_SIGN_IN_WITH_SOLANA
                )
            ),
            MwaCapabilities(
                maxTransactionsPerRequest = 7,
                features = listOf(
                    MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS,
                    MwaCapabilities.FEATURE_SIGN_IN_WITH_SOLANA
                )
            ),
            MwaCapabilities(
                maxTransactionsPerRequest = 5,
                features = listOf(MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS)
            ),
            MwaCapabilities()
        )
        for (caps in cases) {
            val report = MwaWalletConformance.inspect(caps)
            assertTrue(
                "notes non-empty for ${report.knownWallet}",
                report.notes.isNotEmpty()
            )
        }
    }
}
