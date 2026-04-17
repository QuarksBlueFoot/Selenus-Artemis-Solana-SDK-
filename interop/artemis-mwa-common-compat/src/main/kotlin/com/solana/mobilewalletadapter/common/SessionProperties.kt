/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.common.SessionProperties.
 *
 * Upstream publishes this as a top-level class in the `common` package with a
 * nested `ProtocolVersion` enum. Wire values are strings (`legacy`, `v1`), not
 * integers: dapps call `ProtocolVersion.fromWireValue("v1")` when negotiating
 * the session. Replicating the exact names + wire values keeps source compat.
 */
package com.solana.mobilewalletadapter.common

data class SessionProperties(val protocolVersion: ProtocolVersion) {

    enum class ProtocolVersion(val wireValue: String) {
        LEGACY("legacy"),
        V1("v1");

        companion object {
            /** Parse a wire string back into a [ProtocolVersion]. */
            @JvmStatic
            fun fromWireValue(value: String): ProtocolVersion =
                values().first { it.wireValue == value }
        }
    }
}
