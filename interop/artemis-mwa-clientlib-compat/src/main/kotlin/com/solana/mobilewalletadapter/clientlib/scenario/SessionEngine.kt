/*
 * SessionEngine: authority over MWA association identity.
 *
 * Extracted from Scenario so that identity ownership is inverted:
 * Scenario consumes an identity from this engine instead of generating
 * one. The engine is the single source of truth for the association
 * keypair and its SEC1-encoded public point. Callers that need to
 * reconstruct a session across reconnect cycles reuse the same engine
 * instance; identity stays stable even when transport re-establishes.
 */
package com.solana.mobilewalletadapter.clientlib.scenario

/**
 * Owns an MWA association identity for the lifetime of a session.
 *
 * Every call to [currentAssociation] on the same engine returns the same
 * material. Recreate an engine only when a fresh identity is actually
 * desired (e.g. user signed out and wants a clean handshake). Reconnects
 * within a logical session keep the engine alive so the association key
 * does not rotate underneath the wallet.
 */
interface SessionEngine {

    /**
     * Ephemeral association identity. The P-256 keypair powers the
     * association handshake; [publicKey] is its 65-byte SEC1
     * uncompressed encoding, i.e. exactly what the association URI
     * parameter carries in base64url-no-padding form.
     */
    data class AssociationIdentity(
        val keyPair: java.security.KeyPair,
        val publicKey: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AssociationIdentity) return false
            return keyPair == other.keyPair && publicKey.contentEquals(other.publicKey)
        }
        override fun hashCode(): Int = 31 * keyPair.hashCode() + publicKey.contentHashCode()
    }

    /** Returns the identity this engine owns. Stable across calls. */
    fun currentAssociation(): AssociationIdentity

    /**
     * Release any resources this engine holds. After [close], subsequent
     * [currentAssociation] calls are undefined; callers should discard
     * the engine.
     */
    fun close()
}

/**
 * Default [SessionEngine] that owns one ephemeral P-256 keypair for its
 * lifetime. The keypair is generated lazily on first access so tests
 * that never call through pay nothing; once computed, the identity is
 * cached and every subsequent call returns the same bytes.
 *
 * Keypair generation and SEC1 encoding live in [AssociationKeys] below
 * as file-private primitives. Scenario cannot see them at all: the
 * engine is the only path from Scenario to crypto material, and the
 * primitives are scoped to this file.
 */
class DefaultSessionEngine : SessionEngine {

    private val identity: SessionEngine.AssociationIdentity by lazy {
        val kp = AssociationKeys.generateKeyPair()
        SessionEngine.AssociationIdentity(
            keyPair = kp,
            publicKey = AssociationKeys.encodedPublicKey(kp)
        )
    }

    override fun currentAssociation(): SessionEngine.AssociationIdentity = identity

    override fun close() = Unit
}

/**
 * File-private association-key primitives. Only [DefaultSessionEngine]
 * can reach them. Moved here from Scenario so the scenario class
 * carries no crypto code and cannot directly invoke the generator by
 * any symbol it sees in scope.
 */
private object AssociationKeys {

    fun generateKeyPair(): java.security.KeyPair {
        val kpg = java.security.KeyPairGenerator.getInstance("EC")
        kpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    fun encodedPublicKey(keyPair: java.security.KeyPair): ByteArray {
        val pk = keyPair.public as java.security.interfaces.ECPublicKey
        val w = pk.w
        fun pad(b: java.math.BigInteger): ByteArray {
            val raw = b.toByteArray()
            return when {
                raw.size == 32 -> raw
                raw.size == 33 && raw[0] == 0.toByte() -> raw.copyOfRange(1, 33)
                else -> ByteArray(32).also { out -> raw.copyInto(out, 32 - raw.size) }
            }
        }
        val x = pad(w.affineX)
        val y = pad(w.affineY)
        return ByteArray(65).also { buf ->
            buf[0] = 0x04
            x.copyInto(buf, 1)
            y.copyInto(buf, 33)
        }
    }
}
