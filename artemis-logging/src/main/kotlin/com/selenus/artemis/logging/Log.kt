package com.selenus.artemis.logging

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Log {
  @Volatile private var factory: LoggerFactory = DefaultLoggerFactory()

  fun setFactory(factory: LoggerFactory) {
    this.factory = factory
  }

  fun get(tag: String): Logger = factory.get(tag)

  interface LoggerFactory {
    fun get(tag: String): Logger
  }

  private class DefaultLoggerFactory : LoggerFactory {
    private val slf4j = tryCreateSlf4jFactory()
    override fun get(tag: String): Logger = slf4j?.get(tag) ?: StdoutLogger(tag)
  }

  private fun tryCreateSlf4jFactory(): LoggerFactory? {
    return try {
      Class.forName("org.slf4j.LoggerFactory")
      Slf4jLoggerFactory()
    } catch (_: Throwable) {
      null
    }
  }

  private class StdoutLogger(private val tag: String) : Logger {
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private fun ts() = fmt.format(Date())

    override fun debug(msg: String) { println("${ts()} D/$tag $msg") }
    override fun info(msg: String) { println("${ts()} I/$tag $msg") }
    override fun warn(msg: String) { println("${ts()} W/$tag $msg") }
    override fun error(msg: String, t: Throwable?) {
      println("${ts()} E/$tag $msg")
      if (t != null) {
        println(t.stackTraceToString())
      }
    }
  }
}
