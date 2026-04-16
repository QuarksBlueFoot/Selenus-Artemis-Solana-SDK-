package com.selenus.artemis.core

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class ArtemisEventBusTest {

    @Test
    fun `wallet connected events reach the wallet stream`() = runBlocking {
        val event = withTimeout(3_000) {
            ArtemisEventBus.events
                .onSubscription {
                    // Emit only after the downstream collector has subscribed.
                    // This eliminates the classic SharedFlow-no-replay race where the
                    // event is emitted before any collector is listening.
                    ArtemisEventBus.emit(
                        ArtemisEvent.Wallet.Connected(publicKey = "Alice111", walletName = "Phantom")
                    )
                }
                .filterIsInstance<ArtemisEvent.Wallet.Connected>()
                .first()
        }
        assertEquals("Alice111", event.publicKey)
        assertEquals("Phantom", event.walletName)
    }

    @Test
    fun `tx confirmed events reach the tx stream`() = runBlocking {
        val event = withTimeout(3_000) {
            ArtemisEventBus.events
                .onSubscription {
                    ArtemisEventBus.emit(ArtemisEvent.Tx.Confirmed(signature = "sig111", slot = 42))
                }
                .filterIsInstance<ArtemisEvent.Tx.Confirmed>()
                .first()
        }
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
