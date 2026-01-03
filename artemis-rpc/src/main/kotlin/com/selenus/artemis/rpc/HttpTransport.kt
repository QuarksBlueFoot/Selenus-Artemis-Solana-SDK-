package com.selenus.artemis.rpc

/**
 * HttpTransport
 *
 * Small transport abstraction so apps can plug in Ktor, OkHttp, or custom stacks.
 *
 * This interface is blocking by design to avoid pulling coroutine dependencies into core RPC.
 * Mobile apps can call it from Dispatchers.IO or their own executor.
 */
interface HttpTransport {
  data class Response(val code: Int, val body: String, val headers: Map<String, String> = emptyMap())

  fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): Response
}
