package com.selenus.artemis.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtemisEventBusTest {

    @Test
    fun `wallet connected events reach the wallet stream`() = runBlocking {
        // Subscribe first (filter by subtype) then emit.
        val collector = async(Dispatchers.Default) {
            withTimeout(2_000) {
                ArtemisEventBus.events
                    .filterIsInstance<ArtemisEvent.Wallet.Connected>()
                    .first()
            }
        }

        // Give the collector a moment to subscribe before emitting.
        delay(50)
        ArtemisEventBus.emit(
            ArtemisEvent.Wallet.Connected(publicKey = "Alice111", walletName = "Phantom")
        )

        val event = collector.await()
        assertEquals("Alice111", event.publicKey)
        assertEquals("Phantom", event.walletName)
    }

    @Test
    fun `tx confirmed events reach the tx stream`() = runBlocking {
        val collector = async(Dispatchers.Default) {
            withTimeout(2_000) {
                ArtemisEventBus.events
                    .filterIsInstance<ArtemisEvent.Tx.Confirmed>()
                    .first()
            }
        }

        delay(50)
        ArtemisEventBus.emit(ArtemisEvent.Tx.Confirmed(signature = "sig111", slot = 42))

        val event = collector.await()
        assertEquals("sig111", event.signature)
        assertEquals(42L, event.slot)
    }

    @Test
    fun `source tag is stable per subsystem`() {
        assertEquals(ArtemisEvent.Source.WALLET, ArtemisEvent.Wallet.Disconnected().source)
        assertEquals(ArtemisEvent.Source.TX, ArtemisEvent.Tx.Sent("x").source)
        assertEquals(
            ArtemisEvent.Source.REALTIME,
            ArtemisEvent.Realtime.StateChanged("Connecting", "wss://a", epoch = 1).source
        )
        assertEquals(ArtemisEvent.Source.DAS, ArtemisEvent.Das.ProviderFailover("boom").source)
    }

    @Test
    fun `custom events carry tag and payload`() {
        val custom = ArtemisEvent.Custom(tag = "airdrop", payload = 42)
        assertEquals("airdrop", custom.tag)
        assertEquals(42, custom.payload)
        assertEquals(ArtemisEvent.Source.CUSTOM, custom.source)
    }

}
