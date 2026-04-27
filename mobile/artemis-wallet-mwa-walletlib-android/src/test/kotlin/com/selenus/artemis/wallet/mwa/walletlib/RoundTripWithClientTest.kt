package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri
import com.selenus.artemis.wallet.mwa.protocol.EcP256
import com.selenus.artemis.wallet.mwa.protocol.MwaSession
import com.selenus.artemis.wallet.mwa.protocol.MwaWebSocketServer
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.util.Base64

/**
 * End-to-end round trip between the walletlib (this module) and the
 * dApp-side `MwaSession` from `:artemis-wallet-mwa-android`. Uses real
 * loopback TCP because the dApp's WS server already lives on top of
 * `java.net.ServerSocket`; that costs at most a single port allocation
 * per test and avoids the surface-area cost of exposing an in-process
 * transport seam in production code.
 *
 * Sequence: bind dApp server → start wallet scenario → handshake →
 * authorize → sign_transactions → deauthorize. Asserts on every
 * callback shape and every dApp-side response.
 */
class RoundTripWithClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        UriTestSupport.installUriStub()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test(timeout = 30_000)
    fun `connect authorize signTransactions deauthorize end to end`() = runBlocking<Unit> {
        // dApp side: bind a random loopback port through the same WS
        // server the production dApp uses. The wallet's transport
        // factory then connects to that port via WalletWebSocketClient.
        val dappServer = MwaWebSocketServer()
        val port = dappServer.bind(0)
        val associationKeypair: KeyPair = EcP256.generateKeypair()
        val associationPub = EcP256.x962Uncompressed(associationKeypair.public)

        // Wallet side: parse a synthetic association URI that points
        // at the dApp's port + association key.
        val uri = AssociationUri.Local(
            associationPublicKey = associationPub,
            port = port,
            protocolVersions = listOf(AssociationUri.ProtocolVersion.V1)
        )

        val approvedAccountKey = ByteArray(32) { 0x42 }
        val authorizeSeen = CompletableDeferred<AuthorizeRequest>()
        val signTxSeen = CompletableDeferred<SignTransactionsRequest>()
        val deauthSeen = CompletableDeferred<DeauthorizedEvent>()

        // The wallet's auto-issued auth token, captured from inside
        // the authorize callback so the deauthorize step can target it.
        val issuedAuthToken = CompletableDeferred<String>()
        val authRepoForCallbacks = InMemoryAuthRepository(AuthIssuerConfig(name = "RT Wallet"))

        val callbacks = object : Scenario.Callbacks {
            override suspend fun onAuthorizeRequest(request: AuthorizeRequest) {
                authorizeSeen.complete(request)
                request.completeWithAuthorize(
                    accounts = listOf(
                        AuthorizedAccount(
                            publicKey = approvedAccountKey,
                            accountLabel = "Round Trip Account",
                            chains = listOf("solana:mainnet")
                        )
                    )
                )
            }
            override suspend fun onReauthorizeRequest(request: ReauthorizeRequest) {
                request.completeWithReauthorize()
            }
            override suspend fun onSignTransactionsRequest(request: SignTransactionsRequest) {
                signTxSeen.complete(request)
                // Flip a sentinel byte so the assertion has something
                // distinguishable to compare against.
                val signed = request.payloads.map { tx -> tx + ByteArray(64) { 0x55 } }
                request.completeWithSignedPayloads(signed)
            }
            override suspend fun onSignMessagesRequest(request: SignMessagesRequest) {
                request.completeWithDecline()
            }
            override suspend fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest) {
                request.completeWithDecline()
            }
            override fun onDeauthorizedEvent(event: DeauthorizedEvent) {
                deauthSeen.complete(event)
                event.complete()
            }
        }

        val scenario = LocalScenario(
            associationUri = uri,
            config = MobileWalletAdapterConfig(
                optionalFeatures = setOf(MobileWalletAdapterConfig.FEATURE_SIGN_TRANSACTIONS)
            ),
            authRepository = authRepoForCallbacks
        )

        // Start the wallet scenario in a background coroutine. start()
        // suspends until the handshake completes; the dApp side runs
        // concurrently below.
        val scenarioJob = launch(Dispatchers.IO) {
            scenario.start(callbacks)
        }

        // dApp side: connect to the wallet (i.e., accept the wallet's
        // outbound connection at the dApp's bound port), run the dApp
        // end of the HELLO handshake, then exchange JSON-RPC verbs.
        val session = withTimeout(10_000) {
            MwaSession.connectLocal(
                server = dappServer,
                associationKeypair = associationKeypair,
                protocolVersionMajor = 2,
                timeoutMs = 10_000
            )
        }

        try {
            // Wait for the wallet's start() to finish HELLO and start
            // the dispatcher. Without this, the first sendJsonRpc could
            // race the dispatcher's recv loop — which actually still
            // works because the inbound channel buffers, but blocking
            // here makes failures point at the right place.
            withTimeout(5_000) { scenarioJob.join() }

            // ─── authorize ───────────────────────────────────────
            val authParams = buildJsonObject {
                put("identity", buildJsonObject {
                    put("name", "RoundTrip dApp")
                    put("uri", "https://roundtrip.dapp")
                })
                put("chain", "solana:mainnet")
            }
            val authRsp = withTimeout(5_000) {
                session.sendJsonRpc(method = "authorize", params = authParams).await()
            }
            val authResult = authRsp["result"] as JsonObject
            val authToken = (authResult["auth_token"] as JsonPrimitive).content
            issuedAuthToken.complete(authToken)
            val accountsArr = authResult["accounts"] as JsonArray
            val acc0 = accountsArr[0] as JsonObject
            val accountAddrBytes = Base64.getDecoder()
                .decode((acc0["address"] as JsonPrimitive).content)
            assertArrayEquals(approvedAccountKey, accountAddrBytes)

            val seenAuthReq = withTimeout(2_000) { authorizeSeen.await() }
            assertEquals("RoundTrip dApp", seenAuthReq.identityName)
            assertEquals("solana:mainnet", seenAuthReq.chain)

            // ─── sign_transactions ──────────────────────────────
            val tx1 = ByteArray(32) { 0x10 }
            val tx2 = ByteArray(48) { 0x20 }
            val signParams = buildJsonObject {
                put("payloads", buildJsonArray {
                    add(Base64.getEncoder().encodeToString(tx1))
                    add(Base64.getEncoder().encodeToString(tx2))
                })
            }
            val signRsp = withTimeout(5_000) {
                session.sendJsonRpc(method = "sign_transactions", params = signParams).await()
            }
            val signResult = signRsp["result"] as JsonObject
            val signedArr = signResult["signed_payloads"] as JsonArray
            assertEquals(2, signedArr.size)
            val signed1 = Base64.getDecoder()
                .decode((signedArr[0] as JsonPrimitive).content)
            val signed2 = Base64.getDecoder()
                .decode((signedArr[1] as JsonPrimitive).content)
            // Wallet appended 64 bytes of 0x55 to each input.
            assertArrayEquals(tx1 + ByteArray(64) { 0x55 }, signed1)
            assertArrayEquals(tx2 + ByteArray(64) { 0x55 }, signed2)
            val seenSignReq = withTimeout(2_000) { signTxSeen.await() }
            assertEquals(2, seenSignReq.payloads.size)

            // ─── deauthorize ────────────────────────────────────
            val deauthParams = buildJsonObject {
                put("auth_token", authToken)
            }
            val deauthRsp = withTimeout(5_000) {
                session.sendJsonRpc(method = "deauthorize", params = deauthParams).await()
            }
            assertNotNull(deauthRsp["result"])
            assertNull(deauthRsp["error"])
            val seenDeauth = withTimeout(2_000) { deauthSeen.await() }
            assertEquals(authToken, seenDeauth.authToken)
        } finally {
            try { session.close() } catch (_: Throwable) {}
            try { scenario.close() } catch (_: Throwable) {}
            try { dappServer.close() } catch (_: Throwable) {}
        }
    }
}
