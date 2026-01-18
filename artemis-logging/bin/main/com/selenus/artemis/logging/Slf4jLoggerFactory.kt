package com.selenus.artemis.logging

/**
 * SLF4J bridge implemented via reflection so SLF4J is optional.
 */
class Slf4jLoggerFactory : Log.LoggerFactory {

  override fun get(tag: String): Logger {
    val clazz = Class.forName("org.slf4j.LoggerFactory")
    val getLogger = clazz.getMethod("getLogger", String::class.java)
    val logger = getLogger.invoke(null, tag)

    val isDebugEnabled = logger.javaClass.getMethod("isDebugEnabled")
    val isInfoEnabled = logger.javaClass.getMethod("isInfoEnabled")
    val isWarnEnabled = logger.javaClass.getMethod("isWarnEnabled")

    val debug = logger.javaClass.getMethod("debug", String::class.java)
    val info = logger.javaClass.getMethod("info", String::class.java)
    val warn = logger.javaClass.getMethod("warn", String::class.java)
    val error1 = logger.javaClass.getMethod("error", String::class.java)
    val error2 = logger.javaClass.getMethod("error", String::class.java, Throwable::class.java)

    return object : Logger {
      override fun debug(msg: String) {
        val ok = (isDebugEnabled.invoke(logger) as? Boolean) ?: false
        if (ok) debug.invoke(logger, msg)
      }

      override fun info(msg: String) {
        val ok = (isInfoEnabled.invoke(logger) as? Boolean) ?: false
        if (ok) info.invoke(logger, msg)
      }

      override fun warn(msg: String) {
        val ok = (isWarnEnabled.invoke(logger) as? Boolean) ?: false
        if (ok) warn.invoke(logger, msg)
      }

      override fun error(msg: String, t: Throwable?) {
        if (t != null) error2.invoke(logger, msg, t) else error1.invoke(logger, msg)
      }
    }
  }
}
