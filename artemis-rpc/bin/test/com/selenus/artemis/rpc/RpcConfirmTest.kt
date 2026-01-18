package com.selenus.artemis.rpc

import kotlin.test.Test
import kotlin.test.assertTrue

class RpcConfirmTest {

  @Test
  fun confirmTransactionReturnsTrueWhenFinalized() = kotlinx.coroutines.runBlocking {
    var calls = 0
    val client = JsonRpcClient("http://localhost:8899", transport = object : HttpTransport {
      override fun postJson(url: String, body: String, headers: Map<String, String>): HttpTransport.Response {
        calls += 1
        return if (calls < 2) {
          HttpTransport.Response(
            200,
            "{\"jsonrpc\":\"2.0\",\"result\":{\"value\":[{\"confirmationStatus\":\"processed\",\"err\":null}]},\"id\":1}"
          )
        } else {
          HttpTransport.Response(
            200,
            "{\"jsonrpc\":\"2.0\",\"result\":{\"value\":[{\"confirmationStatus\":\"finalized\",\"err\":null}]},\"id\":1}"
          )
        }
      }
    })
    val api = RpcApi(client)
    val ok = api.confirmTransaction("sig", maxAttempts = 3, sleepMs = 1)
    assertTrue(ok)
  }
}
