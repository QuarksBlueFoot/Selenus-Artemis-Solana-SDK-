package com.selenus.artemis.wallet.mwa.walletlib

import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AuthorizeRequestCompletionTest {

    @Before
    fun setUp() {
        UriTestSupport.installUriStub()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun newRequest(): AuthorizeRequest = AuthorizeRequest(
        identityName = "Test",
        identityUri = null,
        iconRelativeUri = null,
        chain = "solana:mainnet",
        features = emptyList(),
        addresses = null,
        signInPayload = null
    )

    private fun newAccount(): AuthorizedAccount = AuthorizedAccount(
        publicKey = ByteArray(32) { 1 }
    )

    @Test
    fun `completeWithAuthorize emits a Result variant`() = runBlocking {
        val req = newRequest()
        assertFalse(req.isComplete)
        req.completeWithAuthorize(accounts = listOf(newAccount()))
        assertTrue(req.isComplete)
        val outcome = withTimeout(1_000) { req.awaitCompletion() }
        assertTrue(outcome is MwaCompletion.Result)
    }

    @Test
    fun `completeWithDecline emits an Error with AUTHORIZATION_FAILED`() = runBlocking {
        val req = newRequest()
        req.completeWithDecline()
        val outcome = req.awaitCompletion()
        check(outcome is MwaCompletion.Error)
        assertEquals(MwaErrorCodes.AUTHORIZATION_FAILED, outcome.code)
    }

    @Test
    fun `completeWithChainNotSupported emits CHAIN_NOT_SUPPORTED`() = runBlocking {
        val req = newRequest()
        req.completeWithChainNotSupported()
        val outcome = req.awaitCompletion()
        check(outcome is MwaCompletion.Error)
        assertEquals(MwaErrorCodes.CHAIN_NOT_SUPPORTED, outcome.code)
    }

    @Test
    fun `double completion throws`() = runBlocking {
        val req = newRequest()
        req.completeWithAuthorize(accounts = listOf(newAccount()))
        try {
            req.completeWithDecline()
            fail("expected IllegalStateException on second completion")
        } catch (_: IllegalStateException) { /* expected */ }
        // The first completion's result is unchanged.
        val outcome = req.awaitCompletion()
        assertTrue(outcome is MwaCompletion.Result)
    }

    @Test
    fun `decline then authorize throws`() = runBlocking {
        val req = newRequest()
        req.completeWithDecline()
        try {
            req.completeWithAuthorize(accounts = listOf(newAccount()))
            fail("expected IllegalStateException on second completion")
        } catch (_: IllegalStateException) { /* expected */ }
    }

    @Test
    fun `requires at least one account on authorize`() = runBlocking<Unit> {
        val req = newRequest()
        try {
            req.completeWithAuthorize(accounts = emptyList())
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* expected */ }
        // Constructor-side validation does NOT consume the completion
        // slot; we must still be able to complete normally afterwards.
        assertFalse(req.isComplete)
        req.completeWithDecline()
        val outcome = req.awaitCompletion()
        check(outcome is MwaCompletion.Error)
    }

    @Test
    fun `signInResult must match an authorized account`() = runBlocking<Unit> {
        val authReq = AuthorizeRequest(
            identityName = "Test",
            identityUri = null,
            iconRelativeUri = null,
            chain = "solana:mainnet",
            features = emptyList(),
            addresses = null,
            signInPayload = SignInPayload(
                domain = "test.dapp",
                statement = "Sign in"
            )
        )
        val account = AuthorizedAccount(publicKey = ByteArray(32) { 1 })
        val mismatchedSignIn = SignInResult(
            publicKey = ByteArray(32) { 9 },
            signedMessage = "msg".toByteArray(),
            signature = ByteArray(64) { 7 }
        )
        try {
            authReq.completeWithAuthorize(
                accounts = listOf(account),
                signInResult = mismatchedSignIn
            )
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `cancel before completion completes exceptionally`() = runBlocking<Unit> {
        val req = newRequest()
        req.cancel("scenario closed")
        try {
            req.awaitCompletion()
            fail("expected CancellationException")
        } catch (_: java.util.concurrent.CancellationException) { /* expected */ }
    }
}
