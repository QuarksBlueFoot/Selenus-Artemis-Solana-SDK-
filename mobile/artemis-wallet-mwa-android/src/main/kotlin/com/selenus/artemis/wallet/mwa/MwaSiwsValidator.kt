/*
 * MwaSiwsValidator
 *
 * Spec-first validation and reconstruction of Sign In With Solana payloads.
 *
 * Background: upstream MWA spec does not fully reference the SIWS message
 * format (upstream #193) and third-party wallets have inconsistent JSON
 * round-tripping behavior that crashes React Native apps (#1331). Artemis
 * provides this validator so apps can:
 *
 *   1. Compose a well-formed SIWS message bit-by-bit.
 *   2. Verify a wallet's signed response against what was requested.
 *   3. Detect and reject inconsistencies between the returned account and the
 *      message Solana asked the user to sign.
 *
 * The verification path uses the existing Artemis Ed25519 implementation, so
 * it works against any wallet that produced an ed25519 signature over the
 * ASCII-canonical SIWS message (the format Phantom, Solflare, Backpack and
 * the Saga stock wallet all produce today).
 */
package com.selenus.artemis.wallet.mwa

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.PlatformBase64
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.wallet.mwa.protocol.MwaSignInPayload
import com.selenus.artemis.wallet.mwa.protocol.MwaSignInResult

/**
 * Outcome of validating a [MwaSignInResult] against the [MwaSignInPayload]
 * the app requested.
 */
sealed class SiwsVerification {

    /** Signature is authentic and every requested field is consistent. */
    data class Valid(
        /** The base58 address the wallet signed as. */
        val address: String,
        /** The exact message (UTF-8 decoded) that the wallet signed. */
        val message: String,
    ) : SiwsVerification()

    /** Signature failed cryptographic verification. */
    object BadSignature : SiwsVerification()

    /** Signature valid, but the content does not match the original payload. */
    data class Mismatch(val field: String, val expected: String?, val actual: String?) : SiwsVerification()

    /** Input could not be decoded (bad base58, bad base64, malformed message). */
    data class Malformed(val reason: String) : SiwsVerification()
}

/**
 * Build and validate SIWS payloads.
 */
object MwaSiwsValidator {

    /**
     * Render a SIWS payload into the canonical EIP-4361-style message the
     * wallet will display and sign. The format matches the reference
     * implementation used by Phantom / Solflare / Saga stock:
     *
     * ```
     * {domain} wants you to sign in with your Solana account:
     * {address}
     *
     * {statement}
     *
     * URI: {uri}
     * Version: {version}
     * Chain ID: {chainId}
     * Nonce: {nonce}
     * Issued At: {issuedAt}
     * Expiration Time: {expirationTime}
     * Not Before: {notBefore}
     * Request ID: {requestId}
     * Resources:
     * - {resource1}
     * - {resource2}
     * ```
     *
     * Fields absent from the payload are omitted from the rendered message
     * so the wallet displays only what was requested. [address] is the base58
     * account the wallet will sign as and must be known before rendering.
     */
    fun composeMessage(payload: MwaSignInPayload, address: String): String = buildString {
        append(payload.domain)
        append(" wants you to sign in with your Solana account:\n")
        append(address)
        if (!payload.statement.isNullOrBlank()) {
            append("\n\n")
            append(payload.statement)
        }
        val fields = linkedMapOf<String, String?>()
        payload.uri?.let { fields["URI"] = it }
        payload.version?.let { fields["Version"] = it }
        payload.chainId?.let { fields["Chain ID"] = it }
        payload.nonce?.let { fields["Nonce"] = it }
        payload.issuedAt?.let { fields["Issued At"] = it }
        payload.expirationTime?.let { fields["Expiration Time"] = it }
        payload.notBefore?.let { fields["Not Before"] = it }
        payload.requestId?.let { fields["Request ID"] = it }

        if (fields.isNotEmpty()) {
            append("\n\n")
            fields.entries.forEachIndexed { i, (key, value) ->
                if (i > 0) append("\n")
                append(key)
                append(": ")
                append(value)
            }
        }

        val resources = payload.resources
        if (!resources.isNullOrEmpty()) {
            append("\nResources:")
            resources.forEach {
                append("\n- ")
                append(it)
            }
        }
    }

    /**
     * Verify a [MwaSignInResult] against the [MwaSignInPayload] the app
     * originally sent. Performs three checks:
     *
     * 1. Decodes the signed message and compares it against the canonical
     *    render of the original payload. Catches wallets that silently
     *    modify the payload (upstream #1331 and related).
     * 2. Verifies the wallet's ed25519 signature over the exact signed bytes.
     * 3. Confirms the returned address matches the signing public key.
     *
     * Returns [SiwsVerification.Valid] only when all three succeed. Specific
     * failures are distinguished so apps can surface targeted errors.
     */
    fun verify(
        originalPayload: MwaSignInPayload,
        result: MwaSignInResult,
    ): SiwsVerification {
        val address = result.address
        val signedMessageBytes = try {
            PlatformBase64.decode(result.signedMessage)
        } catch (_: Throwable) {
            return SiwsVerification.Malformed("signed_message is not valid base64")
        }
        val signatureBytes = try {
            PlatformBase64.decode(result.signature)
        } catch (_: Throwable) {
            return SiwsVerification.Malformed("signature is not valid base64")
        }
        if (signatureBytes.size != 64) {
            return SiwsVerification.Malformed(
                "signature must be 64 bytes, got ${signatureBytes.size}"
            )
        }

        val decodedMessage = signedMessageBytes.decodeToString()
        val expectedMessage = composeMessage(originalPayload, address)
        if (decodedMessage != expectedMessage) {
            return SiwsVerification.Mismatch(
                field = "message",
                expected = expectedMessage,
                actual = decodedMessage,
            )
        }

        val pubkeyBytes = try {
            Base58.decode(address)
        } catch (_: Throwable) {
            return SiwsVerification.Malformed("address is not valid base58")
        }
        if (pubkeyBytes.size != 32) {
            return SiwsVerification.Malformed(
                "address must decode to 32 bytes, got ${pubkeyBytes.size}"
            )
        }

        val ok = Pubkey(pubkeyBytes).verify(signatureBytes, signedMessageBytes)
        if (!ok) return SiwsVerification.BadSignature

        return SiwsVerification.Valid(
            address = address,
            message = decodedMessage,
        )
    }

    /**
     * Convenience: enforce a non-empty nonce and an [issuedAt] within the
     * allowed drift window. Apps should always call this on the happy path
     * before trusting a [SiwsVerification.Valid]; upstream specs do not
     * require nonce semantics so catching replays is the app's responsibility.
     *
     * [allowedClockSkewSeconds] defaults to 60 (a minute). Pass a larger
     * number if the app expects users on long-flight latency.
     *
     * Returns `null` when the replay check passes, or a human-readable
     * reason string when it fails.
     */
    fun checkReplay(
        payload: MwaSignInPayload,
        expectedNonce: String?,
        nowEpochSeconds: Long,
        allowedClockSkewSeconds: Long = 60,
    ): String? {
        if (expectedNonce != null && payload.nonce != expectedNonce) {
            return "nonce mismatch (expected $expectedNonce, got ${payload.nonce})"
        }
        val issuedAt = payload.issuedAt ?: return "missing issuedAt"
        val issuedSeconds = parseIsoSeconds(issuedAt)
            ?: return "issuedAt is not a valid ISO-8601 timestamp: $issuedAt"
        val drift = kotlin.math.abs(nowEpochSeconds - issuedSeconds)
        if (drift > allowedClockSkewSeconds) {
            return "issuedAt is $drift seconds off the server clock (allowed=$allowedClockSkewSeconds)"
        }
        val expiration = payload.expirationTime
        if (expiration != null) {
            val expirationSeconds = parseIsoSeconds(expiration)
                ?: return "expirationTime is not a valid ISO-8601 timestamp: $expiration"
            if (nowEpochSeconds > expirationSeconds + allowedClockSkewSeconds) {
                return "signed message expired at $expiration"
            }
        }
        return null
    }

    /**
     * Tiny ISO-8601 parser. Only handles the `YYYY-MM-DDTHH:mm:ss[.fff][Z|±HH:mm]`
     * subset that every SIWS implementation emits today. Returns the Unix
     * timestamp in seconds, or `null` when the string is not parseable.
     *
     * Avoids `java.time.*` so the helper is reusable from any Artemis module
     * without a JVM dependency gate.
     */
    private fun parseIsoSeconds(iso: String): Long? {
        val re = Regex(
            "^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?$"
        )
        val m = re.matchEntire(iso) ?: return null
        val (y, mo, d, h, mi, s, tz) = m.destructured
        val year = y.toInt()
        val month = mo.toInt()
        val day = d.toInt()
        val hour = h.toInt()
        val min = mi.toInt()
        val sec = s.toInt()
        // Days since epoch using the civil-to-days algorithm from Howard Hinnant.
        // Handles the full proleptic Gregorian range without needing `java.time`.
        val yr = year - if (month <= 2) 1 else 0
        val era = (if (yr >= 0) yr else yr - 399) / 400
        val yoe = (yr - era * 400)
        val doy = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val days = era * 146097L + doe - 719468L
        val baseSeconds = days * 86400L + hour * 3600L + min * 60L + sec
        val offsetSeconds = when {
            tz.isEmpty() || tz == "Z" -> 0L
            else -> {
                val sign = if (tz[0] == '+') 1 else -1
                val h2 = tz.substring(1, 3).toInt()
                val m2 = tz.substring(4, 6).toInt()
                sign * (h2 * 3600L + m2 * 60L)
            }
        }
        return baseSeconds - offsetSeconds
    }
}
