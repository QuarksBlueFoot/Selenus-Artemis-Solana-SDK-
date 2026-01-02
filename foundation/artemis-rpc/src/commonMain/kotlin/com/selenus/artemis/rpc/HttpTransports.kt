package com.selenus.artemis.rpc

/**
 * HttpTransports
 *
 * Helpers for creating HttpTransport instances.
 */
object HttpTransports {

  fun fromPostJson(
    fn: (url: String, body: String, headers: Map<String, String>) -> HttpTransport.Response
  ): HttpTransport {
    return object : HttpTransport {
      override fun postJson(url: String, body: String, headers: Map<String, String>): HttpTransport.Response {
        return fn(url, body, headers)
      }
    }
  }

  /**
   * Ktor bridge helper (no Ktor dependency here).
   *
   * Implement `postJson` using your configured Ktor HttpClient.
   * Return Pair(statusCode, responseBody).
   */
  fun ktorBridge(
    postJson: (url: String, body: String, headers: Map<String, String>) -> Pair<Int, String>
  ): HttpTransport {
    return fromPostJson { url, body, headers ->
      val (code, respBody) = postJson(url, body, headers)
      HttpTransport.Response(code = code, body = respBody)
    }
  }
}
