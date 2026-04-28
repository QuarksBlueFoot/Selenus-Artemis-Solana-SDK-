package com.selenus.artemis.wallet.mwa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behaviour tests for [MwaEnvironmentDetector].
 *
 * Limitation note: the production code accesses [android.webkit.WebView] when
 * fingerprinting in-app and wallet browsers. WebView cannot be instantiated
 * under the JVM unit-test classpath, so the WebView paths fall through to the
 * `Exception -> false` branches in the detector. We assert the deterministic
 * outcomes of those branches (native app, PWA, installed wallet listing,
 * mobile browser detection, recommended strategy) without trying to spin up
 * a real WebView. Full WebView coverage requires Robolectric or instrumented
 * tests on a device.
 */
class MwaEnvironmentDetectorTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            mockk<Uri>(relaxed = true).also {
                every { it.toString() } returns firstArg()
            }
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun makeContext(
        packageName: String = "com.example.app",
        installedWalletPackages: Set<String> = emptySet(),
        appFlags: Int = 0,
        asActivityWithIntent: Intent? = null
    ): Context {
        val pm = mockk<PackageManager>()
        every { pm.getPackageInfo(any<String>(), any<Int>()) } answers {
            val pkg = firstArg<String>()
            if (pkg in installedWalletPackages) {
                PackageInfo().apply { this.packageName = pkg }
            } else {
                throw PackageManager.NameNotFoundException("not installed: $pkg")
            }
        }
        every { pm.getApplicationInfo(packageName, 0) } returns ApplicationInfo().apply {
            this.flags = appFlags
            this.packageName = packageName
        }
        return if (asActivityWithIntent != null) {
            mockk<Activity>(relaxed = true).also { ctx ->
                every { ctx.applicationContext } returns ctx
                every { ctx.packageManager } returns pm
                every { ctx.packageName } returns packageName
                every { ctx.intent } returns asActivityWithIntent
            }
        } else {
            mockk<Context>(relaxed = true).also { ctx ->
                every { ctx.applicationContext } returns ctx
                every { ctx.packageManager } returns pm
                every { ctx.packageName } returns packageName
            }
        }
    }

    @Test
    fun `detect returns NATIVE_APP for plain context with no special markers`() {
        val ctx = makeContext()
        val detector = MwaEnvironmentDetector(ctx)
        assertEquals(MwaEnvironment.NATIVE_APP, detector.detect())
    }

    @Test
    fun `recommendStrategy is StandardMwa for native app`() {
        val ctx = makeContext()
        val detector = MwaEnvironmentDetector(ctx)
        val strat = detector.recommendStrategy()
        assertEquals(MwaConnectionStrategy.StandardMwa, strat)
    }

    @Test
    fun `getInstalledWallets enumerates only the wallets the package manager finds`() {
        val ctx = makeContext(
            installedWalletPackages = setOf("app.phantom", "com.solflare.mobile")
        )
        val detector = MwaEnvironmentDetector(ctx)
        val wallets = detector.getInstalledWallets()

        assertEquals(2, wallets.size)
        val packages = wallets.map { it.packageName }.toSet()
        assertTrue("phantom present", "app.phantom" in packages)
        assertTrue("solflare present", "com.solflare.mobile" in packages)
        assertTrue("Phantom display name set", wallets.any { it.displayName == "Phantom" })
        assertTrue("all marked installed", wallets.all { it.isInstalled })
    }

    @Test
    fun `getInstalledWallets returns empty list when none are installed`() {
        val ctx = makeContext()
        val detector = MwaEnvironmentDetector(ctx)
        assertTrue(detector.getInstalledWallets().isEmpty())
        assertFalse(detector.hasCompatibleWallet())
    }

    @Test
    fun `hasCompatibleWallet flips true once a wallet becomes installed`() {
        val ctxBefore = makeContext()
        val ctxAfter = makeContext(installedWalletPackages = setOf("com.backpack.android"))
        assertFalse(MwaEnvironmentDetector(ctxBefore).hasCompatibleWallet())
        assertTrue(MwaEnvironmentDetector(ctxAfter).hasCompatibleWallet())
    }

    @Test
    fun `detect identifies PWA from package name marker`() {
        val ctx = makeContext(
            packageName = "com.example.pwa.web",
            appFlags = 0 // not a system app
        )
        val detector = MwaEnvironmentDetector(ctx)
        assertEquals(MwaEnvironment.PWA, detector.detect())
        // PWA still gets the StandardMwa strategy (session persistence is
        // handled elsewhere).
        assertEquals(MwaConnectionStrategy.StandardMwa, detector.recommendStrategy())
    }

    @Test
    fun `detect ignores PWA marker when app is a system package`() {
        val ctx = makeContext(
            packageName = "com.system.pwa.builtin",
            appFlags = ApplicationInfo.FLAG_SYSTEM
        )
        val detector = MwaEnvironmentDetector(ctx)
        // System apps don't count as PWAs in the detector's heuristic.
        assertEquals(MwaEnvironment.NATIVE_APP, detector.detect())
    }

    @Test
    fun `detect identifies MOBILE_BROWSER when activity intent is ACTION_VIEW http`() {
        val intent = mockk<Intent>(relaxed = true)
        val data = mockk<Uri>(relaxed = true)
        every { data.scheme } returns "https"
        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.data } returns data

        val ctx = makeContext(asActivityWithIntent = intent)
        val detector = MwaEnvironmentDetector(ctx)

        assertEquals(MwaEnvironment.MOBILE_BROWSER, detector.detect())
        assertEquals(MwaConnectionStrategy.StandardMwa, detector.recommendStrategy())
    }

    @Test
    fun `getWalletStoreLink builds a play store URL`() {
        val ctx = makeContext()
        val detector = MwaEnvironmentDetector(ctx)
        val link = detector.getWalletStoreLink("phantom")
        // Uri.parse stub captures the input string in toString().
        assertEquals(
            "https://play.google.com/store/apps/details?id=app.phantom",
            link.toString()
        )
    }

    @Test
    fun `getWalletStoreLink defaults to phantom`() {
        val ctx = makeContext()
        val detector = MwaEnvironmentDetector(ctx)
        val link = detector.getWalletStoreLink()
        assertEquals(
            "https://play.google.com/store/apps/details?id=app.phantom",
            link.toString()
        )
    }
}
