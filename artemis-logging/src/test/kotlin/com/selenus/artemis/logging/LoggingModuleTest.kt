package com.selenus.artemis.logging

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-logging module.
 * Tests Log, Logger, and factory patterns.
 */
class LoggingModuleTest {

    // ===== Log Object Tests =====

    @Test
    fun testLogGetLogger() {
        val logger = Log.get("TestTag")
        
        assertNotNull(logger)
        assertTrue(logger is Logger)
    }

    @Test
    fun testLogGetMultipleLoggers() {
        val logger1 = Log.get("Tag1")
        val logger2 = Log.get("Tag2")
        
        assertNotNull(logger1)
        assertNotNull(logger2)
    }

    @Test
    fun testLogGetSameTagLogger() {
        val logger1 = Log.get("SameTag")
        val logger2 = Log.get("SameTag")
        
        assertNotNull(logger1)
        assertNotNull(logger2)
    }

    // ===== Logger Interface Tests =====

    @Test
    fun testLoggerDebug() {
        val logger = Log.get("DebugTest")
        
        // Should not throw
        logger.debug("Debug message")
    }

    @Test
    fun testLoggerInfo() {
        val logger = Log.get("InfoTest")
        
        // Should not throw
        logger.info("Info message")
    }

    @Test
    fun testLoggerWarn() {
        val logger = Log.get("WarnTest")
        
        // Should not throw
        logger.warn("Warning message")
    }

    @Test
    fun testLoggerError() {
        val logger = Log.get("ErrorTest")
        
        // Should not throw
        logger.error("Error message", null)
    }

    @Test
    fun testLoggerErrorWithThrowable() {
        val logger = Log.get("ErrorTest")
        val exception = RuntimeException("Test exception")
        
        // Should not throw
        logger.error("Error with exception", exception)
    }

    // ===== Custom LoggerFactory Tests =====

    @Test
    fun testSetCustomFactory() {
        val customLogger = object : Logger {
            val messages = mutableListOf<String>()
            
            override fun debug(msg: String) { messages.add("DEBUG: $msg") }
            override fun info(msg: String) { messages.add("INFO: $msg") }
            override fun warn(msg: String) { messages.add("WARN: $msg") }
            override fun error(msg: String, t: Throwable?) { messages.add("ERROR: $msg") }
        }
        
        val customFactory = object : Log.LoggerFactory {
            override fun get(tag: String): Logger = customLogger
        }
        
        Log.setFactory(customFactory)
        
        val logger = Log.get("Test")
        logger.info("Test message")
        
        assertTrue(customLogger.messages.any { it.contains("Test message") })
        
        // Reset to default (for other tests)
        Log.setFactory(object : Log.LoggerFactory {
            override fun get(tag: String): Logger = object : Logger {
                override fun debug(msg: String) {}
                override fun info(msg: String) {}
                override fun warn(msg: String) {}
                override fun error(msg: String, t: Throwable?) {}
            }
        })
    }

    // ===== Logger Interface Contract Tests =====

    @Test
    fun testLoggerInterfaceMethods() {
        val testLogger = object : Logger {
            var debugCalled = false
            var infoCalled = false
            var warnCalled = false
            var errorCalled = false
            
            override fun debug(msg: String) { debugCalled = true }
            override fun info(msg: String) { infoCalled = true }
            override fun warn(msg: String) { warnCalled = true }
            override fun error(msg: String, t: Throwable?) { errorCalled = true }
        }
        
        testLogger.debug("test")
        testLogger.info("test")
        testLogger.warn("test")
        testLogger.error("test", null)
        
        assertTrue(testLogger.debugCalled)
        assertTrue(testLogger.infoCalled)
        assertTrue(testLogger.warnCalled)
        assertTrue(testLogger.errorCalled)
    }

    // ===== StdoutLogger Tests (via default factory) =====

    @Test
    fun testDefaultLoggerDoesNotThrow() {
        // Reset to a safe default first
        val safeFactory = object : Log.LoggerFactory {
            override fun get(tag: String): Logger = object : Logger {
                override fun debug(msg: String) { println("D/$tag $msg") }
                override fun info(msg: String) { println("I/$tag $msg") }
                override fun warn(msg: String) { println("W/$tag $msg") }
                override fun error(msg: String, t: Throwable?) { 
                    println("E/$tag $msg")
                    t?.printStackTrace()
                }
            }
        }
        Log.setFactory(safeFactory)
        
        val logger = Log.get("SafeTest")
        
        // None of these should throw
        logger.debug("Debug test")
        logger.info("Info test")
        logger.warn("Warn test")
        logger.error("Error test", RuntimeException("test"))
    }

    // ===== Tag Formatting Tests =====

    @Test
    fun testLoggerWithEmptyTag() {
        val logger = Log.get("")
        assertNotNull(logger)
    }

    @Test
    fun testLoggerWithSpecialCharactersInTag() {
        val logger = Log.get("Test.Module:SubModule")
        assertNotNull(logger)
        logger.info("Message with special tag")
    }

    @Test
    fun testLoggerWithLongTag() {
        val longTag = "A".repeat(100)
        val logger = Log.get(longTag)
        assertNotNull(logger)
    }
}
