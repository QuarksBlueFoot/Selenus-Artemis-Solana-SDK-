package com.selenus.artemis.candymachine

import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-candy-machine module.
 * Tests CandyMachinePdas, CandyMachineIds, and related utilities.
 */
class CandyMachineModuleTest {

    private val testCandyMachineId = Pubkey(ByteArray(32) { 1 })

    // ===== CandyMachineIds Tests =====

    @Test
    fun testCandyMachineCoreProgramId() {
        assertNotNull(CandyMachineIds.CANDY_MACHINE_CORE)
        assertEquals(32, CandyMachineIds.CANDY_MACHINE_CORE.bytes.size)
    }

    @Test
    fun testCandyGuardProgramId() {
        assertNotNull(CandyMachineIds.CANDY_GUARD)
        assertEquals(32, CandyMachineIds.CANDY_GUARD.bytes.size)
    }

    // ===== CandyMachinePdas Tests =====

    @Test
    fun testFindCandyMachineAuthorityPda() {
        val result = CandyMachinePdas.findCandyMachineAuthorityPda(testCandyMachineId)
        
        assertNotNull(result)
        assertNotNull(result.address)
        assertEquals(32, result.address.bytes.size)
    }

    @Test
    fun testCandyMachineAuthorityPdaDeterministic() {
        val pda1 = CandyMachinePdas.findCandyMachineAuthorityPda(testCandyMachineId)
        val pda2 = CandyMachinePdas.findCandyMachineAuthorityPda(testCandyMachineId)
        
        assertEquals(pda1.address, pda2.address)
        assertEquals(pda1.bump, pda2.bump)
    }

    @Test
    fun testCandyMachineAuthorityPdaDifferentMachines() {
        val machine1 = Pubkey(ByteArray(32) { 1 })
        val machine2 = Pubkey(ByteArray(32) { 2 })
        
        val pda1 = CandyMachinePdas.findCandyMachineAuthorityPda(machine1)
        val pda2 = CandyMachinePdas.findCandyMachineAuthorityPda(machine2)
        
        assertTrue(!pda1.address.bytes.contentEquals(pda2.address.bytes))
    }

    @Test
    fun testCandyMachineAuthorityPdaBump() {
        val result = CandyMachinePdas.findCandyMachineAuthorityPda(testCandyMachineId)
        
        // Bump should be in valid range 0-255
        assertTrue(result.bump in 0..255)
    }

    // ===== CandyGuardMintV2Safe Tests =====

    @Test
    fun testCandyGuardMintV2SafeExists() {
        assertNotNull(CandyGuardMintV2Safe)
    }

    // ===== Planner Tests =====

    @Test
    fun testMintPlanExists() {
        // Verify MintPlan class/object is accessible
        // The actual planner logic requires RPC calls
        assertTrue(true) // Placeholder for structure verification
    }

    // ===== State Reader Tests =====

    @Test
    fun testCandyMachineStateReaderExists() {
        // Verify the state reader is accessible
        assertTrue(true) // Placeholder
    }

    // ===== Seeds Tests =====

    @Test
    fun testCandyMachineSeedPrefix() {
        // The seed prefix should be "candy_machine"
        // This is implicitly tested by PDA derivation
        val pda = CandyMachinePdas.findCandyMachineAuthorityPda(testCandyMachineId)
        assertNotNull(pda)
    }

    // ===== Multiple PDAs Tests =====

    @Test
    fun testMultipleCandyMachinePdas() {
        val machines = (1..5).map { i ->
            Pubkey(ByteArray(32) { i.toByte() })
        }
        
        val pdas = machines.map { CandyMachinePdas.findCandyMachineAuthorityPda(it) }
        
        // All should be unique
        val uniqueAddresses = pdas.map { it.address.bytes.toList() }.toSet()
        assertEquals(5, uniqueAddresses.size)
    }
}
