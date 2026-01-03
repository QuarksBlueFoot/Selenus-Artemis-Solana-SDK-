# Logging

Artemis includes a small logging facade in `artemis-logging`.

## SLF4J support

If your app includes `org.slf4j:slf4j-api` and an implementation, Artemis logs will route through SLF4J.

If SLF4J is not present, Artemis uses a lightweight stdout logger.

## Usage

```kotlin
import com.selenus.artemis.logging.Log

val log = Log.get("GameClient")
log.info("connected")
```

You can also set a custom factory:

```kotlin
Log.setFactory(object : Log.LoggerFactory {
  override fun get(tag: String): Logger = MyLogger(tag)
})
```
