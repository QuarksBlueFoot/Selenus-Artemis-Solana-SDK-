package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri
import com.selenus.artemis.wallet.mwa.protocol.Base64Url
import com.selenus.artemis.wallet.mwa.protocol.EcP256
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AssociationUriParseTest {

    private lateinit var associationKey: ByteArray
    private lateinit var associationParam: String

    @Before
    fun setUp() {
        UriTestSupport.installUriStub()
        // A real SEC1 uncompressed P-256 point is a much better corpus
        // than ByteArray(65) { 0 } because it exercises the parser's
        // actual length / leading-byte checks the same way a live
        // wallet will see them.
        val keypair = EcP256.generateKeypair()
        associationKey = EcP256.x962Uncompressed(keypair.public)
        associationParam = Base64Url.encode(associationKey)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `parses a local association URI`() {
        val uri = Uri.parse(
            "solana-wallet:/v1/associate/local?association=$associationParam&port=43210&v=v1"
        )
        val parsed = AssociationUri.parse(uri) as AssociationUri.Local
        assertArrayEquals(associationKey, parsed.associationPublicKey)
        assertEquals(43210, parsed.port)
        assertEquals(listOf(AssociationUri.ProtocolVersion.V1), parsed.protocolVersions)
    }

    @Test
    fun `parses a remote association URI`() {
        val reflectorId = ByteArray(16) { it.toByte() }
        val reflectorIdParam = Base64Url.encode(reflectorId)
        val uri = Uri.parse(
            "solana-wallet:/v1/associate/remote?association=$associationParam" +
                "&reflector=reflector.example.com" +
                "&id=$reflectorIdParam" +
                "&v=v1"
        )
        val parsed = AssociationUri.parse(uri) as AssociationUri.Remote
        assertArrayEquals(associationKey, parsed.associationPublicKey)
        assertEquals("reflector.example.com", parsed.reflectorAuthority)
        assertArrayEquals(reflectorId, parsed.reflectorId)
        assertEquals(listOf(AssociationUri.ProtocolVersion.V1), parsed.protocolVersions)
    }

    @Test
    fun `parses comma-separated protocol version list`() {
        val uri = Uri.parse(
            "solana-wallet:/v1/associate/local?association=$associationParam&port=8080&v=v1,legacy"
        )
        val parsed = AssociationUri.parse(uri) as AssociationUri.Local
        assertEquals(
            listOf(AssociationUri.ProtocolVersion.V1, AssociationUri.ProtocolVersion.LEGACY),
            parsed.protocolVersions
        )
    }

    @Test
    fun `parses URI without v parameter as no advertised versions`() {
        val uri = Uri.parse(
            "solana-wallet:/v1/associate/local?association=$associationParam&port=8080"
        )
        val parsed = AssociationUri.parse(uri) as AssociationUri.Local
        assertEquals(emptyList<AssociationUri.ProtocolVersion>(), parsed.protocolVersions)
    }

    @Test
    fun `rejects missing scheme`() {
        val uri = Uri.parse("/v1/associate/local?association=$associationParam&port=8080")
        try {
            AssociationUri.parse(uri); fail("expected MwaAssociationException")
        } catch (e: MwaAssociationException) {
            assertTrue(e.message!!.contains("scheme"))
        }
    }

    @Test
    fun `rejects wrong scheme`() {
        val uri = Uri.parse("https://example.com/v1/associate/local?association=$associationParam&port=8080")
        try {
            AssociationUri.parse(uri); fail("expected MwaAssociationException")
        } catch (e: MwaAssociationException) {
            assertTrue(e.message!!.contains("scheme"))
        }
    }

    @Test
    fun `rejects missing port on local URI`() {
        val uri = Uri.parse("solana-wallet:/v1/associate/local?association=$associationParam")
        try {
            AssociationUri.parse(uri); fail("expected MwaAssociationException")
        } catch (e: MwaAssociationException) {
            assertTrue(e.message!!.contains("port"))
        }
    }

    @Test
    fun `rejects negative port on local URI`() {
        val uri = Uri.parse("solana-wallet:/v1/associate/local?association=$associationParam&port=-1")
        try {
            AssociationUri.parse(uri); fail("expected MwaAssociationException")
        } catch (e: MwaAssociationException) {
            assertTrue(e.message!!.contains("port"))
        }
    }

    @Test
    fun `rejects missing association parameter`() {
        val uri = Uri.parse("solana-wallet:/v1/associate/local?port=8080")
        try {
            AssociationUri.parse(uri); fail("expected MwaAssociationException")
        } catch (e: MwaAssociationException) {
            assertTrue(e.message!!.contains("association"))
        }
    }

    @Test
    fun `rejects malformed association key bytes`() {
        // A 64-byte garbage value is base64url-decodable but not a
        // SEC1 uncompressed point, so the parser should reject it
        // before any handshake code touches it.
        val garbage = Base64Url.encode(ByteArray(64) { 0x42 })
        val uri = Uri.parse("solana-wallet:/v1/associate/local?association=$garbage&port=8080")
        try {
            AssociationUri.parse(uri); fail("expected MwaAssociationException")
        } catch (e: MwaAssociationException) {
            assertTrue(e.message!!.contains("SEC1") || e.message!!.contains("65-byte"))
        }
    }

    @Test
    fun `rejects unsupported path`() {
        val uri = Uri.parse("solana-wallet:/v1/associate/wormhole?association=$associationParam")
        try {
            AssociationUri.parse(uri); fail("expected MwaAssociationException")
        } catch (e: MwaAssociationException) {
            assertTrue(e.message!!.contains("path"))
        }
    }

    @Test
    fun `rejects unknown protocol version token`() {
        val uri = Uri.parse(
            "solana-wallet:/v1/associate/local?association=$associationParam&port=8080&v=v999"
        )
        try {
            AssociationUri.parse(uri); fail("expected MwaAssociationException")
        } catch (e: MwaAssociationException) {
            assertTrue(e.message!!.contains("protocol version"))
        }
    }

    @Test
    fun `roundtrip preserves equality`() {
        val uri = Uri.parse(
            "solana-wallet:/v1/associate/local?association=$associationParam&port=4321&v=v1"
        )
        val a = AssociationUri.parse(uri) as AssociationUri.Local
        val b = AssociationUri.parse(uri) as AssociationUri.Local
        // ByteArray-keyed data classes need contentEquals; data class
        // equality is implemented manually.
        assertEquals(a.port, b.port)
        assertArrayEquals(a.associationPublicKey, b.associationPublicKey)
        assertEquals(a.protocolVersions, b.protocolVersions)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
