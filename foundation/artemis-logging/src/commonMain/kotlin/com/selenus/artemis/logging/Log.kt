package com.selenus.artemis.logging

object Log {
    @Volatile private var factory: LoggerFactory = createDefaultLoggerFactory()

    fun setFactory(factory: LoggerFactory) {
        this.factory = factory
    }

    fun get(tag: String): Logger = factory.get(tag)

    interface LoggerFactory {
        fun get(tag: String): Logger
    }
}

internal expect fun createDefaultLoggerFactory(): Log.LoggerFactory
