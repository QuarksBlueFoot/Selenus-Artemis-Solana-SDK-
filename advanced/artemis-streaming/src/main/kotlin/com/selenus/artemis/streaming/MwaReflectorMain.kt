package com.selenus.artemis.streaming

import java.net.InetAddress
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    val options = args.toList().chunked(2).mapNotNull { chunk ->
        if (chunk.size == 2 && chunk[0].startsWith("--")) chunk[0].removePrefix("--") to chunk[1] else null
    }.toMap()
    val host = options["host"] ?: System.getenv("ARTEMIS_MWA_REFLECTOR_HOST") ?: "0.0.0.0"
    val port = (options["port"] ?: System.getenv("ARTEMIS_MWA_REFLECTOR_PORT") ?: "8080").toInt()
    val maxFrameBytes = (options["max-frame-bytes"]
        ?: System.getenv("ARTEMIS_MWA_REFLECTOR_MAX_FRAME_BYTES")
        ?: "4096").toInt()

    val server = MwaReflectorServer(
        MwaReflectorServer.Config(
            bindAddress = InetAddress.getByName(host),
            port = port,
            maxFrameSizeBytes = maxFrameBytes
        )
    )
    val actualPort = server.start()
    println("Artemis MWA reflector listening on $host:$actualPort")
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })
    CountDownLatch(1).await()
}