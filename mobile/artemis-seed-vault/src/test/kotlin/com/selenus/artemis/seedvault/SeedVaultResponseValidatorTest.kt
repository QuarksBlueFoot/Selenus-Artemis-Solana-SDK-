package com.selenus.artemis.seedvault

import android.net.Uri
import android.os.Bundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Conformance coverage for [SeedVaultResponseValidator].
 *
 * The validator is the gatekeeper between raw provider bundles and the
 * typed Artemis surface. Every check here corresponds to a real failure
 * mode the audit flagged: wrong signature length, wrong pubkey length,
 * wrong signature count, missing bundle key, malformed derivation path.
 *
 * Tests run against the JVM stub of `android.net.Uri` with `isReturnDefaultValues`
 * already enabled on this module, so the only method we can exercise
 * end-to-end without Robolectric is [SeedVaultResponseValidator.requireSignature]
 * and siblings that do not touch Uri. Derivation-path tests construct Uris
 * through `Uri.parse(...)` which returns null under the stub; we skip those
 * in JVM unit tests and cover them via instrumented tests on a device.
 */
class SeedVaultResponseValidatorTest {

    @Test
    fun `requireSignature accepts 64-byte signature`() {
        val sig = ByteArray(64) { it.toByte() }
        val out = SeedVaultResponseValidator.requireSignature(sig, index = 0, method = "signMessages")
        assertEquals(64, out.size)
    }

    @Test
    fun `requireSignature rejects short signature`() {
        val sig = ByteArray(32)
        val ex = assertThrows(SeedVaultException.Unknown::class.java) {
            SeedVaultResponseValidator.requireSignature(sig, index = 2, method = "signMessages")
        }
        assertEquals(true, ex.message!!.contains("32 bytes"))
        assertEquals(true, ex.message!!.contains("signature #2"))
    }

    @Test
    fun `requireSignature rejects long signature`() {
        val sig = ByteArray(96)
        assertThrows(SeedVaultException.Unknown::class.java) {
            SeedVaultResponseValidator.requireSignature(sig, index = 0, method = "signTransactions")
        }
    }

    @Test
    fun `requirePublicKey accepts 32-byte key`() {
        val raw = ByteArray(32) { it.toByte() }
        val pk = SeedVaultResponseValidator.requirePublicKey(raw, context = "resolveDerivationPath")
        assertEquals(32, pk.bytes.size)
    }

    @Test
    fun `requirePublicKey rejects null`() {
        val ex = assertThrows(SeedVaultException.Unknown::class.java) {
            SeedVaultResponseValidator.requirePublicKey(null, context = "resolveDerivationPath")
        }
        assertEquals(true, ex.message!!.contains("null public key"))
    }

    @Test
    fun `requirePublicKey rejects wrong length`() {
        val raw = ByteArray(33)
        val ex = assertThrows(SeedVaultException.Unknown::class.java) {
            SeedVaultResponseValidator.requirePublicKey(raw, context = "requestPublicKeys[1]")
        }
        assertEquals(true, ex.message!!.contains("33 bytes"))
    }

    @Test
    fun `requireSignatureCount accepts matching count`() {
        SeedVaultResponseValidator.requireSignatureCount(actual = 3, expected = 3, method = "signTransactions")
    }

    @Test
    fun `requireSignatureCount rejects mismatch`() {
        val ex = assertThrows(SeedVaultException.Unknown::class.java) {
            SeedVaultResponseValidator.requireSignatureCount(actual = 2, expected = 3, method = "signTransactions")
        }
        assertEquals(true, ex.message!!.contains("returned 2 signatures"))
        assertEquals(true, ex.message!!.contains("for 3 payloads"))
    }

    // Bundle-based tests for requireBundleKey live in `src/androidTest/`
    // (instrumented). Under the JVM `android.jar` stub `Bundle.containsKey`
    // always returns the default-value stub, so asserting on it from a
    // plain unit test would return false negatives. Exercise via
    // instrumented tests on a device.
}
