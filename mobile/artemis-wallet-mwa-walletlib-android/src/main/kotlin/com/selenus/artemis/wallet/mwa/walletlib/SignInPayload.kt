package com.selenus.artemis.wallet.mwa.walletlib

import org.json.JSONArray
import org.json.JSONObject

/**
 * CAIP-122 / SIWS payload as the wallet receives it inside an
 * [AuthorizeRequest].
 *
 * Field names are the SIP-99 canonical identifiers (`domain`,
 * `address`, `chainId`, etc.). The wallet typically hands this to a
 * SIWS validator (the `MwaSiwsValidator` in the dApp-side module is a
 * reference implementation) and renders the resulting message text in
 * a confirmation prompt.
 *
 * `equals`/`hashCode` ignore [resources] order semantics by comparing
 * the list directly: per SIP-99 the order is significant in the signed
 * message so the wallet must preserve it.
 */
data class SignInPayload(
    val domain: String? = null,
    val address: String? = null,
    val statement: String? = null,
    val uri: String? = null,
    val version: String? = null,
    val chainId: String? = null,
    val nonce: String? = null,
    val issuedAt: String? = null,
    val expirationTime: String? = null,
    val notBefore: String? = null,
    val requestId: String? = null,
    val resources: List<String> = emptyList()
) {
    init {
        // Reject smuggled newlines in any field that lands in the SIWS
        // wire message verbatim. A field that contained a newline could
        // forge fake `URI:` / `Nonce:` / `Domain:` lines past a parser
        // that splits on '\n'. The dApp-side `MwaSiwsValidator` enforces
        // the same invariant; mirroring it here means a malicious dApp
        // request never reaches the wallet UI.
        listOf(domain, address, statement, uri, version, chainId, nonce,
            issuedAt, expirationTime, notBefore, requestId).forEach { value ->
            if (value != null) {
                require(!value.contains('\n') && !value.contains('\r')) {
                    "SIWS payload fields cannot contain CR/LF"
                }
            }
        }
        resources.forEach { r ->
            require(!r.contains('\n') && !r.contains('\r')) {
                "SIWS resources entries cannot contain CR/LF"
            }
        }
    }

    /** Render the SIWS message text the wallet should present to the user. */
    fun prepareMessage(signerAddress: String = address ?: ""): String {
        val sb = StringBuilder()
        if (domain != null) sb.append("$domain wants you to sign in with your Solana account:\n")
        sb.append(signerAddress).append("\n\n")
        if (statement != null) sb.append(statement).append("\n\n")
        if (uri != null) sb.append("URI: ").append(uri).append("\n")
        if (version != null) sb.append("Version: ").append(version).append("\n")
        if (chainId != null) sb.append("Chain ID: ").append(chainId).append("\n")
        if (nonce != null) sb.append("Nonce: ").append(nonce).append("\n")
        if (issuedAt != null) sb.append("Issued At: ").append(issuedAt).append("\n")
        if (expirationTime != null) sb.append("Expiration Time: ").append(expirationTime).append("\n")
        if (notBefore != null) sb.append("Not Before: ").append(notBefore).append("\n")
        if (requestId != null) sb.append("Request ID: ").append(requestId).append("\n")
        if (resources.isNotEmpty()) {
            sb.append("Resources:\n")
            resources.forEach { sb.append("- ").append(it).append('\n') }
        }
        return sb.toString().trimEnd('\n')
    }

    /** Wire JSON form (the shape the dApp sent inside the authorize params). */
    fun toJson(): JSONObject = JSONObject().apply {
        domain?.let { put("domain", it) }
        address?.let { put("address", it) }
        statement?.let { put("statement", it) }
        uri?.let { put("uri", it) }
        version?.let { put("version", it) }
        chainId?.let { put("chainId", it) }
        nonce?.let { put("nonce", it) }
        issuedAt?.let { put("issuedAt", it) }
        expirationTime?.let { put("expirationTime", it) }
        notBefore?.let { put("notBefore", it) }
        requestId?.let { put("requestId", it) }
        if (resources.isNotEmpty()) put("resources", JSONArray(resources))
    }

    companion object {
        /** Parse the inverse of [toJson]. Returns null when the input is null. */
        @JvmStatic
        fun fromJson(json: JSONObject?): SignInPayload? {
            if (json == null) return null
            fun opt(key: String): String? =
                if (json.has(key) && !json.isNull(key)) json.getString(key) else null
            val resources = if (json.has("resources") && !json.isNull("resources")) {
                val arr = json.getJSONArray("resources")
                List(arr.length()) { arr.getString(it) }
            } else emptyList()
            return SignInPayload(
                domain = opt("domain"),
                address = opt("address"),
                statement = opt("statement"),
                uri = opt("uri"),
                version = opt("version"),
                chainId = opt("chainId"),
                nonce = opt("nonce"),
                issuedAt = opt("issuedAt"),
                expirationTime = opt("expirationTime"),
                notBefore = opt("notBefore"),
                requestId = opt("requestId"),
                resources = resources
            )
        }
    }
}
