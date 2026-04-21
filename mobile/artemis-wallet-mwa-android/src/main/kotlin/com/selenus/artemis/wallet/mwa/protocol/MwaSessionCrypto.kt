/*
 * Transport-layer crypto for the MWA session handshake.
 *
 * Everything here is session/transport concern: Diffie-Hellman shared
 * secrets for peer-to-peer handshakes, HKDF-SHA256 expansion, and the
 * matching keypair generation. These primitives previously lived under
 * the `seedvault` package, which muddled two distinct responsibilities:
 *
 *   - Seed Vault is about custody: long-lived private keys, account
 *     derivation, signing operations gated by user consent.
 *   - Session / transport crypto is about a pair of endpoints agreeing on
 *     an ephemeral symmetric key so they can talk privately over an
 *     otherwise-public channel.
 *
 * The official Solana Mobile docs keep the two layers separated. Artemis
 * mirrors that split so nothing in the Seed Vault module depends on
 * transport-style DH primitives.
 */
package com.selenus.artemis.wallet.mwa.protocol

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Session-level crypto helpers used by the MWA handshake and other
 * endpoint-to-endpoint transports. Pure crypto primitives with no
 * dependency on Activity, Keystore, Binder, or any Android runtime
 * machinery, so the code is testable on the plain JVM and reusable by
 * non-MWA transports (custom pairing flows, sidechannel-hardened RPC).
 *
 * @see deriveX25519SharedSecret for ephemeral ECDH.
 * @see generateX25519Keypair for fresh keypair creation.
 */
object MwaSessionCrypto {

    private const val KEY_SIZE = 32
    private const val DOMAIN_SEPARATOR = "artemis:mwa:session:v1"
    private val random = SecureRandom()

    /**
     * Real X25519 ECDH shared secret, folded through HKDF-SHA256.
     *
     * Both peers run this with their own private scalar and the peer's
     * public point; they land on the same 32-byte session key. The raw
     * DH output is biased and is never handed to an AEAD directly. The
     * HKDF extract-and-expand step with an Artemis domain-separated salt
     * guarantees that two pairs running with the same DH secret but
     * different contexts end up with different session keys.
     *
     * @param myPrivate 32-byte X25519 private scalar.
     * @param peerPublic 32-byte X25519 public-key point.
     * @param context Domain-separation context. Distinct contexts produce
     *   distinct keys, even with identical DH inputs.
     * @return 32-byte session key.
     */
    fun deriveX25519SharedSecret(
        myPrivate: ByteArray,
        peerPublic: ByteArray,
        context: String = "shared-secret"
    ): ByteArray {
        require(myPrivate.size == 32) { "X25519 private key must be 32 bytes, got ${myPrivate.size}" }
        require(peerPublic.size == 32) { "X25519 public key must be 32 bytes, got ${peerPublic.size}" }
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(myPrivate, 0))
        val dhSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPublic, 0), dhSecret, 0)
        return hkdfExtractAndExpand(
            ikm = dhSecret,
            salt = DOMAIN_SEPARATOR.toByteArray(),
            info = context.toByteArray(),
            length = KEY_SIZE
        )
    }

    /**
     * Generate a fresh X25519 keypair. Returns (private 32 bytes, public 32 bytes).
     */
    fun generateX25519Keypair(): Pair<ByteArray, ByteArray> {
        val gen = X25519KeyPairGenerator().apply {
            init(X25519KeyGenerationParameters(random))
        }
        val kp = gen.generateKeyPair()
        val priv = (kp.private as X25519PrivateKeyParameters).encoded
        val pub = (kp.public as X25519PublicKeyParameters).encoded
        return priv to pub
    }

    private fun hkdfExtractAndExpand(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int
    ): ByteArray {
        require(length in 1..(255 * 32))
        val prk = hmacSha256(salt, ikm)
        var t = ByteArray(0)
        val okm = ByteArray(length)
        var offset = 0
        var counter = 1
        while (offset < length) {
            val input = ByteArray(t.size + info.size + 1).also { buf ->
                t.copyInto(buf, 0)
                info.copyInto(buf, t.size)
                buf[t.size + info.size] = counter.toByte()
            }
            t = hmacSha256(prk, input)
            val take = minOf(t.size, length - offset)
            System.arraycopy(t, 0, okm, offset, take)
            offset += take
            counter++
        }
        return okm
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val effectiveKey = if (key.isEmpty()) ByteArray(32) else key
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(effectiveKey, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
