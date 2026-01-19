package com.selenus.artemis.metaplex

import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-metaplex module.
 * Tests MetaplexIds, MetadataPdas, TokenMetadata.
 */
class MetaplexModuleTest {

    private val testMint = Pubkey(ByteArray(32) { 1 })

    // ===== MetaplexIds Tests =====

    @Test
    fun testTokenMetadataProgramId() {
        assertNotNull(MetaplexIds.TOKEN_METADATA_PROGRAM)
        assertEquals(32, MetaplexIds.TOKEN_METADATA_PROGRAM.bytes.size)
    }

    @Test
    fun testMetadataSeed() {
        assertNotNull(MetaplexIds.METADATA_SEED)
        assertEquals("metadata", String(MetaplexIds.METADATA_SEED))
    }

    @Test
    fun testEditionSeed() {
        assertNotNull(MetaplexIds.EDITION_SEED)
        assertEquals("edition", String(MetaplexIds.EDITION_SEED))
    }

    // ===== MetadataPdas Tests =====

    @Test
    fun testMetadataPda() {
        val pda = MetadataPdas.metadataPda(testMint)
        
        assertNotNull(pda)
        assertEquals(32, pda.bytes.size)
    }

    @Test
    fun testMetadataPdaDeterministic() {
        val pda1 = MetadataPdas.metadataPda(testMint)
        val pda2 = MetadataPdas.metadataPda(testMint)
        
        assertEquals(pda1, pda2)
    }

    @Test
    fun testMetadataPdaDifferentMints() {
        val mint1 = Pubkey(ByteArray(32) { 1 })
        val mint2 = Pubkey(ByteArray(32) { 2 })
        
        val pda1 = MetadataPdas.metadataPda(mint1)
        val pda2 = MetadataPdas.metadataPda(mint2)
        
        assertTrue(!pda1.bytes.contentEquals(pda2.bytes))
    }

    @Test
    fun testMasterEditionPda() {
        val pda = MetadataPdas.masterEditionPda(testMint)
        
        assertNotNull(pda)
        assertEquals(32, pda.bytes.size)
    }

    @Test
    fun testMasterEditionPdaDeterministic() {
        val pda1 = MetadataPdas.masterEditionPda(testMint)
        val pda2 = MetadataPdas.masterEditionPda(testMint)
        
        assertEquals(pda1, pda2)
    }

    @Test
    fun testMasterEditionDifferentFromMetadata() {
        val metadataPda = MetadataPdas.metadataPda(testMint)
        val editionPda = MetadataPdas.masterEditionPda(testMint)
        
        assertTrue(!metadataPda.bytes.contentEquals(editionPda.bytes))
    }

    // ===== MetadataData Tests =====

    @Test
    fun testMetadataDataCreation() {
        val updateAuth = Pubkey(ByteArray(32) { 1 })
        val mint = Pubkey(ByteArray(32) { 2 })
        
        val data = MetadataData(
            name = "Test NFT",
            symbol = "TEST",
            uri = "https://example.com/metadata.json",
            sellerFeeBasisPoints = 500,
            updateAuthority = updateAuth,
            mint = mint
        )
        
        assertEquals("Test NFT", data.name)
        assertEquals("TEST", data.symbol)
        assertEquals("https://example.com/metadata.json", data.uri)
        assertEquals(500, data.sellerFeeBasisPoints)
    }

    // ===== TokenMetadata object Tests =====

    @Test
    fun testTokenMetadataObjectExists() {
        assertNotNull(TokenMetadata)
    }
}
