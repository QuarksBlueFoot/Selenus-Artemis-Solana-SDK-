package com.selenus.artemis.ws

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-ws module.
 * Tests WsEvent, SolanaWsClient, and related utilities.
 */
class WsModuleTest {

    // ===== WsEvent.Connected Tests =====

    @Test
    fun testWsEventConnected() {
        val event = WsEvent.Connected
        
        assertNotNull(event)
        assertTrue(event is WsEvent)
    }

    // ===== WsEvent.Disconnected Tests =====

    @Test
    fun testWsEventDisconnected() {
        val event = WsEvent.Disconnected("Connection reset")
        
        assertEquals("Connection reset", event.reason)
    }

    @Test
    fun testWsEventDisconnectedEmptyReason() {
        val event = WsEvent.Disconnected("")
        
        assertEquals("", event.reason)
    }

    // ===== WsEvent.Reconnecting Tests =====

    @Test
    fun testWsEventReconnecting() {
        val event = WsEvent.Reconnecting(attempt = 3, inMs = 5000L)
        
        assertEquals(3, event.attempt)
        assertEquals(5000L, event.inMs)
    }

    @Test
    fun testWsEventReconnectingFirstAttempt() {
        val event = WsEvent.Reconnecting(attempt = 1, inMs = 500L)
        
        assertEquals(1, event.attempt)
        assertEquals(500L, event.inMs)
    }

    // ===== WsEvent.GaveUp Tests =====

    @Test
    fun testWsEventGaveUp() {
        val event = WsEvent.GaveUp
        
        assertNotNull(event)
        assertTrue(event is WsEvent)
    }

    // ===== WsEvent.Heartbeat Tests =====

    @Test
    fun testWsEventHeartbeat() {
        val now = System.currentTimeMillis()
        val event = WsEvent.Heartbeat(atMs = now)
        
        assertEquals(now, event.atMs)
    }

    // ===== WsEvent.Subscribed Tests =====

    @Test
    fun testWsEventSubscribed() {
        val event = WsEvent.Subscribed(key = "account:abc123", subscriptionId = 42L)
        
        assertEquals("account:abc123", event.key)
        assertEquals(42L, event.subscriptionId)
    }

    // ===== WsEvent.Notification Tests =====

    @Test
    fun testWsEventNotification() {
        val event = WsEvent.Notification(
            key = "balance:wallet1",
            subscriptionId = 1L,
            method = "accountNotification",
            result = JsonPrimitive(1000000000),
            isSampled = false
        )
        
        assertEquals("balance:wallet1", event.key)
        assertEquals(1L, event.subscriptionId)
        assertEquals("accountNotification", event.method)
        assertEquals(false, event.isSampled)
    }

    @Test
    fun testWsEventNotificationSampled() {
        val event = WsEvent.Notification(
            key = "slot",
            subscriptionId = 2L,
            method = "slotNotification",
            result = null,
            isSampled = true
        )
        
        assertTrue(event.isSampled)
    }

    // ===== WsEvent.Backpressure Tests =====

    @Test
    fun testWsEventBackpressure() {
        val event = WsEvent.Backpressure(
            key = "highFrequencyKey",
            dropped = 50,
            windowMs = 1000L
        )
        
        assertEquals("highFrequencyKey", event.key)
        assertEquals(50, event.dropped)
        assertEquals(1000L, event.windowMs)
    }

    @Test
    fun testWsEventBackpressureNullKey() {
        val event = WsEvent.Backpressure(
            key = null,
            dropped = 10,
            windowMs = 500L
        )
        
        assertEquals(null, event.key)
    }

    // ===== WsEvent.Error Tests =====

    @Test
    fun testWsEventError() {
        val payload = buildJsonObject {
            put("code", JsonPrimitive(-32600))
            put("message", JsonPrimitive("Invalid Request"))
        }
        val event = WsEvent.Error(message = "RPC Error", payload = payload)
        
        assertEquals("RPC Error", event.message)
        assertNotNull(event.payload)
    }

    // ===== WsEvent.Raw Tests =====

    @Test
    fun testWsEventRaw() {
        val rawText = """{"jsonrpc":"2.0","method":"test","params":{}}"""
        val event = WsEvent.Raw(text = rawText)
        
        assertEquals(rawText, event.text)
    }

    // ===== WsConfig Tests =====

    @Test
    fun testWsConfigDefaults() {
        val config = SolanaWsClient.WsConfig()
        
        assertEquals(15_000L, config.pingIntervalMs)
        assertEquals(500L, config.minBackoffMs)
        assertEquals(15_000L, config.maxBackoffMs)
        assertEquals(Int.MAX_VALUE, config.maxReconnectAttempts)
        assertEquals(256, config.eventBuffer)
        assertEquals(40L, config.bundleWindowMs)
    }

    @Test
    fun testWsConfigCustom() {
        val config = SolanaWsClient.WsConfig(
            pingIntervalMs = 10_000L,
            minBackoffMs = 1000L,
            maxBackoffMs = 30_000L,
            maxReconnectAttempts = 5,
            eventBuffer = 512,
            bundleWindowMs = 100L
        )
        
        assertEquals(10_000L, config.pingIntervalMs)
        assertEquals(5, config.maxReconnectAttempts)
        assertEquals(512, config.eventBuffer)
    }

    // ===== SubscriptionHandle Tests =====
    // SubscriptionHandle is an internal class with internal constructor - cannot test directly

    // ===== NotificationRouter Tests =====
    // NotificationRouter is an internal class - cannot test directly

    // ===== NotificationPolicy Tests =====

    @Test
    fun testNotificationPolicyDefaults() {
        val policy = NotificationPolicy()
        assertEquals(250L, policy.sampleWindowMs)
        assertEquals(1_000L, policy.backpressureWindowMs)
        assertEquals(512, policy.maxPendingNotifications)
    }

    @Test
    fun testNotificationPolicyIsCritical() {
        val policy = NotificationPolicy()
        assertTrue(policy.isCritical("sig:abc123"))
        assertTrue(policy.isCritical("acct:xyz456"))
        assertFalse(policy.isCritical("slot:123"))
        assertFalse(policy.isCritical(null))
    }

    // ===== Event Type Checks =====

    @Test
    fun testAllEventTypesAreWsEvent() {
        val events: List<WsEvent> = listOf(
            WsEvent.Connected,
            WsEvent.Disconnected("reason"),
            WsEvent.Reconnecting(1, 1000L),
            WsEvent.GaveUp,
            WsEvent.Heartbeat(System.currentTimeMillis()),
            WsEvent.Subscribed("key", 1L),
            WsEvent.Notification("key", 1L, "method", null),
            WsEvent.Backpressure(null, 0, 0L),
            WsEvent.Error("error", buildJsonObject {}),
            WsEvent.Raw("text")
        )
        
        assertEquals(10, events.size)
        assertTrue(events.all { it is WsEvent })
    }

    // ===== Event Pattern Matching =====

    @Test
    fun testEventPatternMatching() {
        val event: WsEvent = WsEvent.Subscribed("test", 123L)
        
        val result = when (event) {
            is WsEvent.Connected -> "connected"
            is WsEvent.Disconnected -> "disconnected"
            is WsEvent.Reconnecting -> "reconnecting"
            is WsEvent.GaveUp -> "gave_up"
            is WsEvent.Heartbeat -> "heartbeat"
            is WsEvent.Subscribed -> "subscribed:${event.key}"
            is WsEvent.Notification -> "notification"
            is WsEvent.Backpressure -> "backpressure"
            is WsEvent.Error -> "error"
            is WsEvent.Raw -> "raw"
        }
        
        assertEquals("subscribed:test", result)
    }
}
