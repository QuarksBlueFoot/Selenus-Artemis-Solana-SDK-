package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri
import com.selenus.artemis.wallet.mwa.protocol.Base64Url

/**
 * Parsed `solana-wallet:` association URI as the wallet sees it.
 *
 * Two flavors map to the two MWA association modes:
 *  - [Local]: same-device flow. The dApp runs a loopback WS server on
 *    [Local.port]; the wallet connects out as a WS client.
 *  - [Remote]: cross-device flow via a reflector. The wallet connects
 *    to `wss://<authority>/reflect?id=<reflectorId>` and exchanges the
 *    same HELLO frames as the local flow.
 *
 * In both cases the URI carries a base64url-encoded P-256 public key
 * (the dApp's association key, used to verify the HELLO_REQ signature)
 * and an optional protocol-version list.
 */
sealed class AssociationUri {
    /** SEC1 uncompressed (0x04 || X32 || Y32) bytes of the dApp's association P-256 key. */
    abstract val associationPublicKey: ByteArray

    /**
     * Protocol versions advertised by the dApp through the `v` query
     * parameter. Empty when the dApp omitted `v`; per the spec the
     * wallet should then fall back to the LEGACY version. Order is
     * preserved from the URI for diagnostics.
     */
    abstract val protocolVersions: List<ProtocolVersion>

    data class Local(
        override val associationPublicKey: ByteArray,
        val port: Int,
        override val protocolVersions: List<ProtocolVersion>
    ) : AssociationUri() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Local) return false
            return port == other.port &&
                associationPublicKey.contentEquals(other.associationPublicKey) &&
                protocolVersions == other.protocolVersions
        }

        override fun hashCode(): Int {
            var h = associationPublicKey.contentHashCode()
            h = 31 * h + port
            h = 31 * h + protocolVersions.hashCode()
            return h
        }
    }

    data class Remote(
        override val associationPublicKey: ByteArray,
        val reflectorAuthority: String,
        val reflectorId: ByteArray,
        override val protocolVersions: List<ProtocolVersion>
    ) : AssociationUri() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Remote) return false
            return reflectorAuthority == other.reflectorAuthority &&
                associationPublicKey.contentEquals(other.associationPublicKey) &&
                reflectorId.contentEquals(other.reflectorId) &&
                protocolVersions == other.protocolVersions
        }

        override fun hashCode(): Int {
            var h = associationPublicKey.contentHashCode()
            h = 31 * h + reflectorAuthority.hashCode()
            h = 31 * h + reflectorId.contentHashCode()
            h = 31 * h + protocolVersions.hashCode()
            return h
        }
    }

    /**
     * Negotiated MWA protocol version. Wire form is the lowercase string
     * the dApp emits in the `v` query parameter (`legacy`, `v1`).
     */
    enum class ProtocolVersion(val wireValue: String) {
        LEGACY("legacy"),
        V1("v1");

        companion object {
            fun fromWireValue(value: String): ProtocolVersion =
                values().firstOrNull { it.wireValue == value }
                    ?: throw MwaAssociationException("unknown protocol version: $value")
        }
    }

    companion object {
        private const val SCHEME = "solana-wallet"
        private const val PATH_LOCAL = "/v1/associate/local"
        private const val PATH_REMOTE = "/v1/associate/remote"

        private const val PARAM_ASSOCIATION = "association"
        private const val PARAM_VERSION = "v"
        private const val PARAM_PORT = "port"
        private const val PARAM_REFLECTOR = "reflector"
        private const val PARAM_REFLECTOR_ID = "id"

        /**
         * Parse a URI that landed on the wallet's MWA Activity through an
         * `Intent.ACTION_VIEW`.
         *
         * Throws [MwaAssociationException] on every malformed input, with
         * a message that names the bad field. Callers (typically
         * [MwaWalletActivity]) `catch` this and render an error UI.
         */
        @JvmStatic
        fun parse(uri: Uri): AssociationUri {
            val scheme = uri.scheme
                ?: throw MwaAssociationException("missing scheme")
            if (!scheme.equals(SCHEME, ignoreCase = true)) {
                throw MwaAssociationException("expected scheme `$SCHEME`, got `$scheme`")
            }

            // The path is normalised through Uri.parse: leading slash kept,
            // trailing slash dropped. Match against both supported suffixes.
            val rawPath = uri.path?.trimEnd('/')
                ?: throw MwaAssociationException("missing path")

            val associationParam = uri.getQueryParameter(PARAM_ASSOCIATION)
                ?: throw MwaAssociationException("missing `$PARAM_ASSOCIATION` query parameter")
            val associationKey = decodeAssociationKey(associationParam)

            val versions = uri.getQueryParameter(PARAM_VERSION)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.map { ProtocolVersion.fromWireValue(it) }
                ?: emptyList()

            return when {
                rawPath.equals(PATH_LOCAL, ignoreCase = true) -> {
                    val portStr = uri.getQueryParameter(PARAM_PORT)
                        ?: throw MwaAssociationException("missing `$PARAM_PORT` query parameter")
                    val port = portStr.toIntOrNull()
                        ?: throw MwaAssociationException("invalid `$PARAM_PORT` value: $portStr")
                    if (port !in 1..65535) {
                        throw MwaAssociationException("`$PARAM_PORT` out of range: $port")
                    }
                    Local(
                        associationPublicKey = associationKey,
                        port = port,
                        protocolVersions = versions
                    )
                }
                rawPath.equals(PATH_REMOTE, ignoreCase = true) -> {
                    val reflector = uri.getQueryParameter(PARAM_REFLECTOR)
                        ?: throw MwaAssociationException("missing `$PARAM_REFLECTOR` query parameter")
                    val reflectorIdParam = uri.getQueryParameter(PARAM_REFLECTOR_ID)
                        ?: throw MwaAssociationException("missing `$PARAM_REFLECTOR_ID` query parameter")
                    val reflectorIdBytes = try {
                        Base64Url.decode(reflectorIdParam)
                    } catch (e: IllegalArgumentException) {
                        throw MwaAssociationException(
                            "invalid base64url for `$PARAM_REFLECTOR_ID`",
                            e
                        )
                    }
                    Remote(
                        associationPublicKey = associationKey,
                        reflectorAuthority = reflector,
                        reflectorId = reflectorIdBytes,
                        protocolVersions = versions
                    )
                }
                else -> throw MwaAssociationException("unsupported path `$rawPath`")
            }
        }

        private fun decodeAssociationKey(raw: String): ByteArray {
            val decoded = try {
                Base64Url.decode(raw)
            } catch (e: IllegalArgumentException) {
                throw MwaAssociationException("invalid base64url for `$PARAM_ASSOCIATION`", e)
            }
            // SEC1 uncompressed P-256: 0x04 || X(32) || Y(32) = 65 bytes.
            // A wallet should reject anything else up front rather than
            // letting the JCA spit out a generic InvalidKeyException
            // halfway through the handshake.
            if (decoded.size != 65 || decoded[0] != 0x04.toByte()) {
                throw MwaAssociationException(
                    "association key must be a 65-byte SEC1 uncompressed P-256 point"
                )
            }
            return decoded
        }
    }
}
