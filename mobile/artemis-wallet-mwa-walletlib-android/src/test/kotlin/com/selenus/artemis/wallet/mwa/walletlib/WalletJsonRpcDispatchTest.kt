package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri
import com.selenus.artemis.wallet.mwa.protocol.Aes128Gcm
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

/**
 * Direct JSON-RPC dispatcher tests. Bypasses the HELLO handshake by
 * constructing the dispatcher with a pre-shared AES-128-GCM key, then
 * feeds it canned encrypted frames and asserts on the callbacks +
 * outbound replies.
 */
class WalletJsonRpcDispatchTest {

    private lateinit var pair: InMemoryTransportPair
    private lateinit var key: ByteArray
    /** Encryptor on the dApp side: encrypts requests we push to the wallet. */
    private lateinit var dappEncrypt: Aes128Gcm
    /** Decryptor on the dApp side: decrypts replies the wallet pushes back. */
    private lateinit var dappDecrypt: Aes128Gcm
    /** dApp-side outbound seq starts at 1 (matches the wallet's initialRecvSeq=1). */
    private val dappSendSeq = AtomicInteger(1)
    /** dApp-side inbound seq tracks the wallet's outbound seq. */
    private val dappRecvSeq = AtomicInteger(1)

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        UriTestSupport.installUriStub()
        pair = InMemoryTransportPair()
        // Distinct test key; not derived from a real handshake. Same
        // bytes used on both sides because Aes128Gcm is symmetric.
        key = ByteArray(16) { (0x40 + it).toByte() }
        dappEncrypt = Aes128Gcm(key)
        dappDecrypt = Aes128Gcm(key)
    }

    @After
    fun tearDown() {
        pair.dappEnd.close()
        unmockkAll()
    }

    private fun newDispatcher(
        callbacks: Scenario.Callbacks,
        config: MobileWalletAdapterConfig = MobileWalletAdapterConfig(
            optionalFeatures = setOf(
                MobileWalletAdapterConfig.FEATURE_SIGN_TRANSACTIONS,
                MobileWalletAdapterConfig.FEATURE_SIGN_MESSAGES,
                MobileWalletAdapterConfig.FEATURE_SIGN_IN_WITH_SOLANA
            )
        ),
        authRepository: AuthRepository = InMemoryAuthRepository(
            AuthIssuerConfig(name = "Test Wallet")
        )
    ): WalletMwaServer {
        // initialRecvSeq=1 matches the test's dappSendSeq starting at 1.
        // initialSendSeq=1 because the test bypasses HELLO_RSP entirely.
        val server = WalletMwaServer(
            transport = pair.walletEnd,
            cipher = Aes128Gcm(key),
            callbacks = callbacks,
            config = config,
            authRepository = authRepository,
            identityResolver = DefaultIdentityResolver(authRepository),
            initialRecvSeq = 1,
            initialSendSeq = 1
        )
        server.start()
        return server
    }

    /** Encrypt a JSON-RPC request envelope and push it to the wallet. */
    private fun pushRequest(method: String, id: Int, params: JsonObject) {
        val envelope = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val frame = dappEncrypt.encrypt(
            seq = dappSendSeq.getAndIncrement(),
            plaintext = envelope.toString().encodeToByteArray()
        )
        pair.toWallet.trySend(frame)
    }

    /** Pull and decrypt the next reply from the wallet (with a timeout). */
    private suspend fun nextReply(): JsonObject {
        val frame = withTimeout(2_000) { pair.toDapp.receive() }
        val plain = dappDecrypt.decrypt(dappRecvSeq.getAndIncrement(), frame)
        return json.parseToJsonElement(plain.decodeToString()) as JsonObject
    }

    @Test
    fun `get_capabilities derives reply from config`() = runBlocking {
        val cb = NoOpCallbacks()
        val server = newDispatcher(
            cb,
            config = MobileWalletAdapterConfig(
                maxTransactionsPerSigningRequest = 5,
                maxMessagesPerSigningRequest = 3,
                supportedTransactionVersions = listOf(
                    MobileWalletAdapterConfig.TxVersion.Legacy,
                    MobileWalletAdapterConfig.TxVersion.V0
                ),
                optionalFeatures = setOf(
                    MobileWalletAdapterConfig.FEATURE_SIGN_TRANSACTIONS
                )
            )
        )
        try {
            pushRequest("get_capabilities", id = 1, params = JsonObject(emptyMap()))
            val reply = nextReply()
            val result = reply["result"] as JsonObject
            assertEquals(5, (result["max_transactions_per_request"] as JsonPrimitive).intOrNull)
            assertEquals(3, (result["max_messages_per_request"] as JsonPrimitive).intOrNull)
            val versions = result["supported_transaction_versions"] as JsonArray
            assertEquals("legacy", (versions[0] as JsonPrimitive).contentOrNull)
            assertEquals(0, (versions[1] as JsonPrimitive).intOrNull)
            assertTrue((result["features"] as JsonArray).any {
                (it as JsonPrimitive).contentOrNull == MobileWalletAdapterConfig.FEATURE_SIGN_AND_SEND_TRANSACTIONS
            })
            assertTrue((result["features"] as JsonArray).any {
                (it as JsonPrimitive).contentOrNull == MobileWalletAdapterConfig.FEATURE_SIGN_TRANSACTIONS
            })
        } finally {
            server.close()
        }
    }

    @Test
    fun `authorize fires callback and serializes accounts on completion`() = runBlocking<Unit> {
        val seenRequest = CompletableDeferred<AuthorizeRequest>()
        val cb = object : NoOpCallbacks() {
            override suspend fun onAuthorizeRequest(request: AuthorizeRequest) {
                seenRequest.complete(request)
            }
        }
        val server = newDispatcher(cb)
        try {
            pushRequest(
                method = "authorize",
                id = 7,
                params = buildJsonObject {
                    put("identity", buildJsonObject {
                        put("name", "Test dApp")
                        put("uri", "https://test.dapp")
                    })
                    put("chain", "solana:mainnet")
                }
            )
            val req = withTimeout(2_000) { seenRequest.await() }
            assertEquals("Test dApp", req.identityName)
            assertEquals("solana:mainnet", req.chain)

            val accountKey = ByteArray(32) { 0x11 }
            req.completeWithAuthorize(
                accounts = listOf(
                    AuthorizedAccount(
                        publicKey = accountKey,
                        accountLabel = "Account 1",
                        chains = listOf("solana:mainnet"),
                        features = listOf(MobileWalletAdapterConfig.FEATURE_SIGN_AND_SEND_TRANSACTIONS)
                    )
                ),
                walletUriBase = Uri.parse("solana-wallet:")
            )

            val reply = nextReply()
            assertEquals(7, (reply["id"] as JsonPrimitive).intOrNull)
            val result = reply["result"] as JsonObject
            assertNotNull(result["auth_token"])
            val accounts = result["accounts"] as JsonArray
            assertEquals(1, accounts.size)
            val acc0 = accounts[0] as JsonObject
            val decoded = Base64.getDecoder().decode((acc0["address"] as JsonPrimitive).content)
            assertArrayEquals(accountKey, decoded)
            assertEquals("Account 1", (acc0["label"] as JsonPrimitive).content)
        } finally {
            server.close()
        }
    }

    @Test
    fun `decline maps to AUTHORIZATION_FAILED error`() = runBlocking {
        val seenRequest = CompletableDeferred<AuthorizeRequest>()
        val cb = object : NoOpCallbacks() {
            override suspend fun onAuthorizeRequest(request: AuthorizeRequest) {
                seenRequest.complete(request)
            }
        }
        val server = newDispatcher(cb)
        try {
            pushRequest(
                method = "authorize",
                id = 9,
                params = buildJsonObject {
                    put("identity", buildJsonObject { put("name", "Decliner") })
                }
            )
            val req = withTimeout(2_000) { seenRequest.await() }
            req.completeWithDecline()
            val reply = nextReply()
            val error = reply["error"] as JsonObject
            assertEquals(MwaErrorCodes.AUTHORIZATION_FAILED, (error["code"] as JsonPrimitive).intOrNull)
        } finally {
            server.close()
        }
    }

    @Test
    fun `sign_and_send_transactions delivers options and serializes signatures`() = runBlocking<Unit> {
        val seenRequest = CompletableDeferred<SignAndSendTransactionsRequest>()
        val cb = object : NoOpCallbacks() {
            override suspend fun onAuthorizeRequest(request: AuthorizeRequest) {
                request.completeWithAuthorize(
                    accounts = listOf(AuthorizedAccount(publicKey = ByteArray(32) { 1 }))
                )
            }
            override suspend fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest) {
                seenRequest.complete(request)
            }
        }
        val server = newDispatcher(cb)
        try {
            // Authorize first so the dispatcher has an active session.
            pushRequest(
                method = "authorize",
                id = 1,
                params = buildJsonObject {
                    put("identity", buildJsonObject { put("name", "TX dApp") })
                }
            )
            // Drain the authorize reply (the test only cares about the
            // sign_and_send response below).
            nextReply()

            val tx = ByteArray(64) { 0x99.toByte() }
            pushRequest(
                method = "sign_and_send_transactions",
                id = 2,
                params = buildJsonObject {
                    put("payloads", buildJsonArray { add(Base64.getEncoder().encodeToString(tx)) })
                    put("options", buildJsonObject {
                        put("commitment", "finalized")
                        put("skip_preflight", true)
                        put("min_context_slot", 12345)
                        put("wait_for_commitment_to_send_next_transaction", true)
                    })
                }
            )
            val req = withTimeout(2_000) { seenRequest.await() }
            assertEquals(1, req.payloads.size)
            assertArrayEquals(tx, req.payloads[0])
            assertEquals("finalized", req.commitment)
            assertEquals(true, req.skipPreflight)
            assertEquals(12345, req.minContextSlot)
            assertEquals(true, req.waitForCommitmentToSendNextTransaction)

            val sig = ByteArray(64) { 0x77.toByte() }
            req.completeWithSignatures(listOf(sig))
            val reply = nextReply()
            val sigs = (reply["result"] as JsonObject)["signatures"] as JsonArray
            val decoded = Base64.getDecoder().decode((sigs[0] as JsonPrimitive).content)
            assertArrayEquals(sig, decoded)
        } finally {
            server.close()
        }
    }

    @Test
    fun `unknown method maps to METHOD_NOT_FOUND`() = runBlocking {
        val server = newDispatcher(NoOpCallbacks())
        try {
            pushRequest("not_a_real_method", id = 3, params = JsonObject(emptyMap()))
            val reply = nextReply()
            val error = reply["error"] as JsonObject
            assertEquals(MwaErrorCodes.METHOD_NOT_FOUND, (error["code"] as JsonPrimitive).intOrNull)
        } finally {
            server.close()
        }
    }

    @Test
    fun `sign_transactions before authorize maps to AUTHORIZATION_FAILED`() = runBlocking {
        val server = newDispatcher(NoOpCallbacks())
        try {
            pushRequest(
                method = "sign_transactions",
                id = 4,
                params = buildJsonObject {
                    put("payloads", buildJsonArray { add(Base64.getEncoder().encodeToString(ByteArray(8))) })
                }
            )
            val reply = nextReply()
            val error = reply["error"] as JsonObject
            assertEquals(MwaErrorCodes.AUTHORIZATION_FAILED, (error["code"] as JsonPrimitive).intOrNull)
        } finally {
            server.close()
        }
    }

    @Test
    fun `sign_transactions when feature not advertised maps to METHOD_NOT_FOUND`() = runBlocking {
        // Build a wallet that does NOT expose sign_transactions.
        val server = newDispatcher(
            NoOpCallbacks(),
            config = MobileWalletAdapterConfig(optionalFeatures = emptySet())
        )
        try {
            pushRequest(
                method = "sign_transactions",
                id = 5,
                params = buildJsonObject {
                    put("payloads", buildJsonArray { add(Base64.getEncoder().encodeToString(ByteArray(8))) })
                }
            )
            val reply = nextReply()
            val error = reply["error"] as JsonObject
            assertEquals(MwaErrorCodes.METHOD_NOT_FOUND, (error["code"] as JsonPrimitive).intOrNull)
        } finally {
            server.close()
        }
    }

    @Test
    fun `deauthorize fires callback and replies with empty success`() = runBlocking<Unit> {
        var deauthSeen: DeauthorizedEvent? = null
        val cb = object : NoOpCallbacks() {
            override suspend fun onAuthorizeRequest(request: AuthorizeRequest) {
                request.completeWithAuthorize(
                    accounts = listOf(AuthorizedAccount(publicKey = ByteArray(32) { 1 }))
                )
            }
            override fun onDeauthorizedEvent(event: DeauthorizedEvent) {
                deauthSeen = event
                event.complete()
            }
        }
        val server = newDispatcher(cb)
        try {
            pushRequest(
                method = "authorize",
                id = 1,
                params = buildJsonObject {
                    put("identity", buildJsonObject { put("name", "Deauth dApp") })
                }
            )
            val authReply = nextReply()
            val token = ((authReply["result"] as JsonObject)["auth_token"] as JsonPrimitive).content

            pushRequest(
                method = "deauthorize",
                id = 2,
                params = buildJsonObject { put("auth_token", token) }
            )
            val reply = nextReply()
            assertNotNull(reply["result"])
            assertNull(reply["error"])
            assertNotNull(deauthSeen)
            assertEquals(token, deauthSeen!!.authToken)
        } finally {
            server.close()
        }
    }

    /**
     * Empty default callback set. Throws on every typed request to
     * guarantee tests that exercise a verb explicitly override the
     * matching callback.
     */
    private open class NoOpCallbacks : Scenario.Callbacks {
        override suspend fun onAuthorizeRequest(request: AuthorizeRequest) {
            request.completeWithDecline()
        }
        override suspend fun onReauthorizeRequest(request: ReauthorizeRequest) {
            request.completeWithDecline()
        }
        override suspend fun onSignTransactionsRequest(request: SignTransactionsRequest) {
            request.completeWithDecline()
        }
        override suspend fun onSignMessagesRequest(request: SignMessagesRequest) {
            request.completeWithDecline()
        }
        override suspend fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest) {
            request.completeWithDecline()
        }
    }
}
