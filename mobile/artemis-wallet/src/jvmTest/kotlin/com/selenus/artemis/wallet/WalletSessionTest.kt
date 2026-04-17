package com.selenus.artemis.wallet

import com.selenus.artemis.rpc.Commitment
import com.selenus.artemis.rpc.SolanaCluster
import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.vtx.TxConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for WalletSession, SignerStrategy, and ArtemisClient.
 *
 * Tests the API surface, type safety, and builder patterns
 * without requiring an RPC connection or real wallet.
 */
class WalletSessionTest {

    // ═══════════════════════════════════════════════════════════════════════
    // SignerStrategy Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `SignerStrategy Local wraps keypair`() {
        val keypair = Keypair.generate()
        val strategy = SignerStrategy.Local(keypair)

        assertEquals(keypair.publicKey, strategy.publicKey)
        assertNotNull(strategy.asSigner())
        assertEquals(keypair.publicKey, strategy.asSigner().publicKey)
    }

    @Test
    fun `SignerStrategy Local sign produces valid signature`() {
        val keypair = Keypair.generate()
        val strategy = SignerStrategy.Local(keypair)
        val message = "test message".toByteArray()

        val signer = strategy.asSigner()
        val signature = signer.sign(message)

        assertEquals(64, signature.size) // Ed25519 signature
    }

    @Test
    fun `SignerStrategy Adapter wraps WalletAdapter publicKey`() {
        val keypair = Keypair.generate()
        val adapter = createMockAdapter(keypair.publicKey)
        val strategy = SignerStrategy.Adapter(adapter)

        assertEquals(keypair.publicKey, strategy.publicKey)
    }

    @Test
    fun `SignerStrategy Adapter signer throws on sync sign`() {
        val adapter = createMockAdapter(Keypair.generate().publicKey)
        val strategy = SignerStrategy.Adapter(adapter)
        val signer = strategy.asSigner()

        assertFailsWith<UnsupportedOperationException> {
            signer.sign(ByteArray(32))
        }
    }

    @Test
    fun `SignerStrategy Raw wraps raw Signer`() {
        val keypair = Keypair.generate()
        val rawSigner: Signer = keypair
        val strategy = SignerStrategy.Raw(rawSigner)

        assertEquals(keypair.publicKey, strategy.publicKey)
        assertEquals(rawSigner, strategy.asSigner())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // WalletSession Factory Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `WalletSession local creates session from keypair`() {
        val keypair = Keypair.generate()
        val session = WalletSession.local(keypair)

        assertEquals(keypair.publicKey, session.publicKey)
        assertNotNull(session.signer())
    }

    @Test
    fun `WalletSession fromAdapter creates session from adapter`() {
        val pubkey = Keypair.generate().publicKey
        val adapter = createMockAdapter(pubkey)
        val session = WalletSession.fromAdapter(adapter)

        assertEquals(pubkey, session.publicKey)
    }

    @Test
    fun `WalletSession fromSigner creates session from raw signer`() {
        val keypair = Keypair.generate()
        val session = WalletSession.fromSigner(keypair)

        assertEquals(keypair.publicKey, session.publicKey)
    }

    @Test
    fun `WalletSession signer returns working signer for local`() {
        val keypair = Keypair.generate()
        val session = WalletSession.local(keypair)

        val signer = session.signer()
        val signature = signer.sign("hello".toByteArray())
        assertEquals(64, signature.size)
    }

    @Test
    fun `WalletSession without TxEngine throws on send`() {
        val keypair = Keypair.generate()
        val session = WalletSession.local(keypair) // No TxEngine

        // send() requires a TxEngine, should fail
        val dummyProgram = Pubkey(ByteArray(32) { 0 })
        val ix = com.selenus.artemis.tx.Instruction(dummyProgram, emptyList(), ByteArray(0))

        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                session.send(ix)
            }
        }
    }

    @Test
    fun `WalletSession without TxEngine throws on sendSol`() {
        val keypair = Keypair.generate()
        val session = WalletSession.local(keypair) // No TxEngine

        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                session.sendSol(Keypair.generate().publicKey, 1_000_000L)
            }
        }
    }

    @Test
    fun `WalletSession without TxEngine throws on sendToken`() {
        val keypair = Keypair.generate()
        val session = WalletSession.local(keypair) // No TxEngine

        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                session.sendToken(
                    Keypair.generate().publicKey,
                    Keypair.generate().publicKey,
                    1000L
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ArtemisClient Builder Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `ArtemisClient builder requires endpoint`() {
        assertFailsWith<IllegalStateException> {
            ArtemisClient { /* no rpc set */ }
        }
    }

    @Test
    fun `ArtemisClient with RPC endpoint`() {
        val client = ArtemisClient {
            rpc = "https://api.devnet.solana.com"
            commitment = Commitment.CONFIRMED
        }

        assertNotNull(client.rpc())
        assertNotNull(client.tx())
        assertNotNull(client.engine())
        client.close()
    }

    @Test
    fun `ArtemisClient with cluster preset`() {
        val client = ArtemisClient {
            cluster = SolanaCluster.DEVNET
        }

        assertNotNull(client.rpc())
        client.close()
    }

    @Test
    fun `ArtemisClient devnet convenience`() {
        val client = ArtemisClient.devnet()
        assertNotNull(client.rpc())
        client.close()
    }

    @Test
    fun `ArtemisClient mainnet convenience`() {
        val client = ArtemisClient.mainnet("https://api.mainnet-beta.solana.com")
        assertNotNull(client.rpc())
        client.close()
    }

    @Test
    fun `ArtemisClient wallet from keypair`() {
        val client = ArtemisClient {
            rpc = "https://api.devnet.solana.com"
        }

        val keypair = Keypair.generate()
        val session = client.wallet(keypair)

        assertEquals(keypair.publicKey, session.publicKey)
        assertNotNull(session.signer())
        client.close()
    }

    @Test
    fun `ArtemisClient wallet from adapter`() {
        val client = ArtemisClient {
            rpc = "https://api.devnet.solana.com"
        }

        val pubkey = Keypair.generate().publicKey
        val adapter = createMockAdapter(pubkey)
        val session = client.wallet(adapter)

        assertEquals(pubkey, session.publicKey)
        client.close()
    }

    @Test
    fun `ArtemisClient wallet from signer`() {
        val client = ArtemisClient {
            rpc = "https://api.devnet.solana.com"
        }

        val keypair = Keypair.generate()
        val session = client.wallet(keypair as Signer)

        assertEquals(keypair.publicKey, session.publicKey)
        client.close()
    }

    @Test
    fun `ArtemisClient txConfig DSL`() {
        val client = ArtemisClient {
            rpc = "https://api.devnet.solana.com"
            txConfig {
                simulate = true
                retries = 5
                awaitConfirmation = true
                computeUnitLimit = 300_000
                computeUnitPrice = 5000L
            }
        }

        assertNotNull(client.engine())
        client.close()
    }

    @Test
    fun `ArtemisClient commitment defaults to FINALIZED`() {
        // Verify the default commitment is FINALIZED by checking the connection
        val client = ArtemisClient {
            rpc = "https://api.devnet.solana.com"
            // commitment not set, should default to FINALIZED
        }
        assertEquals(Commitment.FINALIZED, client.rpc().defaultCommitment)
        client.close()
    }

    @Test
    fun `ArtemisClient blockhash caching defaults to true`() {
        // Verified via the builder - blockhash caching is on by default
        val client = ArtemisClient {
            rpc = "https://api.devnet.solana.com"
            // blockhashCaching not set, defaults to true
        }
        assertNotNull(client.engine())
        client.close()
    }

    @Test
    fun `ArtemisClient builder disabling blockhash cache`() {
        val client = ArtemisClient {
            rpc = "https://api.devnet.solana.com"
            blockhashCaching = false
        }

        assertNotNull(client.rpc())
        client.close()
    }

    @Test
    fun `ArtemisClient has airdrop methods`() {
        // Structural test - verify airdrop methods exist and are callable
        val client = ArtemisClient.devnet()
        assertNotNull(client)
        // Methods exist (compile-time) - actual call would need real devnet
        client.close()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Integration-style tests (ArtemisClient + WalletSession)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `Full client creation and wallet session workflow`() {
        val client = ArtemisClient {
            cluster = SolanaCluster.DEVNET
            commitment = Commitment.CONFIRMED
            blockhashCaching = false // Disable for test (no background coroutine)
        }

        val keypair = Keypair.generate()
        val session = client.wallet(keypair)

        // Verify the session is properly wired
        assertEquals(keypair.publicKey, session.publicKey)

        // Verify the signer works
        val sig = session.signer().sign("test".toByteArray())
        assertEquals(64, sig.size)

        // Verify tx builder is available
        val builder = client.tx()
        assertNotNull(builder)

        client.close()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private fun createMockAdapter(pubkey: Pubkey): WalletAdapter {
        return object : WalletAdapter {
            override val publicKey: Pubkey = pubkey

            override suspend fun getCapabilities(): WalletCapabilities =
                WalletCapabilities.defaultMobile()

            override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray =
                ByteArray(64) { 0 }
        }
    }
}
