/*
 * Pluggable logging abstraction for the metaplex-android shim.
 *
 * Upstream issue `metaplex-foundation/metaplex-android#81` asks for a
 * configurable logger that never shipped. Users either get no logging at all
 * or a hardcoded `Log.d` write that they cannot mute or redirect.
 *
 * Artemis exposes a small sink interface so apps can route Metaplex-shim
 * messages into Timber, a remote logger, or a test recorder. The default is a
 * no-op so the shim is silent out of the box.
 */
package com.metaplex.lib

/**
 * Minimal log sink. Implement this to capture every message the shim
 * emits on behalf of a Metaplex call site.
 *
 * Methods take a [tag] so apps can filter by module ("nft", "tokens", "das")
 * without needing a separate logger instance per subsystem.
 */
interface MetaplexLogger {
    fun d(tag: String, message: String) {}
    fun i(tag: String, message: String) {}
    fun w(tag: String, message: String, error: Throwable? = null) {}
    fun e(tag: String, message: String, error: Throwable? = null) {}
}

/** No-op sink used until the app installs a real logger. */
object NoopMetaplexLogger : MetaplexLogger

/**
 * Global logger holder. Apps install a sink once at startup:
 *
 * ```kotlin
 * MetaplexLogging.install(object : MetaplexLogger {
 *     override fun i(tag: String, message: String) = Timber.tag("metaplex").i(message)
 *     override fun w(tag: String, message: String, error: Throwable?) =
 *         Timber.tag("metaplex").w(error, message)
 * })
 * ```
 *
 * Shim internals call `MetaplexLogging.logger.i("nft", "fetched $mint")` — a
 * single lookup, no allocation when the sink is the default no-op.
 */
object MetaplexLogging {
    @Volatile
    private var current: MetaplexLogger = NoopMetaplexLogger

    /** The active logger. Reads are lock-free and safe from any thread. */
    val logger: MetaplexLogger get() = current

    /** Install a sink. Pass [NoopMetaplexLogger] to reset. */
    fun install(logger: MetaplexLogger) {
        current = logger
    }
}
