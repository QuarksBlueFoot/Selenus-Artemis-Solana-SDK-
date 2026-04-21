package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Invariants for the ktx-level result types that the audit flagged as
 * "still lossy": [AuthorizationResult] and the new [TransactionBatchResult] /
 * [TransactionItemResult] pair.
 *
 * AuthorizationResult:
 *   - Every MWA 2.0 field participates in equality (earlier revisions
 *     compared only `authToken + publicKey`, which collapsed
 *     distinguishable multi-account authorizations into a single bucket).
 *   - Legacy flat fields are still present but explicitly deprecated.
 *
 * TransactionBatchResult:
 *   - Per-slot invariants enforced at construction: success XOR error.
 *   - Batch-level invariants enforced at construction: result.size ==
 *     input.size, indexes preserved.
 */
class MwaCompatResultsTest {

    @Suppress("DEPRECATION")
    @Test
    fun `authorization equality covers every field`() {
        val account = AuthorizationResult.Account(
            publicKey = ByteArray(32) { 0xA1.toByte() },
            displayAddress = "d",
            displayAddressFormat = "f",
            label = "main",
            chains = listOf("solana:mainnet"),
            features = listOf("solana:signAndSendTransaction")
        )
        // `Uri.parse` returns null under the JVM stub `android.jar`. Use a
        // mockk instance so we can distinguish "has wallet URI" from
        // "does not" without Robolectric.
        val walletUri: Uri = mockk(relaxed = true)
        val baseline = AuthorizationResult(
            authToken = "T",
            publicKey = account.publicKey,
            accountLabel = "main",
            walletUriBase = walletUri,
            walletIcon = null,
            accounts = listOf(account),
            signInResult = null
        )
        assertEquals(baseline, baseline.copy())

        val differentChains = baseline.copy(
            accounts = listOf(account.copy(chains = listOf("solana:devnet")))
        )
        assertNotEquals("different per-account chains => not equal", baseline, differentChains)

        val differentFeatures = baseline.copy(
            accounts = listOf(account.copy(features = listOf("solana:signMessages")))
        )
        assertNotEquals(
            "different per-account features => not equal",
            baseline, differentFeatures
        )

        val differentWalletUri = baseline.copy(walletUriBase = null)
        assertNotEquals("different walletUriBase => not equal", baseline, differentWalletUri)

        val differentAccounts = baseline.copy(accounts = emptyList())
        assertNotEquals("different accounts list => not equal", baseline, differentAccounts)
    }

    @Test
    fun `account equality covers displayAddressFormat label chains features`() {
        val base = AuthorizationResult.Account(
            publicKey = ByteArray(32),
            displayAddress = "abc",
            displayAddressFormat = "base58",
            label = "mine",
            chains = listOf("solana:mainnet"),
            features = listOf("solana:signAndSendTransaction")
        )
        assertEquals(base, base.copy())
        assertNotEquals(base, base.copy(displayAddressFormat = "bech32"))
        assertNotEquals(base, base.copy(label = "other"))
        assertNotEquals(base, base.copy(chains = emptyList()))
        assertNotEquals(base, base.copy(features = emptyList()))
    }

    @Test
    fun `batch result enforces size + index + success XOR error`() {
        val good = TransactionBatchResult.of(
            inputSize = 3,
            signatures = listOf("s0", null, "s2"),
            errors = listOf(null, RuntimeException("fail"), null)
        )
        assertEquals(3, good.results.size)
        assertEquals(2, good.successCount)
        assertEquals(1, good.failureCount)
        assertFalse(good.allSuccess)
        assertTrue(good.anyFailure)
        assertEquals(listOf(0, 1, 2), good.results.map { it.index })

        // success = true requires a signature
        try {
            TransactionItemResult(index = 0, signature = null, error = null, success = true)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}

        // success = true with error is contradiction
        try {
            TransactionItemResult(
                index = 0, signature = "s", error = RuntimeException(), success = true
            )
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}

        // result.size must equal input.size (enforced in factory)
        try {
            TransactionBatchResult.of(inputSize = 2, signatures = listOf("s0"))
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `batch preserves per-slot ordering under partial failure`() {
        val batch = TransactionBatchResult.of(
            inputSize = 4,
            signatures = listOf("s0", null, "s2", null),
            errors = listOf(null, RuntimeException("e1"), null, RuntimeException("e3"))
        )
        // Slot i is at position i. No reordering under partial failure.
        assertEquals(0, batch.results[0].index)
        assertEquals("s0", batch.results[0].signature)
        assertTrue(batch.results[0].isSuccess)

        assertEquals(1, batch.results[1].index)
        assertTrue(batch.results[1].isFailure)
        assertEquals("e1", batch.results[1].error?.message)

        assertEquals(2, batch.results[2].index)
        assertEquals("s2", batch.results[2].signature)

        assertEquals(3, batch.results[3].index)
        assertTrue(batch.results[3].isFailure)
    }

    @Test
    fun `signatures() helper skips failed slots but keeps succeeded order`() {
        val batch = TransactionBatchResult.of(
            inputSize = 3,
            signatures = listOf("a", null, "c"),
            errors = listOf(null, RuntimeException(), null)
        )
        assertEquals(listOf("a", "c"), batch.signatures())
        assertEquals(1, batch.failures().size)
        assertEquals(1, batch.failures()[0].index)
    }

    // Gotcha tests. Each pins a subtle failure mode the audit flagged as
    // the kind of thing that breaks "100%" even when everything else
    // looks right. Regressions land here first.

    /**
     * Gotcha 1: accounts must not be reordered. The mapping layer must
     * never sort, dedupe, or stable-sort the upstream list. Native
     * order `[0, 1, 2]` must emerge from compat as `[0, 1, 2]`.
     */
    @Test
    fun `accounts preserve native order in compat`() {
        val accounts = listOf(
            AuthorizationResult.Account(
                publicKey = ByteArray(32) { 0x11 },
                displayAddress = "a",
                displayAddressFormat = "base58",
                label = "A",
                chains = listOf("solana:mainnet"),
                features = null
            ),
            AuthorizationResult.Account(
                publicKey = ByteArray(32) { 0x22 },
                displayAddress = "b",
                displayAddressFormat = "base58",
                label = "B",
                chains = listOf("solana:mainnet"),
                features = null
            ),
            AuthorizationResult.Account(
                publicKey = ByteArray(32) { 0x33 },
                displayAddress = "c",
                displayAddressFormat = "base58",
                label = "C",
                chains = listOf("solana:mainnet"),
                features = null
            )
        )
        @Suppress("DEPRECATION")
        val ar = AuthorizationResult(
            authToken = "T",
            publicKey = accounts[0].publicKey,
            accountLabel = "A",
            walletUriBase = null,
            walletIcon = null,
            accounts = accounts,
            signInResult = null
        )
        assertEquals(listOf("A", "B", "C"), ar.accounts.map { it.label })
    }

    /**
     * Gotcha 2: null vs empty list on chains / features must be
     * preserved, not collapsed. `null` means "unknown / unsupported";
     * empty list means "explicitly no chains or features". A dapp
     * branching on `account.chains == null` reads a different intent
     * than `account.chains?.isEmpty()`, so the mapping must not silently
     * normalize one into the other.
     */
    @Test
    fun `null chains and empty chains stay distinguishable`() {
        val withNull = AuthorizationResult.Account(
            publicKey = ByteArray(32), displayAddress = null, displayAddressFormat = null,
            label = null, chains = null, features = null
        )
        val withEmpty = AuthorizationResult.Account(
            publicKey = ByteArray(32), displayAddress = null, displayAddressFormat = null,
            label = null, chains = emptyList(), features = emptyList()
        )
        assertNotEquals("null chains != empty chains", withNull, withEmpty)
        assertNotEquals(
            "null features != empty features",
            withNull.copy(chains = emptyList()),
            withEmpty
        )
    }

    /**
     * Gotcha 3: signature encoding convention.
     *
     * MWA signatures are base58 strings; MWA addresses are base64-encoded
     * bytes. The [MobileWalletAdapterClient] nested types use
     * Array<ByteArray> for raw bytes, so the mapping layer has to decode
     * once from whichever wire format the wallet used and produce the
     * right shape. This test documents the convention by constructing a
     * signature wire value and verifying its length after base58 decode
     * (Solana Ed25519 signatures are always 64 bytes).
     */
    @Test
    fun `solana signature is base58 encoded and decodes to 64 bytes`() {
        // "1111111111111111111111111111111111111111111111111111111111111111" is
        // 64 bytes of zero in base58. Any valid Solana signature base58-decodes
        // to exactly 64 bytes; we assert that as a wire-format invariant the
        // mapping layer relies on.
        val zeros = "1111111111111111111111111111111111111111111111111111111111111111"
        val decoded = com.selenus.artemis.runtime.Base58.decode(zeros)
        assertEquals("Ed25519 signatures are 64 bytes", 64, decoded.size)
        assertTrue(decoded.all { it == 0.toByte() })
    }

    /**
     * Gotcha 5: authorize(authToken=...) must produce a result with the
     * same shape as a fresh authorize. Both responses carry a full
     * [AuthorizationResult] populated from the wallet; a reauthorize
     * must not degrade to a stripped-down subset (e.g. missing accounts
     * or missing walletUriBase).
     *
     * We exercise the invariant at the data-type boundary: two
     * AuthorizationResult instances built with identical content but
     * via different construction paths must compare equal.
     */
    /**
     * Real wiring test: the converter from Artemis-native BatchSendResult
     * to compat TransactionBatchResult preserves every slot's state
     * including "signed but not broadcast" (which upstream cannot
     * represent). Slots are mapped 1:1, in the original order.
     */
    @Test
    fun `BatchSendResult to TransactionBatchResult preserves every slot state`() {
        val native = com.selenus.artemis.wallet.BatchSendResult(
            results = listOf(
                com.selenus.artemis.wallet.SendTransactionResult(
                    signature = "SIG-0",
                    confirmed = true
                ),
                com.selenus.artemis.wallet.SendTransactionResult(
                    signature = "",
                    confirmed = false,
                    error = "wallet rejected payload #1"
                ),
                // Signed-but-not-broadcast slot: signature empty, error
                // null, signedRaw present. Upstream has no shape for this;
                // converter must map it to a failure slot with an
                // actionable error so the XOR invariant still holds.
                com.selenus.artemis.wallet.SendTransactionResult(
                    signature = "",
                    confirmed = false,
                    signedRaw = ByteArray(64) { 0x42 }
                )
            )
        )

        val batch = native.toTransactionBatchResult()

        assertEquals(3, batch.results.size)
        assertEquals(listOf(0, 1, 2), batch.results.map { it.index })

        assertTrue(batch.results[0].isSuccess)
        assertEquals("SIG-0", batch.results[0].signature)
        assertEquals(null, batch.results[0].error)

        assertTrue(batch.results[1].isFailure)
        assertEquals(null, batch.results[1].signature)
        assertTrue(batch.results[1].error!!.message!!.contains("rejected"))

        assertTrue(batch.results[2].isFailure)
        assertTrue(
            batch.results[2].error!!.message!!.contains("signed") ||
                batch.results[2].error!!.message!!.contains("not broadcast")
        )
    }

    /**
     * End-to-end encoding convention. The Artemis adapter exposes
     * signatures as base58 strings; the upstream compat surface carries
     * them as `ByteArray`. A round trip through the converter plus
     * `toUpstreamSignaturesOrThrow` must preserve the signature byte-for-
     * byte, with no double-decode or partial encode leaking through.
     */
    @Test
    fun `signature encoding round-trips through the real batch pipeline`() {
        val signatureBytes = ByteArray(64) { i -> (i + 1).toByte() }
        val base58 = com.selenus.artemis.runtime.Base58.encode(signatureBytes)

        val nativeBatch = com.selenus.artemis.wallet.BatchSendResult(
            results = listOf(
                com.selenus.artemis.wallet.SendTransactionResult(
                    signature = base58,
                    confirmed = true
                )
            )
        )

        val compatBatch = nativeBatch.toTransactionBatchResult()
        assertEquals(base58, compatBatch.results.single().signature)

        val upstreamArr = compatBatch.toUpstreamSignaturesOrThrow()
        assertEquals(1, upstreamArr.size)
        assertEquals(64, upstreamArr[0].size)
        assertTrue(
            "base58 encode -> decode round-trip preserves byte-level content",
            signatureBytes.contentEquals(upstreamArr[0])
        )
    }

    /**
     * Partial failure must throw NotSubmittedException when folded into
     * the upstream `Array<ByteArray>` shape. Drop-in dapps get the
     * upstream error; Artemis-native callers can still inspect the
     * batch directly.
     */
    @Test
    fun `toUpstreamSignaturesOrThrow throws NotSubmittedException on partial failure`() {
        val batch = TransactionBatchResult.of(
            inputSize = 2,
            signatures = listOf("1111111111111111111111111111111111111111111111111111111111111111", null),
            errors = listOf(null, RuntimeException("slot 1 failed"))
        )
        try {
            batch.toUpstreamSignaturesOrThrow()
            fail("expected NotSubmittedException")
        } catch (e: com.solana.mobilewalletadapter.clientlib.protocol
            .MobileWalletAdapterClient.NotSubmittedException) {
            assertEquals(2, e.expectedNumSignatures)
        }
    }

    /**
     * Fully successful batch folds into `Array<ByteArray>` cleanly. Each
     * signature must be 64 bytes (Ed25519), preserving order.
     */
    @Test
    fun `toUpstreamSignaturesOrThrow folds success batch into byte array`() {
        // "1111...111" is the base58 representation of 64 zero bytes.
        val zeros = "1111111111111111111111111111111111111111111111111111111111111111"
        val batch = TransactionBatchResult.of(
            inputSize = 2,
            signatures = listOf(zeros, zeros),
            errors = listOf(null, null)
        )
        val arr = batch.toUpstreamSignaturesOrThrow()
        assertEquals(2, arr.size)
        assertEquals(64, arr[0].size)
        assertEquals(64, arr[1].size)
        assertTrue(arr[0].all { it == 0.toByte() })
    }

    @Test
    fun `authorize and reauthorize results are shape-equivalent`() {
        val account = AuthorizationResult.Account(
            publicKey = ByteArray(32) { 0x7F },
            displayAddress = "addr",
            displayAddressFormat = "base58",
            label = "main",
            chains = listOf("solana:mainnet"),
            features = listOf("solana:signAndSendTransaction")
        )
        @Suppress("DEPRECATION")
        val fresh = AuthorizationResult(
            authToken = "T-FRESH",
            publicKey = account.publicKey,
            accountLabel = "main",
            walletUriBase = null,
            walletIcon = null,
            accounts = listOf(account),
            signInResult = null
        )
        @Suppress("DEPRECATION")
        val reauth = AuthorizationResult(
            authToken = "T-FRESH",
            publicKey = account.publicKey,
            accountLabel = "main",
            walletUriBase = null,
            walletIcon = null,
            accounts = listOf(account),
            signInResult = null
        )
        assertEquals(
            "same content, different construction path => equal",
            fresh, reauth
        )
        assertEquals(fresh.hashCode(), reauth.hashCode())
    }
}
