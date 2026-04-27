package com.selenus.artemis.wallet.mwa.walletlib

import kotlinx.coroutines.channels.Channel

/**
 * Test-only transport pair that bypasses the real WS framing layer.
 *
 * Both sides expose a [WalletTransport]-shaped surface so the wallet's
 * dispatcher can run against it. The dApp end is a thin glue object
 * exposing the same `send` / `incoming` shape but typed loosely so
 * tests do not have to import the dApp-side `MwaTransport` (which is
 * still internal in the protocol module).
 *
 * Frames pushed into one side appear on the other's `incoming`
 * channel verbatim. AES-GCM framing happens above this layer; the
 * pair carries opaque bytes only.
 */
internal class InMemoryTransportPair {
    val toWallet: Channel<ByteArray> = Channel(Channel.BUFFERED)
    val toDapp: Channel<ByteArray> = Channel(Channel.BUFFERED)

    val walletEnd: WalletTransport = object : WalletTransport {
        override val incoming: Channel<ByteArray> get() = toWallet
        override fun send(data: ByteArray) {
            toDapp.trySend(data)
        }
        override fun close(code: Int, reason: String) {
            toWallet.close()
            toDapp.close()
        }
    }

    val dappEnd: DappTransport = object : DappTransport {
        override val incoming: Channel<ByteArray> get() = toDapp
        override fun send(data: ByteArray) {
            toWallet.trySend(data)
        }
        override fun close() {
            toWallet.close()
            toDapp.close()
        }
    }

    /**
     * Simplified dApp-side surface. Distinct from the wallet-side
     * transport because the dApp's WS server returns a different
     * `MwaTransport` type that we can't import across modules without
     * promoting it; this lightweight interface gives the test what it
     * needs.
     */
    interface DappTransport {
        val incoming: Channel<ByteArray>
        fun send(data: ByteArray)
        fun close()
    }
}
