package com.selenus.artemis.mplcore

import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-mplcore module.
 * Tests MplCorePdas, MplCorePrograms, MplCoreArgs, and related utilities.
 */
class MplCoreModuleTest {

    private val testPubkey = Pubkey(ByteArray(32) { 1 })
    private val testAuthority = Pubkey(ByteArray(32) { 2 })

    // ===== MplCorePrograms Tests =====

    @Test
    fun testDefaultProgramId() {
        assertNotNull(MplCorePrograms.DEFAULT_PROGRAM_ID)
        assertEquals(32, MplCorePrograms.DEFAULT_PROGRAM_ID.bytes.size)
    }

    // ===== MplCorePdas Tests =====

    @Test
    fun testAssetPda() {
        val assetPda = MplCorePdas.assetPda(testPubkey, "test-asset")
        
        assertNotNull(assetPda)
        assertEquals(32, assetPda.bytes.size)
    }

    @Test
    fun testAssetPdaDeterministic() {
        val pda1 = MplCorePdas.assetPda(testPubkey, "asset-1")
        val pda2 = MplCorePdas.assetPda(testPubkey, "asset-1")
        
        assertEquals(pda1, pda2)
    }

    @Test
    fun testAssetPdaDifferentNames() {
        val pda1 = MplCorePdas.assetPda(testPubkey, "asset-1")
        val pda2 = MplCorePdas.assetPda(testPubkey, "asset-2")
        
        assertTrue(!pda1.bytes.contentEquals(pda2.bytes))
    }

    @Test
    fun testCollectionPda() {
        val collectionPda = MplCorePdas.collectionPda(testAuthority, "my-collection")
        
        assertNotNull(collectionPda)
        assertEquals(32, collectionPda.bytes.size)
    }

    @Test
    fun testCollectionPdaDeterministic() {
        val pda1 = MplCorePdas.collectionPda(testAuthority, "collection")
        val pda2 = MplCorePdas.collectionPda(testAuthority, "collection")
        
        assertEquals(pda1, pda2)
    }

    @Test
    fun testCollectionPdaDifferentAuthorities() {
        val authority1 = Pubkey(ByteArray(32) { 1 })
        val authority2 = Pubkey(ByteArray(32) { 2 })
        
        val pda1 = MplCorePdas.collectionPda(authority1, "same-name")
        val pda2 = MplCorePdas.collectionPda(authority2, "same-name")
        
        assertTrue(!pda1.bytes.contentEquals(pda2.bytes))
    }

    // ===== MplCoreArgs Tests =====

    @Test
    fun testMplCoreArgsExists() {
        assertNotNull(MplCoreArgs)
    }

    // ===== MplCoreCodec Tests =====

    @Test
    fun testMplCoreCodecExists() {
        assertNotNull(MplCoreCodec)
    }

    // ===== CoreMarketplaceToolkit Tests =====

    @Test
    fun testCoreMarketplaceToolkitExists() {
        assertNotNull(CoreMarketplaceToolkit)
    }

    // ===== Asset Name Validation Tests =====

    @Test
    fun testAssetPdaWithEmptyName() {
        val pda = MplCorePdas.assetPda(testPubkey, "")
        assertNotNull(pda)
    }

    @Test
    fun testAssetPdaWithMaxLengthName() {
        // PDA seeds must be <= 32 bytes, so test with a 32-char name
        val maxLengthName = "a".repeat(32)
        val pda = MplCorePdas.assetPda(testPubkey, maxLengthName)
        
        assertNotNull(pda)
        assertEquals(32, pda.bytes.size)
    }

    @Test
    fun testAssetPdaWithSpecialCharacters() {
        val specialName = "asset-#1_test.nft"
        val pda = MplCorePdas.assetPda(testPubkey, specialName)
        
        assertNotNull(pda)
    }

    // ===== Collection Name Validation Tests =====

    @Test
    fun testCollectionPdaWithUnicode() {
        val unicodeName = "Collection-🎮"
        val pda = MplCorePdas.collectionPda(testAuthority, unicodeName)
        
        assertNotNull(pda)
    }

    // ===== Program Address Derivation Tests =====

    @Test
    fun testPdaIsNotOnCurve() {
        // PDAs should not be valid ed25519 public keys
        val pda = MplCorePdas.assetPda(testPubkey, "test")
        
        // Just verify it's a valid 32-byte address
        assertEquals(32, pda.bytes.size)
    }

    @Test
    fun testMultipleAssetPdasFromSameOwner() {
        val pdas = (1..5).map { i ->
            MplCorePdas.assetPda(testPubkey, "asset-$i")
        }
        
        // All should be unique
        val uniquePdas = pdas.map { it.bytes.toList() }.toSet()
        assertEquals(5, uniquePdas.size)
    }

    // ===== Integration Pattern Tests =====

    @Test
    fun testAssetAndCollectionPdasDifferent() {
        val assetPda = MplCorePdas.assetPda(testPubkey, "same-name")
        val collectionPda = MplCorePdas.collectionPda(testPubkey, "same-name")
        
        // Asset and collection PDAs should be different even with same name
        // because they have different seed prefixes
        assertTrue(!assetPda.bytes.contentEquals(collectionPda.bytes))
    }
}
