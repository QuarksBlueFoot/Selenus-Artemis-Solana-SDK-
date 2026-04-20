/*
 * Bridges the upstream SIWS payload type
 * (`com.solana.mobilewalletadapter.common.protocol.SignInWithSolana.Payload`)
 * to the Artemis native `MwaSignInPayload` so the ktx `transact { signInPayload }`
 * path can actually reach the wallet with the user-supplied fields, rather
 * than dropping them on the floor.
 */
package com.solana.mobilewalletadapter.clientlib

import com.selenus.artemis.wallet.mwa.protocol.MwaSignInPayload
import com.solana.mobilewalletadapter.common.protocol.SignInWithSolana

internal fun SignInWithSolana.Payload.toMwa(): MwaSignInPayload = MwaSignInPayload(
    domain = domain.orEmpty(),
    uri = uri,
    statement = statement,
    resources = resources?.toList(),
    version = version,
    chainId = chainId,
    nonce = nonce,
    issuedAt = issuedAt,
    expirationTime = expirationTime,
    notBefore = notBefore,
    requestId = requestId
)
