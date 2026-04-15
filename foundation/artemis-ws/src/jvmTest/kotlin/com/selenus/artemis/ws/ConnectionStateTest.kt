package com.selenus.artemis.ws

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ConnectionStateTest {

    @Test
    fun `Idle is not live and not terminal`() {
        val s = ConnectionState.Idle()
        assertFalse(s.isLive)
        assertFalse(s.isTerminal)
    }

    @Test
    fun `Connected is live and not terminal`() {
        val s = ConnectionState.Connected(
            endpoint = "wss://rpc.example",
            subscriptions = 3,
            epoch = 5,
            atMs = 100
        )
        assertTrue(s.isLive)
        assertFalse(s.isTerminal)
    }

    @Test
    fun `Reconnecting is not live and not terminal`() {
        val s = ConnectionState.Reconnecting(
            endpoint = "wss://rpc.example",
            attempt = 2,
            nextDelayMs = 750,
            reason = "socket dropped",
            epoch = 6,
            atMs = 200
        )
        assertFalse(s.isLive)
        assertFalse(s.isTerminal)
        assertEquals(2, s.attempt)
    }

    @Test
    fun `Closed is terminal but not live`() {
        val s = ConnectionState.Closed(reason = "budget exhausted", epoch = 9, atMs = 500)
        assertFalse(s.isLive)
        assertTrue(s.isTerminal)
    }

    @Test
    fun `epoch differentiates fresh connects from reconnects`() {
        val first = ConnectionState.Connected("wss://a", 0, epoch = 1, atMs = 1)
        val second = ConnectionState.Connected("wss://a", 0, epoch = 4, atMs = 50)
        assertEquals(first.endpoint, second.endpoint)
        assertNotEquals(first.epoch, second.epoch)
    }
}
