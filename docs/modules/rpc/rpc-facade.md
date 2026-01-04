# RPC facade

`RpcFacade` organizes `RpcApi` into smaller surfaces:

- core: account, send, confirm, raw calls
- blocks: blocks and signatures
- tokens: token queries
- stake: stake and vote queries

```kotlin
val facade = RpcFacade(RpcApi(JsonRpcClient(rpcUrl)))
val bh = facade.core.getLatestBlockhash()
```
