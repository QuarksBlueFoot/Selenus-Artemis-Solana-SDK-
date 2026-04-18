/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.common.protocol.SignInWithSolana.
 *
 * Verified against the 2.0.7 sources jar. Upstream's Payload primary
 * constructor takes 12 String-or-String[] arguments (String address, not
 * ByteArray). The two deprecated constructors are short convenience forms:
 *
 *   @Deprecated Payload(String domain, String address)
 *   @Deprecated Payload(String domain, String address, String statement)
 *
 * The HEADER_TYPE constant is `"sip-99"` (hyphenated). `fromMessage`,
 * `fromJson(JSONObject)`, `fromJson(String)`, and `toJson()` are all public
 * static / instance helpers.
 */
package com.solana.mobilewalletadapter.common.protocol

import org.json.JSONObject

/** Top-level SIWS helper. Named to match upstream exactly. */
class SignInWithSolana {

    /** SIWS payload with SIP-99 field coverage. */
    class Payload(
        @JvmField val domain: String?,
        @JvmField val address: String?,
        @JvmField val statement: String?,
        @JvmField val uri: String?,
        @JvmField val version: String?,
        @JvmField val chainId: String?,
        @JvmField val nonce: String?,
        @JvmField val issuedAt: String?,
        @JvmField val expirationTime: String?,
        @JvmField val notBefore: String?,
        @JvmField val requestId: String?,
        @JvmField val resources: Array<String>?
    ) {

        init {
            // Defence in depth against signature-confusion attacks: every
            // field is interpolated into the SIWS wire message separated by
            // newlines. A field that itself contains a newline could smuggle
            // fake domain / address lines past `fromMessage`, producing a
            // signed payload that verifies under a different interpretation.
            // Reject up front.
            listOf(domain, address, statement, uri, version, chainId, nonce,
                issuedAt, expirationTime, notBefore, requestId).forEach { value ->
                if (value != null) {
                    require(!value.contains('\n') && !value.contains('\r')) {
                        "SIWS payload fields cannot contain newline characters"
                    }
                }
            }
            resources?.forEach { r ->
                require(!r.contains('\n') && !r.contains('\r')) {
                    "SIWS resources entries cannot contain newline characters"
                }
            }
        }


        /** Deprecated convenience form (domain + address only). */
        @Deprecated("Use the full 12-arg constructor.")
        constructor(domain: String?, address: String?) : this(
            domain = domain,
            address = address,
            statement = null,
            uri = null,
            version = null,
            chainId = null,
            nonce = null,
            issuedAt = null,
            expirationTime = null,
            notBefore = null,
            requestId = null,
            resources = null
        )

        /** Deprecated convenience form (domain + address + statement). */
        @Deprecated("Use the full 12-arg constructor.")
        constructor(domain: String?, address: String?, statement: String?) : this(
            domain = domain,
            address = address,
            statement = statement,
            uri = null,
            version = null,
            chainId = null,
            nonce = null,
            issuedAt = null,
            expirationTime = null,
            notBefore = null,
            requestId = null,
            resources = null
        )

        /** Default message uses the payload's own [address] field. */
        fun prepareMessage(): String = buildMessageString(address ?: "")

        /**
         * Message-preparation override: use [signerAddress] instead of the
         * payload's stored one. Matches upstream signature.
         */
        fun prepareMessage(signerAddress: String): String = buildMessageString(signerAddress)

        /** JSON serialization. Field names match the SIWS specification. */
        fun toJson(): JSONObject {
            val json = JSONObject()
            domain?.let { json.put("domain", it) }
            address?.let { json.put("address", it) }
            statement?.let { json.put("statement", it) }
            uri?.let { json.put("uri", it) }
            version?.let { json.put("version", it) }
            chainId?.let { json.put("chainId", it) }
            nonce?.let { json.put("nonce", it) }
            issuedAt?.let { json.put("issuedAt", it) }
            expirationTime?.let { json.put("expirationTime", it) }
            notBefore?.let { json.put("notBefore", it) }
            requestId?.let { json.put("requestId", it) }
            resources?.let { json.put("resources", it.toList()) }
            return json
        }

        private fun buildMessageString(addressStr: String): String {
            val sb = StringBuilder()
            if (domain != null) sb.append("$domain wants you to sign in with your Solana account:\n")
            sb.append(addressStr).append("\n\n")
            if (statement != null) sb.append(statement).append("\n\n")
            if (uri != null) sb.append("URI: ").append(uri).append("\n")
            if (version != null) sb.append("Version: ").append(version).append("\n")
            if (chainId != null) sb.append("Chain ID: ").append(chainId).append("\n")
            if (nonce != null) sb.append("Nonce: ").append(nonce).append("\n")
            if (issuedAt != null) sb.append("Issued At: ").append(issuedAt).append("\n")
            if (expirationTime != null) sb.append("Expiration Time: ").append(expirationTime).append("\n")
            if (notBefore != null) sb.append("Not Before: ").append(notBefore).append("\n")
            if (requestId != null) sb.append("Request ID: ").append(requestId).append("\n")
            if (!resources.isNullOrEmpty()) {
                sb.append("Resources:\n")
                for (r in resources) sb.append("- ").append(r).append('\n')
            }
            return sb.toString().trimEnd('\n')
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Payload) return false
            return domain == other.domain && address == other.address &&
                statement == other.statement && uri == other.uri &&
                version == other.version && chainId == other.chainId &&
                nonce == other.nonce && issuedAt == other.issuedAt &&
                expirationTime == other.expirationTime && notBefore == other.notBefore &&
                requestId == other.requestId &&
                ((resources == null && other.resources == null) ||
                    (resources != null && other.resources != null && resources.contentEquals(other.resources)))
        }

        override fun hashCode(): Int {
            var result = domain?.hashCode() ?: 0
            result = 31 * result + (address?.hashCode() ?: 0)
            result = 31 * result + (statement?.hashCode() ?: 0)
            result = 31 * result + (resources?.contentHashCode() ?: 0)
            return result
        }

        override fun toString(): String = toJson().toString()

        companion object {
            /** SIP-99 header type. NOTE the hyphen; `sip99` would be wrong. */
            const val HEADER_TYPE: String = "sip-99"

            /**
             * Parse a previously-built SIWS message. Throws `IllegalArgumentException`
             * on malformed input. Matches upstream `fromMessage(String)`.
             */
            @JvmStatic
            fun fromMessage(message: String): Payload {
                require(message.isNotBlank()) { "message must not be blank" }
                val lines = message.split("\n").toMutableList()
                val domainLine = lines.removeAt(0)
                val domain = domainLine.substringBefore(" wants you to sign in").ifBlank { null }
                val address = if (lines.isNotEmpty()) lines.removeAt(0).trim() else null
                val remaining = lines.joinToString("\n")
                fun field(prefix: String): String? =
                    Regex("(?m)^$prefix\\s*(.+)$").find(remaining)?.groupValues?.get(1)?.trim()
                val statement = remaining
                    .substringBefore("\nURI:")
                    .substringBefore("\nVersion:")
                    .trim()
                    .takeIf { it.isNotEmpty() && !it.startsWith("URI:") && !it.startsWith("Version:") }
                return Payload(
                    domain = domain,
                    address = address,
                    statement = statement,
                    uri = field("URI:"),
                    version = field("Version:"),
                    chainId = field("Chain ID:"),
                    nonce = field("Nonce:"),
                    issuedAt = field("Issued At:"),
                    expirationTime = field("Expiration Time:"),
                    notBefore = field("Not Before:"),
                    requestId = field("Request ID:"),
                    resources = null
                )
            }

            /** Parse from a `JSONObject`. */
            @JvmStatic
            fun fromJson(json: JSONObject): Payload {
                fun opt(key: String): String? =
                    if (json.has(key) && !json.isNull(key)) json.getString(key) else null
                val resources = if (json.has("resources") && !json.isNull("resources")) {
                    val arr = json.getJSONArray("resources")
                    Array(arr.length()) { arr.getString(it) }
                } else null
                return Payload(
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

            /** Parse from a JSON string. Thin wrapper around `fromJson(JSONObject)`. */
            @JvmStatic
            fun fromJson(json: String): Payload = fromJson(JSONObject(json))
        }
    }
}
