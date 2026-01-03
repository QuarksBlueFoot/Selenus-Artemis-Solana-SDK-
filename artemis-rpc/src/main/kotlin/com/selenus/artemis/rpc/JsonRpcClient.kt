package com.selenus.artemis.rpc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicLong

/**
 * JsonRpcClient
 *
 * - OkHttp by default
 * - optional HttpTransport override for Ktor or custom networking
 * - retry/backoff for mobile reliability
 */
class JsonRpcClient(
  private val endpoint: String,
  private val http: OkHttpClient = OkHttpClient(),
  private val transport: HttpTransport? = null,
  private val config: RpcClientConfig = RpcClientConfig(),
  private val backoff: BackoffStrategy = ExponentialJitterBackoff
) {
  private val id = AtomicLong(1)
  private val mediaType = "application/json".toMediaType()

  fun call(method: String, params: JsonElement? = null): JsonObject {
    val payload = buildJsonObject {
      put("jsonrpc", "2.0")
      put("id", id.getAndIncrement())
      put("method", method)
      if (params != null) put("params", params)
    }

    var attempt = 1
    var lastErr: Throwable? = null

    while (attempt <= config.maxAttempts) {
      try {
        val bodyText = doPost(payload.toString())
        val json = Json.parseToJsonElement(bodyText).jsonObject
        json["error"]?.let { throw RpcException(it.toString()) }
        return json
      } catch (t: Throwable) {
        lastErr = t
        if (!shouldRetry(t, attempt)) break
        val sleep = backoff.backoffMs(attempt, config)
        try { Thread.sleep(sleep) } catch (_: Throwable) { }
        attempt += 1
      }
    }

    throw (lastErr ?: RpcException("unknown_rpc_error"))
  }

  private fun doPost(body: String): String {
    if (transport != null) {
      val resp = transport.postJson(
        url = endpoint,
        body = body,
        headers = mapOf("Content-Type" to "application/json")
      )
      if (resp.code < 200 || resp.code >= 300) throw RpcHttpException(resp.code)
      return resp.body
    }

    val req = Request.Builder()
      .url(endpoint)
      .post(body.toRequestBody(mediaType))
      .build()

    http.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) throw RpcHttpException(resp.code)
      return resp.body?.string() ?: throw RpcException("Empty response body")
    }
  }

  private fun shouldRetry(t: Throwable, attempt: Int): Boolean {
    if (attempt >= config.maxAttempts) return false
    if (t is RpcHttpException) {
      if (t.code == 429) return config.retryOnHttp429
      if (t.code >= 500) return config.retryOnHttp5xx
      return false
    }
    val msg = (t.message ?: "").lowercase()
    if (msg.contains("timeout")) return config.retryOnTimeout
    return false
  }
}

class RpcException(message: String) : RuntimeException(message)
class RpcHttpException(val code: Int) : RuntimeException("HTTP $code")
