# RPC transport

Artemis RPC uses OkHttp by default, but supports custom transports so you can use Ktor or your own networking stack.

## HttpTransport

`com.selenus.artemis.rpc.HttpTransport` is a small blocking interface:

- `postJson(url, body, headers) -> Response`

Apps should call RPC methods from a background thread (Dispatchers.IO, executor, etc).

## Ktor usage

Artemis does not require Ktor. You can bridge your Ktor client:

```kotlin
val transport = HttpTransports.ktorBridge { url, body, headers ->
  // Use your Ktor HttpClient here (blocking wrapper or runBlocking in your layer).
  // Return Pair(statusCode, responseBody)
  200 to "{ \"jsonrpc\": \"2.0\", \"result\":  }"
}

val rpc = RpcApi(JsonRpcClient(endpoint = rpcUrl, transport = transport))
```
