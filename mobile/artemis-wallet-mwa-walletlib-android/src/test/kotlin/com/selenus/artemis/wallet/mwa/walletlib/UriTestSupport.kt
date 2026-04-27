package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic

/**
 * Shared test support: rewires `android.net.Uri.parse` so unit tests
 * (which run against the JVM stub jar where `Uri.parse` returns null
 * by default) get a working URI parser. The replacement uses
 * [java.net.URI] under the hood and adapts the result to the small
 * subset of `Uri` methods the walletlib actually calls.
 *
 * Tests call [installUriStub] in `@Before`. mockk's static replacement
 * is automatically reset between test classes by `unmockkAll`, but
 * each test class does so in `@After` for clarity.
 */
object UriTestSupport {

    fun installUriStub() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val raw = firstArg<String>()
            stubFromString(raw)
        }
    }

    fun stubFromString(raw: String): Uri {
        val javaUri = try {
            java.net.URI(raw)
        } catch (e: java.net.URISyntaxException) {
            // Fallback: caller passed something Uri.parse() would also
            // choke on. Return a stub whose every getter answers null
            // so the parser surfaces a typed error.
            return mockk<Uri>(relaxed = true).also {
                every { it.scheme } returns null
                every { it.path } returns null
                every { it.getQueryParameter(any()) } returns null
                every { it.toString() } returns raw
            }
        }
        // Hand-roll path + query split because java.net.URI doesn't
        // surface `solana-wallet:/v1/associate/local?...` cleanly:
        // when the scheme is opaque it stuffs the rest into
        // schemeSpecificPart.
        val ssp = javaUri.rawSchemeSpecificPart
        val (pathPart, queryPart) = if (ssp.contains('?')) {
            val q = ssp.indexOf('?')
            ssp.substring(0, q) to ssp.substring(q + 1)
        } else ssp to ""
        val pathDecoded = pathPart
            .let { if (it.startsWith("//")) it.removePrefix("//") else it }
            .let { if (it.startsWith("/")) it else "/$it" }
        val queryParams: Map<String, String> = if (queryPart.isEmpty()) emptyMap() else {
            queryPart.split('&').filter { it.isNotEmpty() }.associate { kv ->
                val eq = kv.indexOf('=')
                if (eq == -1) kv to ""
                else java.net.URLDecoder.decode(kv.substring(0, eq), "UTF-8") to
                    java.net.URLDecoder.decode(kv.substring(eq + 1), "UTF-8")
            }
        }
        return mockk<Uri>(relaxed = true).also { uri ->
            every { uri.scheme } returns javaUri.scheme
            every { uri.path } returns pathDecoded
            every { uri.toString() } returns raw
            every { uri.getQueryParameter(any()) } answers {
                queryParams[firstArg<String>()]
            }
        }
    }
}
