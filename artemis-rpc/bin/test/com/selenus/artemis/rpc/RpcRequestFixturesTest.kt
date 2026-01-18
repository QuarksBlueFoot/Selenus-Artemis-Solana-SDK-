package com.selenus.artemis.rpc

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.*

class RpcRequestFixturesTest {

  @Test
  fun buildsGetLatestBlockhashPayload() = kotlinx.coroutines.runBlocking {
    val client = JsonRpcClient("http://localhost:8899", transport = object : HttpTransport {
      override fun postJson(url: String, body: String, headers: Map<String, String>): HttpTransport.Response {
        // Validate request shape, then respond with minimal valid payload.
        assertTrue(body.contains("\"method\":\"getLatestBlockhash\""))
        assertTrue(body.contains("\"commitment\":\"finalized\""))
        return HttpTransport.Response(
          200,
          "{\"jsonrpc\":\"2.0\",\"result\":{\"value\":{\"blockhash\":\"abc\",\"lastValidBlockHeight\":1}},\"id\":1}"
        )
      }
    })
    val api = RpcApi(client)
    val res = api.getLatestBlockhash()
    assertTrue(res.blockhash == "abc")
  }

  @Test
  fun buildsSendTransactionPayload() = kotlinx.coroutines.runBlocking { // modified
    val client = JsonRpcClient("http://localhost:8899", transport = object : HttpTransport {
      override fun postJson(url: String, body: String, headers: Map<String, String>): HttpTransport.Response {
        assertTrue(body.contains("\"method\":\"sendTransaction\""))
        assertTrue(body.contains("\"encoding\":\"base64\""))
        return HttpTransport.Response(
          200,
          "{\"jsonrpc\":\"2.0\",\"result\":\"sig\",\"id\":1}"
        )
      }
    })
    val api = RpcApi(client)
    val sig = api.sendTransaction("dHh=", skipPreflight = true, maxRetries = 3)
    assertTrue(sig == "sig")
  }
}
