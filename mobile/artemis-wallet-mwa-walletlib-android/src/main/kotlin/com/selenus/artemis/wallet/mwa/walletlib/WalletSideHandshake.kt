package com.selenus.artemis.wallet.mwa.walletlib

import com.selenus.artemis.wallet.mwa.protocol.Aes128Gcm
import com.selenus.artemis.wallet.mwa.protocol.EcP256
import com.selenus.artemis.wallet.mwa.protocol.HkdfSha256
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.PublicKey

/**
 * Result of the wallet end of the HELLO exchange.
 *
 * @property cipher AES-128-GCM cipher initialised with the derived
 *   session key. Subsequent JSON-RPC frames are encrypted under this.
 * @property protocolVersion The version we agreed on. Wallet picks
 *   the highest version it supports that the dApp also advertised; we
 *   pin to V1 for any dApp that claimed V1, else LEGACY.
 * @property initialRecvSeq The next sequence number the dispatcher
 *   expects on inbound RPC frames. Always 1 because the wallet did
 *   not consume any encrypted frames during the handshake.
 * @property initialSendSeq The next sequence number the dispatcher
 *   should use on outbound RPC frames. 2 when we encrypted a
 *   `SessionProperties` envelope inside HELLO_RSP, 1 otherwise.
 */
internal data class HandshakeOutcome(
    val cipher: Aes128Gcm,
    val protocolVersion: AssociationUri.ProtocolVersion,
    val initialRecvSeq: Int,
    val initialSendSeq: Int
)

/**
 * Wallet end of the MWA HELLO exchange.
 *
 * Inverse of `MwaSession.connectLocal` in the dApp-side module:
 *  1. Receive `HELLO_REQ`: 65 bytes ECDH point Qd || 64 bytes ECDSA
 *     signature Sa(Qd) signed by the dApp's association key.
 *  2. Verify Sa with the URI's `associationPublicKey`.
 *  3. Generate our own ephemeral P-256 keypair Qw.
 *  4. Compute IKM = ECDH(Qw_priv, Qd), salt = associationPublicKey
 *     bytes, derive a 16-byte AES-128-GCM session key via HKDF-SHA256.
 *  5. Send `HELLO_RSP`: 65 bytes Qw || encrypted SessionProperties.
 *
 * The handshake never touches the wallet's long-term keys. Only the
 * dApp's association ECDSA public key (already on the wire in the
 * intent URI) is referenced.
 */
internal object WalletSideHandshake {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun perform(
        transport: WalletTransport,
        associationPublicKey: ByteArray,
        supportedVersions: List<AssociationUri.ProtocolVersion>,
        dappAdvertisedVersions: List<AssociationUri.ProtocolVersion>,
        timeoutMs: Long = 10_000
    ): HandshakeOutcome {
        require(associationPublicKey.size == 65 && associationPublicKey[0] == 0x04.toByte()) {
            "associationPublicKey must be a 65-byte SEC1 uncompressed P-256 point"
        }
        val helloReq = withTimeout(timeoutMs) { transport.incoming.receive() }
        // 65 bytes Qd || 64 bytes Sa. Anything shorter cannot be a
        // valid HELLO_REQ.
        if (helloReq.size < 129) {
            throw MwaHandshakeException(
                "HELLO_REQ too short: ${helloReq.size} bytes (expected >= 129)"
            )
        }
        val qd = helloReq.copyOfRange(0, 65)
        val sa = helloReq.copyOfRange(65, 129)
        if (qd[0] != 0x04.toByte()) {
            throw MwaHandshakeException("HELLO_REQ Qd is not a SEC1 uncompressed point")
        }

        val associationKey: PublicKey = try {
            EcP256.publicKeyFromX962(associationPublicKey)
        } catch (e: Throwable) {
            throw MwaHandshakeException("invalid association public key", e)
        }
        val signatureValid = try {
            EcP256.verifyP1363(associationKey, qd, sa)
        } catch (e: Throwable) {
            throw MwaHandshakeException("association signature verification threw", e)
        }
        if (!signatureValid) {
            throw MwaHandshakeException("association signature does not verify HELLO_REQ Qd")
        }

        // dApp's ephemeral public key Qd (now trusted because the
        // signature verified under the URI's association key).
        val dappEphemeralPublic: PublicKey = try {
            EcP256.publicKeyFromX962(qd)
        } catch (e: Throwable) {
            throw MwaHandshakeException("invalid dApp ephemeral public key", e)
        }

        // Wallet ephemeral keypair Qw.
        val walletEphemeral = EcP256.generateKeypair()
        val qw = EcP256.x962Uncompressed(walletEphemeral.public)
        val ikm = EcP256.ecdhSecret(walletEphemeral.private, dappEphemeralPublic)
        val salt = associationPublicKey
        val key16 = HkdfSha256.derive(ikm = ikm, salt = salt, length = 16)
        val cipher = Aes128Gcm(key16)

        // Pick the highest version both sides support. The wallet
        // advertises [V1, LEGACY] by default; if the dApp passed only
        // LEGACY we drop to LEGACY. An empty dApp list means the dApp
        // omitted `v` entirely, which spec-wise means LEGACY.
        val negotiated = chooseProtocolVersion(supportedVersions, dappAdvertisedVersions)

        // V1 dApps expect a `SessionProperties` envelope after Qw so
        // they can read the negotiated protocol version off the wire;
        // LEGACY dApps were written before that frame existed and reject
        // it as an unexpected encrypted RPC. Branch on the negotiated
        // version so the wire shape matches what the dApp is parsing.
        val helloRsp: ByteArray
        val initialSendSeq: Int
        if (negotiated == AssociationUri.ProtocolVersion.V1) {
            val propsJson = JsonObject(mapOf("v" to JsonPrimitive(negotiated.wireValue)))
            val propsBytes = json.encodeToString(JsonObject.serializer(), propsJson)
                .encodeToByteArray()
            val encryptedProps = cipher.encrypt(seq = 1, plaintext = propsBytes)
            helloRsp = qw + encryptedProps
            initialSendSeq = 2
        } else {
            // LEGACY: Qw is the entire HELLO_RSP. The dApp uses Qw to
            // derive the same shared secret and starts JSON-RPC at seq=1.
            helloRsp = qw
            initialSendSeq = 1
        }
        try {
            transport.send(helloRsp)
        } catch (e: Throwable) {
            throw MwaHandshakeException("failed to send HELLO_RSP", e)
        }

        return HandshakeOutcome(
            cipher = cipher,
            protocolVersion = negotiated,
            initialRecvSeq = 1,
            initialSendSeq = initialSendSeq
        )
    }

    private fun chooseProtocolVersion(
        supported: List<AssociationUri.ProtocolVersion>,
        advertised: List<AssociationUri.ProtocolVersion>
    ): AssociationUri.ProtocolVersion {
        // Prefer V1 when both sides allow it; fall back to LEGACY.
        if (advertised.isEmpty()) {
            // dApp omitted `v`; per the MWA spec this implies LEGACY.
            return if (AssociationUri.ProtocolVersion.LEGACY in supported)
                AssociationUri.ProtocolVersion.LEGACY
            else supported.first()
        }
        val intersection = advertised.filter { it in supported }
        if (intersection.isEmpty()) {
            throw MwaHandshakeException(
                "no protocol version overlap (wallet supports $supported, dApp offered $advertised)"
            )
        }
        return when {
            AssociationUri.ProtocolVersion.V1 in intersection -> AssociationUri.ProtocolVersion.V1
            else -> AssociationUri.ProtocolVersion.LEGACY
        }
    }
}
