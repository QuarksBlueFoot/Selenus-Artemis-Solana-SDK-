/*
 * Auth-token workaround for the upstream Seed Vault SDK.
 *
 * Upstream issue `solana-mobile/seed-vault-sdk#548` (open since 2023) reports
 * that `Wallet.importSeed` and `Wallet.createSeed` can return an auth token
 * that fails the next time it is used, producing an obscure IPC error deep in
 * the consumer app. The upstream SDK has not fixed this.
 *
 * Artemis ships a small guard that validates an auth token right after import
 * or creation and, when the token is invalid, surfaces a typed error instead
 * of letting the caller learn about it two calls later. Apps migrating from
 * the upstream SDK wrap their `onAuthorizeSeedResult` / `onCreateSeedResult`
 * / `onImportSeedResult` handlers in [AuthTokenGuard.validateOrThrow] and
 * the problem is eliminated at the edge of their code.
 */
package com.solanamobile.seedvault

import android.content.Context

/**
 * Typed error surfaced when a fresh auth token is not usable.
 *
 * The upstream SDK does not raise this; it lets callers discover the bad
 * token on a later content-provider query. Raising it at the source gives
 * apps a clean failure path to react to.
 */
class AuthTokenInvalidException(message: String) : RuntimeException(message)

/** Helpers that close the upstream `importSeed` / `createSeed` auth-token gap. */
object AuthTokenGuard {

    /**
     * Validate [authToken] against the Seed Vault authorised-seeds content
     * provider. Returns the token unchanged when it resolves, or throws
     * [AuthTokenInvalidException] when the provider cannot find it.
     *
     * Callers wrap the result of `Wallet.onAuthorizeSeedResult`, `onCreateSeedResult`,
     * or `onImportSeedResult`:
     *
     * ```kotlin
     * val token = AuthTokenGuard.validateOrThrow(
     *     context,
     *     Wallet.onCreateSeedResult(resultCode, data)
     * )
     * ```
     *
     * When the token resolves, the caller proceeds as normal. When it does
     * not, the caller catches the exception and re-runs the authorize flow.
     */
    @JvmStatic
    fun validateOrThrow(context: Context, authToken: Long): Long {
        val resolver = context.contentResolver
        val cursor = try {
            resolver.query(
                WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
                arrayOf(WalletContractV1.AUTHORIZED_SEEDS_AUTH_TOKEN),
                "${WalletContractV1.AUTHORIZED_SEEDS_AUTH_TOKEN} = ?",
                arrayOf(authToken.toString()),
                null
            )
        } catch (e: Exception) {
            throw AuthTokenInvalidException("Seed Vault provider unreachable: ${e.message}")
        }
        cursor.use { c ->
            if (c == null || !c.moveToFirst()) {
                throw AuthTokenInvalidException(
                    "Auth token $authToken not found in Seed Vault. Re-run authorizeSeed()."
                )
            }
        }
        return authToken
    }

    /**
     * Same as [validateOrThrow] but returns `null` instead of throwing, for
     * callers that prefer a nullable-return flow.
     */
    @JvmStatic
    fun validateOrNull(context: Context, authToken: Long): Long? = try {
        validateOrThrow(context, authToken)
    } catch (_: AuthTokenInvalidException) {
        null
    }
}
