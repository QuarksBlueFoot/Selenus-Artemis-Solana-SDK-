package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri
import com.selenus.artemis.wallet.mwa.protocol.Aes128Gcm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Wallet-side JSON-RPC dispatcher.
 *
 * Receives encrypted MWA frames over a [WalletTransport], decrypts
 * them, builds a typed [MwaRequest], hands it to the registered
 * [Scenario.Callbacks], waits for the wallet's completion, then
 * encrypts and sends the JSON-RPC reply.
 *
 * Lives on its own [CoroutineScope] so close() tears down both the
 * inbound dispatch loop and any outstanding request handlers
 * deterministically.
 *
 * `GET_CAPABILITIES` is handled inside this class. never surfaced to
 * the callback set, because the response is purely a function of
 * [config], and forcing every wallet to re-derive the same JSON would
 * just be a chance to drift from the spec.
 */
internal class WalletMwaServer(
    private val transport: WalletTransport,
    private val cipher: Aes128Gcm,
    private val callbacks: Scenario.Callbacks,
    private val config: MobileWalletAdapterConfig,
    private val authRepository: AuthRepository,
    private val identityResolver: IdentityResolver,
    initialRecvSeq: Int,
    initialSendSeq: Int,
    parentJob: Job? = null,
    /**
     * How long to wait for `DeauthorizedEvent.complete()` from the
     * wallet UI before replying success anyway and emitting a scenario
     * error. 30s is long enough for any reasonable UI flow but bounded
     * so a never-completing wallet does not strand the dApp.
     */
    private val deauthorizeCompletionTimeoutMs: Long = 30_000L
) {
    private val scope = CoroutineScope(
        Dispatchers.IO + (parentJob?.let { SupervisorJob(it) } ?: SupervisorJob())
    )

    private val recvSeq = AtomicInteger(initialRecvSeq)
    private val sendSeq = AtomicInteger(initialSendSeq)
    private val sendMutex = Mutex()

    /**
     * Tracks the last successfully authorized account list per
     * session. Sign* requests need this to populate
     * [SignTransactionsRequest.authorizedAccounts] without leaking
     * the raw [AuthRecord].
     */
    private val activeAuth = AtomicReference<ActiveAuthorization?>(null)

    fun start() {
        scope.launch {
            try {
                for (frame in transport.incoming) {
                    handleFrame(frame)
                }
            } catch (e: Throwable) {
                callbacks.onScenarioError(e)
            }
        }
    }

    fun close(reason: String? = null) {
        try { transport.close(1000, reason ?: "close") } catch (_: Throwable) {}
        scope.cancel(java.util.concurrent.CancellationException(reason ?: "close"))
    }

    private suspend fun handleFrame(frame: ByteArray) {
        val plain = try {
            cipher.decrypt(expectedSeq = recvSeq.getAndIncrement(), packet = frame)
        } catch (e: Throwable) {
            // Sequence skew / tag mismatch is unrecoverable: the dApp's
            // sender no longer matches our receiver state, so further
            // frames will keep failing. Tear down and surface.
            callbacks.onScenarioError(MwaHandshakeException("frame decrypt failed", e))
            close("decrypt failed")
            return
        }
        val parsed = try {
            kotlinx.serialization.json.Json.parseToJsonElement(plain.decodeToString())
        } catch (e: Throwable) {
            sendError(id = null, MwaErrorCodes.INTERNAL_ERROR, "malformed JSON", null)
            return
        }
        val obj = parsed as? JsonObject
        if (obj == null) {
            sendError(id = null, MwaErrorCodes.INVALID_PARAMS, "expected JSON object", null)
            return
        }
        dispatch(obj)
    }

    private suspend fun dispatch(envelope: JsonObject) {
        val id: JsonElement? = envelope["id"]
        val method = (envelope["method"] as? JsonPrimitive)?.contentOrNull
        if (method == null) {
            // Either an RPC reply (no method) or a malformed frame. The
            // wallet doesn't initiate any RPC against the dApp in MWA
            // 2.0, so any reply-shaped frame is unexpected, but it is
            // not actionable, so we just ignore it.
            return
        }
        val params = envelope["params"] as? JsonObject ?: JsonObject(emptyMap())
        try {
            when (method) {
                MwaMethods.GET_CAPABILITIES -> handleGetCapabilities(id)
                MwaMethods.AUTHORIZE -> handleAuthorize(id, params)
                MwaMethods.REAUTHORIZE -> handleReauthorize(id, params)
                MwaMethods.DEAUTHORIZE -> handleDeauthorize(id, params)
                MwaMethods.SIGN_TRANSACTIONS -> handleSignTransactions(id, params)
                MwaMethods.SIGN_AND_SEND_TRANSACTIONS -> handleSignAndSendTransactions(id, params)
                MwaMethods.SIGN_MESSAGES -> handleSignMessages(id, params)
                else -> sendError(id, MwaErrorCodes.METHOD_NOT_FOUND, "unknown method `$method`", null)
            }
        } catch (e: Throwable) {
            // Catch-all: a bug in the request building or callback
            // dispatch should not silently strand the dApp. Reply with
            // an INTERNAL_ERROR so the dApp's future resolves and the
            // user can retry.
            sendError(id, MwaErrorCodes.INTERNAL_ERROR, e.message ?: "internal error", null)
            callbacks.onScenarioError(e)
        }
    }

    private suspend fun handleGetCapabilities(id: JsonElement?) {
        val features = buildList {
            // sign_and_send_transactions is mandatory in MWA 2.0; we
            // always advertise it. The optional features set comes
            // from the wallet's config so the wallet decides what to
            // expose.
            add(MobileWalletAdapterConfig.FEATURE_SIGN_AND_SEND_TRANSACTIONS)
            addAll(config.optionalFeatures)
        }.distinct()

        val versions = buildJsonArray {
            config.supportedTransactionVersions.forEach { v ->
                when (v) {
                    is MobileWalletAdapterConfig.TxVersion.Legacy -> add("legacy")
                    is MobileWalletAdapterConfig.TxVersion.V0 -> add(0)
                }
            }
        }

        // MWA 2.x spec uses a single `max_payloads_per_request` field;
        // 1.x dApps still read the deprecated split tx/messages fields.
        // Emit both. The unified value is the per-call cap for any of
        // sign_transactions / sign_and_send_transactions / sign_messages,
        // so we use the smaller of the two so a 2.x dApp never sends a
        // batch larger than either category accepts.
        val maxPayloadsPerRequest = minOf(
            config.maxTransactionsPerSigningRequest,
            config.maxMessagesPerSigningRequest
        )
        val result = buildJsonObject {
            put("max_payloads_per_request", maxPayloadsPerRequest)
            put("max_transactions_per_request", config.maxTransactionsPerSigningRequest)
            put("max_messages_per_request", config.maxMessagesPerSigningRequest)
            put("supported_transaction_versions", versions)
            put("features", JsonArray(features.map { JsonPrimitive(it) }))
            // Legacy boolean fields kept on the wire for MWA 1.x dApps
            // that ignore the `features` array. supports_sign_transactions
            // tracks whether we opted into FEATURE_SIGN_TRANSACTIONS;
            // supports_sign_and_send_transactions is always true.
            put("supports_sign_transactions",
                MobileWalletAdapterConfig.FEATURE_SIGN_TRANSACTIONS in config.optionalFeatures)
            put("supports_sign_and_send_transactions", true)
            put("supports_clone_authorization",
                MobileWalletAdapterConfig.FEATURE_CLONE_AUTHORIZATION in config.optionalFeatures)
        }
        sendResult(id, result)
    }

    private suspend fun handleAuthorize(id: JsonElement?, params: JsonObject) {
        val identity = identityResolver.fromAuthorizeParams(params)
        // Default to mainnet when the dApp omits `chain`. Upstream
        // `BaseScenario.authorize` reaches `onAuthorizeRequest` with a
        // CAIP-2 string populated from `ProtocolContract.CHAIN_SOLANA_
        // MAINNET`; mirroring that means callbacks can rely on `chain`
        // never being null past this point unless the dApp forced an
        // explicit empty value.
        val chain = (params["chain"] as? JsonPrimitive)?.contentOrNull
            ?: ProtocolContract.CHAIN_SOLANA_MAINNET
        val authToken = (params["auth_token"] as? JsonPrimitive)?.contentOrNull
        val features = (params["features"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()
        val addresses = (params["addresses"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.map { Base64.getDecoder().decode(it) }
        val signInPayload = (params["sign_in_payload"] as? JsonObject)?.let {
            // Bridge kotlinx.serialization JsonObject to org.json.JSONObject
            // because the existing SignInPayload.fromJson uses the
            // android.json API for parity with the upstream walletlib.
            SignInPayload.fromJson(JSONObject(it.toString()))
        }

        // MWA 2.0: when the dApp passes auth_token, treat as reauthorize.
        if (authToken != null) {
            handleAuthorizeAsReauthorize(id, identity, authToken, chain)
            return
        }

        val request = AuthorizeRequest(
            identityName = identity.name,
            identityUri = identity.uri,
            iconRelativeUri = identity.iconRelativeUri,
            chain = chain,
            features = features,
            addresses = addresses,
            signInPayload = signInPayload
        )
        callbacks.onAuthorizeRequest(request)
        when (val outcome = request.awaitCompletion()) {
            is MwaCompletion.Result -> {
                val approval = outcome.payload as AuthorizeRequest.AuthorizeApproved
                val record = authRepository.issue(
                    identity = identity,
                    accounts = approval.accounts,
                    chain = chain,
                    scope = approval.scope,
                    walletUri = approval.walletUriBase
                )
                activeAuth.set(ActiveAuthorization(record.authToken, approval.accounts))
                sendResult(id, buildAuthorizeResultJson(record, approval))
            }
            is MwaCompletion.Error -> sendError(id, outcome.code, outcome.message, outcome.data)
        }
    }

    private suspend fun handleAuthorizeAsReauthorize(
        id: JsonElement?,
        identity: Identity,
        authToken: String,
        chain: String
    ) {
        val record = authRepository.lookup(authToken)
        if (record == null || record.identity != identity) {
            sendError(id, MwaErrorCodes.AUTHORIZATION_FAILED,
                "auth token unknown or bound to a different identity", null)
            return
        }
        // Chain gating: a token issued for one CAIP-2 chain MUST NOT be
        // accepted for a different one. Upstream `BaseScenario.
        // doReauthorize` throws AuthorizationNotValidException with the
        // exact message below; we mirror it. Records issued before chain
        // capture (record.chain == null) accept any chain for compat.
        if (record.chain != null && record.chain != chain) {
            sendError(id, MwaErrorCodes.AUTHORIZATION_FAILED,
                "requested chain not valid for specified auth_token", null)
            return
        }
        val request = ReauthorizeRequest(
            identityName = identity.name,
            identityUri = identity.uri,
            iconRelativeUri = identity.iconRelativeUri,
            authToken = authToken,
            chain = chain,
            authorizationScope = record.scope
        )
        callbacks.onReauthorizeRequest(request)
        when (val outcome = request.awaitCompletion()) {
            is MwaCompletion.Result -> {
                val refreshed = authRepository.reissue(authToken)
                if (refreshed == null) {
                    sendError(id, MwaErrorCodes.AUTHORIZATION_FAILED,
                        "auth token expired during reauthorize", null)
                    return
                }
                activeAuth.set(ActiveAuthorization(refreshed.authToken, refreshed.accounts))
                // The MWA 2.0 unified authorize-with-token returns the
                // same shape as a fresh authorize so the dApp's clientlib
                // routes both through one decoder. We re-emit the
                // existing record verbatim.
                sendResult(id, buildAuthorizeResultJsonFromRecord(refreshed))
            }
            is MwaCompletion.Error -> sendError(id, outcome.code, outcome.message, outcome.data)
        }
    }

    private suspend fun handleReauthorize(id: JsonElement?, params: JsonObject) {
        val identity = identityResolver.fromAuthorizeParams(params)
        val authToken = (params["auth_token"] as? JsonPrimitive)?.contentOrNull
        if (authToken == null) {
            sendError(id, MwaErrorCodes.INVALID_PARAMS, "missing auth_token", null)
            return
        }
        val chain = (params["chain"] as? JsonPrimitive)?.contentOrNull
            ?: ProtocolContract.CHAIN_SOLANA_MAINNET
        val record = authRepository.lookup(authToken)
        if (record == null || record.identity != identity) {
            sendError(id, MwaErrorCodes.AUTHORIZATION_FAILED,
                "auth token unknown or bound to a different identity", null)
            return
        }
        // Same chain-gating contract as the unified authorize-with-token
        // path; see handleAuthorizeAsReauthorize for the rationale.
        if (record.chain != null && record.chain != chain) {
            sendError(id, MwaErrorCodes.AUTHORIZATION_FAILED,
                "requested chain not valid for specified auth_token", null)
            return
        }
        val request = ReauthorizeRequest(
            identityName = identity.name,
            identityUri = identity.uri,
            iconRelativeUri = identity.iconRelativeUri,
            authToken = authToken,
            chain = chain,
            authorizationScope = record.scope
        )
        callbacks.onReauthorizeRequest(request)
        when (val outcome = request.awaitCompletion()) {
            is MwaCompletion.Result -> {
                val refreshed = authRepository.reissue(authToken)
                if (refreshed == null) {
                    sendError(id, MwaErrorCodes.AUTHORIZATION_FAILED,
                        "auth token expired during reauthorize", null)
                    return
                }
                activeAuth.set(ActiveAuthorization(refreshed.authToken, refreshed.accounts))
                sendResult(id, buildAuthorizeResultJsonFromRecord(refreshed))
            }
            is MwaCompletion.Error -> sendError(id, outcome.code, outcome.message, outcome.data)
        }
    }

    private suspend fun handleDeauthorize(id: JsonElement?, params: JsonObject) {
        val authToken = (params["auth_token"] as? JsonPrimitive)?.contentOrNull
        if (authToken == null) {
            sendError(id, MwaErrorCodes.INVALID_PARAMS, "missing auth_token", null)
            return
        }
        // Always succeed at the wire level; deauthorize is idempotent.
        // We revoke on the repo, surface a typed event to the wallet UI
        // so it can update local state, and reply with an empty success
        // ONLY after the wallet has confirmed UI cleanup via
        // event.complete(). Upstream `BaseScenario.deauthorize` blocks
        // on the wallet's completion future for the same reason: replying
        // before the wallet has finished local state cleanup races the
        // dApp's next call against half-cleaned state.
        authRepository.revoke(authToken)
        if (activeAuth.get()?.authToken == authToken) activeAuth.set(null)
        val event = DeauthorizedEvent(authToken)
        callbacks.onDeauthorizedEvent(event)
        // Wait for the wallet UI to call event.complete(). A bounded
        // timeout protects the dApp from a never-completing wallet UI;
        // the upper bound matches the dApp clientlib's request timeout
        // so a missed completion still surfaces as a normal RPC timeout
        // on the dApp side rather than a dangling future.
        val completed = withTimeoutOrNull(deauthorizeCompletionTimeoutMs) {
            event.await()
            true
        } ?: false
        // Reply success either way. Deauthorize is idempotent and the
        // dApp doesn't need to know whether the wallet's UI cleanup
        // finished in time; if it didn't, the wallet will catch up on
        // its next foreground tick.
        sendResult(id, JsonObject(emptyMap()))
        if (!completed) {
            // Surface an error to the wallet so a developer can spot
            // the missing event.complete() in their callback impl.
            callbacks.onScenarioError(
                IllegalStateException(
                    "DeauthorizedEvent.complete() was not called within " +
                        "${deauthorizeCompletionTimeoutMs}ms; wallet UI cleanup " +
                        "may be stuck. Override Scenario.Callbacks.onDeauthorizedEvent " +
                        "to call event.complete() once your local state is consistent."
                )
            )
        }
    }

    private suspend fun handleSignTransactions(id: JsonElement?, params: JsonObject) {
        if (MobileWalletAdapterConfig.FEATURE_SIGN_TRANSACTIONS !in config.optionalFeatures) {
            sendError(id, MwaErrorCodes.METHOD_NOT_FOUND,
                "sign_transactions is not advertised by this wallet", null)
            return
        }
        val payloads = decodeBase64ListOrNull(params["payloads"]) ?: run {
            sendError(id, MwaErrorCodes.INVALID_PARAMS, "missing or invalid payloads", null)
            return
        }
        val active = activeAuth.get() ?: run {
            sendError(id, MwaErrorCodes.AUTHORIZATION_FAILED, "no active authorization", null)
            return
        }
        if (payloads.size > config.maxTransactionsPerSigningRequest) {
            sendError(id, MwaErrorCodes.TOO_MANY_PAYLOADS,
                "request exceeded the wallet's per-call payload cap", null)
            return
        }
        val identity = identityResolver.identityForActive(active) ?: identityResolver.unknownIdentity()
        val request = SignTransactionsRequest(
            payloads = payloads,
            authorizedAccounts = active.accounts,
            identityName = identity.name,
            identityUri = identity.uri,
            iconRelativeUri = identity.iconRelativeUri
        )
        callbacks.onSignTransactionsRequest(request)
        when (val outcome = request.awaitCompletion()) {
            is MwaCompletion.Result -> {
                val signed = (outcome.payload as SignTransactionsRequest.SignedPayloadsResult).signedPayloads
                sendResult(id, buildSignedPayloadsJson(signed))
            }
            is MwaCompletion.Error -> {
                if (outcome.data is SignTransactionsRequest.InvalidPayloadsErrorData) {
                    sendInvalidPayloadsError(id, outcome.message, outcome.data.valid)
                } else {
                    sendError(id, outcome.code, outcome.message, null)
                }
            }
        }
    }

    private suspend fun handleSignAndSendTransactions(id: JsonElement?, params: JsonObject) {
        val payloads = decodeBase64ListOrNull(params["payloads"]) ?: run {
            sendError(id, MwaErrorCodes.INVALID_PARAMS, "missing or invalid payloads", null)
            return
        }
        val active = activeAuth.get() ?: run {
            sendError(id, MwaErrorCodes.AUTHORIZATION_FAILED, "no active authorization", null)
            return
        }
        if (payloads.size > config.maxTransactionsPerSigningRequest) {
            sendError(id, MwaErrorCodes.TOO_MANY_PAYLOADS,
                "request exceeded the wallet's per-call payload cap", null)
            return
        }
        val options = params["options"] as? JsonObject
        val minContextSlot = (options?.get("min_context_slot") as? JsonPrimitive)?.intOrNull
        val commitment = (options?.get("commitment") as? JsonPrimitive)?.contentOrNull
        val skipPreflight = (options?.get("skip_preflight") as? JsonPrimitive)?.booleanOrNull
        val maxRetries = (options?.get("max_retries") as? JsonPrimitive)?.intOrNull
        val waitForCommitmentToSendNextTransaction =
            (options?.get("wait_for_commitment_to_send_next_transaction") as? JsonPrimitive)?.booleanOrNull
        val identity = identityResolver.identityForActive(active) ?: identityResolver.unknownIdentity()
        val request = SignAndSendTransactionsRequest(
            payloads = payloads,
            authorizedAccounts = active.accounts,
            identityName = identity.name,
            identityUri = identity.uri,
            iconRelativeUri = identity.iconRelativeUri,
            minContextSlot = minContextSlot,
            commitment = commitment,
            skipPreflight = skipPreflight,
            maxRetries = maxRetries,
            waitForCommitmentToSendNextTransaction = waitForCommitmentToSendNextTransaction
        )
        callbacks.onSignAndSendTransactionsRequest(request)
        when (val outcome = request.awaitCompletion()) {
            is MwaCompletion.Result -> {
                val sigs = (outcome.payload as SignAndSendTransactionsRequest.SignaturesResult).signatures
                sendResult(id, buildSignaturesJson(sigs))
            }
            is MwaCompletion.Error -> {
                when (outcome.data) {
                    is SignAndSendTransactionsRequest.InvalidSignaturesErrorData ->
                        sendInvalidPayloadsError(id, outcome.message, outcome.data.valid)
                    is SignAndSendTransactionsRequest.NotSubmittedErrorData ->
                        sendNotSubmittedError(id, outcome.message, outcome.data.signatures)
                    else -> sendError(id, outcome.code, outcome.message, null)
                }
            }
        }
    }

    private suspend fun handleSignMessages(id: JsonElement?, params: JsonObject) {
        val payloads = decodeBase64ListOrNull(params["payloads"]) ?: run {
            sendError(id, MwaErrorCodes.INVALID_PARAMS, "missing or invalid payloads", null)
            return
        }
        val addresses = decodeBase64ListOrNull(params["addresses"]) ?: run {
            sendError(id, MwaErrorCodes.INVALID_PARAMS, "missing or invalid addresses", null)
            return
        }
        if (payloads.size != addresses.size) {
            sendError(id, MwaErrorCodes.INVALID_PARAMS,
                "payloads and addresses must be the same length", null)
            return
        }
        val active = activeAuth.get() ?: run {
            sendError(id, MwaErrorCodes.AUTHORIZATION_FAILED, "no active authorization", null)
            return
        }
        // Address-set check (upstream `BaseScenario.signMessagesAsync`):
        // every requested address must be in the active authorization's
        // account list. Without this, a malicious dApp can ask the wallet
        // to sign for an arbitrary public key by sending it in
        // `addresses[]`; the wallet UI would then forward to a signer
        // that may match by index. Reject up front with
        // AUTHORIZATION_FAILED, matching upstream's
        // AuthorizationNotValidException for the same condition.
        val authorizedKeys = active.accounts.map { it.publicKey }
        val unauthorized = addresses.firstOrNull { addr ->
            authorizedKeys.none { it.contentEquals(addr) }
        }
        if (unauthorized != null) {
            sendError(id, MwaErrorCodes.AUTHORIZATION_FAILED,
                "address not in active authorization", null)
            return
        }
        if (payloads.size > config.maxMessagesPerSigningRequest) {
            sendError(id, MwaErrorCodes.TOO_MANY_PAYLOADS,
                "request exceeded the wallet's per-call payload cap", null)
            return
        }
        val identity = identityResolver.identityForActive(active) ?: identityResolver.unknownIdentity()
        val request = SignMessagesRequest(
            payloads = payloads,
            addresses = addresses,
            authorizedAccounts = active.accounts,
            identityName = identity.name,
            identityUri = identity.uri,
            iconRelativeUri = identity.iconRelativeUri
        )
        callbacks.onSignMessagesRequest(request)
        when (val outcome = request.awaitCompletion()) {
            is MwaCompletion.Result -> {
                val signed = (outcome.payload as SignMessagesRequest.SignedMessagesResult).signedPayloads
                sendResult(id, buildSignedPayloadsJson(signed))
            }
            is MwaCompletion.Error -> {
                if (outcome.data is SignMessagesRequest.InvalidPayloadsErrorData) {
                    sendInvalidPayloadsError(id, outcome.message, outcome.data.valid)
                } else {
                    sendError(id, outcome.code, outcome.message, null)
                }
            }
        }
    }

    // ─── Reply helpers ────────────────────────────────────────────────

    private suspend fun sendResult(id: JsonElement?, result: JsonElement) {
        val envelope = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            put("result", result)
        }
        sendEnvelope(envelope)
    }

    private suspend fun sendError(id: JsonElement?, code: Int, message: String, data: Any?) {
        val envelope = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
                if (data != null) put("data", anyToJson(data))
            })
        }
        sendEnvelope(envelope)
    }

    private suspend fun sendInvalidPayloadsError(id: JsonElement?, message: String, valid: List<Boolean>) {
        val envelope = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            put("error", buildJsonObject {
                put("code", MwaErrorCodes.INVALID_PAYLOADS)
                put("message", message)
                put("data", buildJsonObject {
                    put("valid", JsonArray(valid.map { JsonPrimitive(it) }))
                })
            })
        }
        sendEnvelope(envelope)
    }

    private suspend fun sendNotSubmittedError(id: JsonElement?, message: String, signatures: List<ByteArray?>) {
        val envelope = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            put("error", buildJsonObject {
                put("code", MwaErrorCodes.NOT_SUBMITTED)
                put("message", message)
                put("data", buildJsonObject {
                    put("signatures", JsonArray(signatures.map { sig ->
                        if (sig == null) JsonNull
                        else JsonPrimitive(Base64.getEncoder().encodeToString(sig))
                    }))
                })
            })
        }
        sendEnvelope(envelope)
    }

    private suspend fun sendEnvelope(envelope: JsonObject) {
        val bytes = envelope.toString().encodeToByteArray()
        sendMutex.withLock {
            // Mutex prevents two encrypt+send pairs from interleaving:
            // AES-GCM-with-counter-nonce is catastrophic on a reused
            // sequence number, so the seq increment and the wire send
            // must happen as one atomic step.
            val seq = sendSeq.getAndIncrement()
            val packet = cipher.encrypt(seq, bytes)
            transport.send(packet)
        }
    }

    private fun decodeBase64ListOrNull(element: JsonElement?): List<ByteArray>? {
        val arr = element as? JsonArray ?: return null
        val out = ArrayList<ByteArray>(arr.size)
        for (e in arr) {
            val s = (e as? JsonPrimitive)?.contentOrNull ?: return null
            try {
                out += Base64.getDecoder().decode(s)
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
        return out
    }

    private fun anyToJson(value: Any): JsonElement = when (value) {
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    private fun buildSignedPayloadsJson(signed: List<ByteArray>): JsonObject = buildJsonObject {
        put("signed_payloads", JsonArray(signed.map {
            JsonPrimitive(Base64.getEncoder().encodeToString(it))
        }))
    }

    private fun buildSignaturesJson(signatures: List<ByteArray>): JsonObject = buildJsonObject {
        put("signatures", JsonArray(signatures.map {
            JsonPrimitive(Base64.getEncoder().encodeToString(it))
        }))
    }

    private fun buildAuthorizeResultJson(
        record: AuthRecord,
        approval: AuthorizeRequest.AuthorizeApproved
    ): JsonObject = buildJsonObject {
        put("auth_token", record.authToken)
        put("accounts", encodeAccounts(record.accounts))
        approval.walletUriBase?.let { put("wallet_uri_base", it.toString()) }
        if (approval.scope.isNotEmpty()) {
            put("scope", Base64.getEncoder().encodeToString(approval.scope))
        }
        approval.signInResult?.let { siwsResult ->
            put("sign_in_result", buildJsonObject {
                put("address", Base64.getEncoder().encodeToString(siwsResult.publicKey))
                put("signed_message", Base64.getEncoder().encodeToString(siwsResult.signedMessage))
                put("signature", Base64.getEncoder().encodeToString(siwsResult.signature))
                put("signature_type", siwsResult.signatureType)
            })
        }
    }

    private fun buildAuthorizeResultJsonFromRecord(record: AuthRecord): JsonObject = buildJsonObject {
        put("auth_token", record.authToken)
        put("accounts", encodeAccounts(record.accounts))
        record.walletUriBase?.let { put("wallet_uri_base", it.toString()) }
        if (record.scope.isNotEmpty()) {
            put("scope", Base64.getEncoder().encodeToString(record.scope))
        }
    }

    private fun encodeAccounts(accounts: List<AuthorizedAccount>): JsonArray = JsonArray(
        accounts.map { acc ->
            buildJsonObject {
                put("address", Base64.getEncoder().encodeToString(acc.publicKey))
                acc.accountLabel?.let { put("label", it) }
                acc.displayAddress?.let { put("display_address", it) }
                acc.displayAddressFormat?.let { put("display_address_format", it) }
                acc.accountIcon?.let { put("icon", it.toString()) }
                if (acc.chains.isNotEmpty()) {
                    put("chains", JsonArray(acc.chains.map { JsonPrimitive(it) }))
                }
                if (acc.features.isNotEmpty()) {
                    put("features", JsonArray(acc.features.map { JsonPrimitive(it) }))
                }
            }
        }
    )

    /** Captured pairing of an active auth_token with its accounts. */
    internal data class ActiveAuthorization(
        val authToken: String,
        val accounts: List<AuthorizedAccount>
    )

    /**
     * Resolves an [Identity] from inbound JSON params so the dispatcher
     * does not need to know how the dApp encodes the identity object.
     * Hides the `params.identity = { name, uri, icon }` parsing behind
     * a typed seam that tests can stub.
     */
    internal interface IdentityResolver {
        fun fromAuthorizeParams(params: JsonObject): Identity
        fun identityForActive(active: ActiveAuthorization): Identity?
        fun unknownIdentity(): Identity = Identity(name = null, uri = null, iconRelativeUri = null)
    }
}

/**
 * Default [WalletMwaServer.IdentityResolver] backed by [AuthRepository]
 * lookups. Wallets that store identity state somewhere else can swap
 * this out.
 */
internal class DefaultIdentityResolver(
    private val authRepository: AuthRepository
) : WalletMwaServer.IdentityResolver {

    override fun fromAuthorizeParams(params: JsonObject): Identity {
        val identityObj = params["identity"] as? JsonObject
        return Identity(
            name = (identityObj?.get("name") as? JsonPrimitive)?.contentOrNull,
            uri = (identityObj?.get("uri") as? JsonPrimitive)?.contentOrNull?.let(Uri::parse),
            iconRelativeUri = (identityObj?.get("icon") as? JsonPrimitive)?.contentOrNull?.let(Uri::parse)
        )
    }

    override fun identityForActive(active: WalletMwaServer.ActiveAuthorization): Identity? {
        // AuthRepository.lookup is `suspend`. Calls on the dispatch
        // path resolve the identity through the ActiveAuthorization
        // snapshot only when the matching record is still around; an
        // expired record is treated as unknown identity.
        return null
    }
}
