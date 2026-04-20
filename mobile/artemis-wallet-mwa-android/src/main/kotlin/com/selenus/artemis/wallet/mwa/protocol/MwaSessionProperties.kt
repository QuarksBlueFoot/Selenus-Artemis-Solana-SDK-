package com.selenus.artemis.wallet.mwa.protocol

/**
 * MWA session-properties envelope advertised by the wallet during the HELLO
 * handshake. The wire form is `{"v":"v1"}` (or `"legacy"` for MWA 1.x
 * wallets). Exposed on [MwaSession.sessionProperties] so the dapp layer can
 * branch on the wallet's protocol version instead of assuming.
 */
data class MwaSessionProperties(
    val protocolVersion: ProtocolVersion
) {
    enum class ProtocolVersion(val wireValue: String) {
        LEGACY("legacy"),
        V1("v1");

        companion object {
            /** Parse a wire string, throwing on unknown values. */
            @JvmStatic
            fun fromWireValue(value: String): ProtocolVersion =
                values().firstOrNull { it.wireValue == value }
                    ?: throw IllegalArgumentException("unknown protocol version: $value")

            /** Same as [fromWireValue] but returns [default] instead of throwing. */
            @JvmStatic
            fun fromWireValueOrDefault(value: String, default: ProtocolVersion): ProtocolVersion =
                values().firstOrNull { it.wireValue == value } ?: default
        }
    }
}
