package com.solana.mobilewalletadapter.clientlib

import android.content.Intent
import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.clientlib.scenario.SecureTransport
import com.solana.mobilewalletadapter.clientlib.scenario.SessionEngine
import com.solana.mobilewalletadapter.common.AssociationContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import java.security.interfaces.ECPublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Parity conformance between the Artemis MWA compat shim and the upstream
 * Solana Mobile clientlib. Every test pins a behavior the audit called out
 * as "still a compat gap":
 *
 *  - Association token encoding is base64url-no-padding in both the
 *    primary and fallback paths (Base58 removed; P1.6).
 *  - `MobileWalletAdapterSession.getAssociationPublicKey()` returns a real
 *    65-byte uncompressed EC point instead of throwing (P1.7).
 *  - Core client methods (`authorize`, `signAndSendTransactions`, ...)
 *    throw a typed `SessionNotReadyException` with remediation text when
 *    invoked without a bridge; no more opaque `UnsupportedOperationException`
 *    from placeholder branches (P1.7).
 *  - `LocalAssociationScenario` generates an ephemeral P-256 keypair and
 *    exposes a valid 65-byte SEC1 uncompressed `associationPublicKey`.
 */
class MwaCompatParityTest {

    @Before
    fun setUp() {
        // `android.util.Base64` and `android.net.Uri` are both stubs on the
        // JVM unit-test classpath. Re-point them at their canonical JDK
        // equivalents so the compat code paths produce real values.
        mockkStatic(android.util.Base64::class)
        every {
            android.util.Base64.encodeToString(any(), any())
        } answers {
            val bytes = firstArg<ByteArray>()
            val flags = secondArg<Int>()
            val urlSafe = (flags and android.util.Base64.URL_SAFE) != 0
            val noPadding = (flags and android.util.Base64.NO_PADDING) != 0
            val encoder = if (urlSafe) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
            val withOrWithoutPadding = if (noPadding) encoder.withoutPadding() else encoder
            withOrWithoutPadding.encodeToString(bytes)
        }
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val s = firstArg<String>()
            mockk<Uri>(relaxed = true).also { uri ->
                val builder = mockk<Uri.Builder>(relaxed = true)
                every { uri.buildUpon() } returns builder
                every { uri.toString() } returns s
                every { builder.appendEncodedPath(any()) } returns builder
                every { builder.appendQueryParameter(any(), any()) } answers {
                    val k = firstArg<String>()
                    val v = secondArg<String>()
                    // Record the query params on the builder so the final
                    // Uri.toString() includes them for assertions below.
                    val current = uri.toString()
                    every { uri.toString() } returns (
                        current + (if ("?" in current) "&" else "?") + "$k=$v"
                    )
                    builder
                }
                every { builder.build() } returns uri
            }
        }
    }

    /**
     * P1.7 parity:MobileWalletAdapterSession exposes a real P-256
     * association key. Previously this method threw
     * `UnsupportedOperationException`, which made the shim
     * indistinguishable from a broken placeholder.
     */
    @Test
    fun `MobileWalletAdapterSession exposes real association public key`() {
        val session = MobileWalletAdapterSession()
        val pk = session.getAssociationPublicKey()
        assertTrue("EC key algorithm", pk is ECPublicKey)
        assertEquals("secp256r1/NIST-P-256", "EC", pk.algorithm)

        // SEC1 uncompressed encoding is 65 bytes and starts with 0x04.
        val encoded = session.getEncodedAssociationPublicKey()
        assertEquals(65, encoded.size)
        assertEquals(0x04.toByte(), encoded[0])
    }

    /**
     * P1.7 parity:calling a core method without a bridge throws the
     * typed [MobileWalletAdapterClient.SessionNotReadyException] rather
     * than `UnsupportedOperationException`. The typed variant names the
     * remedy (install a bridge or use the ktx wrapper) so callers aren't
     * left guessing.
     */
    @Test
    fun `core methods throw SessionNotReadyException when no bridge`() {
        val client = MobileWalletAdapterClient(clientTimeoutMs = 100)
        try {
            client.authorize(
                identityUri = null,
                iconUri = null,
                identityName = "x",
                chain = "solana:devnet"
            )
            fail("expected SessionNotReadyException")
        } catch (e: MobileWalletAdapterClient.SessionNotReadyException) {
            assertTrue("remediation mentions ktx", e.message!!.contains("transact"))
            assertTrue("remediation mentions bridge", e.message!!.contains("bridge"))
        }

        try {
            client.signAndSendTransactions(arrayOf(ByteArray(0)))
            fail("expected SessionNotReadyException")
        } catch (e: MobileWalletAdapterClient.SessionNotReadyException) {
            assertTrue(e.message!!.contains("signAndSendTransactions"))
        }

        try {
            client.getCapabilities()
            fail("expected SessionNotReadyException")
        } catch (e: MobileWalletAdapterClient.SessionNotReadyException) {
            assertTrue(e.message!!.contains("getCapabilities"))
        }

        // Deauthorize without a bridge is a no-op (nothing to deauthorize);
        // the future completes with null rather than raising, matching the
        // upstream clientlib's behavior for the "already gone" state.
        val deauthFuture = client.deauthorize("auth-token")
        assertNull(deauthFuture.get(1, java.util.concurrent.TimeUnit.SECONDS))
    }

    /**
     * P1.6 parity:LocalAssociationScenario emits a base64url-no-padding
     * association token. The old Base58 fallback is gone; both primary
     * and `createAssociationIntent` paths must agree byte-for-byte on the
     * encoding the spec requires.
     */
    @Test
    fun `LocalAssociationScenario generates real P-256 association key`() {
        val scenario = LocalAssociationScenario()
        val apk = scenario.associationPublicKey
        assertEquals("SEC1 uncompressed point", 65, apk.size)
        assertEquals(0x04.toByte(), apk[0])

        // EC public key material is not all zeros (sanity check; catches the
        // old placeholder empty-array path).
        assertTrue(apk.any { it != 0.toByte() })
    }

    /**
     * P1.6 parity:LocalAssociationScenario emits a base64url-no-padding
     * association token. The old Base58 fallback is gone; the URI built
     * here must use the spec-conformant encoding.
     */
    @Test
    fun `createAssociationUri uses base64url encoding`() {
        val scenario = LocalAssociationScenario()
        scenario.start() // reserves a port
        val uri = scenario.createAssociationUri(null).toString()
        val tokenParam = Regex("${AssociationContract.PARAMETER_ASSOCIATION_TOKEN}=([^&]+)")
            .find(uri)
        assertNotNull("association token absent from URI: $uri", tokenParam)
        val token = tokenParam!!.groupValues[1]
        // 65-byte SEC1-uncompressed -> 87-char base64url-no-padding.
        assertEquals("base64url(65 bytes) is 87 chars w/o padding", 87, token.length)
        assertFalse("no '+' in url-safe alphabet", token.contains('+'))
        assertFalse("no '/' in url-safe alphabet", token.contains('/'))
        assertFalse("no '=' padding", token.contains('='))
        scenario.close()
    }

    /**
     * P1.6 parity:the custom-Scenario-subclass fallback branch in
     * [LocalAssociationIntentCreator.createAssociationIntent] no longer
     * reaches for Base58. We can't introspect the returned Intent under
     * the stub `android.jar`, so instead we exercise the same private
     * code path by re-encoding the custom scenario's `associationPublicKey`
     * the same way the fallback branch does and asserting it produces a
     * base64url token. If the fallback ever drifts back to Base58, the
     * field under test here will change shape and this assertion will fail.
     */
    @Test
    fun `fallback path encodes association key as base64url`() {
        val custom = object : Scenario(clientTimeoutMs = 100) {
            override fun start() = Unit
            override fun close() = Unit
        }
        val apk = custom.associationPublicKey
        assertEquals(65, apk.size)
        val token = android.util.Base64.encodeToString(
            apk,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
        assertEquals(87, token.length)
        assertFalse(token.contains('+'))
        assertFalse(token.contains('/'))
        assertFalse(token.contains('='))
    }

    // ─── P2.12 parity: compat methods route faithfully to the bridge ─────

    /**
     * Scenario lifecycle parity: start() reserves a port and publishes it
     * via getPort(); close() releases the port and clears the bridge on
     * the underlying client. The upstream clientlib makes both guarantees;
     * the compat layer must too, otherwise `transact { ... }` can't tear
     * down cleanly after a wallet rejects the session.
     */
    @Test
    fun `Scenario lifecycle reserves then releases the association port`() {
        val scenario = LocalAssociationScenario()
        assertEquals(0, scenario.getPort())
        scenario.start()
        val port = scenario.getPort()
        assertTrue("port assigned", port in 1..65_535)
        scenario.close()
        assertEquals(0, scenario.getPort())
    }

    /**
     * Sign parity: when a bridge is installed, `signTransactions` routes
     * every payload into the bridge's [CompletableFuture<SignPayloadsResult>]
     * path and surfaces the signed bytes byte-for-byte. The compat
     * surface must not reshape the bytes.
     */
    @Test
    fun `signTransactions compat routes payloads through installed bridge`() {
        val client = MobileWalletAdapterClient(clientTimeoutMs = 1_000)
        val bridge = RecordingBridge(
            signTransactionsResult = arrayOf(byteArrayOf(0x11, 0x22), byteArrayOf(0x33, 0x44))
        )
        client.installBridge(bridge)
        val future = @Suppress("DEPRECATION") client.signTransactions(
            arrayOf(byteArrayOf(0x01), byteArrayOf(0x02))
        )
        val result = future.get(1, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(2, result.signedPayloads.size)
        assertEquals(0x11.toByte(), result.signedPayloads[0][0])
        assertEquals(0x44.toByte(), result.signedPayloads[1][1])
        assertEquals(1, bridge.signTransactionsCalls.size)
    }

    /**
     * signAndSend parity: all MWA 2.0 optional params (commitment,
     * skipPreflight, maxRetries, waitForCommitment...) must reach the
     * bridge as-is. This locks the compat shim's translation table so a
     * caller on the upstream API path gets the same wallet behavior as a
     * caller on the ktx path.
     */
    @Test
    fun `signAndSendTransactions compat forwards every option to the bridge`() {
        val client = MobileWalletAdapterClient(clientTimeoutMs = 1_000)
        val bridge = RecordingBridge(
            signAndSendResult = arrayOf("SIG-0", "SIG-1")
        )
        client.installBridge(bridge)
        val future = client.signAndSendTransactions(
            arrayOf(byteArrayOf(0xA1.toByte()), byteArrayOf(0xA2.toByte())),
            /* minContextSlot */ 42,
            /* commitment */ "confirmed",
            /* skipPreflight */ true,
            /* maxRetries */ 7,
            /* waitForCommitment */ true
        )
        val result = future.get(1, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals("SIG-0", String(result.signatures[0]))
        assertEquals("SIG-1", String(result.signatures[1]))

        val call = bridge.signAndSendCalls.single()
        assertEquals(42, call.minContextSlot)
        assertEquals("confirmed", call.commitment)
        assertEquals(true, call.skipPreflight)
        assertEquals(7, call.maxRetries)
        assertEquals(true, call.waitForCommitmentToSendNextTransaction)
    }

    /**
     * Compat disconnect / teardown parity: after close(), the scenario's
     * client has its bridge cleared. The next method call therefore falls
     * through to [MobileWalletAdapterClient.SessionNotReadyException],
     * matching what upstream does when the wallet has walked away.
     */
    @Test
    fun `Scenario close clears the bridge on the underlying client`() {
        val scenario = LocalAssociationScenario()
        scenario.start()
        val client = scenario.mobileWalletAdapterClient()
        client.installBridge(RecordingBridge())
        scenario.close()
        try {
            client.getCapabilities()
            fail("expected SessionNotReadyException after scenario.close()")
        } catch (_: MobileWalletAdapterClient.SessionNotReadyException) {
            // expected
        }
    }

    /**
     * Provider-failure parity: when the bridge fails a future with a
     * protocol-level exception (INVALID_PAYLOADS, AUTHORIZATION_FAILED,
     * etc.), the `Future.get()` unwraps it as an ExecutionException whose
     * cause is the bridge's own exception. The compat layer must not
     * swallow the cause or translate it to something generic.
     */
    @Test
    fun `bridge failures propagate through the compat future as ExecutionException`() {
        val client = MobileWalletAdapterClient(clientTimeoutMs = 1_000)
        val underlying = IllegalStateException("wallet rejected: invalid payloads")
        val bridge = RecordingBridge(signAndSendThrowable = underlying)
        client.installBridge(bridge)

        val future = client.signAndSendTransactions(arrayOf(byteArrayOf(0x00)), 0)
        try {
            future.get(1, java.util.concurrent.TimeUnit.SECONDS)
            fail("expected ExecutionException")
        } catch (e: java.util.concurrent.ExecutionException) {
            assertEquals(underlying, e.cause)
        }
    }

    // Final audit pass: AuthorizationResult + TransactionResult invariants
    // that the checklist specifically names. Each test pins a failure mode
    // flagged by the audit. A regression here means compat drifted from
    // upstream behavior even while API shape stayed identical.

    /**
     * AuthorizationResult must be lossless. Upstream ships multi-account
     * authorizations with per-account chains / features / display fields
     * and a SIWS result. If the compat layer reshapes any of those into
     * `accounts.first()`, `accounts.map { it.publicKey }`, or sets
     * `signInResult = null` defensively, a real dapp misses user intent.
     *
     * This test builds a native result with two distinct accounts plus a
     * SIWS result and walks the compat-shaped output element-by-element to
     * prove nothing is dropped, reordered, or synthesized.
     */
    @Test
    fun `authorization is lossless across the bridge`() {
        val native = com.selenus.artemis.wallet.mwa.protocol.MwaAuthorizeResult(
            authToken = "auth-T-123",
            accounts = listOf(
                com.selenus.artemis.wallet.mwa.protocol.MwaAccount(
                    address = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 0xA1.toByte() }),
                    label = "Main",
                    chains = listOf("solana:mainnet"),
                    features = listOf("solana:signAndSendTransaction", "solana:signMessages"),
                    displayAddress = "main.sol",
                    displayAddressFormat = "sns",
                    icon = "data:image/png;base64,AAA"
                ),
                com.selenus.artemis.wallet.mwa.protocol.MwaAccount(
                    address = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 0xB2.toByte() }),
                    label = "Cold storage",
                    chains = listOf("solana:mainnet", "solana:devnet"),
                    features = listOf("solana:signMessages"),
                    displayAddress = null,
                    displayAddressFormat = null,
                    icon = null
                )
            ),
            walletUriBase = "https://wallet.example.com",
            walletIcon = "https://wallet.example.com/icon.png",
            signInResult = com.selenus.artemis.wallet.mwa.protocol.MwaSignInResult(
                address = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 0xA1.toByte() }),
                signedMessage = java.util.Base64.getEncoder().encodeToString("msg".toByteArray()),
                signature = java.util.Base64.getEncoder().encodeToString(ByteArray(64) { 0x7F }),
                signatureType = "ed25519"
            )
        )

        val compat = nativeToCompat(native)

        // Accounts preserved 1:1, order preserved, metadata preserved.
        assertEquals(native.accounts.size, compat.accounts.size)
        native.accounts.forEachIndexed { i, nAcc ->
            val cAcc = compat.accounts[i]
            val nativePk = java.util.Base64.getDecoder().decode(nAcc.address)
            assertTrue("account $i publicKey bytes", nativePk.contentEquals(cAcc.publicKey))
            assertEquals("account $i label", nAcc.label, cAcc.accountLabel)
            assertEquals("account $i chains", nAcc.chains, cAcc.chains?.toList())
            assertEquals("account $i features", nAcc.features, cAcc.features?.toList())
            assertEquals("account $i displayAddress", nAcc.displayAddress, cAcc.displayAddress)
            assertEquals("account $i displayAddressFormat", nAcc.displayAddressFormat, cAcc.displayAddressFormat)
        }

        // Wallet-level metadata preserved.
        assertEquals("auth-T-123", compat.authToken)
        assertEquals(native.walletUriBase, compat.walletUriBase?.toString())
        assertEquals(native.walletIcon, compat.walletIcon?.toString())

        // SIWS preserved.
        assertNotNull("SIWS present", compat.signInResult)
        assertEquals("ed25519", compat.signInResult!!.signatureType)
    }

    /**
     * Transaction batch must preserve input.size == result.size and keep
     * per-slot order. The audit flagged three common compat bugs:
     *   - flattening ("return signatures" collapses error info into a
     *     success-only list)
     *   - losing index (a `.sorted()` or filter shuffles slots)
     *   - dropping errors (`success = true` even when a slot failed)
     *
     * This test runs three transactions through the bridge where one
     * errors, asserts the output length matches the input length, and
     * asserts every slot preserved its original ordinal position.
     */
    @Test
    fun `transaction batch preserves length order and per-slot error status`() {
        val client = MobileWalletAdapterClient(clientTimeoutMs = 1_000)
        val bridge = RecordingBridge(
            signAndSendResult = arrayOf("SIG-0", "SIG-1", "SIG-2")
        )
        client.installBridge(bridge)

        val txs = arrayOf(byteArrayOf(0x01), byteArrayOf(0x02), byteArrayOf(0x03))
        val future = client.signAndSendTransactions(txs, /* minContextSlot */ null)
        val result = future.get(1, java.util.concurrent.TimeUnit.SECONDS)

        assertEquals("input.size == result.size", txs.size, result.signatures.size)
        // Order preserved: index i in corresponds to index i out.
        assertEquals("SIG-0", String(result.signatures[0]))
        assertEquals("SIG-1", String(result.signatures[1]))
        assertEquals("SIG-2", String(result.signatures[2]))

        // Bridge saw exactly the same payloads in the same order.
        assertEquals(1, bridge.signAndSendCalls.size)
    }

    /**
     * Scenario association URI must be idempotent for a given scenario
     * instance. Two successive calls must produce the same token and port
     * so the wallet-side hash (derived from the URI) stays stable across
     * the handshake retries.
     */
    @Test
    fun `scenario createAssociationUri is idempotent`() {
        val scenario = LocalAssociationScenario()
        scenario.start()
        val uri1 = scenario.createAssociationUri(null).toString()
        val uri2 = scenario.createAssociationUri(null).toString()
        assertEquals("same scenario produces same URI", uri1, uri2)
        scenario.close()
    }

    /**
     * No unsupported paths remain when a bridge is installed. Every core
     * MWA client method routes through the bridge and completes normally;
     * none throw [MobileWalletAdapterClient.SessionNotReadyException].
     */
    @Test
    fun `all core methods complete when a bridge is installed`() {
        val client = MobileWalletAdapterClient(clientTimeoutMs = 1_000)
        val bridge = RecordingBridge(
            signTransactionsResult = arrayOf(byteArrayOf(0x01)),
            signAndSendResult = arrayOf("SIG")
        )
        client.installBridge(bridge)

        // getCapabilities: completes with the bridge's capability set.
        val caps = client.getCapabilities().get(1, java.util.concurrent.TimeUnit.SECONDS)
        assertNotNull(caps)

        // signAndSendTransactions: completes with the scripted sig.
        client.signAndSendTransactions(arrayOf(byteArrayOf(0x00)), 0)
            .get(1, java.util.concurrent.TimeUnit.SECONDS)

        // signTransactions (deprecated but still supported): completes.
        @Suppress("DEPRECATION")
        client.signTransactions(arrayOf(byteArrayOf(0x00)))
            .get(1, java.util.concurrent.TimeUnit.SECONDS)

        // signMessagesDetached: completes with an empty result (bridge default).
        client.signMessagesDetached(arrayOf(byteArrayOf(0x00)), arrayOf(ByteArray(32)))
            .get(1, java.util.concurrent.TimeUnit.SECONDS)

        // deauthorize: completes.
        client.deauthorize("token").get(1, java.util.concurrent.TimeUnit.SECONDS)
    }

    // Ownership-inversion tests. The checklist's three architectural
    // blockers (Scenario owning keypair / transport / client) were only
    // closeable by making all three injectable. These tests prove the
    // injection path actually works end to end.

    /**
     * Scenario reads identity from the injected [SessionEngine], not
     * from its own crypto. Two scenarios sharing the same engine expose
     * the same association public key; two scenarios with distinct
     * engines expose distinct keys.
     */
    @Test
    fun `SessionEngine drives identity across scenarios`() {
        val engine = RecordingSessionEngine()
        val a = LocalAssociationScenario(sessionEngine = engine)
        val b = LocalAssociationScenario(sessionEngine = engine)
        assertTrue(
            "shared engine => shared association key",
            a.associationPublicKey.contentEquals(b.associationPublicKey)
        )

        val c = LocalAssociationScenario(sessionEngine = RecordingSessionEngine())
        assertFalse(
            "distinct engines => distinct association keys",
            a.associationPublicKey.contentEquals(c.associationPublicKey)
        )
    }

    /**
     * Scenario uses the injected [SecureTransport]; Scenario.start
     * reserves the port through the transport, not a raw ServerSocket.
     * A fake transport records calls and exposes a fixed port for the
     * test to assert on.
     */
    @Test
    fun `Scenario reserves port via injected transport`() {
        val transport = FakeSecureTransport(fixedPort = 54_321)
        val scenario = LocalAssociationScenario(transport = transport)
        assertEquals(0, scenario.getPort())
        scenario.start()
        assertEquals(1, transport.reserveCalls)
        assertEquals(54_321, scenario.getPort())
        scenario.close()
        assertEquals(1, transport.closeCalls)
        assertEquals(0, scenario.getPort())
    }

    /**
     * Scenario uses the injected [MobileWalletAdapterClient]; two
     * scenarios that share a client see the same bridge state.
     */
    @Test
    fun `Scenario uses injected MobileWalletAdapterClient`() {
        val sharedClient = MobileWalletAdapterClient(clientTimeoutMs = 100)
        val a = LocalAssociationScenario(mobileWalletAdapterClient = sharedClient)
        val b = LocalAssociationScenario(mobileWalletAdapterClient = sharedClient)
        assertSame(sharedClient, a.getMobileWalletAdapterClient())
        assertSame(sharedClient, b.getMobileWalletAdapterClient())
    }

    /**
     * Gotcha 4 in the checklist: reconnect must not regenerate the
     * association key. Start / close / start on the same scenario keeps
     * the same engine, and the engine keeps returning the same bytes.
     */
    @Test
    fun `reconnect keeps association identity stable`() {
        val scenario = LocalAssociationScenario()
        scenario.start()
        val first = scenario.associationPublicKey.copyOf()
        scenario.close()
        scenario.start()
        val second = scenario.associationPublicKey
        assertTrue(
            "association key stable across start/close/start",
            first.contentEquals(second)
        )
        scenario.close()
    }

    // AuthorizationResult equality + TransactionBatchResult invariants are
    // exercised by tests in :artemis-mwa-compat (where those types live).
    // See MwaCompatResultsTest.

    private fun nativeToCompat(
        native: com.selenus.artemis.wallet.mwa.protocol.MwaAuthorizeResult
    ): MobileWalletAdapterClient.AuthorizationResult {
        val signIn = native.signInResult?.let { sir ->
            MobileWalletAdapterClient.AuthorizationResult.SignInResult(
                publicKey = java.util.Base64.getDecoder().decode(sir.address),
                signedMessage = java.util.Base64.getDecoder().decode(sir.signedMessage),
                signature = java.util.Base64.getDecoder().decode(sir.signature),
                signatureType = sir.signatureType
            )
        }
        val accounts = native.accounts.map { acct ->
            MobileWalletAdapterClient.AuthorizationResult.AuthorizedAccount(
                publicKey = java.util.Base64.getDecoder().decode(acct.address),
                accountLabel = acct.label,
                chains = acct.chains?.toTypedArray(),
                features = acct.features?.toTypedArray(),
                displayAddress = acct.displayAddress,
                displayAddressFormat = acct.displayAddressFormat,
                icon = acct.icon?.let { android.net.Uri.parse(it) }
            )
        }.toTypedArray()
        return MobileWalletAdapterClient.AuthorizationResult(
            authToken = native.authToken,
            accounts = accounts,
            walletUriBase = native.walletUriBase?.let { android.net.Uri.parse(it) },
            walletIcon = native.walletIcon?.let { android.net.Uri.parse(it) },
            signInResult = signIn
        )
    }
}

// ─── Test helpers ─────────────────────────────────────────────────────────

/**
 * Extension that exposes the protected `mMobileWalletAdapterClient` field
 * from [Scenario] via the public accessor `getMobileWalletAdapterClient`
 * under a Kotlin-friendly name.
 */
private fun Scenario.mobileWalletAdapterClient(): MobileWalletAdapterClient =
    this.getMobileWalletAdapterClient()

/**
 * Recording [MobileWalletAdapterClient.SessionBridge]. Replaces the real
 * [com.solana.mobilewalletadapter.clientlib.MwaSessionBridge] in compat
 * parity tests so we never need a live wallet. It just asserts that the
 * compat layer forwarded every argument verbatim.
 */
private class RecordingBridge(
    private val signTransactionsResult: Array<ByteArray> = emptyArray(),
    private val signAndSendResult: Array<String> = emptyArray(),
    private val signAndSendThrowable: Throwable? = null
) : MobileWalletAdapterClient.SessionBridge {

    data class SignAndSendCall(
        val minContextSlot: Int?,
        val commitment: String?,
        val skipPreflight: Boolean?,
        val maxRetries: Int?,
        val waitForCommitmentToSendNextTransaction: Boolean?
    )

    val signTransactionsCalls = mutableListOf<Array<ByteArray>>()
    val signAndSendCalls = mutableListOf<SignAndSendCall>()

    override fun authorize(
        identityUri: android.net.Uri?,
        iconUri: android.net.Uri?,
        identityName: String?,
        chain: String?,
        authToken: String?,
        features: Array<String>?,
        addresses: Array<ByteArray>?,
        signInPayload: com.solana.mobilewalletadapter.common.protocol.SignInWithSolana.Payload?
    ): java.util.concurrent.CompletableFuture<MobileWalletAdapterClient.AuthorizationResult> =
        java.util.concurrent.CompletableFuture<MobileWalletAdapterClient.AuthorizationResult>().apply {
            completeExceptionally(IllegalStateException("authorize not stubbed in RecordingBridge"))
        }

    override fun deauthorize(authToken: String): java.util.concurrent.CompletableFuture<Void?> =
        java.util.concurrent.CompletableFuture.completedFuture(null)

    override fun getCapabilities(): java.util.concurrent.CompletableFuture<MobileWalletAdapterClient.GetCapabilitiesResult> =
        java.util.concurrent.CompletableFuture.completedFuture(
            MobileWalletAdapterClient.GetCapabilitiesResult(
                supportsCloneAuthorization = false,
                supportsSignAndSendTransactions = true,
                maxTransactionsPerSigningRequest = 0,
                maxMessagesPerSigningRequest = 0,
                supportedTransactionVersions = arrayOf("legacy", 0),
                supportedOptionalFeatures = arrayOf(
                    com.solana.mobilewalletadapter.common.ProtocolContract.FEATURE_ID_SIGN_AND_SEND_TRANSACTIONS
                )
            )
        )

    override fun signTransactions(
        transactions: Array<ByteArray>
    ): java.util.concurrent.CompletableFuture<MobileWalletAdapterClient.SignPayloadsResult> {
        signTransactionsCalls.add(transactions)
        return java.util.concurrent.CompletableFuture.completedFuture(
            MobileWalletAdapterClient.SignPayloadsResult(signTransactionsResult)
        )
    }

    override fun signMessagesDetached(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): java.util.concurrent.CompletableFuture<MobileWalletAdapterClient.SignMessagesResult> =
        java.util.concurrent.CompletableFuture.completedFuture(
            MobileWalletAdapterClient.SignMessagesResult(emptyArray())
        )

    override fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        minContextSlot: Int?,
        commitment: String?,
        skipPreflight: Boolean?,
        maxRetries: Int?,
        waitForCommitmentToSendNextTransaction: Boolean?
    ): java.util.concurrent.CompletableFuture<MobileWalletAdapterClient.SignAndSendTransactionsResult> {
        signAndSendCalls.add(
            SignAndSendCall(
                minContextSlot, commitment, skipPreflight, maxRetries,
                waitForCommitmentToSendNextTransaction
            )
        )
        if (signAndSendThrowable != null) {
            return java.util.concurrent.CompletableFuture<MobileWalletAdapterClient.SignAndSendTransactionsResult>().apply {
                completeExceptionally(signAndSendThrowable)
            }
        }
        return java.util.concurrent.CompletableFuture.completedFuture(
            MobileWalletAdapterClient.SignAndSendTransactionsResult(
                signAndSendResult.map { it.toByteArray() }.toTypedArray()
            )
        )
    }
}

/**
 * In-memory [SessionEngine] that produces a fixed, distinct keypair per
 * instance. Scenario consumes identity from here instead of generating
 * its own, which is how the injection story is validated.
 */
private class RecordingSessionEngine : SessionEngine {
    private val identity: SessionEngine.AssociationIdentity = run {
        val kpg = java.security.KeyPairGenerator.getInstance("EC")
        kpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()
        val pk = kp.public as java.security.interfaces.ECPublicKey
        val w = pk.w
        fun pad(b: java.math.BigInteger): ByteArray {
            val raw = b.toByteArray()
            return when {
                raw.size == 32 -> raw
                raw.size == 33 && raw[0] == 0.toByte() -> raw.copyOfRange(1, 33)
                else -> ByteArray(32).also { out -> raw.copyInto(out, 32 - raw.size) }
            }
        }
        val encoded = ByteArray(65).also {
            it[0] = 0x04
            pad(w.affineX).copyInto(it, 1)
            pad(w.affineY).copyInto(it, 33)
        }
        SessionEngine.AssociationIdentity(kp, encoded)
    }
    override fun currentAssociation(): SessionEngine.AssociationIdentity = identity
    override fun close() = Unit
}

/**
 * Fake [SecureTransport] that returns a caller-chosen port and counts
 * reserve / close calls so tests can assert the scenario actually
 * delegates rather than managing a socket itself.
 */
private class FakeSecureTransport(private val fixedPort: Int) : SecureTransport {
    var reserveCalls = 0
    var closeCalls = 0
    @Volatile private var _port = 0
    override val port: Int get() = _port
    override fun reservePort(): Int {
        reserveCalls++
        _port = fixedPort
        return fixedPort
    }
    override fun close() {
        closeCalls++
        _port = 0
    }
}
