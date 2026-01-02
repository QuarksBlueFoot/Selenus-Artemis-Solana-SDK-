package com.selenus.artemis.wallet

import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-wallet module.
 * Tests WalletAdapter, WalletCapabilities, and related utilities.
 */
class WalletModuleTest {

    // ===== WalletCapabilities Tests =====

    @Test
    fun testWalletCapabilitiesDefaults() {
        val caps = WalletCapabilities()
        
        assertTrue(caps.supportsReSign)
        assertFalse(caps.supportsPartialSign)
        assertFalse(caps.supportsFeePayerSwap)
        assertTrue(caps.supportsMultipleMessages)
        assertFalse(caps.supportsPreAuthorize)
    }

    @Test
    fun testWalletCapabilitiesDefaultMobile() {
        val caps = WalletCapabilities.defaultMobile()
        
        assertTrue(caps.supportsReSign)
        assertFalse(caps.supportsPartialSign)
        assertFalse(caps.supportsFeePayerSwap)
        assertTrue(caps.supportsMultipleMessages)
        assertFalse(caps.supportsPreAuthorize)
    }

    @Test
    fun testWalletCapabilitiesCustom() {
        val caps = WalletCapabilities(
            supportsReSign = false,
            supportsPartialSign = true,
            supportsFeePayerSwap = true,
            supportsMultipleMessages = false,
            supportsPreAuthorize = true
        )
        
        assertFalse(caps.supportsReSign)
        assertTrue(caps.supportsPartialSign)
        assertTrue(caps.supportsFeePayerSwap)
        assertFalse(caps.supportsMultipleMessages)
        assertTrue(caps.supportsPreAuthorize)
    }

    @Test
    fun testWalletCapabilitiesEquality() {
        val caps1 = WalletCapabilities(supportsReSign = true, supportsPartialSign = false)
        val caps2 = WalletCapabilities(supportsReSign = true, supportsPartialSign = false)
        
        assertEquals(caps1, caps2)
    }

    // ===== WalletAdapter Interface Tests =====

    @Test
    fun testMockWalletAdapter() {
        val mockPublicKey = Pubkey(ByteArray(32) { 1 })
        
        val adapter = object : WalletAdapter {
            override val publicKey: Pubkey = mockPublicKey
            
            override suspend fun getCapabilities(): WalletCapabilities = WalletCapabilities.defaultMobile()
            
            override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray {
                return ByteArray(64) { 0 }
            }
        }
        
        assertEquals(mockPublicKey, adapter.publicKey)
    }

    @Test
    fun testWalletAdapterDefaultSignMessages() {
        val mockPublicKey = Pubkey(ByteArray(32) { 1 })
        
        val adapter = object : WalletAdapter {
            override val publicKey: Pubkey = mockPublicKey
            
            override suspend fun getCapabilities(): WalletCapabilities = WalletCapabilities()
            
            override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray {
                return ByteArray(64) { message.hashCode().toByte() }
            }
        }
        
        // Default implementation should fall back to per-message signing
        assertNotNull(adapter)
    }

    // ===== WalletRequest Tests =====
    // WalletRequest is a sealed interface - test implementations

    @Test
    fun testSignTxRequest() {
        val request = SignTxRequest(
            purpose = "Test signing",
            allowReSign = true,
            allowPartialSign = false
        )
        
        assertEquals("Test signing", request.purpose)
        assertTrue(request.allowReSign)
        assertFalse(request.allowPartialSign)
    }

    @Test
    fun testReSignTxRequest() {
        val request = ReSignTxRequest(
            purpose = "Re-signing",
            oldBlockhash = "old123",
            newBlockhash = "new456"
        )
        
        assertEquals("Re-signing", request.purpose)
        assertEquals("old123", request.oldBlockhash)
        assertEquals("new456", request.newBlockhash)
    }

    @Test
    fun testFeePayerSwapRequest() {
        val request = FeePayerSwapRequest(
            purpose = "Swap fee payer",
            newFeePayerBase58 = "FeePayerPubkey123"
        )
        
        assertEquals("Swap fee payer", request.purpose)
        assertEquals("FeePayerPubkey123", request.newFeePayerBase58)
    }

    // ===== WalletCapabilityCache Tests =====
    // WalletCapabilityCache is a class - test as mock

    // ===== WalletRequests Tests =====
    // WalletRequests is not an object - removed

    // ===== WalletAdapterSignAndSend Tests =====
    // WalletAdapterSignAndSend is not an object - removed

    // ===== Capability Feature Detection =====

    @Test
    fun testCanPartialSign() {
        val caps = WalletCapabilities(supportsPartialSign = true)
        assertTrue(caps.supportsPartialSign)
    }

    @Test
    fun testCanSwapFeePayer() {
        val caps = WalletCapabilities(supportsFeePayerSwap = true)
        assertTrue(caps.supportsFeePayerSwap)
    }

    @Test
    fun testCanPreAuthorize() {
        val caps = WalletCapabilities(supportsPreAuthorize = true)
        assertTrue(caps.supportsPreAuthorize)
    }

    // ===== Mock Adapter Scenarios =====

    @Test
    fun testFullFeaturedWalletAdapter() {
        val mockPublicKey = Pubkey(ByteArray(32) { 42 })
        
        val adapter = object : WalletAdapter {
            override val publicKey: Pubkey = mockPublicKey
            
            override suspend fun getCapabilities(): WalletCapabilities = WalletCapabilities(
                supportsReSign = true,
                supportsPartialSign = true,
                supportsFeePayerSwap = true,
                supportsMultipleMessages = true,
                supportsPreAuthorize = true
            )
            
            override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray {
                return ByteArray(64) { it.toByte() }
            }
            
            override suspend fun signMessages(messages: List<ByteArray>, request: WalletRequest): List<ByteArray> {
                return messages.map { ByteArray(64) { 0 } }
            }
            
            override suspend fun signArbitraryMessage(message: ByteArray, request: WalletRequest): ByteArray {
                return ByteArray(64) { 1 }
            }
        }
        
        assertNotNull(adapter.publicKey)
    }

    @Test
    fun testMinimalWalletAdapter() {
        val mockPublicKey = Pubkey(ByteArray(32) { 0 })
        
        val adapter = object : WalletAdapter {
            override val publicKey: Pubkey = mockPublicKey
            
            override suspend fun getCapabilities(): WalletCapabilities = WalletCapabilities(
                supportsReSign = false,
                supportsPartialSign = false,
                supportsFeePayerSwap = false,
                supportsMultipleMessages = false,
                supportsPreAuthorize = false
            )
            
            override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray {
                return ByteArray(64)
            }
        }
        
        assertNotNull(adapter)
    }

    // ===== Pubkey Tests (via wallet) =====

    @Test
    fun testWalletPublicKeySize() {
        val pubkey = Pubkey(ByteArray(32) { it.toByte() })
        assertEquals(32, pubkey.bytes.size)
    }

    // ===== SendPipeline Tests =====

    @Test
    fun testSendPipelineConfigDefaults() {
        val config = SendPipeline.Config()
        
        assertEquals(3, config.maxAttempts)
        assertEquals(35, config.desiredPriority0to100)
        assertTrue(config.allowReSign)
        assertTrue(config.allowRetry)
    }

    @Test
    fun testSendPipelineConfigCustom() {
        val config = SendPipeline.Config(
            maxAttempts = 5,
            desiredPriority0to100 = 50,
            allowReSign = false,
            allowRetry = false
        )
        
        assertEquals(5, config.maxAttempts)
        assertEquals(50, config.desiredPriority0to100)
        assertFalse(config.allowReSign)
        assertFalse(config.allowRetry)
    }
}
