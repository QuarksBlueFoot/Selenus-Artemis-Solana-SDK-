/*
 * Artemis innovation on top of sol4k: pluggable logger.
 *
 * Upstream sol4k writes nothing to any logger, which is fine for a lib
 * expected to run in backend JVM processes but terrible on Android where
 * apps need to see why a Connection call timed out. Artemis lets callers
 * install their own logger with zero dependency churn.
 *
 * Mirrors the shape Artemis already uses in metaplex-android-compat
 * (`MetaplexLogging.install(...)`) so apps that wire logging across several
 * compat modules only have to learn one idiom.
 */
package org.sol4k

interface Sol4kLogger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String, throwable: Throwable? = null)
    fun error(message: String, throwable: Throwable? = null)
}

/** No-op default: matches upstream silence. */
object NoopSol4kLogger : Sol4kLogger {
    override fun debug(message: String) = Unit
    override fun info(message: String) = Unit
    override fun warn(message: String, throwable: Throwable?) = Unit
    override fun error(message: String, throwable: Throwable?) = Unit
}

/**
 * Global [Sol4kLogger] handle. Apps call [install] at startup; everything
 * inside the compat module pulls from [current] at emit-time so hot-swap
 * works for tests.
 */
object Sol4kLogging {
    @Volatile
    private var instance: Sol4kLogger = NoopSol4kLogger

    @JvmStatic
    fun install(logger: Sol4kLogger) {
        instance = logger
    }

    @JvmStatic
    fun current(): Sol4kLogger = instance
}
