package com.selenus.artemis.wallet.mwa.walletlib

import android.content.Context
import android.os.PowerManager

/**
 * Provider that tells the scenario whether the device is currently
 * power-constrained, so the "no connection within
 * [MobileWalletAdapterConfig.noConnectionWarningTimeoutMs]" hint
 * surfaces only when battery optimisation is the likely culprit.
 *
 * Mirrors upstream walletlib's `PowerConfigProvider` /
 * `DevicePowerConfigProvider` split. Wallets that bind a real
 * `Context` pass a [DevicePowerConfigProvider]; tests and the in-memory
 * default use [AlwaysHighPower] which suppresses the warning.
 */
fun interface PowerConfigProvider {
    /** True when the device is in power-save / low-power mode. */
    fun isLowPowerMode(): Boolean

    companion object {
        /** Default for tests + the in-memory scenario. Suppresses the warning. */
        val AlwaysHighPower: PowerConfigProvider = PowerConfigProvider { false }

        /** Forces the warning on every wait. Useful for emulator demos. */
        val AlwaysLowPower: PowerConfigProvider = PowerConfigProvider { true }
    }
}

/**
 * [PowerConfigProvider] that reads the system [PowerManager]. The
 * provided [context] is used only to fetch the system service; we
 * never hold any UI references.
 */
class DevicePowerConfigProvider(context: Context) : PowerConfigProvider {
    private val powerManager =
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    override fun isLowPowerMode(): Boolean = powerManager.isPowerSaveMode
}
