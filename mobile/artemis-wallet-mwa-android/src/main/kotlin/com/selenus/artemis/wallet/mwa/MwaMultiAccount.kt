/*
 * MwaMultiAccount
 *
 * First-class multi-account session helper for Mobile Wallet Adapter 2.0.
 *
 * Background: upstream `solana-mobile/mobile-wallet-adapter` #438 has tracked
 * multi-account support for 2+ years with no implementation; apps with
 * multi-account wallets (Phantom, Solflare, Backpack) today must either ask
 * the user to switch accounts in the wallet itself before connecting, or
 * handle the full `accounts: MwaAccount[]` array manually and route signer
 * selection through their own index tables.
 *
 * Artemis already returns the full list on [MwaAuthorizeResult.accounts].
 * This helper wraps that list in a small, ergonomic API so apps can:
 *
 *   - select which account signs a given transaction by pubkey or label
 *   - build multi-sig transactions that reference N accounts
 *   - ask the user to re-authorize with a specific subset of addresses
 *   - carry per-account features/chains without the app tracking them
 *
 * The session does not store secret material (the wallet still holds keys);
 * it only indexes the authorized account set and offers typed lookups.
 */
package com.selenus.artemis.wallet.mwa

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.PlatformBase64
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.wallet.mwa.protocol.MwaAccount
import com.selenus.artemis.wallet.mwa.protocol.MwaAuthorizeResult

/**
 * Multi-account session wrapper. Construct via [MwaMultiAccountSession.from]
 * on a fresh [MwaAuthorizeResult].
 *
 * The session is immutable; to reflect a reauthorize, build a new one.
 */
class MwaMultiAccountSession private constructor(
    /** Wallet's auth token - carry through every subsequent MWA call. */
    val authToken: String,
    /** Every account the wallet authorized in this session, in original order. */
    val accounts: List<ResolvedAccount>,
    /** Wallet's base URI, if any. */
    val walletUriBase: String? = null,
) {

    /**
     * Resolved wallet account with both the raw upstream shape and Artemis's
     * decoded [Pubkey] view. All lookups on [MwaMultiAccountSession] return
     * this type so callers do not re-parse the base64 address.
     */
    data class ResolvedAccount(
        val pubkey: Pubkey,
        val label: String?,
        val icon: String?,
        /** CAIP chain identifiers advertised for this account. */
        val chains: List<String>,
        /** Per-account feature set. Empty when the wallet did not specialize. */
        val features: List<String>,
        /** Raw upstream record, preserved for apps that need to forward it. */
        val raw: MwaAccount,
    )

    /** The primary account. Matches the wallet's first entry (index 0). */
    val primary: ResolvedAccount get() = accounts.first()

    /** Convenience: every account's pubkey, in the wallet-provided order. */
    val pubkeys: List<Pubkey> get() = accounts.map { it.pubkey }

    /**
     * Find an authorized account by base58 pubkey. Returns `null` when the
     * pubkey is not one this session is authorized for. Apps should treat a
     * `null` return as "the user needs to reauthorize to expose this account".
     */
    fun byPubkey(pubkey: Pubkey): ResolvedAccount? =
        accounts.firstOrNull { it.pubkey == pubkey }

    /** String variant of [byPubkey]. Accepts base58. */
    fun byPubkey(base58: String): ResolvedAccount? = try {
        byPubkey(Pubkey(Base58.decode(base58)))
    } catch (_: Throwable) {
        null
    }

    /** Case-insensitive label lookup. Returns the first match or `null`. */
    fun byLabel(label: String): ResolvedAccount? =
        accounts.firstOrNull { it.label.equals(label, ignoreCase = true) }

    /**
     * Accounts authorized to transact on a specific CAIP chain. Apps can
     * reject signing if the user picks an account that isn't authorized for
     * the chain they are targeting - a common UX bug (upstream #958).
     */
    fun forChain(chain: String): List<ResolvedAccount> =
        accounts.filter { it.chains.isEmpty() || chain in it.chains }

    /**
     * Return the subset of [candidates] that are actually authorized on this
     * session. Useful for multi-sig transactions where the caller has a list
     * of required signers and wants to know which the wallet can cover.
     */
    fun resolvable(candidates: Collection<Pubkey>): List<ResolvedAccount> =
        candidates.mapNotNull { byPubkey(it) }

    /**
     * Pubkeys that the caller expects to sign but that this session cannot
     * produce signatures for. Use this to short-circuit a transaction before
     * asking the wallet for a signature it will always refuse.
     */
    fun missing(candidates: Collection<Pubkey>): List<Pubkey> {
        val available = accounts.map { it.pubkey }.toSet()
        return candidates.filterNot { it in available }
    }

    /**
     * Subset of base58 addresses suitable to pass back into `authorize(...)`
     * or `reauthorize(...)` as the `addresses` parameter. MWA 2.0 accepts
     * base64 encoded addresses; this helper returns that encoding so callers
     * can forward it without reformatting.
     */
    fun reauthorizeAddressesB64(): Array<String> =
        accounts.map { PlatformBase64.encode(it.pubkey.bytes) }.toTypedArray()

    companion object {
        /**
         * Build a session from an [MwaAuthorizeResult]. Every account in the
         * result is resolved eagerly so subsequent lookups are O(1) plus a
         * small base58 decode.
         */
        fun from(result: MwaAuthorizeResult): MwaMultiAccountSession {
            val resolved = result.accounts.map { account ->
                val bytes = runCatching { PlatformBase64.decode(account.address) }
                    .getOrElse { runCatching { Base58.decode(account.address) }.getOrThrow() }
                ResolvedAccount(
                    pubkey = Pubkey(bytes),
                    label = account.label,
                    icon = account.icon,
                    chains = account.chains.orEmpty(),
                    features = account.features.orEmpty(),
                    raw = account,
                )
            }
            require(resolved.isNotEmpty()) {
                "MwaAuthorizeResult contained zero accounts; cannot build a session"
            }
            return MwaMultiAccountSession(
                authToken = result.authToken,
                accounts = resolved,
                walletUriBase = result.walletUriBase,
            )
        }
    }
}
