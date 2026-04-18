/*
 * Drop-in source compatibility with the upstream ktx
 * `com.solana.mobilewalletadapter.clientlib.AssociationScenarioProvider`.
 *
 * Upstream uses this as the single factory entry point that `MobileWalletAdapter`
 * delegates to when opening a local association scenario. Apps that supply
 * their own provider (e.g. to inject a remote-association scenario in tests)
 * keep compiling because the shim matches upstream's single-method surface.
 */
package com.solana.mobilewalletadapter.clientlib

import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario

/**
 * Factory that mints a fresh [LocalAssociationScenario] per connect attempt.
 *
 * Tests typically subclass and return a mock scenario; production code relies
 * on the default no-arg instance.
 */
open class AssociationScenarioProvider {
    open fun provideAssociationScenario(timeoutMs: Int): LocalAssociationScenario =
        LocalAssociationScenario(timeoutMs)
}
