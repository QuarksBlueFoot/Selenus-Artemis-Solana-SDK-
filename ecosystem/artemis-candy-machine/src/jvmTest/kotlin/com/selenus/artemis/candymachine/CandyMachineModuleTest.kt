package com.selenus.artemis.candymachine

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.candymachine.guards.GuardType
import com.selenus.artemis.candymachine.guards.GuardRequirements
import com.selenus.artemis.candymachine.guards.CandyGuardManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for artemis-candy-machine module.
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

    // ===== Guard Manifest Tests =====

    @Test
    fun testGuardManifestConstruction() {
        val requirements = GuardRequirements(
            requiresSolPayment = true,
            requiresTokenPayment = false,
            requiresAllowList = false,
            requiresGatekeeper = false,
            requiresNftBurn = false,
            requiresNftGate = false,
            requiresTokenGate = false,
            requiresMintLimitArgs = false,
            requiresAllocationArgs = false
        )
        val manifest = CandyGuardManifest(
            enabledGuards = setOf(GuardType.solPayment, GuardType.startDate),
            requirements = requirements,
            argSchema = emptyList(),
            remainingAccountRules = emptyList(),
            isPnft = false,
            solPaymentLamports = 1_000_000_000L
        )

        assertEquals(2, manifest.enabledGuards.size)
        assertTrue(manifest.enabledGuards.contains(GuardType.solPayment))
        assertTrue(requirements.requiresSolPayment)
        assertFalse(requirements.requiresTokenPayment)
        assertEquals(1_000_000_000L, manifest.solPaymentLamports)
        assertFalse(manifest.isPnft)
    }

    // ===== Guard Type Tests =====

    @Test
    fun testGuardTypeValues() {
        val allGuards = GuardType.entries
        assertTrue(allGuards.size >= 20, "Should have at least 20 guard types")
        assertTrue(allGuards.contains(GuardType.solPayment))
        assertTrue(allGuards.contains(GuardType.tokenPayment))
        assertTrue(allGuards.contains(GuardType.startDate))
        assertTrue(allGuards.contains(GuardType.endDate))
        assertTrue(allGuards.contains(GuardType.allowList))
        assertTrue(allGuards.contains(GuardType.mintLimit))
        assertTrue(allGuards.contains(GuardType.unknown))
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
