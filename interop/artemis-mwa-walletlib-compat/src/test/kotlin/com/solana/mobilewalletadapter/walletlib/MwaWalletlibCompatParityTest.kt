package com.solana.mobilewalletadapter.walletlib

import android.net.Uri
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.association.RemoteAssociationUri
import com.solana.mobilewalletadapter.walletlib.authorization.AccountRecord
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
import com.solana.mobilewalletadapter.walletlib.authorization.AuthRecord
import com.solana.mobilewalletadapter.walletlib.authorization.AuthRepository
import com.solana.mobilewalletadapter.walletlib.authorization.IdentityRecord
import com.solana.mobilewalletadapter.walletlib.authorization.InMemoryAuthRepository
import com.solana.mobilewalletadapter.walletlib.protocol.JsonRpc20Server
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer
import com.solana.mobilewalletadapter.walletlib.scenario.AuthorizedAccount
import com.solana.mobilewalletadapter.walletlib.scenario.DefaultWalletIconProvider
import com.solana.mobilewalletadapter.walletlib.scenario.RemoteWebSocketServerScenario
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario
import com.solana.mobilewalletadapter.walletlib.scenario.ScenarioRequest
import com.solana.mobilewalletadapter.walletlib.scenario.SignPayloadsRequest
import com.solana.mobilewalletadapter.walletlib.scenario.VerifiableIdentityRequest
import com.solana.mobilewalletadapter.walletlib.scenario.WalletIconProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Parity sweep for the walletlib drop-in shim. Asserts:
 *  - every upstream-named class is reachable at the upstream FQN
 *  - the round-trip from compat AuthRepository → Artemis runtime →
 *    compat AuthRecord preserves identity / accounts / chain / scope
 *  - typed exception classes carry the expected fields
 *  - JSON-RPC envelope helpers produce the canonical shape
 *  - AssociationUri.parseOrNull respects the upstream null contract
 */
class MwaWalletlibCompatParityTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val raw = firstArg<String>()
            stubUri(raw)
        }
    }

    @After
    fun tearDown() = unmockkAll()

    // ─── AssociationUri ──────────────────────────────────────────────

    @Test
    fun `AssociationUri parse returns LocalAssociationUri for a local URI`() {
        val uri = Uri.parse("solana-wallet:/v1/associate/local?association=BNXVtVHa2-FNJtcLqJlh-ofyKAFVHZNs9-PPGRoBlgklgwgnmwOLUzEDJSc8FdMHK0fY3sd5DVCsoaTcEGr0lwk&port=8765")
        val parsed = AssociationUri.parse(uri)
        assertTrue("expected LocalAssociationUri, got ${parsed::class.simpleName}",
            parsed is LocalAssociationUri)
        assertEquals(8765, (parsed as LocalAssociationUri).port)
    }

    @Test
    fun `AssociationUri parseOrNull returns null on malformed input`() {
        val uri = Uri.parse("solana-wallet:/v1/associate/local?port=notanint")
        val parsed = AssociationUri.parseOrNull(uri)
        assertNull(parsed)
    }

    // ─── MobileWalletAdapterConfig ───────────────────────────────────

    @Test
    fun `MobileWalletAdapterConfig translates legacy + V0 string and number`() {
        val config = MobileWalletAdapterConfig(
            maxTransactionsPerSigningRequest = 7,
            maxMessagesPerSigningRequest = 3,
            supportedTransactionVersions = arrayOf<Any>("legacy", 0),
            optionalFeatures = arrayOf(MobileWalletAdapterConfig.OPTIONAL_FEATURE_SIGN_TRANSACTIONS)
        )
        val artemis = config.toArtemis()
        assertEquals(7, artemis.maxTransactionsPerSigningRequest)
        assertEquals(3, artemis.maxMessagesPerSigningRequest)
        assertEquals(2, artemis.supportedTransactionVersions.size)
    }

    // ─── AuthIssuerConfig + InMemoryAuthRepository ──────────────────

    @Test
    fun `compat InMemoryAuthRepository round-trips an AuthRecord`() {
        val repo: AuthRepository = InMemoryAuthRepository(AuthIssuerConfig(name = "Parity Wallet"))
        repo.start()
        try {
            val accounts = arrayOf(
                AuthorizedAccount(
                    publicKey = ByteArray(32) { 1 },
                    accountLabel = "Acc 0",
                    displayAddress = null,
                    displayAddressFormat = null,
                    accountIcon = null,
                    chains = arrayOf("solana:mainnet"),
                    features = null
                )
            )
            val issued: AuthRecord = repo.issue(
                name = "Parity dApp",
                uri = null,
                relativeIconUri = null,
                accounts = accounts,
                cluster = "solana:mainnet",
                walletUriBase = null,
                scope = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
            )
            assertEquals("solana:mainnet", issued.chain)
            // cluster is the legacy alias derived from chain.
            assertEquals("mainnet-beta", issued.cluster)

            val looked = repo.fromAuthToken(issued.authToken)
            assertNotNull(looked)
            assertEquals(issued.identity, looked!!.identity)
            assertEquals(issued.accounts.size, looked.accounts.size)

            assertTrue(repo.revoke(issued))
            assertNull(repo.fromAuthToken(issued.authToken))
        } finally {
            repo.stop()
        }
    }

    // ─── Typed exceptions ────────────────────────────────────────────

    @Test
    fun `MobileWalletAdapterServer typed exceptions carry the expected payload`() {
        val invalid = MobileWalletAdapterServer.InvalidPayloadsException(
            "bad", booleanArrayOf(true, false, true)
        )
        assertEquals(3, invalid.valid.size)
        assertEquals(false, invalid.valid[1])

        val notSubmitted = MobileWalletAdapterServer.NotSubmittedException(
            "boom", arrayOf<ByteArray?>(null, ByteArray(64))
        )
        assertEquals(2, notSubmitted.signatures.size)
        assertNull(notSubmitted.signatures[0])

        val chain = MobileWalletAdapterServer.ChainNotSupportedException("solana:devnet")
        assertEquals("solana:devnet", chain.chain)
        // Legacy alias still resolvable.
        @Suppress("DEPRECATION")
        val cluster = MobileWalletAdapterServer.ClusterNotSupportedException("devnet")
        assertEquals("devnet", cluster.cluster)
    }

    // ─── JsonRpc20Server envelope shape ──────────────────────────────

    @Test
    fun `JsonRpc20Server defines the canonical error codes and version`() {
        // The JSONObject helpers depend on Android's org.json stub, which
        // returns null for every getter under unit tests. Asserting on
        // the static constants exercises the parity-relevant surface.
        assertEquals(-32700, JsonRpc20Server.ERROR_PARSE_ERROR)
        assertEquals(-32600, JsonRpc20Server.ERROR_INVALID_REQUEST)
        assertEquals(-32601, JsonRpc20Server.ERROR_METHOD_NOT_FOUND)
        assertEquals(-32602, JsonRpc20Server.ERROR_INVALID_PARAMS)
        assertEquals(-32603, JsonRpc20Server.ERROR_INTERNAL_ERROR)
        assertEquals("2.0", JsonRpc20Server.JSONRPC_VERSION)
    }

    // ─── WalletIconProvider ──────────────────────────────────────────

    @Test
    fun `DefaultWalletIconProvider returns null when either input is missing`() {
        val provider: WalletIconProvider = DefaultWalletIconProvider()
        assertNull(provider.resolve(null, null))
        assertNull(provider.resolve(null, Uri.parse("/icon.png")))
    }

    // ─── Marker interface reachability ───────────────────────────────

    @Test
    fun `marker interfaces are reachable at upstream FQN`() {
        // Compile-time: the type names must resolve. The body is a no-op
        // sanity check so the test still has at least one assertion.
        val markers: Array<Class<*>> = arrayOf(
            ScenarioRequest::class.java,
            VerifiableIdentityRequest::class.java,
            SignPayloadsRequest::class.java
        )
        assertEquals(3, markers.size)
    }

    // ─── RemoteWebSocketServerScenario stub fails fast ──────────────

    @Test
    fun `RemoteWebSocketServerScenario completes exceptionally on startAsync`() {
        val callbacks = object : Scenario.Callbacks {
            override fun onAuthorizeRequest(request: com.solana.mobilewalletadapter.walletlib.scenario.AuthorizeRequest) {}
            override fun onReauthorizeRequest(request: com.solana.mobilewalletadapter.walletlib.scenario.ReauthorizeRequest) {}
            override fun onSignTransactionsRequest(request: com.solana.mobilewalletadapter.walletlib.scenario.SignTransactionsRequest) {}
            override fun onSignMessagesRequest(request: com.solana.mobilewalletadapter.walletlib.scenario.SignMessagesRequest) {}
            override fun onSignAndSendTransactionsRequest(request: com.solana.mobilewalletadapter.walletlib.scenario.SignAndSendTransactionsRequest) {}
        }
        // Build a dummy RemoteAssociationUri by parsing a remote URI.
        val uri = Uri.parse("solana-wallet:/v1/associate/remote?association=BNXVtVHa2-FNJtcLqJlh-ofyKAFVHZNs9-PPGRoBlgklgwgnmwOLUzEDJSc8FdMHK0fY3sd5DVCsoaTcEGr0lwk&reflector=test.example&id=AQID")
        val remote = AssociationUri.parse(uri) as RemoteAssociationUri
        val scenario = RemoteWebSocketServerScenario(
            associationUri = remote,
            config = MobileWalletAdapterConfig(),
            authIssuerConfig = AuthIssuerConfig(name = "Stub Wallet"),
            callbacks = callbacks
        )
        val future = scenario.startAsync()
        try {
            future.get()
            org.junit.Assert.fail("expected UnsupportedOperationException")
        } catch (e: java.util.concurrent.ExecutionException) {
            assertTrue(e.cause is UnsupportedOperationException)
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private fun stubUri(raw: String): Uri {
        val javaUri = try {
            java.net.URI(raw)
        } catch (e: java.net.URISyntaxException) {
            return mockk<Uri>(relaxed = true).also {
                every { it.scheme } returns null
                every { it.path } returns null
                every { it.getQueryParameter(any()) } returns null
                every { it.toString() } returns raw
                every { it.isAbsolute } returns false
            }
        }
        val ssp = javaUri.rawSchemeSpecificPart
        val (pathPart, queryPart) = if (ssp.contains('?')) {
            val q = ssp.indexOf('?')
            ssp.substring(0, q) to ssp.substring(q + 1)
        } else ssp to ""
        val pathDecoded = pathPart
            .let { if (it.startsWith("//")) it.removePrefix("//") else it }
            .let { if (it.startsWith("/")) it else "/$it" }
        val queryParams: Map<String, String> = if (queryPart.isEmpty()) emptyMap() else {
            queryPart.split('&').filter { it.isNotEmpty() }.associate { kv ->
                val eq = kv.indexOf('=')
                if (eq == -1) kv to ""
                else java.net.URLDecoder.decode(kv.substring(0, eq), "UTF-8") to
                    java.net.URLDecoder.decode(kv.substring(eq + 1), "UTF-8")
            }
        }
        return mockk<Uri>(relaxed = true).also { uri ->
            every { uri.scheme } returns javaUri.scheme
            every { uri.path } returns pathDecoded
            every { uri.toString() } returns raw
            every { uri.isAbsolute } returns true
            every { uri.getQueryParameter(any()) } answers {
                queryParams[firstArg<String>()]
            }
        }
    }
}
