package com.solana.mobilewalletadapter.common

import com.solana.mobilewalletadapter.common.protocol.SignInWithSolana
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MwaCommonCompatTest {

    @Test
    fun `ProtocolContract exposes canonical MWA 2 method names and chains`() {
        assertEquals("authorize", ProtocolContract.METHOD_AUTHORIZE)
        assertEquals("reauthorize", ProtocolContract.METHOD_REAUTHORIZE)
        assertEquals("sign_and_send_transactions", ProtocolContract.METHOD_SIGN_AND_SEND_TRANSACTIONS)
        assertEquals("clone_authorization", ProtocolContract.METHOD_CLONE_AUTHORIZATION)
        assertEquals("solana:mainnet", ProtocolContract.CHAIN_SOLANA_MAINNET)
        assertEquals("solana:devnet", ProtocolContract.CHAIN_SOLANA_DEVNET)
        assertEquals(-7, ProtocolContract.ERROR_CHAIN_NOT_SUPPORTED)
        assertEquals(ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED, ProtocolContract.ERROR_CHAIN_NOT_SUPPORTED)
    }

    @Test
    fun `AssociationContract exposes local and remote association wire keys`() {
        assertEquals("solana-wallet", AssociationContract.SCHEME_MOBILE_WALLET_ADAPTER)
        assertEquals("association", AssociationContract.PARAMETER_ASSOCIATION_TOKEN)
        assertEquals("v", AssociationContract.PARAMETER_PROTOCOL_VERSION)
        assertEquals("v1/associate/local", AssociationContract.LOCAL_PATH_SUFFIX)
        assertEquals("port", AssociationContract.LOCAL_PARAMETER_PORT)
        assertEquals("v1/associate/remote", AssociationContract.REMOTE_PATH_SUFFIX)
        assertEquals("reflector", AssociationContract.REMOTE_PARAMETER_REFLECTOR_HOST_AUTHORITY)
        assertEquals("id", AssociationContract.REMOTE_PARAMETER_REFLECTOR_ID)
    }

    @Test
    fun `SessionProperties parses wire values and rejects unknown versions`() {
        assertEquals(SessionProperties.ProtocolVersion.LEGACY, SessionProperties.ProtocolVersion.fromWireValue("legacy"))
        assertEquals(SessionProperties.ProtocolVersion.V1, SessionProperties.ProtocolVersion.fromWireValue("v1"))
        assertThrows(IllegalArgumentException::class.java) {
            SessionProperties.ProtocolVersion.fromWireValue("v2")
        }
    }

    @Test
    fun `SIWS payload round trips resources and rejects newline injection`() {
        val payload = SignInWithSolana.Payload(
            domain = "myapp.example.com",
            address = "11111111111111111111111111111111",
            statement = "Sign in",
            uri = "https://myapp.example.com/login",
            version = "1",
            chainId = "solana:mainnet",
            nonce = "nonce-1",
            issuedAt = "2026-05-02T00:00:00Z",
            expirationTime = null,
            notBefore = null,
            requestId = "req-1",
            resources = arrayOf("https://myapp.example.com/terms", "ipfs://example")
        )

        val parsed = SignInWithSolana.Payload.fromMessage(payload.prepareMessage())
        assertEquals(payload.domain, parsed.domain)
        assertEquals(payload.address, parsed.address)
        assertEquals(payload.chainId, parsed.chainId)
        assertArrayEquals(payload.resources, parsed.resources)
        assertEquals("sip-99", SignInWithSolana.Payload.HEADER_TYPE)

        assertThrows(IllegalArgumentException::class.java) {
            SignInWithSolana.Payload(
                domain = "myapp.example.com\nmalicious",
                address = "11111111111111111111111111111111",
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
        }
    }
}
