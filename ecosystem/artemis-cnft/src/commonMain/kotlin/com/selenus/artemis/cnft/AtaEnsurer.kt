package com.selenus.artemis.cnft

import com.selenus.artemis.programs.AssociatedToken
import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject

/**
 * AtaEnsurer — idempotent Associated Token Account resolution and creation.
 *
 * Standalone utility that apps can drop in front of any token-transfer flow to
 * guarantee the destination ATA exists before the transfer hits the chain.
 *
 * This is the missing piece most SDKs (sol4k, solana-kt) leave to the caller,
 * and the biggest footgun in marketplace flows:
 *
 * - User A sends 100 USDC to User B
 * - User B has never touched USDC before, so their ATA doesn't exist yet
 * - The transfer fails with `InvalidAccountData`
 *
 * [AtaEnsurer] prevents this by fetching the destination ATA once and, if it's
 * missing, producing a `createAssociatedTokenAccount` instruction that the caller
 * prepends to the transaction. On-chain, ATA program creation is idempotent, but
 * on-chain failure costs gas — this lets you skip the call entirely.
 *
 * ```kotlin
 * val ensurer = AtaEnsurer(rpc)
 *
 * val (destinationAta, createIx) = ensurer.resolve(
 *     payer  = wallet.publicKey,
 *     owner  = recipient,
 *     mint   = usdcMint
 * )
 *
 * val instructions = buildList {
 *     createIx?.let(::add)               // only added if the ATA didn't exist
 *     add(tokenTransfer(src, destinationAta, amount))
 * }
 * ```
 *
 * A negative cache remembers ATAs that didn't exist so batch operations don't
 * re-query them inside the same window; a positive cache remembers confirmed
 * ATAs so the same address isn't re-fetched when ensuring many transfers at once.
 *
 * @param rpc       RPC client used to check account existence.
 * @param cacheTtlMs How long cache entries stay valid. Default 10s matches the typical
 *                   block interval and keeps the ensurer safe against short-lived races.
 */
class AtaEnsurer(
    private val rpc: RpcApi,
    private val cacheTtlMs: Long = 10_000,
    private val clock: () -> Long = { com.selenus.artemis.runtime.currentTimeMillis() }
) {

    /**
     * Result of an ATA resolution.
     *
     * @param ata        The derived associated token account address.
     * @param createIx   `createAssociatedTokenAccount` instruction if the ATA does not
     *                   currently exist on-chain, otherwise `null`. Prepend to the
     *                   outgoing transaction if non-null.
     * @param existed    True when the ATA was already present on-chain.
     */
    data class AtaResolution(
        val ata: Pubkey,
        val createIx: Instruction?,
        val existed: Boolean
    ) {
        /** Whether a create instruction must be prepended to the transaction. */
        val needsCreate: Boolean get() = createIx != null
    }

    private data class CacheEntry(val existed: Boolean, val atMs: Long)

    private val cache = mutableMapOf<String, CacheEntry>()

    /**
     * Resolve [owner]'s ATA for [mint]. Returns the ATA address and, if it does
     * not exist yet, a create instruction that [payer] must sign.
     *
     * @param payer       Account that pays rent if the ATA needs to be created.
     * @param owner       Token account owner (destination of the transfer).
     * @param mint        SPL token mint.
     * @param tokenProgram Token program owning the mint. Pass [ProgramIds.TOKEN_2022_PROGRAM]
     *                    for Token-2022 mints. Defaults to the original SPL Token program.
     */
    suspend fun resolve(
        payer: Pubkey,
        owner: Pubkey,
        mint: Pubkey,
        tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM
    ): AtaResolution {
        val ata = AssociatedToken.address(owner, mint, tokenProgram)
        val key = ata.toBase58()
        val now = clock()

        val cached = cache[key]
        if (cached != null && now - cached.atMs < cacheTtlMs) {
            return AtaResolution(
                ata = ata,
                createIx = if (cached.existed) null else AssociatedToken.createAssociatedTokenAccount(payer, owner, mint, ata),
                existed = cached.existed
            )
        }

        val existed = accountExists(ata)
        cache[key] = CacheEntry(existed, now)

        return AtaResolution(
            ata = ata,
            createIx = if (existed) null else AssociatedToken.createAssociatedTokenAccount(payer, owner, mint, ata),
            existed = existed
        )
    }

    /**
     * Resolve many ATAs in a single batched RPC call via `getMultipleAccounts`.
     *
     * Returns one [AtaResolution] per input in the same order. Prefer this over
     * a loop when ensuring many destinations (airdrops, batch sends).
     */
    suspend fun resolveBatch(
        payer: Pubkey,
        targets: List<Target>,
        tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM
    ): List<AtaResolution> {
        if (targets.isEmpty()) return emptyList()

        val atas = targets.map { AssociatedToken.address(it.owner, it.mint, tokenProgram) }
        val now = clock()

        // Split into cached vs needs-fetch.
        val cachedResults = MutableList<AtaResolution?>(targets.size) { null }
        val toFetchIndices = mutableListOf<Int>()
        for ((i, ata) in atas.withIndex()) {
            val cached = cache[ata.toBase58()]
            if (cached != null && now - cached.atMs < cacheTtlMs) {
                cachedResults[i] = AtaResolution(
                    ata = ata,
                    createIx = if (cached.existed) null
                    else AssociatedToken.createAssociatedTokenAccount(payer, targets[i].owner, targets[i].mint, ata),
                    existed = cached.existed
                )
            } else {
                toFetchIndices.add(i)
            }
        }

        if (toFetchIndices.isNotEmpty()) {
            val fetchAddrs = toFetchIndices.map { atas[it].toBase58() }
            val response = rpc.getMultipleAccounts(fetchAddrs)
            val valueArr = response["value"]
            val values = if (valueArr is kotlinx.serialization.json.JsonArray) valueArr else null
            for ((idxInList, originalIdx) in toFetchIndices.withIndex()) {
                val entry = values?.getOrNull(idxInList)
                val exists = entry != null && entry !is JsonNull
                cache[atas[originalIdx].toBase58()] = CacheEntry(exists, now)
                val target = targets[originalIdx]
                cachedResults[originalIdx] = AtaResolution(
                    ata = atas[originalIdx],
                    createIx = if (exists) null
                    else AssociatedToken.createAssociatedTokenAccount(
                        payer, target.owner, target.mint, atas[originalIdx]
                    ),
                    existed = exists
                )
            }
        }

        return cachedResults.map { it!! }
    }

    /**
     * Mark an ATA as existing without issuing an RPC call. Useful right after
     * a confirmed create instruction when the caller already knows the ATA is live.
     */
    fun markExists(ata: Pubkey) {
        cache[ata.toBase58()] = CacheEntry(existed = true, atMs = clock())
    }

    /** Drop all cached results — forces a fresh on-chain check on the next call. */
    fun invalidate() {
        cache.clear()
    }

    /** Input for [resolveBatch]. */
    data class Target(val owner: Pubkey, val mint: Pubkey)

    private suspend fun accountExists(ata: Pubkey): Boolean {
        val res = runCatching { rpc.getAccountInfo(ata.toBase58()) }.getOrNull() ?: return false
        val value = res["value"] ?: return false
        if (value is JsonNull) return false
        return value.jsonObject.isNotEmpty()
    }
}
