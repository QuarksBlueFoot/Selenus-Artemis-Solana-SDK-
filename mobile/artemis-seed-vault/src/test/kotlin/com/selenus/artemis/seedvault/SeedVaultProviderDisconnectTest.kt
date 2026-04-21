package com.selenus.artemis.seedvault

import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Behavior conformance for the disconnect / binder-death path.
 *
 * [SeedVaultManager] tracks every in-flight IPC and fails them with
 * [SeedVaultException.ServiceUnavailable] when the binder dies or the
 * system service disconnects. Driving a real binder from a JVM unit test
 * is not practical — those paths are exercised by instrumented tests on
 * a device — but we can still prove the error vocabulary end-to-end by:
 *
 *   - pushing a ServiceUnavailable out of a fake [SeedVaultContractClient]
 *   - running it through both providers (account + signing)
 *   - asserting the typed error surfaces to the caller, never swallowed.
 *
 * Together with the in-Manager tracking logic (see
 * [SeedVaultManager.failAllPending]) this locks the "no hanging call"
 * guarantee the audit requires.
 */
class SeedVaultProviderDisconnectTest {

    /**
     * Fake contract whose every verb throws [SeedVaultException.ServiceUnavailable].
     * Mirrors what [SeedVaultManager.failAllPending] does when the binder
     * dies in the middle of a live call.
     */
    private class DisconnectedContract(
        private val reason: String = "binder died mid-call"
    ) : SeedVaultContractClient {
        override suspend fun authorize(params: Bundle) = fail()
        override suspend fun createSeed(params: Bundle) = fail()
        override suspend fun importSeed(params: Bundle) = fail()
        override suspend fun updateSeed(params: Bundle) = fail()
        override suspend fun getAccounts(params: Bundle) = fail()
        override suspend fun resolveDerivationPath(params: Bundle) = fail()
        override suspend fun signTransactions(params: Bundle) = fail()
        override suspend fun signMessages(params: Bundle) = fail()
        override suspend fun deauthorize(params: Bundle) = fail()

        private fun fail(): Nothing = throw SeedVaultException.ServiceUnavailable(reason)
    }

    @Test
    fun `getAccounts surfaces ServiceUnavailable when contract disconnects`() = runBlocking {
        val contract = DisconnectedContract("binder died during getAccounts")
        val provider = SeedVaultAccountProviderImpl(contract)
        val ex = assertThrows<SeedVaultException.ServiceUnavailable> {
            runBlocking { provider.getAccounts("12345") }
        }
        assertTrue(ex.message!!.contains("binder died"))
    }

    @Test
    fun `signTransactions surfaces ServiceUnavailable when contract disconnects`() = runBlocking {
        val contract = DisconnectedContract("binder died during signTransactions")
        val provider = SeedVaultSigningProviderImpl(contract)
        val ex = assertThrows<SeedVaultException.ServiceUnavailable> {
            runBlocking { provider.signTransactions("12345", listOf(ByteArray(32))) }
        }
        assertTrue(ex.message!!.contains("binder died"))
    }

    @Test
    fun `signMessages surfaces ServiceUnavailable when contract disconnects`() = runBlocking {
        val contract = DisconnectedContract("binder died during signMessages")
        val provider = SeedVaultSigningProviderImpl(contract)
        val ex = assertThrows<SeedVaultException.ServiceUnavailable> {
            runBlocking { provider.signMessages("12345", listOf(ByteArray(32))) }
        }
        assertTrue(ex.message!!.contains("binder died"))
    }

    /**
     * The ServiceUnavailable subtype is the contract-level signal; it must
     * be distinguishable from an ordinary InternalError so callers can
     * react differently (retry binder reconnect vs. fail the operation).
     */
    @Test
    fun `ServiceUnavailable is a distinct SeedVaultException subtype`() {
        val ex: SeedVaultException = SeedVaultException.ServiceUnavailable("x")
        assertTrue(ex is SeedVaultException.ServiceUnavailable)
        // Must not accidentally extend the other error subtypes.
        assertEquals(false, ex is SeedVaultException.InternalError)
        assertEquals(false, ex is SeedVaultException.Unknown)
    }
}
