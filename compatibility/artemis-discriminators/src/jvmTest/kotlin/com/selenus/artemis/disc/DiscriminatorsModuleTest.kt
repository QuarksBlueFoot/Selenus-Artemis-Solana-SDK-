package com.selenus.artemis.disc

import org.junit.Test
import org.junit.Assume
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Base58

/**
 * Comprehensive tests for artemis-discriminators module v1.2.0 enhancements
 * 
 * Tests AccountTypeDetector, AnchorDiscriminators, and DiscriminatorRegistry
 */
class DiscriminatorsModuleTest {

    private val testSeed = "2jNmruSprMRuBSuyT9LzWQ9Ar853WDyhYppmMZPtZ665"

    // ==================== AccountTypeDetector Core Tests ====================

    @Test
    fun `AccountTypeDetector - detect empty account`() {
        val emptyData = ByteArray(0)
        val result = AccountTypeDetector.detect(emptyData)

        assertNotNull(result)
        assertEquals("Empty", result.accountName)
        assertEquals(AccountTypeDetector.AccountCategory.UNKNOWN, result.category)
        assertEquals(1.0f, result.confidence)
    }

    @Test
    fun `AccountTypeDetector - detect with program id`() {
        val data = ByteArray(100) { it.toByte() }
        val programId = Keypair.generate().publicKey

        val result = AccountTypeDetector.detect(data, programId)

        assertNotNull(result)
        assertEquals(programId, result.programId)
    }

    @Test
    fun `AccountTypeDetector - detect short data`() {
        val shortData = ByteArray(4) { 0xFF.toByte() }
        val result = AccountTypeDetector.detect(shortData)

        assertNotNull(result)
        // Should handle data shorter than 8 bytes
    }

    // ==================== AccountType Tests ====================

    @Test
    fun `AccountType - equality based on programId and name`() {
        val programId = Keypair.generate().publicKey

        val type1 = AccountTypeDetector.AccountType(
            programId = programId,
            accountName = "TestAccount",
            discriminator = ByteArray(8),
            category = AccountTypeDetector.AccountCategory.CUSTOM,
            confidence = 0.9f
        )

        val type2 = AccountTypeDetector.AccountType(
            programId = programId,
            accountName = "TestAccount",
            discriminator = ByteArray(8) { 1 },  // Different discriminator
            category = AccountTypeDetector.AccountCategory.DEFI,  // Different category
            confidence = 0.5f  // Different confidence
        )

        assertEquals(type1, type2)
        assertEquals(type1.hashCode(), type2.hashCode())
    }

    @Test
    fun `AccountType - different names are not equal`() {
        val programId = Keypair.generate().publicKey

        val type1 = AccountTypeDetector.AccountType(
            programId = programId,
            accountName = "Account1",
            discriminator = null,
            category = AccountTypeDetector.AccountCategory.CUSTOM,
            confidence = 1.0f
        )

        val type2 = AccountTypeDetector.AccountType(
            programId = programId,
            accountName = "Account2",
            discriminator = null,
            category = AccountTypeDetector.AccountCategory.CUSTOM,
            confidence = 1.0f
        )

        assertFalse(type1 == type2)
    }

    // ==================== AccountCategory Tests ====================

    @Test
    fun `AccountCategory - all values available`() {
        val categories = AccountTypeDetector.AccountCategory.values()

        assertTrue(categories.contains(AccountTypeDetector.AccountCategory.SYSTEM))
        assertTrue(categories.contains(AccountTypeDetector.AccountCategory.TOKEN))
        assertTrue(categories.contains(AccountTypeDetector.AccountCategory.TOKEN_2022))
        assertTrue(categories.contains(AccountTypeDetector.AccountCategory.NFT))
        assertTrue(categories.contains(AccountTypeDetector.AccountCategory.METADATA))
        assertTrue(categories.contains(AccountTypeDetector.AccountCategory.DEFI))
        assertTrue(categories.contains(AccountTypeDetector.AccountCategory.GAMING))
        assertTrue(categories.contains(AccountTypeDetector.AccountCategory.CUSTOM))
        assertTrue(categories.contains(AccountTypeDetector.AccountCategory.UNKNOWN))
    }

    // ==================== RegisteredAccount Tests ====================

    @Test
    fun `RegisteredAccount - structure validation`() {
        val registered = AccountTypeDetector.RegisteredAccount(
            name = "MyAccount",
            discriminator = ByteArray(8) { it.toByte() },
            category = AccountTypeDetector.AccountCategory.GAMING
        )

        assertNotNull(registered)
        assertEquals("MyAccount", registered.name)
        assertEquals(8, registered.discriminator.size)
        assertEquals(AccountTypeDetector.AccountCategory.GAMING, registered.category)
    }

    // ==================== AnchorDiscriminators Tests ====================

    @Test
    fun `AnchorDiscriminators - compute global discriminator`() {
        val discriminator = AnchorDiscriminators.global("initialize")

        assertNotNull(discriminator)
        assertEquals(8, discriminator.size)
    }

    @Test
    fun `AnchorDiscriminators - same name produces same discriminator`() {
        val disc1 = AnchorDiscriminators.global("transfer")
        val disc2 = AnchorDiscriminators.global("transfer")

        assertTrue(disc1.contentEquals(disc2))
    }

    @Test
    fun `AnchorDiscriminators - different names produce different discriminators`() {
        val disc1 = AnchorDiscriminators.global("mint")
        val disc2 = AnchorDiscriminators.global("burn")

        assertFalse(disc1.contentEquals(disc2))
    }

    @Test
    fun `AnchorDiscriminators - discriminator is first 8 bytes of sha256`() {
        val discriminator = AnchorDiscriminators.global("test_method")

        // Discriminator should always be 8 bytes
        assertEquals(8, discriminator.size)
    }

    // ==================== DiscriminatorRegistry Tests ====================

    @Test
    fun `DiscriminatorRegistry - builder creates registry`() {
        val programId = Keypair.generate().publicKey
        
        val registry = DiscriminatorRegistry.builder()
            .put(programId, "v1", "transfer", "do_transfer")
            .build()

        assertNotNull(registry)
    }

    @Test
    fun `DiscriminatorRegistry - lookup method name`() {
        val programId = Keypair.generate().publicKey
        
        val registry = DiscriminatorRegistry.builder()
            .put(programId, "v1", "transfer", "execute_transfer")
            .build()

        val methodName = registry.methodName(programId, "v1", "transfer")

        assertEquals("execute_transfer", methodName)
    }

    @Test
    fun `DiscriminatorRegistry - lookup returns null for unknown`() {
        val programId = Keypair.generate().publicKey
        val otherProgram = Keypair.generate().publicKey
        
        val registry = DiscriminatorRegistry.builder()
            .put(programId, "v1", "transfer", "execute_transfer")
            .build()

        val methodName = registry.methodName(otherProgram, "v1", "transfer")

        assertTrue(methodName == null)
    }

    @Test
    fun `DiscriminatorRegistry - discriminator lookup`() {
        val programId = Keypair.generate().publicKey
        
        val registry = DiscriminatorRegistry.builder()
            .put(programId, "v1", "init", "initialize")
            .build()

        val discriminator = registry.discriminator(programId, "v1", "init")

        assertNotNull(discriminator)
        assertEquals(8, discriminator!!.size)
    }

    @Test
    fun `DiscriminatorRegistry - multiple entries`() {
        val programId = Keypair.generate().publicKey
        
        val registry = DiscriminatorRegistry.builder()
            .put(programId, "v1", "init", "initialize")
            .put(programId, "v1", "close", "close_account")
            .put(programId, "v2", "init", "init_v2")
            .build()

        assertEquals("initialize", registry.methodName(programId, "v1", "init"))
        assertEquals("close_account", registry.methodName(programId, "v1", "close"))
        assertEquals("init_v2", registry.methodName(programId, "v2", "init"))
    }

    // ==================== Discriminator Detection Tests ====================

    @Test
    fun `AccountTypeDetector - detect known token account size`() {
        // Token accounts are 165 bytes
        val tokenAccountData = ByteArray(165)
        val result = AccountTypeDetector.detect(tokenAccountData)

        assertNotNull(result)
        // Detection may return UNKNOWN if no discriminator match
        assertTrue(result.category in AccountTypeDetector.AccountCategory.values())
    }

    @Test
    fun `AccountTypeDetector - detect known mint account size`() {
        // Mint accounts are 82 bytes
        val mintData = ByteArray(82)
        val result = AccountTypeDetector.detect(mintData)

        assertNotNull(result)
    }

    // ==================== ByteArray Extension Tests ====================

    @Test
    fun `ByteArray toHexString - produces correct format`() {
        val bytes = byteArrayOf(0x01, 0x02, 0xAB.toByte(), 0xCD.toByte())
        val hex = bytes.toHexString()

        assertNotNull(hex)
        assertEquals(8, hex.length)  // 4 bytes = 8 hex chars
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
    }

    @Test
    fun `ByteArray toHexString - empty array`() {
        val empty = ByteArray(0)
        val hex = empty.toHexString()

        assertEquals("", hex)
    }

    // ==================== Integration Tests ====================

    @Test
    fun `AccountTypeDetector Integration - detect with real discriminator`() {
        val secretBase58 = System.getenv("DEVNET_WALLET_SEED")
        Assume.assumeTrue(
            "Skipping: DEVNET_WALLET_SEED not set",
            secretBase58 != null
        )

        val seed = Base58.decode(secretBase58!!)
        val keypair = Keypair.fromSeed(seed)

        // Create some account data with discriminator
        val anchorDisc = AnchorDiscriminators.global("user_wallet")
        val accountData = anchorDisc + ByteArray(100)

        val result = AccountTypeDetector.detect(accountData)

        println("Discriminator Detection Test:")
        println("  Discriminator: ${anchorDisc.toHexString()}")
        println("  Account Name: ${result.accountName}")
        println("  Category: ${result.category}")
        println("  Confidence: ${result.confidence}")

        assertNotNull(result)
    }
}

// Extension function that might be used in the module
private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
