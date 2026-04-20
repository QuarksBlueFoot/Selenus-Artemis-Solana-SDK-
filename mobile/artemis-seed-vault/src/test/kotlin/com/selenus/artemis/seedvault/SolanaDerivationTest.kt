package com.selenus.artemis.seedvault

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Golden-vector tests for [SolanaDerivation]. Each path is verified against
 * the canonical BIP-44 / Ledger Live / Ed25519-BIP32 specifications. The
 * int arrays include the 0x80000000 hardened bit where applicable so
 * consumers that feed [ParsedDerivationPath.toPathArray] into a native
 * SLIP-0010 implementation stay byte-exact.
 */
class SolanaDerivationTest {

    private val hardenedBit = 0x80000000.toInt()

    @Test
    fun standard_path_for_account_zero() {
        val path = SolanaDerivation.STANDARD
        assertEquals("m/44'/501'/0'/0'", path)
        val parsed = SolanaDerivation.parse(path)
        assertArrayEquals(
            intArrayOf(44 or hardenedBit, 501 or hardenedBit, 0 or hardenedBit, 0 or hardenedBit),
            parsed.toPathArray()
        )
        assertTrue(SolanaDerivation.isSolanaPath(path))
    }

    @Test
    fun standard_path_for_account_ten_round_trip() {
        val generated = SolanaDerivation.forAccount(10)
        assertEquals("m/44'/501'/10'/0'", generated)
        val parsed = SolanaDerivation.parse(generated)
        assertEquals(44, parsed.purpose)
        assertEquals(501, parsed.coinType)
        assertEquals(10, parsed.account)
        assertEquals(0, parsed.change)
        assertTrue(parsed.components.all { it.isHardened })
    }

    @Test
    fun ledger_live_path_omits_change_level() {
        val path = SolanaDerivation.forAccount(3, DerivationScheme.LEDGER_LIVE)
        assertEquals("m/44'/501'/3'", path)
        val parsed = SolanaDerivation.parse(path)
        assertEquals(3, parsed.components.size)
        assertArrayEquals(
            intArrayOf(44 or hardenedBit, 501 or hardenedBit, 3 or hardenedBit),
            parsed.toPathArray()
        )
        assertEquals(DerivationScheme.LEDGER_LIVE, SolanaDerivation.detectScheme(path))
    }

    @Test
    fun ed25519_bip32_uses_coin_type_1022() {
        val path = SolanaDerivation.forAccount(2, DerivationScheme.ED25519_BIP32)
        assertEquals("m/44'/1022'/2'/0'", path)
        assertEquals(DerivationScheme.ED25519_BIP32, SolanaDerivation.detectScheme(path))
        assertTrue(SolanaDerivation.isSolanaPath(path))
    }

    @Test
    fun scheme_detection_round_trip() {
        val table = mapOf(
            "m/44'/501'/0'/0'" to DerivationScheme.STANDARD,
            "m/44'/501'/5'" to DerivationScheme.LEDGER_LIVE,
            "m/44'/1022'/0'/0'" to DerivationScheme.ED25519_BIP32
        )
        for ((path, expected) in table) {
            assertEquals(expected, SolanaDerivation.detectScheme(path), "scheme($path)")
        }
    }

    @Test
    fun extract_account_index() {
        assertEquals(0, SolanaDerivation.extractAccountIndex("m/44'/501'/0'/0'"))
        assertEquals(7, SolanaDerivation.extractAccountIndex("m/44'/501'/7'"))
        assertEquals(99, SolanaDerivation.extractAccountIndex("m/44'/1022'/99'/0'"))
    }

    @Test
    fun convert_scheme_preserves_account_index() {
        val standardAccount5 = "m/44'/501'/5'/0'"
        val ledger = SolanaDerivation.convertScheme(standardAccount5, DerivationScheme.LEDGER_LIVE)
        assertEquals("m/44'/501'/5'", ledger)
        val back = SolanaDerivation.convertScheme(ledger, DerivationScheme.STANDARD)
        assertEquals("m/44'/501'/5'/0'", back)
    }

    @Test
    fun next_account_increments_index() {
        assertEquals("m/44'/501'/1'/0'", SolanaDerivation.nextAccount("m/44'/501'/0'/0'"))
        assertEquals("m/44'/501'/6'", SolanaDerivation.nextAccount("m/44'/501'/5'"))
    }

    @Test
    fun path_validation_rejects_malformed() {
        assertFalse(SolanaDerivation.isValid("m/not_an_int'"))
        assertFalse(SolanaDerivation.isValid(""))
        assertFalse(SolanaDerivation.isValid("m/44'/xx'/0'/0'"))
    }

    @Test
    fun non_solana_paths_rejected_by_isSolanaPath() {
        assertFalse(SolanaDerivation.isSolanaPath("m/44'/60'/0'/0/0"))
        assertFalse(SolanaDerivation.isSolanaPath("m/44/501/0'/0'"))
    }

    @Test
    fun multiple_accounts_produces_distinct_paths() {
        val paths = SolanaDerivation.multipleAccounts(5)
        assertEquals(5, paths.size)
        assertEquals(5, paths.toSet().size)
        paths.forEachIndexed { i, p ->
            assertEquals("m/44'/501'/$i'/0'", p)
        }
    }

    @Test
    fun hardened_bit_encoded_correctly_in_toBip32Value() {
        assertEquals(44 or hardenedBit, PathComponent(44, true).toBip32Value())
        assertEquals(0, PathComponent(0, false).toBip32Value())
        assertEquals(501 or hardenedBit, PathComponent(501, true).toBip32Value())
    }
}
