package com.selenus.artemis.wallet.mwa

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.PlatformBase64
import com.selenus.artemis.wallet.mwa.protocol.MwaSignInPayload
import com.selenus.artemis.wallet.mwa.protocol.MwaSignInResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour tests for [MwaSiwsValidator]. End-to-end round trip uses a real
 * Artemis Ed25519 keypair to sign the canonical SIWS message and feed the
 * signature back through [MwaSiwsValidator.verify]. Tampered signatures and
 * mismatched messages must reject; replay checks gate nonce reuse.
 */
class MwaSiwsValidatorTest {

    private val payload = MwaSignInPayload(
        domain = "artemis.test",
        uri = "https://artemis.test/login",
        statement = "Sign in to Artemis",
        version = "1",
        chainId = "solana:mainnet",
        nonce = "abc123",
        issuedAt = "2026-04-27T12:00:00Z",
        resources = listOf("https://artemis.test/tos", "https://artemis.test/privacy")
    )

    @Test
    fun `composed message includes every populated field`() {
        val keypair = Keypair.generate()
        val address = Base58.encode(keypair.publicKey.bytes)
        val msg = MwaSiwsValidator.composeMessage(payload, address)

        assertTrue(
            "message announces the requesting domain",
            msg.startsWith("artemis.test wants you to sign in with your Solana account:\n$address")
        )
        assertTrue("statement embedded", msg.contains("Sign in to Artemis"))
        assertTrue("URI embedded", msg.contains("URI: https://artemis.test/login"))
        assertTrue("version embedded", msg.contains("Version: 1"))
        assertTrue("chain id embedded", msg.contains("Chain ID: solana:mainnet"))
        assertTrue("nonce embedded", msg.contains("Nonce: abc123"))
        assertTrue("issuedAt embedded", msg.contains("Issued At: 2026-04-27T12:00:00Z"))
        assertTrue("resources block emitted", msg.contains("Resources:"))
        assertTrue(msg.contains("- https://artemis.test/tos"))
        assertTrue(msg.contains("- https://artemis.test/privacy"))
    }

    @Test
    fun `composed message omits absent fields`() {
        val minimal = MwaSignInPayload(domain = "min.test")
        val keypair = Keypair.generate()
        val address = Base58.encode(keypair.publicKey.bytes)
        val msg = MwaSiwsValidator.composeMessage(minimal, address)

        // Only the announce line + address; nothing else.
        assertEquals(
            "min.test wants you to sign in with your Solana account:\n$address",
            msg
        )
    }

    @Test
    fun `verify accepts a real ed25519 signature on the canonical message`() {
        val keypair = Keypair.generate()
        val address = Base58.encode(keypair.publicKey.bytes)
        val message = MwaSiwsValidator.composeMessage(payload, address)
        val messageBytes = message.encodeToByteArray()
        val signature = keypair.sign(messageBytes)

        val result = MwaSiwsValidator.verify(
            originalPayload = payload,
            result = MwaSignInResult(
                address = address,
                signedMessage = PlatformBase64.encode(messageBytes),
                signature = PlatformBase64.encode(signature),
                signatureType = "ed25519"
            )
        )

        assertTrue("verification ok", result is SiwsVerification.Valid)
        result as SiwsVerification.Valid
        assertEquals(address, result.address)
        assertEquals(message, result.message)
    }

    @Test
    fun `verify rejects a tampered signature`() {
        val keypair = Keypair.generate()
        val address = Base58.encode(keypair.publicKey.bytes)
        val message = MwaSiwsValidator.composeMessage(payload, address)
        val messageBytes = message.encodeToByteArray()
        val signature = keypair.sign(messageBytes)
        // Flip a single bit in the signature.
        val tampered = signature.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }

        val result = MwaSiwsValidator.verify(
            originalPayload = payload,
            result = MwaSignInResult(
                address = address,
                signedMessage = PlatformBase64.encode(messageBytes),
                signature = PlatformBase64.encode(tampered),
                signatureType = "ed25519"
            )
        )

        assertEquals(SiwsVerification.BadSignature, result)
    }

    @Test
    fun `verify flags message mismatch when wallet silently rewrites payload`() {
        val keypair = Keypair.generate()
        val address = Base58.encode(keypair.publicKey.bytes)
        // Wallet signs a message with a DIFFERENT statement than the app sent.
        val divergedMessage = MwaSiwsValidator.composeMessage(
            payload.copy(statement = "Sign in to MALICIOUS"),
            address
        )
        val divergedBytes = divergedMessage.encodeToByteArray()
        val signature = keypair.sign(divergedBytes)

        val result = MwaSiwsValidator.verify(
            originalPayload = payload,
            result = MwaSignInResult(
                address = address,
                signedMessage = PlatformBase64.encode(divergedBytes),
                signature = PlatformBase64.encode(signature),
                signatureType = "ed25519"
            )
        )

        assertTrue("classified as Mismatch", result is SiwsVerification.Mismatch)
        result as SiwsVerification.Mismatch
        assertEquals("message", result.field)
    }

    @Test
    fun `verify rejects malformed base64 inputs`() {
        val keypair = Keypair.generate()
        val address = Base58.encode(keypair.publicKey.bytes)

        val result = MwaSiwsValidator.verify(
            originalPayload = payload,
            result = MwaSignInResult(
                address = address,
                signedMessage = "***not-base64***",
                signature = "***also-not-base64***",
                signatureType = "ed25519"
            )
        )

        assertTrue(result is SiwsVerification.Malformed)
    }

    @Test
    fun `verify rejects signatures whose length is not 64 bytes`() {
        val keypair = Keypair.generate()
        val address = Base58.encode(keypair.publicKey.bytes)
        val message = MwaSiwsValidator.composeMessage(payload, address)
        val messageBytes = message.encodeToByteArray()

        val result = MwaSiwsValidator.verify(
            originalPayload = payload,
            result = MwaSignInResult(
                address = address,
                signedMessage = PlatformBase64.encode(messageBytes),
                signature = PlatformBase64.encode(ByteArray(32) { 1 }), // wrong length
                signatureType = "ed25519"
            )
        )

        assertTrue(result is SiwsVerification.Malformed)
        result as SiwsVerification.Malformed
        assertTrue(result.reason.contains("64"))
    }

    @Test
    fun `checkReplay catches nonce mismatch`() {
        val replayPayload = payload.copy(nonce = "actual-nonce")
        val reason = MwaSiwsValidator.checkReplay(
            payload = replayPayload,
            expectedNonce = "expected-nonce",
            nowEpochSeconds = parseEpoch("2026-04-27T12:00:00Z")
        )
        assertNotNull(reason)
        assertTrue(reason!!.contains("nonce"))
    }

    @Test
    fun `checkReplay accepts matching nonce within drift window`() {
        val now = parseEpoch("2026-04-27T12:00:30Z") // 30s after issuedAt
        val reason = MwaSiwsValidator.checkReplay(
            payload = payload,
            expectedNonce = payload.nonce,
            nowEpochSeconds = now,
            allowedClockSkewSeconds = 60
        )
        assertNull("within 60s of issuedAt. no replay flag expected", reason)
    }

    @Test
    fun `checkReplay flags second invocation with same nonce as replay`() {
        // Apps store nonces. The second call passes a different "expected"
        // because the previous nonce is now invalidated.
        val firstUseExpected = payload.nonce
        val firstReason = MwaSiwsValidator.checkReplay(
            payload = payload,
            expectedNonce = firstUseExpected,
            nowEpochSeconds = parseEpoch("2026-04-27T12:00:00Z")
        )
        assertNull("first use accepted", firstReason)

        // Second use: caller now expects a different nonce because they
        // rotated their server-side state. Replay flagged.
        val secondReason = MwaSiwsValidator.checkReplay(
            payload = payload, // same payload, same nonce
            expectedNonce = "rotated-nonce-not-${payload.nonce}",
            nowEpochSeconds = parseEpoch("2026-04-27T12:00:01Z")
        )
        assertNotNull(secondReason)
        assertTrue(secondReason!!.contains("nonce"))
    }

    @Test
    fun `checkReplay flags expired payloads`() {
        // issuedAt close to now (so the drift check passes) but
        // expirationTime well in the past (so the expiration check fails).
        val expiringPayload = payload.copy(
            issuedAt = "2026-04-27T12:00:00Z",
            expirationTime = "2026-04-27T11:55:00Z"
        )
        // Now is 30 seconds after issuedAt. within the 60-second drift.
        // but five+ minutes after expirationTime + 60s skew.
        val reason = MwaSiwsValidator.checkReplay(
            payload = expiringPayload,
            expectedNonce = expiringPayload.nonce,
            nowEpochSeconds = parseEpoch("2026-04-27T12:00:30Z"),
            allowedClockSkewSeconds = 60
        )
        assertNotNull(reason)
        assertTrue(
            "reason mentions expiry, got: $reason",
            reason!!.contains("expired")
        )
    }

    /** Tiny helper used only inside this test. relies on java.time. */
    private fun parseEpoch(iso: String): Long =
        java.time.Instant.parse(iso).epochSecond
}
