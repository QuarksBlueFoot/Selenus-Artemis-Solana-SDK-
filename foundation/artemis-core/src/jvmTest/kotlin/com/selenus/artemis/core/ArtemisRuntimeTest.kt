/*
 * Tests for the ArtemisRuntime central orchestration surface.
 */
package com.selenus.artemis.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArtemisRuntimeTest {

    @Test
    fun `initial state is Idle`() {
        val runtime = ArtemisRuntime()
        assertIs<ArtemisRuntime.State.Idle>(runtime.state.value)
    }

    @Test
    fun `start transitions to Running`(): Unit = runBlocking {
        val runtime = ArtemisRuntime()
        runtime.start()
        assertIs<ArtemisRuntime.State.Running>(runtime.state.value)
        runtime.stop()
    }

    @Test
    fun `stop transitions to Stopped with reason`() = runBlocking {
        val runtime = ArtemisRuntime()
        runtime.start()
        runtime.stop(reason = "test")
        val state = runtime.state.value as ArtemisRuntime.State.Stopped
        assertEquals("test", state.reason)
    }

    @Test
    fun `emit publishes a Custom event onto the shared bus`() = runBlocking {
        val runtime = ArtemisRuntime()
        val event = withTimeout(3_000) {
            runtime.events
                .onSubscription {
                    // Emit only once the collector is subscribed. SharedFlow has no
                    // replay, so emitting before subscription would drop the event.
                    runtime.emit(tag = "feature-flag", payload = "dark-mode-on")
                }
                .filterIsInstance<ArtemisEvent.Custom>()
                .first()
        }
        assertEquals("feature-flag", event.tag)
        assertEquals("dark-mode-on", event.payload)
    }

    @Test
    fun `subsystems start and stop in the expected order`(): Unit = runBlocking {
        val order = mutableListOf<String>()

        class Recorder(private val name: String) : ArtemisRuntime.Subsystem {
            override suspend fun start(runtime: ArtemisRuntime) {
                order += "$name:start"
            }

            override fun stop() {
                order += "$name:stop"
            }
        }

        val runtime = ArtemisRuntime()
        runtime.attach(Recorder("a"))
        runtime.attach(Recorder("b"))
        runtime.attach(Recorder("c"))

        runtime.start()
        // Subsystems start in a coroutine, so give the dispatcher a tick.
        delay(100)
        runtime.stop()

        assertEquals(
            listOf("a:start", "b:start", "c:start", "c:stop", "b:stop", "a:stop"),
            order
        )
    }

    @Test
    fun `start is idempotent`() {
        val runtime = ArtemisRuntime()
        runtime.start()
        val firstState = runtime.state.value
        runtime.start()
        assertTrue(runtime.state.value === firstState)
        runtime.stop()
    }

    @Test
    fun `stop is idempotent when already stopped`() {
        val runtime = ArtemisRuntime()
        // Never started; calling stop should not throw or change state from Idle.
        runtime.stop()
        assertIs<ArtemisRuntime.State.Idle>(runtime.state.value)
    }
}
