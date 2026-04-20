/*
 * Response validation for the Seed Vault IPC.
 *
 * Phase 3.4 of the audit plan: malformed provider results used to fall
 * through to generic `Unknown` errors with no context. This validator
 * checks every field the Artemis layer consumes and throws a typed error
 * the moment the provider sends something that cannot be correct.
 *
 * Contract enforced here:
 *   - response Bundles are non-null and contain the expected keys
 *   - signature arrays are exactly 64 bytes (Ed25519)
 *   - public keys are exactly 32 bytes (Ed25519)
 *   - signature counts match the requested payload count
 *   - derivation path URIs are non-null and use `bip32:` / `bip44:` schemes
 */
package com.selenus.artemis.seedvault

import android.net.Uri
import android.os.Bundle
import com.selenus.artemis.runtime.Pubkey

internal object SeedVaultResponseValidator {

    private const val ED25519_SIGNATURE_LEN = 64
    private const val ED25519_PUBLIC_KEY_LEN = 32

    fun requireBundleKey(bundle: Bundle, key: String, method: String) {
        if (!bundle.containsKey(key)) {
            throw SeedVaultException.Unknown(
                "Seed Vault $method response missing required key: $key"
            )
        }
    }

    fun requireSignature(bytes: ByteArray, index: Int, method: String): ByteArray {
        if (bytes.size != ED25519_SIGNATURE_LEN) {
            throw SeedVaultException.Unknown(
                "Seed Vault $method signature #$index has ${bytes.size} bytes, expected $ED25519_SIGNATURE_LEN"
            )
        }
        return bytes
    }

    fun requirePublicKey(bytes: ByteArray?, context: String): Pubkey {
        if (bytes == null) {
            throw SeedVaultException.Unknown("Seed Vault $context: null public key")
        }
        if (bytes.size != ED25519_PUBLIC_KEY_LEN) {
            throw SeedVaultException.Unknown(
                "Seed Vault $context: public key is ${bytes.size} bytes, expected $ED25519_PUBLIC_KEY_LEN"
            )
        }
        return Pubkey(bytes)
    }

    fun requireSignatureCount(actual: Int, expected: Int, method: String) {
        if (actual != expected) {
            throw SeedVaultException.Unknown(
                "Seed Vault $method returned $actual signatures for $expected payloads"
            )
        }
    }

    fun requireDerivationPath(path: Uri, context: String) {
        val scheme = path.scheme
        if (scheme != "bip32" && scheme != "bip44") {
            throw SeedVaultException.Unknown(
                "Seed Vault $context: derivation-path URI must use bip32:/bip44: scheme, got $scheme"
            )
        }
    }
}
