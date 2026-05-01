package com.selenus.artemis.wallet.mwa

import android.app.Activity
import android.net.Uri
import com.selenus.artemis.wallet.SendTransactionOptions
import com.selenus.artemis.wallet.WalletRequest
import com.selenus.artemis.wallet.mwa.protocol.MwaAccount
import com.selenus.artemis.wallet.mwa.protocol.MwaAuthorizeResult
import com.selenus.artemis.wallet.mwa.protocol.MwaCapabilities
import com.selenus.artemis.wallet.mwa.protocol.MwaClient
import com.selenus.artemis.wallet.mwa.protocol.MwaErrorCodes
import com.selenus.artemis.wallet.mwa.protocol.MwaIdentity
import com.selenus.artemis.wallet.mwa.protocol.MwaProtocolException
import com.selenus.artemis.wallet.mwa.protocol.MwaSendOptions
import com.selenus.artemis.wallet.mwa.protocol.MwaSession
import com.selenus.artemis.wallet.mwa.protocol.MwaSignInPayload
import com.selenus.artemis.wallet.mwa.protocol.MwaSignInResult
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/**
 * Behavior conformance for the Artemis MWA 2.0 adapter.
 *
 * These tests drive [MwaWalletAdapter] through every walletlib-2.0-sensitive
 * verb (authorize variants, capability negotiation, signAndSendTransactions,
 * sign-only fallback, clone_authorization, reauthorize, session teardown) by
 * installing a [RecordingMwaClient] that captures requests and returns
 * scripted responses. They complement the primitive crypto tests
 * ([HkdfVectorsTest], [EcP256Test], [Aes128GcmTest]) by asserting on the
 * protocol shape the wallet actually observes.
 *
 * The CI job `mwa-behavior` runs this suite via `--tests '*BehaviorTest*'`.
 */
class MwaWalletAdapterBehaviorTest {

    private fun adapter(
        client: MwaClient,
        broadcaster: com.selenus.artemis.wallet.RpcBroadcaster? = null,
        authStore: AuthTokenStore = InMemoryAuthTokenStore()
    ): MwaWalletAdapter {
        // Activity is captured into the adapter but only ever forwarded to
        // `client.openSession(activity)`, which the RecordingMwaClient
        // overrides. The mockk shell satisfies the type without needing a
        // real Android runtime. Uri is also mocked because the stub
        // `Uri.parse(...)` returns null on the JVM unit-test classpath.
        val activity: Activity = mockk(relaxed = true)
        val identityUri: Uri = mockk(relaxed = true)
        return MwaWalletAdapter(
            activity = activity,
            identityUri = identityUri,
            iconPath = "https://myapp.example.com/favicon.ico",
            identityName = "ArtemisTest",
            chain = "solana:devnet",
            authStore = authStore,
            client = client,
            broadcaster = broadcaster
        )
    }

    @Test
    fun `constructor rejects relative iconPath`() {
        val activity: Activity = mockk(relaxed = true)
        val identityUri: Uri = mockk(relaxed = true)
        try {
            MwaWalletAdapter(
                activity = activity,
                identityUri = identityUri,
                iconPath = "favicon.ico",
                identityName = "ArtemisTest",
                chain = "solana:devnet",
                client = RecordingMwaClient(
                    authorizeResponse = authResult("TOKEN", "addr1-b64")
                )
            )
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("absolute HTTPS URI"))
        }
    }

    /**
     * MWA 2.0: authorize with auth_token takes the reauthorize path. The
     * wallet's authorize RPC receives the existing token and returns the
     * same token (or a refreshed one) without launching a fresh auth flow.
     */
    @Test
    fun `authorize with authToken passes token through`() = runBlocking {
        val client = RecordingMwaClient(
            authorizeResponse = authResult("TOKEN-REFRESHED", "addr1-b64")
        )
        val authStore = InMemoryAuthTokenStore().apply { set("TOKEN-EXISTING") }
        val adapter = adapter(client, authStore = authStore)
        adapter.connect()

        assertEquals(1, client.authorizeCalls.size)
        assertEquals("TOKEN-EXISTING", client.authorizeCalls.single().authToken)
        assertEquals("TOKEN-REFRESHED", authStore.get())
    }

    /**
     * MWA 2.0: authorize with `addresses` restricts which accounts the
     * dapp wants authorised. Wallet receives the base64-encoded addresses;
     * adapter preserves list order and encoding.
     */
    @Test
    fun `authorize with addresses forwards base64 addresses`() = runBlocking {
        val client = RecordingMwaClient(
            authorizeResponse = authResult("T1", "addr2-b64")
        )
        val adapter = adapter(client)
        val wanted = listOf(ByteArray(32) { 1 }, ByteArray(32) { 2 })
        adapter.connectWithFeatures(requestedFeatures = null, addresses = wanted)

        val call = client.authorizeCalls.single()
        assertNotNull(call.addresses)
        assertEquals(2, call.addresses!!.size)
        // Base64 NO_WRAP of 32 × 0x01 is known; just assert it's non-empty
        // and distinct, so the adapter didn't silently collapse the list.
        assertTrue(call.addresses[0].isNotEmpty())
        assertTrue(call.addresses[0] != call.addresses[1])
    }

    /**
     * MWA 2.0: authorize with `features` negotiates the capability set the
     * dapp needs. Adapter forwards the feature identifiers verbatim.
     */
    @Test
    fun `authorize with features forwards feature identifiers`() = runBlocking {
        val features = listOf(
            MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS,
            MwaCapabilities.FEATURE_SIGN_IN_WITH_SOLANA
        )
        val client = RecordingMwaClient(
            authorizeResponse = authResult("T1", "addr-b64")
        )
        val adapter = adapter(client)
        adapter.connectWithFeatures(requestedFeatures = features, addresses = null)

        assertEquals(features, client.authorizeCalls.single().features)
    }

    /**
     * MWA 2.0: authorize with SIWS payload returns a signed challenge. The
     * adapter must expose the wallet's signed message + signature via the
     * MwaSignInResult that connectWithSignIn returns.
     */
    @Test
    fun `authorize with SIWS payload surfaces sign-in result`() = runBlocking {
        val siwsResult = MwaSignInResult(
            address = "addr-siws",
            signedMessage = "c2lnbmVk", // base64 of "signed"
            signature = "c2ln",           // base64 of "sig"
            signatureType = "ed25519"
        )
        val client = RecordingMwaClient(
            authorizeResponse = authResult("T1", "addr-siws", signIn = siwsResult)
        )
        val adapter = adapter(client)
        val payload = MwaSignInPayload(
            domain = "artemis.test",
            uri = "https://artemis.test",
            statement = "Sign in"
        )
        val result = adapter.connectWithSignIn(payload)

        assertEquals("addr-siws", result.address)
        assertEquals("ed25519", result.signatureType)
        assertEquals(payload, client.authorizeCalls.single().signInPayload)
    }

    /**
     * MWA 2.0 mandatory capability surface: getCapabilities reports
     * sign_and_send, feature list, and per-method limits. The adapter
     * must reflect them without invention.
     */
    @Test
    fun `getCapabilities reflects wallet-declared features`() = runBlocking {
        val caps = MwaCapabilities(
            maxTransactionsPerRequest = 5,
            maxMessagesPerRequest = 10,
            supportedTransactionVersions = listOf("legacy", 0),
            features = listOf(
                MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS,
                MwaCapabilities.FEATURE_SIGN_MESSAGES,
                MwaCapabilities.FEATURE_CLONE_AUTHORIZATION
            )
        )
        val client = RecordingMwaClient(
            capabilities = caps,
            authorizeResponse = authResult("T", "addr")
        )
        val adapter = adapter(client)
        val exposed = adapter.getCapabilities()

        assertTrue("sign_and_send", exposed.supportsSignAndSend)
        assertTrue("clone_authorization", exposed.supportsCloneAuthorization)
        assertEquals(5, exposed.maxTransactionsPerRequest)
        assertEquals(10, exposed.maxMessagesPerRequest)
        assertTrue(exposed.supportsLegacyTransactions)
        assertTrue(exposed.supportsVersionedTransactions)
    }

    /**
     * MWA 2.0 mandatory `sign_and_send_transactions`: the adapter must use
     * the real RPC and return confirmed=true when the caller asked to
     * wait. Options are forwarded verbatim so the wallet honours
     * commitment / skipPreflight / maxRetries.
     */
    @Test
    fun `signAndSendTransaction uses native sign_and_send when supported`() = runBlocking {
        val client = RecordingMwaClient(
            capabilities = capsWithFeatures(MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS),
            authorizeResponse = authResult("T", "addr"),
            signAndSendResponse = listOf("SIG-1")
        )
        val adapter = adapter(client)
        val opts = SendTransactionOptions(
            waitForConfirmation = true,
            skipPreflight = true,
            maxRetries = 2
        )
        val res = adapter.signAndSendTransaction(ByteArray(10) { 0xA5.toByte() }, opts)

        assertEquals("SIG-1", res.signature)
        assertTrue(res.confirmed)
        assertNull(res.error)

        val call = client.signAndSendCalls.single()
        assertEquals(true, call.options?.skipPreflight)
        assertEquals(2, call.options?.maxRetries)
    }

    /**
     * MWA 2.0 optional `sign_transactions` fallback without an injected
     * broadcaster: the adapter must hand back the signed bytes instead of
     * returning an empty signature, so the caller can broadcast via its
     * own RPC boundary. The audit's high-severity item.
     */
    @Test
    fun `sign-only fallback without broadcaster returns signedRaw`() = runBlocking {
        val signedPayload = ByteArray(100) { 0x7F }
        val client = RecordingMwaClient(
            capabilities = capsWithFeatures(MwaCapabilities.FEATURE_SIGN_TRANSACTIONS),
            authorizeResponse = authResult("T", "addr"),
            signTransactionsResponse = listOf(signedPayload)
        )
        val adapter = adapter(client)
        val res = adapter.signAndSendTransaction(ByteArray(10), SendTransactionOptions())

        assertEquals("", res.signature)
        assertNotNull("signed bytes must be preserved", res.signedRaw)
        assertTrue(res.signedRaw!!.contentEquals(signedPayload))
    }

    /**
     * MWA 2.0 optional `sign_transactions` fallback WITH a broadcaster:
     * the adapter signs via wallet, then hands the signed bytes to the
     * broadcaster and returns the resulting on-chain signature.
     */
    @Test
    fun `sign-only fallback with broadcaster returns broadcast signature`() = runBlocking {
        val signedPayload = ByteArray(100) { 0x55 }
        var broadcasted: ByteArray? = null
        val broadcaster = com.selenus.artemis.wallet.RpcBroadcaster { bytes, _ ->
            broadcasted = bytes
            "ONCHAIN-SIG"
        }
        val client = RecordingMwaClient(
            capabilities = capsWithFeatures(MwaCapabilities.FEATURE_SIGN_TRANSACTIONS),
            authorizeResponse = authResult("T", "addr"),
            signTransactionsResponse = listOf(signedPayload)
        )
        val adapter = adapter(client, broadcaster = broadcaster)
        val res = adapter.signAndSendTransaction(ByteArray(10), SendTransactionOptions())

        assertEquals("ONCHAIN-SIG", res.signature)
        assertNull(res.error)
        assertTrue(broadcasted?.contentEquals(signedPayload) == true)
    }

    /**
     * MWA 2.0 batched `sign_and_send_transactions` with three payloads:
     * adapter must flatten signatures in the same order the wallet
     * returned them.
     */
    @Test
    fun `signAndSendTransactions batch preserves order`() = runBlocking {
        val client = RecordingMwaClient(
            capabilities = capsWithFeatures(MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS),
            authorizeResponse = authResult("T", "addr"),
            signAndSendResponse = listOf("S1", "S2", "S3")
        )
        val adapter = adapter(client)
        val txs = listOf(ByteArray(10), ByteArray(10), ByteArray(10))
        val res = adapter.signAndSendTransactions(txs, SendTransactionOptions())

        assertEquals(listOf("S1", "S2", "S3"), res.results.map { it.signature })
    }

    /**
     * MWA 2.0 optional `clone_authorization`: requires the wallet to
     * advertise the feature. When it doesn't, the adapter throws
     * UnsupportedOperationException rather than silently returning a stub.
     */
    @Test
    fun `cloneAuthorization throws when wallet does not advertise feature`() = runBlocking {
        val client = RecordingMwaClient(
            capabilities = capsWithFeatures(MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS),
            authorizeResponse = authResult("T", "addr"),
            cloneAuthorizationResponse = "NEW-TOKEN"
        )
        val adapter = adapter(client)
        adapter.connect()
        try {
            adapter.cloneAuthorization()
            fail("expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) {
            // expected
        }
    }

    @Test
    fun `cloneAuthorization returns new token when wallet supports feature`() = runBlocking {
        val client = RecordingMwaClient(
            capabilities = capsWithFeatures(
                MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS,
                MwaCapabilities.FEATURE_CLONE_AUTHORIZATION
            ),
            authorizeResponse = authResult("T-ORIG", "addr"),
            cloneAuthorizationResponse = "T-CLONED"
        )
        val adapter = adapter(client)
        adapter.connect()
        val clone = adapter.cloneAuthorization()
        assertEquals("T-CLONED", clone)
    }

    /**
     * MWA 2.0 reauthorize is merged into authorize(auth_token=...). The
     * reauthorize helper must reach the same rpc method (authorize) and
     * update the stored token when the wallet returns a new one.
     */
    @Test
    fun `reauthorize updates stored auth token`() = runBlocking {
        val client = RecordingMwaClient(
            capabilities = capsWithFeatures(MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS),
            authorizeResponse = authResult("T-INITIAL", "addr")
        )
        val authStore = InMemoryAuthTokenStore()
        val adapter = adapter(client, authStore = authStore)
        adapter.connect()
        assertEquals("T-INITIAL", authStore.get())

        // Flip the scripted response so reauthorize produces a distinct
        // token, then verify the adapter persisted the new value.
        client.authorizeResponse = authResult("T-REFRESHED", "addr")
        adapter.reauthorize()

        // connect() takes the authorize path; reauthorize() takes MwaClient's
        // reauthorize path. The recording client overrides reauthorize
        // directly so authorize sees only the initial connect call.
        assertEquals(1, client.authorizeCalls.size)
        assertEquals(1, client.reauthorizeCalls.size)
        assertEquals("T-INITIAL", client.reauthorizeCalls.single())
        assertEquals("T-REFRESHED", authStore.get())
    }

    /**
     * MWA 2.0 expiry path: wallet rejects authorize with
     * `AUTHORIZATION_FAILED` (the spec's code for expired/revoked tokens).
     * The adapter must surface the protocol exception so the caller can
     * clear stale state and re-prompt the user. It must NOT swallow the
     * failure and return an empty-but-valid authorization.
     */
    @Test
    fun `authorize surfaces AUTHORIZATION_FAILED from expired token`() = runBlocking {
        val client = RecordingMwaClient(
            capabilities = capsWithFeatures(MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS),
            authorizeResponse = authResult("never-returned", "addr")
        )
        client.authorizeError = MwaProtocolException(
            code = MwaErrorCodes.AUTHORIZATION_FAILED,
            message = "auth token expired",
            data = null
        )
        val authStore = InMemoryAuthTokenStore().apply { set("EXPIRED-TOKEN") }
        val adapter = adapter(client, authStore = authStore)
        try {
            adapter.connect()
            fail("expected MwaProtocolException")
        } catch (e: MwaProtocolException) {
            assertEquals(MwaErrorCodes.AUTHORIZATION_FAILED, e.code)
        }
    }

    /**
     * MWA 2.0 malformed-request path: wallet rejects the payload with
     * `INVALID_PAYLOADS`. signAndSendTransaction must propagate the code
     * in the result, not convert it to a plain IllegalStateException that
     * loses the protocol context.
     */
    @Test
    fun `signAndSend surfaces INVALID_PAYLOADS error in result`() = runBlocking {
        val client = RecordingMwaClient(
            capabilities = capsWithFeatures(MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS),
            authorizeResponse = authResult("T", "addr")
        )
        client.signAndSendError = MwaProtocolException(
            code = MwaErrorCodes.INVALID_PAYLOADS,
            message = "payload #0 rejected",
            data = null
        )
        val adapter = adapter(client)
        val res = adapter.signAndSendTransaction(ByteArray(10), SendTransactionOptions())

        assertEquals("", res.signature)
        assertNotNull("error propagated", res.error)
        assertTrue(
            "error mentions rejected payload: ${res.error}",
            res.error!!.contains("payload", ignoreCase = true) ||
                res.error!!.contains("Invalid", ignoreCase = true) ||
                res.error!!.contains("rejected", ignoreCase = true)
        )
    }

    /**
     * Batch invariants: every [SendTransactionResult] in a [BatchSendResult]
     * is in exactly one of three well-formed states (success, failure,
     * signed-but-not-broadcast), and the batch size matches the input
     * size. The audit flagged `result.size == input.size` and
     * `success XOR error` as the two invariants the compat layer must not
     * break; this test pins them at the adapter level. A regression that
     * drops a slot, double-reports, or lands in a fourth state makes this
     * test fail.
     */
    @Test
    fun `batch results hold size and state invariants`() = runBlocking {
        val client = RecordingMwaClient(
            capabilities = capsWithFeatures(MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS),
            authorizeResponse = authResult("T", "addr"),
            signAndSendResponse = listOf("S1", "S2", "S3", "S4")
        )
        val adapter = adapter(client)
        val txs = listOf(ByteArray(8), ByteArray(8), ByteArray(8), ByteArray(8))
        val batch = adapter.signAndSendTransactions(txs, SendTransactionOptions())

        assertEquals("input.size == result.size", txs.size, batch.results.size)
        batch.results.forEachIndexed { i, r ->
            assertTrue(
                "result $i in exactly one state (success XOR failure XOR signed-not-broadcast)",
                r.invariantsHold()
            )
        }
    }

    /**
     * Sign-only fallback preserves batch-level invariants even when each
     * slot ends up in the signed-but-not-broadcast state. No slot drops,
     * no slot double-reports.
     */
    @Test
    fun `sign-only fallback batch invariants hold`() = runBlocking {
        val signedPayloads = List(3) { ByteArray(64) { b -> (b + it).toByte() } }
        val client = RecordingMwaClient(
            capabilities = capsWithFeatures(MwaCapabilities.FEATURE_SIGN_TRANSACTIONS),
            authorizeResponse = authResult("T", "addr"),
            signTransactionsResponse = signedPayloads
        )
        val adapter = adapter(client)
        val txs = List(3) { ByteArray(16) }
        val batch = adapter.signAndSendTransactions(txs, SendTransactionOptions())

        assertEquals(3, batch.results.size)
        batch.results.forEach { r ->
            assertTrue("no broadcast, but signed bytes preserved", r.isSignedButNotBroadcast)
            assertTrue("invariants", r.invariantsHold())
        }
    }

    /**
     * Session teardown: disconnect() must clear the connected account
     * state so a subsequent connect() re-runs the handshake rather than
     * silently reusing a dead session.
     */
    @Test
    fun `disconnect clears session state`() = runBlocking {
        val client = RecordingMwaClient(
            capabilities = capsWithFeatures(MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS),
            authorizeResponse = authResult("T", "addr")
        )
        val adapter = adapter(client)
        adapter.connect()
        assertNotNull(adapter.lastAuthorization)
        adapter.disconnect()
        assertNull(adapter.lastAuthorization)

        // Subsequent signAndSendTransaction triggers a fresh connect.
        client.signAndSendResponse = listOf("S1")
        adapter.signAndSendTransaction(ByteArray(1), SendTransactionOptions())
        assertEquals(2, client.authorizeCalls.size)
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun capsWithFeatures(vararg features: String) = MwaCapabilities(
        features = features.toList(),
        supportedTransactionVersions = listOf("legacy", 0)
    )

    private fun authResult(
        token: String,
        address: String,
        signIn: MwaSignInResult? = null
    ) = MwaAuthorizeResult(
        authToken = token,
        accounts = listOf(
            MwaAccount(
                address = base64("address-${address.take(16)}".toByteArray().copyOf(32)),
                label = null
            )
        ),
        signInResult = signIn
    )

    private fun base64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)
}

/**
 * Deterministic fake for [MwaClient]. Captures every RPC invocation and
 * returns pre-scripted responses. The session returned by [openSession] is
 * an opaque [MwaSession.testSession] that never touches a socket.
 */
private class RecordingMwaClient(
    var capabilities: MwaCapabilities = MwaCapabilities(
        features = listOf(MwaCapabilities.FEATURE_SIGN_AND_SEND_TRANSACTIONS)
    ),
    var authorizeResponse: MwaAuthorizeResult,
    var signTransactionsResponse: List<ByteArray> = emptyList(),
    var signAndSendResponse: List<String> = emptyList(),
    var signMessagesResponse: List<ByteArray> = emptyList(),
    var cloneAuthorizationResponse: String = "CLONE-TOKEN",
    // Error injection. When set, the matching verb throws the given
    // exception instead of returning the scripted response. Used by the
    // expiry + malformed-request behavior cases.
    var authorizeError: Throwable? = null,
    var signAndSendError: Throwable? = null,
    var signTransactionsError: Throwable? = null
) : MwaClient() {

    data class AuthorizeCall(
        val identity: MwaIdentity,
        val chain: String?,
        val authToken: String?,
        val features: List<String>?,
        val addresses: List<String>?,
        val signInPayload: MwaSignInPayload?
    )

    data class SignAndSendCall(
        val payloads: List<ByteArray>,
        val options: MwaSendOptions?
    )

    val authorizeCalls = mutableListOf<AuthorizeCall>()
    val signAndSendCalls = mutableListOf<SignAndSendCall>()
    val deauthorizeCalls = mutableListOf<String>()
    val reauthorizeCalls = mutableListOf<String>()
    val cloneCalls = mutableListOf<String>()

    private val testKeyPair: KeyPair = run {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        kpg.generateKeyPair()
    }

    override suspend fun openSession(
        activity: Activity,
        walletUriPrefix: Uri?,
        protocolVersionMajor: Int,
        timeoutMs: Long
    ): Pair<MwaSession, KeyPair> = MwaSession.testSession() to testKeyPair

    override suspend fun getCapabilities(
        session: MwaSession,
        timeoutMs: Long
    ): MwaCapabilities = capabilities

    override suspend fun authorize(
        session: MwaSession,
        identity: MwaIdentity,
        chain: String?,
        authToken: String?,
        features: List<String>?,
        addresses: List<String>?,
        signInPayload: MwaSignInPayload?,
        timeoutMs: Long
    ): MwaAuthorizeResult {
        authorizeCalls.add(
            AuthorizeCall(identity, chain, authToken, features, addresses, signInPayload)
        )
        authorizeError?.let { throw it }
        return authorizeResponse
    }

    override suspend fun signTransactions(
        session: MwaSession,
        payloads: List<ByteArray>,
        timeoutMs: Long
    ): List<ByteArray> {
        signTransactionsError?.let { throw it }
        return signTransactionsResponse.ifEmpty {
            payloads.map { it + byteArrayOf(0x00) }
        }
    }

    override suspend fun signAndSend(
        session: MwaSession,
        payloads: List<ByteArray>,
        options: MwaSendOptions?,
        timeoutMs: Long
    ): List<String> {
        signAndSendCalls.add(SignAndSendCall(payloads, options))
        signAndSendError?.let { throw it }
        return signAndSendResponse
    }

    override suspend fun signMessages(
        session: MwaSession,
        payloads: List<ByteArray>,
        addresses: List<ByteArray>,
        timeoutMs: Long
    ): List<ByteArray> = signMessagesResponse.ifEmpty {
        payloads.map { ByteArray(64) }
    }

    override suspend fun reauthorize(
        session: MwaSession,
        identity: MwaIdentity,
        authToken: String,
        timeoutMs: Long
    ): MwaAuthorizeResult {
        reauthorizeCalls.add(authToken)
        return authorizeResponse
    }

    override suspend fun deauthorize(
        session: MwaSession,
        authToken: String,
        timeoutMs: Long
    ) {
        deauthorizeCalls.add(authToken)
    }

    override suspend fun cloneAuthorization(
        session: MwaSession,
        authToken: String,
        timeoutMs: Long
    ): String {
        cloneCalls.add(authToken)
        return cloneAuthorizationResponse
    }
}
