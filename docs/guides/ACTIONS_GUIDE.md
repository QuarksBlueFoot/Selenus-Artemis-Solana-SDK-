# artemis-actions

Native Kotlin implementation of the [Solana Actions specification](https://solana.com/docs/advanced/actions). Parse action and Blink URLs, fetch action metadata (`GET /action`), execute an action to get a signable transaction (`POST /action`), follow the action-chaining callback, and validate an `actions.json` rules file for a domain.

Source: [../../ecosystem/artemis-actions/](../../ecosystem/artemis-actions/). Public entry point is `ActionsClient`.

## Install

```kotlin
dependencies {
    implementation("xyz.selenus:artemis-actions:2.3.0")
}
```

## Create a client

```kotlin
import com.selenus.artemis.actions.ActionsClient
import com.selenus.artemis.actions.ActionsConfig

val actions = ActionsClient.create()

// Or with custom timeouts
val actions = ActionsClient.create(
    ActionsConfig(
        connectTimeoutMs = 5_000,
        readTimeoutMs    = 15_000,
        writeTimeoutMs   = 15_000,
        validateSsl      = true
    )
)
```

If you already manage an HTTP client, pass it as the first argument: `ActionsClient.create(httpClient, config)`.

## Parse an action or Blink URL

`parseActionUrl` tells you what kind of URL you're looking at so you can route the rest accordingly:

```kotlin
import com.selenus.artemis.actions.ActionUrlType

val info = actions.parseActionUrl(userInput)
when (info.type) {
    ActionUrlType.ACTION_SCHEME -> handleScheme(info.resolvedPath)  // "solana-action:..."
    ActionUrlType.BLINK         -> handleBlink(info.resolvedPath)   // dial.to / actions.solana.com / blink.to
    ActionUrlType.DIRECT_ACTION -> handleDirect(info.resolvedPath)  // /api/actions/... or actions.json hosts
    ActionUrlType.SOLANA_PAY    -> /* Solana Pay URI */ TODO()
    ActionUrlType.UNKNOWN       -> reject("Not a recognized Actions URL")
}
```

`actions.isBlink(url)` is a quick boolean shortcut for the common hosted-Blink case.

## Fetch action metadata

```kotlin
val action = actions.getAction(url)

println(action.title)
println(action.description)
println(action.icon)           // URL (validator checks it starts with http)
println(action.label)          // default button label

action.links?.actions?.forEach { linked ->
    println("${linked.label} -> ${linked.href}")
    linked.parameters?.forEach { p ->
        println("  ${p.name} (${p.type ?: ActionParameterType.TEXT}) required=${p.required == true}")
    }
}
```

`ActionGetResponse` mirrors the spec: `type`, `title`, `icon`, `description`, `label`, optional `disabled`, optional `error: ActionError`, optional `links: ActionLinks` containing `actions: List<LinkedAction>?` and an optional `next: NextAction` for inline chaining.

### Validate before you render

Before you paint a Blink card, run the response through `validateAction`:

```kotlin
val validation = actions.validateAction(action)
if (!validation.isValid) {
    Log.w("actions", "invalid action: ${validation.issues.joinToString()}")
    return
}
```

The checker flags blank title / icon / description, non-URL icons, disabled actions without an error message, blank linked-action labels, and blank parameter names.

### UI shortcut: blink preview

`createBlinkPreview` squashes the metadata into a flat UI struct so the card view has no null-walking:

```kotlin
val preview = actions.createBlinkPreview(action)
BlinkCard(
    title       = preview.title,
    description = preview.description,
    iconUrl     = preview.iconUrl,
    primary     = preview.primaryActionLabel,
    disabled    = preview.isDisabled,
    error       = preview.errorMessage,
    hasForm     = preview.hasFormInputs,
    count       = preview.actionCount
)
```

## Execute an action

Two entry points. Use `executeAction(url, block)` when you already have the final action URL (for example one of the linked-action hrefs). Use `executeLinkedAction(action, linkedAction, block)` when you want Artemis to resolve the URL from the parent and substitute form inputs into templated paths.

### Simple execute

```kotlin
import com.selenus.artemis.actions.ActionExecuteBuilder

val post = actions.executeAction(action.links!!.actions!!.first().href) {
    account(wallet.publicKey)        // Pubkey or base58 String
}

// post.transaction is a base64 serialized transaction
// post.message is an optional human string to show the user
// post.links?.next is the chaining hint (may be null)
```

### Execute with form parameters

`ActionExecuteBuilder` uses method-call setters. Three overloads of `input(...)` cover strings, numbers, and booleans. `inputs(...)` takes a varargs of pairs if you have them ready as a list.

```kotlin
val swap = action.links!!.actions!!.first { it.parameters != null }

val post = actions.executeLinkedAction(action, swap) {
    account(wallet.publicKey)
    input("amount", 100)             // Number -> "100"
    input("slippage", "0.5")         // String
    input("confirm", true)           // Boolean -> "true"
    // Or: inputs("amount" to "100", "slippage" to "0.5")
}
```

The builder only emits a `data` block in the POST body when at least one input is present (absent when `inputs` is empty), matching the spec.

### Sign and send the transaction

```kotlin
import com.selenus.artemis.runtime.PlatformBase64
import com.selenus.artemis.wallet.SignTxRequest

val txBytes = PlatformBase64.decode(post.transaction)
val signed  = artemis.wallet.signMessage(txBytes, SignTxRequest(purpose = "blink"))
val signature = artemis.rpc.sendRawTransaction(signed)

post.message?.let { toast(it) }  // Spec recommends showing this in the result sheet
```

## Action chaining

If `post.links?.next` is set, the provider wants to run another step after confirmation. `confirmTransaction` handles both variants (`PostAction` that needs a callback, and `InlineAction` that is already embedded):

```kotlin
import com.selenus.artemis.actions.NextActionResult

when (val next = actions.confirmTransaction(post, signature)) {
    is NextActionResult.Continue -> {
        // next.action is an ActionGetResponse you can render like a normal step
        renderStep(next.action)
    }
    NextActionResult.Complete -> {
        // End of the chain
        toast("Done")
    }
}
```

For a `PostAction.next.href`, `confirmTransaction` POSTs `{ "signature": "<sig>" }` to the callback URL and returns the parsed `ActionGetResponse` for the next step.

## Identity verification

If you want the provider to see an identity header (for example to track which app submitted a swap), use `postActionWithIdentity`. You supply a pubkey and a signer lambda; Artemis builds the `X-Action-Identity`, `X-Action-Signature`, and `X-Action-Timestamp` headers per the spec:

```kotlin
import com.selenus.artemis.actions.ActionIdentity

val identity = ActionIdentity(
    publicKey = wallet.publicKey.toBase58(),
    name      = "MyApp"
)

val request = ActionExecuteBuilder().apply {
    account(wallet.publicKey)
    input("amount", 100)
}.build()

val post = actions.postActionWithIdentity(
    url      = action.links!!.actions!!.first().href,
    request  = request,
    identity = identity,
    signer   = { payload -> keypair.sign(payload) }
)
```

## actions.json and allowlist

A domain publishes a [`/actions.json`](https://solana.com/docs/advanced/actions#actions-json) file that maps URL patterns to action endpoints. Use `getActionsJson` to fetch it and `isActionAllowed` to check whether a candidate URL is covered by the rules:

```kotlin
val rules = actions.getActionsJson("example.com")
rules?.rules?.forEach { println("${it.pathPattern} -> ${it.apiPath ?: "(same path)"}") }

val ok = actions.isActionAllowed("https://example.com/donate")
if (!ok) reject("Action not allowed by domain rules")
```

`getActionsJson` returns `null` on any network or parse error (never throws); `isActionAllowed` is conservative and returns `false` when no rules file is available.

## QR codes and deep links

For mobile flows, wrap an action URL in the `solana-action:` scheme and either render it as a QR code payload or hand it to `Intent.ACTION_VIEW`:

```kotlin
val qr = actions.generateActionQrCode(actionUrl)
// qr.data is the `solana-action:<percent-encoded-url>` payload you encode
// qr.actionUrl is the original URL
// qr.protocol is "solana-action"

val intent = actions.createDeepLinkIntent(actionUrl)
// intent.uri is "solana-action:<percent-encoded-url>"
// intent.schemes lists which deep-link schemes you should register in AndroidManifest.xml
```

Register the intent filter in your manifest (see [../MOBILE_APP_GUIDE.md](../MOBILE_APP_GUIDE.md#10-manifest-deep-links-and-sign-in-with-solana)).

## Error handling

Non-2xx responses raise `ActionException(code, message, details)`. The message is the HTTP status; `details` is the raw response body when the server provided one. Catch and surface them to the user:

```kotlin
val action = try {
    actions.getAction(url)
} catch (e: ActionException) {
    log("actions failed ${e.code}: ${e.details ?: "no details"}")
    return
}
```

## Status

Listed as `In Progress` in [../PARITY_MATRIX.md](../PARITY_MATRIX.md). GET/POST, parameter builders, action-chaining callback, identity-verified POST, actions.json, and the Blink/QR/deep-link helpers are implemented. Not yet on the `Verified` tier: interactive action replays, the `next.inline` case in the execute-time spec revisions, and cross-origin `actions.json` delegation. Tests live at [../../ecosystem/artemis-actions/src/jvmTest/kotlin/com/selenus/artemis/actions/ActionsModuleTest.kt](../../ecosystem/artemis-actions/src/jvmTest/kotlin/com/selenus/artemis/actions/ActionsModuleTest.kt).

## License

Apache License 2.0. See [../../LICENSE](../../LICENSE).
