package com.solana.rpccore

import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import com.solana.rpc.AccountInfo
import com.solana.rpc.Commitment
import com.solana.rpc.Encoding
import com.solana.rpc.SolanaAccount
import com.solana.rpc.SolanaRpcClient
import com.solana.rpc.TransactionOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RpcCoreCompatTest {

    @Test
    fun `Rpc20Driver serializes request and decodes typed result`() = runBlocking {
        val transport = RecordingNetworkDriver("""{"jsonrpc":"2.0","id":"42","result":"ok"}""")
        val driver = Rpc20Driver(url = "https://rpc.example", networkDriver = transport)

        val response = driver.makeRequest(
            JsonRpc20Request(method = "getHealth", id = "42"),
            String.serializer()
        )

        assertEquals("ok", response.result)
        assertNull(response.error)
        assertEquals("42", response.id)
        val request = assertNotNull(transport.lastRequest)
        assertEquals("https://rpc.example", request.url)
        assertEquals("POST", request.method)
        assertEquals("application/json", request.properties["Content-Type"])
        val requestBody = assertNotNull(request.body)
        assertTrue(requestBody.contains("\"method\":\"getHealth\""))
        assertTrue(requestBody.contains("\"id\":\"42\""))
    }

    @Test
    fun `Rpc20Driver preserves JSON-RPC errors`() = runBlocking {
        val transport = RecordingNetworkDriver(
            """{"jsonrpc":"2.0","id":"7","error":{"code":-32002,"message":"node unhealthy"}}"""
        )
        val driver = Rpc20Driver(url = "https://rpc.example", networkDriver = transport)

        val response = driver.makeRequest(
            JsonRpc20Request(method = "getBalance", id = "7"),
            Long.serializer()
        )

        assertNull(response.result)
        assertEquals(-32002, response.error?.code)
        assertEquals("node unhealthy", response.error?.message)
        assertEquals("7", response.id)
        assertEquals("2.0", response.jsonrpc)
    }

    @Test
    fun `rpc-core model types preserve upstream source-compatible shape`() {
        val options = TransactionOptions(
            commitment = Commitment.CONFIRMED,
            encoding = Encoding.BASE64,
            skipPreflight = true,
            preflightCommitment = Commitment.PROCESSED,
            maxRetries = 3,
            minContextSlot = 99L,
            timeout = 5_000L
        )

        assertEquals("confirmed", options.commitment.value)
        assertEquals("base64", options.encoding.value)
        assertTrue(options.skipPreflight)
        assertEquals(3, options.maxRetries)
        assertEquals("processed", options.preflightCommitment?.value)

        val account = AccountInfo(
            data = byteArrayOf(1, 2, 3),
            executable = false,
            lamports = 10L,
            owner = "11111111111111111111111111111111",
            rentEpoch = 0L,
            space = 3L
        )
        assertContentEquals(byteArrayOf(1, 2, 3), account.data)
        assertFalse(account.executable)

        val solanaAccount = SolanaAccount(
            lamports = 10L,
            owner = SolanaPublicKey(ByteArray(32)),
            data = byteArrayOf(4, 5),
            executable = false,
            rentEpoch = 1L,
            space = 2L
        )
        assertEquals(solanaAccount, solanaAccount.copy(data = byteArrayOf(4, 5)))
        assertEquals(solanaAccount.hashCode(), solanaAccount.copy(data = byteArrayOf(4, 5)).hashCode())
    }

    @Test
    fun `SolanaRpcClient constructors accept rpc-core drivers without network work`() {
        val transport = RecordingNetworkDriver("""{"jsonrpc":"2.0","id":"1","result":"ok"}""")
        val driver = Rpc20Driver(url = "https://rpc.example", networkDriver = transport)

        val fromDriver = SolanaRpcClient(driver)
        val fromUrl = SolanaRpcClient("https://rpc.example", transport)

        assertNotNull(fromDriver.asArtemis())
        assertNotNull(fromUrl.asArtemis())
        assertNull(transport.lastRequest)
    }

    private class RecordingNetworkDriver(private val response: String) : HttpNetworkDriver {
        var lastRequest: HttpRequest? = null
            private set

        override suspend fun makeHttpRequest(request: HttpRequest): String {
            lastRequest = request
            return response
        }
    }
}
