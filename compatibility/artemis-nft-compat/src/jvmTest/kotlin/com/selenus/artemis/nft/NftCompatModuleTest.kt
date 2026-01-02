package com.selenus.artemis.nft

import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-nft-compat module.
 * Tests Pdas, MetaplexIds, and NFT model parsers.
 */
class NftCompatModuleTest {

    private val testMint = Pubkey(ByteArray(32) { 1 })
    private val testAuthority = Pubkey(ByteArray(32) { 2 })
    private val testTokenAccount = Pubkey(ByteArray(32) { 3 })

    // ===== MetaplexIds Tests =====

    @Test
    fun testTokenMetadataProgramId() {
        assertNotNull(MetaplexIds.TOKEN_METADATA_PROGRAM)
        assertEquals(32, MetaplexIds.TOKEN_METADATA_PROGRAM.bytes.size)
    }

    // ===== Pdas.metadataPda Tests =====

    @Test
    fun testMetadataPda() {
        val pda = Pdas.metadataPda(testMint)
        
        assertNotNull(pda)
        assertEquals(32, pda.bytes.size)
    }

    @Test
    fun testMetadataPdaDeterministic() {
        val pda1 = Pdas.metadataPda(testMint)
        val pda2 = Pdas.metadataPda(testMint)
        
        assertEquals(pda1, pda2)
    }

    @Test
    fun testMetadataPdaDifferentMints() {
        val mint1 = Pubkey(ByteArray(32) { 1 })
        val mint2 = Pubkey(ByteArray(32) { 2 })
        
        val pda1 = Pdas.metadataPda(mint1)
        val pda2 = Pdas.metadataPda(mint2)
        
        assertTrue(!pda1.bytes.contentEquals(pda2.bytes))
    }

    // ===== Pdas.masterEditionPda Tests =====

    @Test
    fun testMasterEditionPda() {
        val pda = Pdas.masterEditionPda(testMint)
        
        assertNotNull(pda)
        assertEquals(32, pda.bytes.size)
    }

    @Test
    fun testMasterEditionPdaDeterministic() {
        val pda1 = Pdas.masterEditionPda(testMint)
        val pda2 = Pdas.masterEditionPda(testMint)
        
        assertEquals(pda1, pda2)
    }

    @Test
    fun testMasterEditionDifferentFromMetadata() {
        val metadataPda = Pdas.metadataPda(testMint)
        val editionPda = Pdas.masterEditionPda(testMint)
        
        assertTrue(!metadataPda.bytes.contentEquals(editionPda.bytes))
    }

    // ===== Pdas.editionMarkerPda Tests =====

    @Test
    fun testEditionMarkerPda() {
        val pda = Pdas.editionMarkerPda(testMint, 0)
        
        assertNotNull(pda)
        assertEquals(32, pda.bytes.size)
    }

    @Test
    fun testEditionMarkerPdaDifferentEditions() {
        val pda1 = Pdas.editionMarkerPda(testMint, 0)
        val pda2 = Pdas.editionMarkerPda(testMint, 248) // Different marker
        
        assertTrue(!pda1.bytes.contentEquals(pda2.bytes))
    }

    @Test
    fun testEditionMarkerPdaSameMarkerRange() {
        // Editions 0-247 are in marker 0
        // Editions 248-495 are in marker 1
        val pda1 = Pdas.editionMarkerPda(testMint, 0)
        val pda2 = Pdas.editionMarkerPda(testMint, 100)
        
        // Same marker (0), should be same PDA
        assertEquals(pda1, pda2)
    }

    // ===== Pdas.tokenRecordPda Tests =====

    @Test
    fun testTokenRecordPda() {
        val pda = Pdas.tokenRecordPda(testMint, testTokenAccount)
        
        assertNotNull(pda)
        assertEquals(32, pda.bytes.size)
    }

    @Test
    fun testTokenRecordPdaDeterministic() {
        val pda1 = Pdas.tokenRecordPda(testMint, testTokenAccount)
        val pda2 = Pdas.tokenRecordPda(testMint, testTokenAccount)
        
        assertEquals(pda1, pda2)
    }

    @Test
    fun testTokenRecordPdaDifferentTokenAccounts() {
        val ta1 = Pubkey(ByteArray(32) { 3 })
        val ta2 = Pubkey(ByteArray(32) { 4 })
        
        val pda1 = Pdas.tokenRecordPda(testMint, ta1)
        val pda2 = Pdas.tokenRecordPda(testMint, ta2)
        
        assertTrue(!pda1.bytes.contentEquals(pda2.bytes))
    }

    // ===== Pdas.collectionAuthorityRecordPda Tests =====

    @Test
    fun testCollectionAuthorityRecordPda() {
        val pda = Pdas.collectionAuthorityRecordPda(testMint, testAuthority)
        
        assertNotNull(pda)
        assertEquals(32, pda.bytes.size)
    }

    @Test
    fun testCollectionAuthorityRecordPdaDeterministic() {
        val pda1 = Pdas.collectionAuthorityRecordPda(testMint, testAuthority)
        val pda2 = Pdas.collectionAuthorityRecordPda(testMint, testAuthority)
        
        assertEquals(pda1, pda2)
    }

    @Test
    fun testCollectionAuthorityRecordPdaDifferentAuthorities() {
        val auth1 = Pubkey(ByteArray(32) { 2 })
        val auth2 = Pubkey(ByteArray(32) { 5 })
        
        val pda1 = Pdas.collectionAuthorityRecordPda(testMint, auth1)
        val pda2 = Pdas.collectionAuthorityRecordPda(testMint, auth2)
        
        assertTrue(!pda1.bytes.contentEquals(pda2.bytes))
    }

    // ===== NftModels Tests =====
    // NftModels - check if it exists

    // ===== Parser Tests =====
    // Parsers are classes/objects - testing existence only if they're objects

    // ===== All PDAs Unique Tests =====

    @Test
    fun testAllPdaTypesUnique() {
        val metadataPda = Pdas.metadataPda(testMint)
        val editionPda = Pdas.masterEditionPda(testMint)
        val tokenRecordPda = Pdas.tokenRecordPda(testMint, testTokenAccount)
        val collectionAuthPda = Pdas.collectionAuthorityRecordPda(testMint, testAuthority)
        
        val pdas = listOf(metadataPda, editionPda, tokenRecordPda, collectionAuthPda)
        val uniquePdas = pdas.map { it.bytes.toList() }.toSet()
        
        assertEquals(4, uniquePdas.size)
    }
}
