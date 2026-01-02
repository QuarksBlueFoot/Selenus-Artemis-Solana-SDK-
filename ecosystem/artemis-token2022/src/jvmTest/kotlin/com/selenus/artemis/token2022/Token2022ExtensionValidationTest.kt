package com.selenus.artemis.token2022

import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Tests for Token-2022 extension validation logic.
 */
class Token2022ExtensionValidationTest {

    private val dummyKey = Pubkey(ByteArray(32) { 0x42 })

    // ════════════════════════════════════════════════════════════════════════
    // validateExtensions tests
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `valid single extension passes`() {
        val errors = AdvancedToken2022Extensions.validateExtensions(
            listOf(AdvancedToken2022Extensions.MintExtension.NonTransferable)
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `compatible extensions pass validation`() {
        val errors = AdvancedToken2022Extensions.validateExtensions(listOf(
            AdvancedToken2022Extensions.MintExtension.TransferFee(
                transferFeeConfigAuthority = dummyKey,
                withdrawWithheldAuthority = dummyKey,
                transferFeeBasisPoints = 100,
                maximumFee = 1_000_000
            ),
            AdvancedToken2022Extensions.MintExtension.TransferHook(
                authority = dummyKey,
                programId = dummyKey
            )
        ))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `NonTransferable + TransferFee is incompatible`() {
        val errors = AdvancedToken2022Extensions.validateExtensions(listOf(
            AdvancedToken2022Extensions.MintExtension.NonTransferable,
            AdvancedToken2022Extensions.MintExtension.TransferFee(
                transferFeeConfigAuthority = dummyKey,
                withdrawWithheldAuthority = dummyKey,
                transferFeeBasisPoints = 100,
                maximumFee = 1_000_000
            )
        ))
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("NonTransferable"))
        assertTrue(errors[0].contains("TransferFee"))
    }

    @Test
    fun `NonTransferable + TransferHook is incompatible`() {
        val errors = AdvancedToken2022Extensions.validateExtensions(listOf(
            AdvancedToken2022Extensions.MintExtension.NonTransferable,
            AdvancedToken2022Extensions.MintExtension.TransferHook(
                authority = dummyKey,
                programId = dummyKey
            )
        ))
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("NonTransferable"))
        assertTrue(errors[0].contains("TransferHook"))
    }

    @Test
    fun `duplicate extensions detected`() {
        val errors = AdvancedToken2022Extensions.validateExtensions(listOf(
            AdvancedToken2022Extensions.MintExtension.InterestBearing(
                rateAuthority = dummyKey, rate = 10
            ),
            AdvancedToken2022Extensions.MintExtension.InterestBearing(
                rateAuthority = dummyKey, rate = 20
            )
        ))
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Duplicate"))
    }

    @Test
    fun `all three errors detected at once`() {
        val errors = AdvancedToken2022Extensions.validateExtensions(listOf(
            AdvancedToken2022Extensions.MintExtension.NonTransferable,
            AdvancedToken2022Extensions.MintExtension.TransferFee(
                transferFeeConfigAuthority = dummyKey,
                withdrawWithheldAuthority = dummyKey,
                transferFeeBasisPoints = 100,
                maximumFee = 1_000_000
            ),
            AdvancedToken2022Extensions.MintExtension.TransferHook(
                authority = dummyKey,
                programId = dummyKey
            ),
            AdvancedToken2022Extensions.MintExtension.TransferFee(
                transferFeeConfigAuthority = dummyKey,
                withdrawWithheldAuthority = dummyKey,
                transferFeeBasisPoints = 200,
                maximumFee = 2_000_000
            )
        ))
        // NonTransferable+TransferFee, NonTransferable+TransferHook, Duplicate TransferFee
        assertEquals(3, errors.size)
    }

    @Test
    fun `empty extensions list is valid`() {
        val errors = AdvancedToken2022Extensions.validateExtensions(emptyList())
        assertTrue(errors.isEmpty())
    }

    // ════════════════════════════════════════════════════════════════════════
    // prepareMintWithExtensions validation integration
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `prepareMintWithExtensions rejects incompatible extensions`() {
        assertFailsWith<IllegalArgumentException> {
            AdvancedToken2022Extensions.prepareMintWithExtensions(
                mint = dummyKey,
                decimals = 6,
                mintAuthority = dummyKey,
                freezeAuthority = null,
                payer = dummyKey,
                extensions = listOf(
                    AdvancedToken2022Extensions.MintExtension.NonTransferable,
                    AdvancedToken2022Extensions.MintExtension.TransferFee(
                        transferFeeConfigAuthority = dummyKey,
                        withdrawWithheldAuthority = dummyKey,
                        transferFeeBasisPoints = 100,
                        maximumFee = 1_000_000
                    )
                )
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // calculateMintSpace tests
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `calculateMintSpace returns base for no extensions`() {
        val space = AdvancedToken2022Extensions.calculateMintSpace(emptyList())
        assertEquals(82, space)
    }

    @Test
    fun `calculateMintSpace accounts for transfer fee extension`() {
        val space = AdvancedToken2022Extensions.calculateMintSpace(listOf(
            AdvancedToken2022Extensions.MintExtension.TransferFee(
                transferFeeConfigAuthority = dummyKey,
                withdrawWithheldAuthority = dummyKey,
                transferFeeBasisPoints = 100,
                maximumFee = 1_000_000
            )
        ))
        assertTrue(space > 82)
    }
}
