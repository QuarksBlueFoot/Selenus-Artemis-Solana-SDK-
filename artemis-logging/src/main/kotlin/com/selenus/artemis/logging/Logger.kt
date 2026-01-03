package com.selenus.artemis.logging

/**
 * Small logging facade used across Artemis.
 *
 * If SLF4J is on the classpath, Artemis will route logs to it.
 * Otherwise it falls back to a lightweight stdout logger.
 */
interface Logger {
  fun debug(msg: String)
  fun info(msg: String)
  fun warn(msg: String)
  fun error(msg: String, t: Throwable? = null)
}
