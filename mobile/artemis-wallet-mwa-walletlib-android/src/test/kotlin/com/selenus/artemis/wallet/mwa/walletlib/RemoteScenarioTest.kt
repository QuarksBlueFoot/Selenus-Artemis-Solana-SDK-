package com.selenus.artemis.wallet.mwa.walletlib

import com.selenus.artemis.wallet.mwa.protocol.Aes128Gcm
import com.selenus.artemis.wallet.mwa.protocol.Base64Url
import com.selenus.artemis.wallet.mwa.protocol.EcP256
import com.selenus.artemis.wallet.mwa.protocol.HkdfSha256
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.security.KeyPair
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

class RemoteScenarioTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reflector URL uses WSS reflect path and base64url id`() {
        val remote = AssociationUri.Remote(
            associationPublicKey = ByteArray(65).also { it[0] = 4 },
            reflectorAuthority = "reflector.example.com:443",
            reflectorId = byteArrayOf(1, 2, 3, 4),
            protocolVersions = listOf(AssociationUri.ProtocolVersion.V1)
        )

        assertEquals(
            "wss://reflector.example.com:443/reflect?id=${Base64Url.encode(byteArrayOf(1, 2, 3, 4))}",
            ReflectorWebSocketClient.reflectorUrlFor(remote)
        )
    }

    @Test(timeout = 30_000)
    fun `remote scenario reuses HELLO and wallet JSON-RPC server over reflected transport`() = runBlocking<Unit> {
        val pair = InMemoryTransportPair()
        val associationKeypair = EcP256.generateKeypair()
        val associationPublicKey = EcP256.x962Uncompressed(associationKeypair.public)
        val remote = AssociationUri.Remote(
            associationPublicKey = associationPublicKey,
            reflectorAuthority = "reflector.example.com",
            reflectorId = ByteArray(16) { it.toByte() },
            protocolVersions = listOf(AssociationUri.ProtocolVersion.V1)
        )

        val approvedAccountKey = ByteArray(32) { 0x44 }
        val authorizeSeen = CompletableDeferred<AuthorizeRequest>()
        val callbacks = object : Scenario.Callbacks {
            override suspend fun onAuthorizeRequest(request: AuthorizeRequest) {
                authorizeSeen.complete(request)
                request.completeWithAuthorize(
                    accounts = listOf(
                        AuthorizedAccount(
                            publicKey = approvedAccountKey,
                            accountLabel = "Remote Account",
                            chains = listOf("solana:mainnet")
                        )
                    )
                )
            }

            override suspend fun onReauthorizeRequest(request: ReauthorizeRequest) {
                request.completeWithReauthorize()
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

        val scenario = RemoteScenario.forTransport(
            associationUri = remote,
            config = MobileWalletAdapterConfig(),
            authRepository = InMemoryAuthRepository(AuthIssuerConfig(name = "Remote Wallet")),
            transport = pair.walletEnd
        )

        val scenarioStart = async(Dispatchers.IO) { scenario.start(callbacks) }
        val dapp = connectDapp(pair.dappEnd, associationKeypair)

        try {
            val sessionId = withTimeout(5_000) { scenarioStart.await() }
            assertNotNull(sessionId)
            assertEquals(ScenarioState.ServingClients, scenario.state.value)

            val authParams = buildJsonObject {
                put("identity", buildJsonObject {
                    put("name", "Remote dApp")
                    put("uri", "https://remote.dapp")
                })
                put("chain", "solana:mainnet")
            }
            val response = dapp.sendJsonRpc("authorize", authParams)
            val result = response["result"]!!.jsonObject
            val account = result["accounts"]!!.jsonArray[0].jsonObject
            val accountBytes = Base64.getDecoder().decode(account["address"]!!.jsonPrimitive.content)
            assertArrayEquals(approvedAccountKey, accountBytes)

            val request = withTimeout(2_000) { authorizeSeen.await() }
            assertEquals("Remote dApp", request.identityName)
            assertEquals("solana:mainnet", request.chain)
        } finally {
            dapp.close()
            scenario.close()
        }
    }

    private suspend fun connectDapp(
        transport: InMemoryTransportPair.DappTransport,
        associationKeypair: KeyPair
    ): DappHarness {
        val ephemeral = EcP256.generateKeypair()
        val qd = EcP256.x962Uncompressed(ephemeral.public)
        val signature = EcP256.signP1363(associationKeypair.private, qd)
        transport.send(qd + signature)

        val helloRsp = withTimeout(5_000) { transport.incoming.receive() }
        val qw = helloRsp.copyOfRange(0, 65)
        val walletEphemeralPublic = EcP256.publicKeyFromX962(qw)
        val ikm = EcP256.ecdhSecret(ephemeral.private, walletEphemeralPublic)
        val salt = EcP256.x962Uncompressed(associationKeypair.public)
        val cipher = Aes128Gcm(HkdfSha256.derive(ikm = ikm, salt = salt, length = 16))

        var nextReceiveSeq = 1
        if (helloRsp.size > 65) {
            val propsPacket = helloRsp.copyOfRange(65, helloRsp.size)
            cipher.decrypt(expectedSeq = 1, packet = propsPacket)
            nextReceiveSeq = 2
        }
        return DappHarness(transport, cipher, nextReceiveSeq, json)
    }

    private class DappHarness(
        private val transport: InMemoryTransportPair.DappTransport,
        private val cipher: Aes128Gcm,
        initialReceiveSeq: Int,
        private val json: Json
    ) {
        private val sendSeq = AtomicInteger(1)
        private val recvSeq = AtomicInteger(initialReceiveSeq)
        private val nextId = AtomicInteger(1)

        suspend fun sendJsonRpc(method: String, params: JsonElement): JsonObject {
            val request = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", nextId.getAndIncrement())
                put("method", method)
                put("params", params)
            }
            val payload = json.encodeToString(JsonObject.serializer(), request).encodeToByteArray()
            transport.send(cipher.encrypt(sendSeq.getAndIncrement(), payload))
            val packet = withTimeout(5_000) { transport.incoming.receive() }
            val plain = cipher.decrypt(recvSeq.getAndIncrement(), packet)
            return json.parseToJsonElement(plain.decodeToString()).jsonObject
        }

        fun close() = transport.close()
    }
}