package com.selenus.artemis.wallet.mwa

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.PlatformBase64
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.wallet.mwa.protocol.MwaAccount
import com.selenus.artemis.wallet.mwa.protocol.MwaAuthorizeResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour tests for [MwaMultiAccountSession]. The session is the typed
 * wrapper around the wallet's `accounts: MwaAccount[]` that keeps lookup by
 * pubkey/label/chain ergonomic without the caller re-parsing base64.
 */
class MwaMultiAccountSessionTest {

    private val pkA = ByteArray(32) { 0x11 }
    private val pkB = ByteArray(32) { 0x22 }
    private val pkC = ByteArray(32) { 0x33 }

    private fun account(
        pk: ByteArray,
        label: String? = null,
        chains: List<String>? = null,
        features: List<String>? = null,
        icon: String? = null
    ): MwaAccount = MwaAccount(
        address = PlatformBase64.encode(pk),
        label = label,
        chains = chains,
        features = features,
        icon = icon
    )

    private fun authResult(vararg accounts: MwaAccount, token: String = "T-1"): MwaAuthorizeResult =
        MwaAuthorizeResult(
            authToken = token,
            accounts = accounts.toList(),
            walletUriBase = "https://wallet.example/uri"
        )

    @Test
    fun `from produces non-empty session and primary equals first account`() {
        val session = MwaMultiAccountSession.from(
            authResult(
                account(pkA, label = "Primary"),
                account(pkB, label = "Secondary")
            )
        )

        assertEquals("T-1", session.authToken)
        assertEquals("https://wallet.example/uri", session.walletUriBase)
        assertEquals(2, session.accounts.size)
        assertArrayEquals(pkA, session.primary.pubkey.bytes)
        assertEquals("Primary", session.primary.label)
        assertEquals(listOf(Pubkey(pkA), Pubkey(pkB)), session.pubkeys)
    }

    @Test
    fun `from rejects empty account lists`() {
        try {
            MwaMultiAccountSession.from(authResult())
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `byPubkey finds the account by Pubkey value`() {
        val session = MwaMultiAccountSession.from(
            authResult(
                account(pkA, label = "Alpha"),
                account(pkB, label = "Beta")
            )
        )

        val found = session.byPubkey(Pubkey(pkB))
        assertNotNull(found)
        assertEquals("Beta", found!!.label)

        val missing = session.byPubkey(Pubkey(pkC))
        assertNull(missing)
    }

    @Test
    fun `byPubkey by base58 string finds the account`() {
        val session = MwaMultiAccountSession.from(
            authResult(
                account(pkA),
                account(pkB, label = "Bravo")
            )
        )

        val found = session.byPubkey(Base58.encode(pkB))
        assertNotNull(found)
        assertEquals("Bravo", found!!.label)

        // Bad base58 returns null instead of throwing.
        assertNull(session.byPubkey("not-a-real-base58!!"))
    }

    @Test
    fun `byLabel is case-insensitive and returns first match`() {
        val session = MwaMultiAccountSession.from(
            authResult(
                account(pkA, label = "Trading"),
                account(pkB, label = "Savings")
            )
        )

        assertEquals(Pubkey(pkA), session.byLabel("trading")!!.pubkey)
        assertEquals(Pubkey(pkB), session.byLabel("SAVINGS")!!.pubkey)
        assertNull(session.byLabel("nonexistent"))
    }

    @Test
    fun `forChain filters accounts whose chains list includes the target`() {
        val session = MwaMultiAccountSession.from(
            authResult(
                account(pkA, label = "MainnetOnly", chains = listOf("solana:mainnet")),
                account(pkB, label = "BothChains",
                    chains = listOf("solana:mainnet", "solana:devnet")),
                account(pkC, label = "ChainAgnostic", chains = emptyList())
            )
        )

        val mainnet = session.forChain("solana:mainnet").map { it.label }
        // Both pkA and pkB include mainnet, plus pkC has no chain restriction.
        assertEquals(listOf("MainnetOnly", "BothChains", "ChainAgnostic"), mainnet)

        val devnet = session.forChain("solana:devnet").map { it.label }
        // pkA does NOT include devnet, but pkC's empty chains list = chain-agnostic.
        assertEquals(listOf("BothChains", "ChainAgnostic"), devnet)
    }

    @Test
    fun `resolvable returns subset of candidates this session can sign`() {
        val session = MwaMultiAccountSession.from(
            authResult(
                account(pkA, label = "A"),
                account(pkB, label = "B")
            )
        )

        val resolved = session.resolvable(listOf(Pubkey(pkA), Pubkey(pkC), Pubkey(pkB)))
        assertEquals(2, resolved.size)
        assertEquals("A", resolved[0].label)
        assertEquals("B", resolved[1].label)
    }

    @Test
    fun `missing returns candidates this session cannot sign`() {
        val session = MwaMultiAccountSession.from(
            authResult(
                account(pkA),
                account(pkB)
            )
        )

        val missing = session.missing(listOf(Pubkey(pkA), Pubkey(pkC), Pubkey(pkB)))
        assertEquals(listOf(Pubkey(pkC)), missing)
    }

    @Test
    fun `reauthorizeAddressesB64 round-trips back to the original account bytes`() {
        val session = MwaMultiAccountSession.from(
            authResult(
                account(pkA),
                account(pkB)
            )
        )

        val b64 = session.reauthorizeAddressesB64()
        assertEquals(2, b64.size)
        assertArrayEquals(pkA, PlatformBase64.decode(b64[0]))
        assertArrayEquals(pkB, PlatformBase64.decode(b64[1]))
    }

    @Test
    fun `disambiguates accounts by features`() {
        // Multi-account: A advertises sign-and-send, B advertises sign-in,
        // C is the stock account with no features. Caller routes signing
        // verbs against the right account by reading the feature lists.
        val session = MwaMultiAccountSession.from(
            authResult(
                account(pkA, features = listOf("solana:signAndSendTransaction")),
                account(pkB, features = listOf("solana:signInWithSolana")),
                account(pkC, features = emptyList())
            )
        )

        val signers = session.accounts.filter {
            "solana:signAndSendTransaction" in it.features
        }
        val siwsAccounts = session.accounts.filter {
            "solana:signInWithSolana" in it.features
        }

        assertEquals(1, signers.size)
        assertArrayEquals(pkA, signers.single().pubkey.bytes)
        assertEquals(1, siwsAccounts.size)
        assertArrayEquals(pkB, siwsAccounts.single().pubkey.bytes)
    }

    @Test
    fun `ResolvedAccount preserves the raw upstream MwaAccount`() {
        // Apps that need to forward the raw upstream record can do so without
        // reconstructing it from the resolved fields.
        val raw = account(pkA, label = "Raw-Carry", chains = listOf("solana:mainnet"))
        val session = MwaMultiAccountSession.from(authResult(raw))
        val resolved = session.primary

        assertSame(raw, resolved.raw)
        assertEquals(listOf("solana:mainnet"), resolved.chains)
        assertFalse(
            "no extra mutation of features",
            resolved.features.isNotEmpty()
        )
    }
}
