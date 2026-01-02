package com.selenus.artemis.runtime

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64 as JBase64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal actual object PlatformCrypto {
    actual fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        for (p in parts) md.update(p)
        return md.digest()
    }

    actual fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    actual fun pbkdf2Sha512(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBits: Int
    ): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val spec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        return factory.generateSecret(spec).encoded
    }

    actual fun secureRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return bytes
    }
}

internal actual object PlatformEd25519 {
    actual fun publicKeyFromSeed(seed: ByteArray): ByteArray {
        val priv = Ed25519PrivateKeyParameters(seed, 0)
        return priv.generatePublicKey().encoded
    }

    actual fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        val priv = Ed25519PrivateKeyParameters(seed, 0)
        val signer = Ed25519Signer()
        signer.init(true, priv)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    actual fun verify(publicKey: ByteArray, signature: ByteArray, message: ByteArray): Boolean {
        return try {
            val pub = Ed25519PublicKeyParameters(publicKey, 0)
            val signer = Ed25519Signer()
            signer.init(false, pub)
            signer.update(message, 0, message.size)
            signer.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }

    actual fun isOnCurve(publicKey: ByteArray): Boolean {
        val P = BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
        val D = BigInteger("-121665").multiply(BigInteger("121666").modInverse(P)).mod(P)
        try {
            val yBytes = publicKey.clone()
            yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()
            val y = BigInteger(1, yBytes.reversedArray())
            if (y >= P) return false
            val y2 = y.multiply(y).mod(P)
            val one = BigInteger.ONE
            val num = y2.subtract(one).mod(P)
            val den = D.multiply(y2).add(one).mod(P)
            if (den == BigInteger.ZERO) return false
            val x2 = num.multiply(den.modInverse(P)).mod(P)
            if (x2 == BigInteger.ZERO) return true
            val exp = P.subtract(one).divide(BigInteger("2"))
            val check = x2.modPow(exp, P)
            return check == one
        } catch (e: Exception) {
            return false
        }
    }
}

actual object PlatformBase64 {
    actual fun encode(data: ByteArray): String =
        JBase64.getEncoder().encodeToString(data)

    actual fun decode(data: String): ByteArray =
        JBase64.getDecoder().decode(data)

    actual fun urlEncode(data: ByteArray): String =
        JBase64.getUrlEncoder().withoutPadding().encodeToString(data)

    actual fun urlDecode(data: String): ByteArray =
        JBase64.getUrlDecoder().decode(data)
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun currentNanoTime(): Long = System.nanoTime()
