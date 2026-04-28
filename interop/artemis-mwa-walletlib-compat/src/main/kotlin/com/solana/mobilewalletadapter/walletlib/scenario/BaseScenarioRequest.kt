/*
 * Drop-in source compatibility for upstream walletlib's BaseScenarioRequest /
 * BaseVerifiableIdentityRequest / VerifiableIdentityRequest types.
 *
 * Upstream uses these as a small inheritance ladder so the dispatcher
 * can branch on identity-bearing requests vs. anonymous ones. The
 * Artemis dispatcher carries identity on every typed request directly,
 * so these surface as marker interfaces. Source code that does
 * `if (req instanceof VerifiableIdentityRequest)` keeps compiling.
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.walletlib.scenario

import android.net.Uri

/** Marker for any request that flows through the dispatcher. */
interface ScenarioRequest

/** Common shape for every request. Mirrors upstream `BaseScenarioRequest`. */
interface BaseScenarioRequest : ScenarioRequest

/**
 * Requests that carry a verified dApp identity (every request type
 * except `DeauthorizedEvent`). Mirrors upstream
 * `BaseVerifiableIdentityRequest` so callers that switch on the
 * presence of identity fields stay source-compatible.
 */
interface VerifiableIdentityRequest : BaseScenarioRequest {
    val identityName: String?
    val identityUri: Uri?
    val iconRelativeUri: Uri?
}

/**
 * Marker for requests whose payload list is multiple opaque buffers
 * (sign_transactions / sign_messages). Mirrors upstream
 * `SignPayloadsRequest` superclass.
 */
interface SignPayloadsRequest : VerifiableIdentityRequest {
    val payloads: Array<ByteArray>
}
