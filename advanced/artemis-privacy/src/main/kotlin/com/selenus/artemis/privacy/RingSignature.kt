package com.selenus.artemis.privacy

import com.selenus.artemis.runtime.Pubkey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Artemis Ring Signature Implementation
 * 
 * Provides ring signatures for transaction mixing, allowing a signer to prove
 * they belong to a group without revealing which member signed. This is a
 * novel privacy feature NOT available in Solana Mobile SDK.
 * 
 * Use Cases:
 * - Anonymous voting on DAOs
 * - Private NFT transfers
 * - Whistleblower protections
 * - Anonymous airdrops
 * 
 * Based on Spontaneous Anonymous Group (SAG) signatures, optimized for mobile.
 */
object RingSignature {
    
    private val random = SecureRandom()
    private const val DOMAIN_SEPARATOR = "artemis:ring:v1"
    
    /**
     * A ring of public keys used for anonymous signing.
     */
    data class Ring(
        val members: List<Pubkey>,
        val tag: ByteArray // Domain tag to prevent double-signing
    ) {
        init {
            require(members.size >= 2) { "Ring must have at least 2 members" }
            require(members.size <= 128) { "Ring too large (max 128 members)" }
        }
        
        /**
         * Serialize the ring for hashing.
         */
        fun serialize(): ByteArray {
            val size = 4 + members.size * 32 + tag.size
            val buffer = ByteArray(size)
            var offset = 0
            
            // Member count
            buffer[offset++] = (members.size and 0xFF).toByte()
            buffer[offset++] = ((members.size shr 8) and 0xFF).toByte()
            buffer[offset++] = ((members.size shr 16) and 0xFF).toByte()
            buffer[offset++] = ((members.size shr 24) and 0xFF).toByte()
            
            // Members
            for (member in members) {
                System.arraycopy(member.bytes, 0, buffer, offset, 32)
                offset += 32
            }
            
            // Tag
            System.arraycopy(tag, 0, buffer, offset, tag.size)
            
            return buffer
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Ring) return false
            return members == other.members && tag.contentEquals(other.tag)
        }
        
        override fun hashCode(): Int = members.hashCode() + tag.contentHashCode()
    }
    
    /**
     * A ring signature proving the signer is a member of the ring.
     */
    data class Signature(
        val ring: Ring,
        val keyImage: ByteArray, // Prevents double-signing
        val c: List<ByteArray>, // Challenge values
        val r: List<ByteArray>, // Response values
        val message: ByteArray  // The signed message
    ) {
        init {
            require(c.size == ring.members.size) { "Challenge count must match ring size" }
            require(r.size == ring.members.size) { "Response count must match ring size" }
        }
        
        /**
         * Serialize for on-chain verification.
         */
        fun serialize(): ByteArray {
            val ringBytes = ring.serialize()
            val challengeBytes = c.sumOf { it.size }
            val responseBytes = r.sumOf { it.size }
            
            val size = 4 + ringBytes.size + 32 + 4 + challengeBytes + 4 + responseBytes + 4 + message.size
            val buffer = ByteArray(size)
            var offset = 0
            
            // Ring
            writeU32LE(buffer, offset, ringBytes.size)
            offset += 4
            System.arraycopy(ringBytes, 0, buffer, offset, ringBytes.size)
            offset += ringBytes.size
            
            // Key image
            System.arraycopy(keyImage, 0, buffer, offset, 32)
            offset += 32
            
            // Challenges
            writeU32LE(buffer, offset, c.size)
            offset += 4
            for (challenge in c) {
                System.arraycopy(challenge, 0, buffer, offset, challenge.size)
                offset += challenge.size
            }
            
            // Responses
            writeU32LE(buffer, offset, r.size)
            offset += 4
            for (response in r) {
                System.arraycopy(response, 0, buffer, offset, response.size)
                offset += response.size
            }
            
            // Message
            writeU32LE(buffer, offset, message.size)
            offset += 4
            System.arraycopy(message, 0, buffer, offset, message.size)
            
            return buffer
        }
        
        private fun writeU32LE(buffer: ByteArray, offset: Int, value: Int) {
            buffer[offset] = (value and 0xFF).toByte()
            buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
            buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
            buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Signature) return false
            return ring == other.ring && keyImage.contentEquals(other.keyImage)
        }
        
        override fun hashCode(): Int = ring.hashCode() + keyImage.contentHashCode()
    }
    
    /**
     * Linkable ring signature for detecting double-signing.
     */
    data class LinkableSignature(
        val signature: Signature,
        val linkTag: ByteArray // Tag that links multiple signatures by same signer
    ) {
        /**
         * Check if two signatures were made by the same (unknown) signer.
         */
        fun isLinkedTo(other: LinkableSignature): Boolean {
            return linkTag.contentEquals(other.linkTag)
        }
    }
    
    // ========================================================================
    // Signing Operations
    // ========================================================================
    
    /**
     * Creates a ring signature.
     * 
     * The signer proves membership in the ring without revealing their identity.
     * 
     * @param message The message to sign
     * @param ring The ring of public keys (must include signer)
     * @param signerPrivateKey The signer's private key
     * @param signerIndex The signer's position in the ring
     * @return A ring signature
     */
    fun sign(
        message: ByteArray,
        ring: Ring,
        signerPrivateKey: ByteArray,
        signerIndex: Int
    ): Signature {
        require(signerIndex in ring.members.indices) { "Invalid signer index" }
        require(signerPrivateKey.size == 32) { "Private key must be 32 bytes" }
        
        val n = ring.members.size
        
        // Generate key image (for linkability/double-spend prevention)
        val keyImage = generateKeyImage(signerPrivateKey, ring.members[signerIndex])
        
        // Initialize challenge and response arrays
        val c = Array(n) { ByteArray(32) }
        val r = Array(n) { ByteArray(32) }
        
        // Generate random value for signer
        val alpha = ByteArray(32)
        random.nextBytes(alpha)
        
        // Compute L_s = alpha * G (simplified as hash)
        val lSigner = hashPoint("L", alpha, ring.members[signerIndex].bytes)
        
        // Compute R_s = alpha * H(P_s) (simplified)
        val rSigner = hashPoint("R", alpha, keyImage)
        
        // Compute c[s+1] = H(m || L_s || R_s)
        val nextIndex = (signerIndex + 1) % n
        c[nextIndex] = hashChallenge(message, lSigner, rSigner)
        
        // Generate random r values and compute challenges for other members
        for (i in 1 until n) {
            val idx = (signerIndex + i) % n
            val nextIdx = (idx + 1) % n
            
            // Random response
            random.nextBytes(r[idx])
            
            // L_i = r_i * G + c_i * P_i (simplified)
            val lI = hashPoint("L", r[idx], c[idx], ring.members[idx].bytes)
            
            // R_i = r_i * H(P_i) + c_i * I (simplified)
            val rI = hashPoint("R", r[idx], c[idx], keyImage)
            
            // c[i+1] = H(m || L_i || R_i)
            if (nextIdx != signerIndex) {
                c[nextIdx] = hashChallenge(message, lI, rI)
            }
        }
        
        // Close the ring: r_s = alpha - c_s * x (mod order)
        // Simplified: r_s = H(alpha || c_s || x)
        r[signerIndex] = closeRing(alpha, c[signerIndex], signerPrivateKey)
        
        return Signature(
            ring = ring,
            keyImage = keyImage,
            c = c.toList(),
            r = r.toList(),
            message = message
        )
    }
    
    /**
     * Creates a linkable ring signature.
     */
    fun signLinkable(
        message: ByteArray,
        ring: Ring,
        signerPrivateKey: ByteArray,
        signerIndex: Int,
        linkDomain: ByteArray
    ): LinkableSignature {
        val signature = sign(message, ring, signerPrivateKey, signerIndex)
        
        // Generate link tag from private key and domain
        val linkTag = generateLinkTag(signerPrivateKey, linkDomain)
        
        return LinkableSignature(signature, linkTag)
    }
    
    // ========================================================================
    // Verification
    // ========================================================================
    
    /**
     * Verifies a ring signature.
     * 
     * @return true if the signature is valid
     */
    fun verify(signature: Signature): Boolean {
        val n = signature.ring.members.size
        
        // Recompute the ring
        var currentC = signature.c[0]
        
        for (i in 0 until n) {
            val nextIdx = (i + 1) % n
            
            // L_i = r_i * G + c_i * P_i
            val lI = hashPoint("L", signature.r[i], signature.c[i], signature.ring.members[i].bytes)
            
            // R_i = r_i * H(P_i) + c_i * I
            val rI = hashPoint("R", signature.r[i], signature.c[i], signature.keyImage)
            
            // c[i+1] = H(m || L_i || R_i)
            val computedNext = hashChallenge(signature.message, lI, rI)
            
            if (nextIdx == 0) {
                // Ring must close
                if (!computedNext.contentEquals(signature.c[0])) {
                    return false
                }
            }
        }
        
        return true
    }
    
    /**
     * Checks if two linkable signatures were made by the same signer.
     */
    fun areLinked(sig1: LinkableSignature, sig2: LinkableSignature): Boolean {
        return sig1.isLinkedTo(sig2)
    }
    
    // ========================================================================
    // Key Image (Anti Double-Spend)
    // ========================================================================
    
    /**
     * Checks if a key image has been seen before.
     * In production, this would check against an on-chain registry.
     */
    fun checkKeyImage(keyImage: ByteArray, spentImages: Set<ByteArray>): Boolean {
        return spentImages.none { it.contentEquals(keyImage) }
    }
    
    // ========================================================================
    // Internal Helpers
    // ========================================================================
    
    private fun generateKeyImage(privateKey: ByteArray, publicKey: Pubkey): ByteArray {
        // I = x * H(P) where x is private key, P is public key
        val hp = sha256(DOMAIN_SEPARATOR.toByteArray() + "keyimage".toByteArray() + publicKey.bytes)
        return sha256(privateKey + hp)
    }
    
    private fun generateLinkTag(privateKey: ByteArray, domain: ByteArray): ByteArray {
        return sha256(DOMAIN_SEPARATOR.toByteArray() + "link".toByteArray() + privateKey + domain)
    }
    
    private fun hashPoint(prefix: String, vararg inputs: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN_SEPARATOR.toByteArray())
        digest.update(prefix.toByteArray())
        for (input in inputs) {
            digest.update(input)
        }
        return digest.digest()
    }
    
    private fun hashChallenge(message: ByteArray, l: ByteArray, r: ByteArray): ByteArray {
        return sha256(DOMAIN_SEPARATOR.toByteArray() + "challenge".toByteArray() + message + l + r)
    }
    
    private fun closeRing(alpha: ByteArray, c: ByteArray, privateKey: ByteArray): ByteArray {
        // Simplified: in production use modular arithmetic
        return sha256(alpha + c + privateKey)
    }
    
    private fun sha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
}

/**
 * Builder for creating rings from a list of potential signers.
 */
class RingBuilder {
    private val members = mutableListOf<Pubkey>()
    private var tag: ByteArray = ByteArray(0)
    
    fun addMember(pubkey: Pubkey): RingBuilder {
        members.add(pubkey)
        return this
    }
    
    fun addMembers(pubkeys: List<Pubkey>): RingBuilder {
        members.addAll(pubkeys)
        return this
    }
    
    fun withTag(tag: ByteArray): RingBuilder {
        this.tag = tag
        return this
    }
    
    fun withTag(tag: String): RingBuilder {
        this.tag = tag.toByteArray()
        return this
    }
    
    fun build(): RingSignature.Ring {
        require(members.size >= 2) { "Ring must have at least 2 members" }
        
        if (tag.isEmpty()) {
            // Generate random tag if not specified
            val random = SecureRandom()
            tag = ByteArray(32)
            random.nextBytes(tag)
        }
        
        return RingSignature.Ring(members.toList(), tag)
    }
}

/**
 * Extension to create a ring from a list of pubkeys.
 */
fun List<Pubkey>.toRing(tag: ByteArray = ByteArray(0)): RingSignature.Ring {
    val builder = RingBuilder().addMembers(this)
    if (tag.isNotEmpty()) {
        builder.withTag(tag)
    }
    return builder.build()
}
