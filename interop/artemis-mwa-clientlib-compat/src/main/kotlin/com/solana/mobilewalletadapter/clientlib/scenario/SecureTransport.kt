/*
 * SecureTransport: authority over the networking surface a scenario
 * exposes to the wallet.
 *
 * Extracted from Scenario so the scenario class does not touch
 * ServerSocket, port allocation, or socket lifecycle directly. The
 * transport owns the bind and the teardown; Scenario asks for a port
 * and stops asking when it closes.
 */
package com.solana.mobilewalletadapter.clientlib.scenario

import java.net.ServerSocket

/**
 * Network-layer authority for a local association scenario.
 *
 * Implementations reserve the port the wallet will dial, report it
 * through [port] once reserved, and release the port on [close]. The
 * interface is intentionally tiny: Scenario should not know that there
 * is a TCP socket behind it, only that there is an abstract port it can
 * put in the association URI.
 *
 * Thread-safety: callers should treat transports as not-safe-for-
 * concurrent-use. Scenario.start / Scenario.close run on a single
 * caller-controlled thread in upstream; mirroring that here avoids
 * over-locking for no benefit.
 */
interface SecureTransport {

    /**
     * Reserve the ephemeral port for this scenario. Idempotent: calling
     * more than once returns the originally reserved port. Returns the
     * port number (1..65535).
     *
     * @throws java.io.IOException if no ephemeral port could be bound.
     */
    fun reservePort(): Int

    /**
     * Current reserved port, or 0 when no port has been reserved yet or
     * the transport has been closed. Reading this field never triggers a
     * bind.
     */
    val port: Int

    /**
     * Release any resources the transport holds. Safe to call more than
     * once. After [close], [port] returns 0 and subsequent
     * [reservePort] calls start a fresh reservation.
     */
    fun close()
}

/**
 * Default [SecureTransport] backed by a single local [ServerSocket].
 * Binds to the loopback interface via ephemeral port 0 so the operating
 * system picks a free port for us and nothing else on the device can
 * snipe it during the window between reserving and the wallet dialing
 * in.
 */
class LocalSocketTransport : SecureTransport {

    @Volatile private var socket: ServerSocket? = null
    @Volatile private var _port: Int = 0

    override val port: Int get() = _port

    override fun reservePort(): Int {
        socket?.let { return _port }
        val ss = ServerSocket(0).apply { reuseAddress = true }
        socket = ss
        _port = ss.localPort
        return _port
    }

    override fun close() {
        val s = socket
        socket = null
        _port = 0
        if (s != null) {
            try { s.close() } catch (_: Throwable) {}
        }
    }
}
