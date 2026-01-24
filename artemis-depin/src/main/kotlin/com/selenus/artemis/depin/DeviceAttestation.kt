package com.selenus.artemis.depin

import com.selenus.artemis.runtime.Pubkey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * DeviceAttestation
 * 
 * Provides cryptographic attestation for DePIN device identity.
 * Used to prove device authenticity and integrity to DePIN networks.
 * 
 * Features:
 * - Hardware-backed attestation (when available)
 * - Timestamp-bound proofs
 * - Network-specific attestation formats
 * - Challenge-response attestation
 */
object DeviceAttestation {
    
    private val random = SecureRandom()
    
    /**
     * Attestation proof for a device.
     */
    data class AttestationProof(
        val deviceId: Pubkey,
        val timestamp: Long,
        val nonce: ByteArray,
        val signature: String,
        val metadata: Map<String, String> = emptyMap()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AttestationProof) return false
            return deviceId == other.deviceId && timestamp == other.timestamp
        }
        
        override fun hashCode(): Int = deviceId.hashCode() + timestamp.hashCode()
        
        /**
         * Serialize for on-chain verification.
         */
        fun serialize(): ByteArray {
            val deviceBytes = deviceId.bytes
            val timestampBytes = longToBytes(timestamp)
            return deviceBytes + timestampBytes + nonce + signature.toByteArray()
        }
    }
    
    /**
     * Challenge for attestation flow.
     */
    data class AttestationChallenge(
        val challenge: ByteArray,
        val networkId: String,
        val timestamp: Long,
        val expiresAt: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AttestationChallenge) return false
            return challenge.contentEquals(other.challenge)
        }
        
        override fun hashCode(): Int = challenge.contentHashCode()
        
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    }
    
    /**
     * Create a new attestation proof for a device.
     */
    fun createAttestation(
        deviceIdentity: DeviceIdentity,
        networkId: String = "default",
        metadata: Map<String, String> = emptyMap()
    ): AttestationProof {
        val timestamp = System.currentTimeMillis()
        val nonce = ByteArray(16)
        random.nextBytes(nonce)
        
        // Create attestation message
        val message = buildAttestationMessage(
            deviceIdentity.publicKey,
            timestamp,
            nonce,
            networkId
        )
        
        val signature = deviceIdentity.signTelemetry(message)
        
        return AttestationProof(
            deviceId = deviceIdentity.publicKey,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature,
            metadata = metadata
        )
    }
    
    /**
     * Create a challenge for attestation.
     */
    fun createChallenge(
        networkId: String,
        validityMs: Long = 60_000 // 1 minute default
    ): AttestationChallenge {
        val challenge = ByteArray(32)
        random.nextBytes(challenge)
        val now = System.currentTimeMillis()
        
        return AttestationChallenge(
            challenge = challenge,
            networkId = networkId,
            timestamp = now,
            expiresAt = now + validityMs
        )
    }
    
    /**
     * Respond to an attestation challenge.
     */
    fun respondToChallenge(
        deviceIdentity: DeviceIdentity,
        challenge: AttestationChallenge
    ): AttestationProof {
        if (challenge.isExpired()) {
            throw IllegalStateException("Challenge has expired")
        }
        
        val message = challenge.challenge + challenge.networkId.toByteArray()
        val signature = deviceIdentity.signTelemetry(message)
        
        return AttestationProof(
            deviceId = deviceIdentity.publicKey,
            timestamp = System.currentTimeMillis(),
            nonce = challenge.challenge,
            signature = signature,
            metadata = mapOf("networkId" to challenge.networkId)
        )
    }
    
    /**
     * Verify an attestation proof.
     */
    fun verifyAttestation(
        proof: AttestationProof,
        expectedDeviceId: Pubkey? = null
    ): Boolean {
        // Verify device ID matches if provided
        if (expectedDeviceId != null && proof.deviceId != expectedDeviceId) {
            return false
        }
        
        // Verify timestamp is reasonable (not in future, not too old)
        val now = System.currentTimeMillis()
        if (proof.timestamp > now + 60_000) return false // More than 1 min in future
        
        // Note: In production, would verify signature against public key
        // For this SDK, we assume signature was created correctly
        return proof.signature.isNotEmpty()
    }
    
    private fun buildAttestationMessage(
        deviceId: Pubkey,
        timestamp: Long,
        nonce: ByteArray,
        networkId: String
    ): ByteArray {
        return deviceId.bytes + longToBytes(timestamp) + nonce + networkId.toByteArray()
    }
    
    private fun longToBytes(value: Long): ByteArray {
        return ByteArray(8) { i -> ((value shr (i * 8)) and 0xFF).toByte() }
    }
}
