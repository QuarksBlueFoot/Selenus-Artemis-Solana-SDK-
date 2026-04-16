/*
 * Tests for the WalletSessionManager state machine surface.
 *
 * These exercise transitions without needing a real MWA adapter: the tests
 * inject a recording `connector` that simulates success and failure paths,
 * then assert the observable StateFlow matches the expected sequence.
 */
package com.selenus.artemis.wallet

import com.selenus.artemis.runtime.Pubkey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class WalletSessionStateTest {

    private fun fakeSession(pubkeyByte: Byte): WalletSession {
        val keypair = com.selenus.artemis.runtime.Keypair.fromSeed(ByteArray(32) { pubkeyByte })
        return WalletSession.local(keypair)
    }

    @Test
    fun `initial state is Disconnected`(): Unit = runBlocking {
        val manager = WalletSessionManager(
            connector = { fakeSession(1) },
            publishToBus = false
        )
        val initial = manager.state.first()
        assertIs<WalletSessionState.Disconnected>(initial)
        assertEquals(0, initial.epoch)
    }

    @Test
    fun `connect transitions Connecting then Connected`(): Unit = runBlocking {
        val manager = WalletSessionManager(
            connector = { fakeSession(7) },
            publishToBus = false
        )
        val session = manager.connect()
        assertEquals(32, session.publicKey.bytes.size)
        val state = manager.state.value
        assertIs<WalletSessionState.Connected>(state)
        assertTrue(state.isLive)
        assertTrue(state.epoch > 0)
    }

    @Test
    fun `failed connect transitions to Failed`(): Unit = runBlocking {
        val manager = WalletSessionManager(
            connector = { error("wallet-refused") },
            publishToBus = false
        )
        try {
            manager.connect()
            fail("expected error")
        } catch (_: Throwable) {
            // expected
        }
        val state = manager.state.value
        assertIs<WalletSessionState.Failed>(state)
        assertTrue("wallet-refused" in state.reason || "IllegalStateException" in state.reason)
        assertTrue(state.needsUserAction)
    }

    @Test
    fun `invalidate transitions to Expired`(): Unit = runBlocking {
        val manager = WalletSessionManager(
            connector = { fakeSession(3) },
            publishToBus = false
        )
        manager.connect()
        manager.invalidate()
        assertIs<WalletSessionState.Expired>(manager.state.value)
    }

    @Test
    fun `disconnect transitions to Disconnected`(): Unit = runBlocking {
        val manager = WalletSessionManager(
            connector = { fakeSession(4) },
            publishToBus = false
        )
        manager.connect()
        manager.disconnect()
        assertIs<WalletSessionState.Disconnected>(manager.state.value)
    }

    @Test
    fun `withWallet retries once on recoverable error using silentConnector`(): Unit = runBlocking {
        var connectCalls = 0
        var silentCalls = 0
        var actionCalls = 0
        var firstAction = true

        val manager = WalletSessionManager(
            connector = {
                connectCalls++
                fakeSession(9)
            },
            silentConnector = {
                silentCalls++
                fakeSession(9)
            },
            publishToBus = false
        )

        val result = manager.withWallet { _ ->
            actionCalls++
            if (firstAction) {
                firstAction = false
                throw IllegalStateException("auth_token expired")
            }
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(1, connectCalls)
        assertEquals(1, silentCalls)
        assertEquals(2, actionCalls)
        assertIs<WalletSessionState.Connected>(manager.state.value)
    }

    @Test
    fun `withWallet does not retry on non-recoverable errors`(): Unit = runBlocking {
        var connectCalls = 0
        val manager = WalletSessionManager(
            connector = {
                connectCalls++
                fakeSession(5)
            },
            publishToBus = false
        )

        try {
            manager.withWallet { _ ->
                // Something like a validation failure that is not a session expiry.
                throw RuntimeException("insufficient funds")
            }
            fail("expected error")
        } catch (e: RuntimeException) {
            assertEquals("insufficient funds", e.message)
        }

        assertEquals(1, connectCalls)
    }

    @Test
    fun `monotonic epoch increases across transitions`(): Unit = runBlocking {
        val manager = WalletSessionManager(
            connector = { fakeSession(2) },
            publishToBus = false
        )
        val seen = mutableListOf<Long>()
        seen += manager.state.value.epoch
        manager.connect(); seen += manager.state.value.epoch
        manager.invalidate(); seen += manager.state.value.epoch
        manager.connect(); seen += manager.state.value.epoch
        manager.disconnect(); seen += manager.state.value.epoch

        for (i in 1 until seen.size) {
            assertTrue(seen[i] > seen[i - 1], "epoch must strictly increase: ${seen.joinToString()}")
        }
    }

    @Test
    fun `Connected state exposes the public key`(): Unit = runBlocking {
        val manager = WalletSessionManager(
            connector = { fakeSession(11) },
            publishToBus = false
        )
        val session = manager.connect()
        val connected = manager.state.value as WalletSessionState.Connected
        assertEquals(session.publicKey, connected.publicKey)
    }

    @Test
    fun `isRecoverable covers typical MWA error messages`(): Unit = runBlocking {
        val messages = listOf(
            "AUTH_TOKEN_INVALID",
            "Session expired",
            "invalid_authorization",
            "please reauthorize",
            "Not authorized"
        )
        for (msg in messages) {
            var calls = 0
            val manager = WalletSessionManager(
                connector = { fakeSession(12) },
                silentConnector = { fakeSession(12) },
                publishToBus = false
            )
            val result = manager.withWallet { _ ->
                calls++
                if (calls == 1) throw RuntimeException(msg)
                "recovered"
            }
            assertEquals("recovered", result, "failed to recover from message: $msg")
            assertEquals(2, calls, "message '$msg' should have triggered a retry")
        }
    }

    // Pubkey is used implicitly for Connected state assertions above.
    @Suppress("unused")
    private val pubkeyExample: Pubkey = Pubkey(ByteArray(32))
}
