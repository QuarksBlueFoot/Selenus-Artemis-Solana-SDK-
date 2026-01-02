package com.selenus.artemis.wallet.mwa.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MwaSignInPayload(
    val domain: String,
    val uri: String? = null,
    val statement: String? = null,
    val resources: List<String>? = null,
    val version: String? = null,
    @SerialName("chain_id") val chainId: String? = null,
    val nonce: String? = null,
    @SerialName("issued_at") val issuedAt: String? = null,
    @SerialName("expiration_time") val expirationTime: String? = null,
    @SerialName("not_before") val notBefore: String? = null,
    @SerialName("request_id") val requestId: String? = null
)

@Serializable
data class MwaSignInResult(
    val address: String,
    @SerialName("signed_message") val signedMessage: String,
    val signature: String,
    @SerialName("signature_type") val signatureType: String? = null
)
