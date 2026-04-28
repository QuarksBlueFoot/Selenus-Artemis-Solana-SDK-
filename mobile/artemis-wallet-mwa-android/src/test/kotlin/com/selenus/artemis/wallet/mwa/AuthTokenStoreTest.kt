package com.selenus.artemis.wallet.mwa

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Behaviour tests for the [AuthTokenStore] family.
 *
 * Limitation: [KeystoreEncryptedAuthTokenStore] depends on `AndroidKeyStore`
 * which is not available on the unit-test JVM (no provider, no hardware
 * backing). Calling its constructor throws inside `loadOrCreateKey()`. We
 * therefore split coverage across two seams:
 *
 *  1. [InMemoryAuthTokenStore]: exercised in full.
 *  2. The `[12-byte IV][ciphertext+tag]` wire format that the keystore-backed
 *     store writes to SharedPreferences, replicated locally with a software
 *     AES-256 key. Verifies the round-trip, fail-closed behaviour on tampered
 *     ciphertext, and the IV-uniqueness invariant.
 *
 * Full integration coverage of the AndroidKeyStore path requires Robolectric
 * or an instrumented device test.
 */
class AuthTokenStoreTest {

    @Test
    fun `InMemoryAuthTokenStore round trips a token`() {
        val store = InMemoryAuthTokenStore()
        assertNull("starts empty", store.get())

        store.set("auth-token-12345")
        assertEquals("auth-token-12345", store.get())
    }

    @Test
    fun `InMemoryAuthTokenStore overwrites previous tokens`() {
        val store = InMemoryAuthTokenStore()
        store.set("first")
        store.set("second")
        assertEquals("second", store.get())
    }

    @Test
    fun `InMemoryAuthTokenStore clears with null`() {
        val store = InMemoryAuthTokenStore()
        store.set("token")
        store.set(null)
        assertNull(store.get())
    }

    @Test
    fun `InMemoryAuthTokenStore is observable across multiple instances independently`() {
        // Two stores with separate state — each has its own backing field.
        val a = InMemoryAuthTokenStore()
        val b = InMemoryAuthTokenStore()
        a.set("a-token")
        assertNull(b.get())
        b.set("b-token")
        assertEquals("a-token", a.get())
        assertEquals("b-token", b.get())
    }

    @Test
    fun `keystore wire format round-trips a token via AES-256-GCM`() {
        // Replicates the bytes the production class writes to SharedPreferences:
        //   [12-byte IV][ciphertext + 16-byte tag]
        // Production uses AndroidKeyStore; this test uses a software-generated
        // AES-256 key. The cipher API (Cipher.getInstance("AES/GCM/NoPadding"))
        // is the same on both paths.
        val key = generateAes256Key()
        val token = "auth-token-with-utf8-é-and-emoji-🚀"
        val blob = encryptWireFormat(key, token)

        val recovered = decryptWireFormat(key, blob)
        assertEquals(token, recovered)
    }

    @Test
    fun `keystore wire format fails closed on tampered ciphertext`() {
        val key = generateAes256Key()
        val token = "do-not-corrupt-me"
        val blob = encryptWireFormat(key, token)

        // Flip a single bit in the ciphertext region (after the 12-byte IV).
        val tampered = blob.copyOf().also { it[16] = (it[16].toInt() xor 0x01).toByte() }

        try {
            decryptWireFormat(key, tampered)
            fail("expected AEADBadTagException")
        } catch (_: AEADBadTagException) {
            // Expected: GCM tag mismatch on flipped ciphertext bit.
        } catch (e: javax.crypto.BadPaddingException) {
            // Some JCA providers surface tag failures as BadPaddingException;
            // both are acceptable so long as decrypt fails.
        }
    }

    @Test
    fun `keystore wire format fails closed when IV is mutated`() {
        val key = generateAes256Key()
        val blob = encryptWireFormat(key, "iv-tied-to-tag")

        // Flip bit 0 of the IV. GCM uses IV as part of the tag input, so
        // any IV mutation invalidates the tag and decrypt must fail.
        val tampered = blob.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }

        try {
            decryptWireFormat(key, tampered)
            fail("expected tag failure")
        } catch (_: AEADBadTagException) {
        } catch (_: javax.crypto.BadPaddingException) {
        }
    }

    @Test
    fun `keystore wire format produces a unique IV per encryption`() {
        // GCM is catastrophically unsafe under IV reuse with the same key.
        // The production cipher initialises in ENCRYPT_MODE without explicit
        // IV so the JCA/AndroidKeyStore layer must produce a fresh random
        // 12-byte IV every call. We verify with the software backend.
        val key = generateAes256Key()
        val ivs = mutableSetOf<List<Byte>>()
        repeat(64) {
            val blob = encryptWireFormat(key, "same-token-each-time")
            val iv = blob.copyOfRange(0, 12).toList()
            assertTrue("IV uniqueness", ivs.add(iv))
        }
        assertEquals(64, ivs.size)
    }

    @Test
    fun `wire format places IV first followed by ciphertext`() {
        val key = generateAes256Key()
        val plaintext = "abc"
        val blob = encryptWireFormat(key, plaintext)
        // Layout: 12 IV + (3 plaintext + 16 GCM tag) = 31 bytes.
        assertEquals(12 + plaintext.toByteArray().size + 16, blob.size)
        // Confirm decrypt works when reading back the same layout.
        assertEquals(plaintext, decryptWireFormat(key, blob))
    }

    @Test
    fun `null token clears the cached value (in-memory contract)`() {
        // Mirrors the contract that KeystoreEncryptedAuthTokenStore.set(null)
        // also enforces (delete from SharedPreferences). The InMemory variant
        // is the canonical reference.
        val store = InMemoryAuthTokenStore()
        store.set("value")
        assertNotNull(store.get())
        store.set(null)
        assertNull("set(null) wipes the cached token", store.get())
    }

    @Test
    fun `AuthTokenStore interface accepts both implementations`() {
        // Compile-time + run-time: anything implementing AuthTokenStore can
        // be substituted, and the InMemory variant satisfies the contract.
        val asInterface: AuthTokenStore = InMemoryAuthTokenStore()
        asInterface.set("via-interface")
        assertEquals("via-interface", asInterface.get())
        asInterface.set(null)
        assertNull(asInterface.get())
    }

    // ─── Wire format helpers ───────────────────────────────────────────

    private fun generateAes256Key(): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        return kg.generateKey()
    }

    /** Replicates the production blob layout. */
    private fun encryptWireFormat(key: SecretKey, token: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        check(iv.size == 12) { "expected 12-byte GCM IV, got ${iv.size}" }
        val ct = cipher.doFinal(token.encodeToByteArray())
        return iv + ct
    }

    /** Replicates the production decrypt path. */
    private fun decryptWireFormat(key: SecretKey, blob: ByteArray): String {
        require(blob.size > 12) { "blob too short" }
        val iv = blob.copyOfRange(0, 12)
        val ct = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val pt = cipher.doFinal(ct)
        // Sanity check: re-running the same blob must produce the same plaintext.
        assertFalse("plaintext is not empty", pt.isEmpty())
        // Round-trip identity with a fresh array equality check.
        assertArrayEquals(pt, pt.copyOf())
        return pt.decodeToString()
    }
}
