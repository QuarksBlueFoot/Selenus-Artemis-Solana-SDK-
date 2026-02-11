package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Pubkey

data class AccountMeta(val pubkey: Pubkey, val isSigner: Boolean, val isWritable: Boolean) {
    companion object {
        /** Create a signer + writable account meta. */
        fun signerAndWritable(pubkey: Pubkey): AccountMeta = AccountMeta(pubkey, isSigner = true, isWritable = true)

        /** Create a signer (read-only) account meta. */
        fun signer(pubkey: Pubkey): AccountMeta = AccountMeta(pubkey, isSigner = true, isWritable = false)

        /** Create a writable (non-signer) account meta. */
        fun writable(pubkey: Pubkey): AccountMeta = AccountMeta(pubkey, isSigner = false, isWritable = true)

        /** Create a read-only (non-signer) account meta. */
        fun readOnly(pubkey: Pubkey): AccountMeta = AccountMeta(pubkey, isSigner = false, isWritable = false)
    }
}

data class Instruction(val programId: Pubkey, val accounts: List<AccountMeta>, val data: ByteArray)

/**
 * Well-known Solana program addresses.
 *
 * Central constant object matching sol4k conventions.
 * Avoids scattering these across individual module files.
 */
object SolanaPrograms {
    val SYSTEM_PROGRAM = Pubkey.fromBase58("11111111111111111111111111111111")
    val TOKEN_PROGRAM = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    val TOKEN_2022_PROGRAM = Pubkey.fromBase58("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")
    val ASSOCIATED_TOKEN_PROGRAM = Pubkey.fromBase58("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
    val COMPUTE_BUDGET_PROGRAM = Pubkey.fromBase58("ComputeBudget111111111111111111111111111111")
    val MEMO_PROGRAM = Pubkey.fromBase58("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr")
    val MEMO_PROGRAM_V1 = Pubkey.fromBase58("Memo1UhkJBfCR6MNB7Gvn7VBqhUP6PNBJtkjGRaZVE")
    val STAKE_PROGRAM = Pubkey.fromBase58("Stake11111111111111111111111111111111111111")
    val VOTE_PROGRAM = Pubkey.fromBase58("Vote111111111111111111111111111111111111111")
    val BPF_LOADER = Pubkey.fromBase58("BPFLoaderUpgradeab1e11111111111111111111111")
    val METAPLEX_TOKEN_METADATA = Pubkey.fromBase58("metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s")
    val SYSVAR_RENT = Pubkey.fromBase58("SysvarRent111111111111111111111111111111111")
    val SYSVAR_CLOCK = Pubkey.fromBase58("SysvarC1ock11111111111111111111111111111111")
    val SYSVAR_RECENT_BLOCKHASHES = Pubkey.fromBase58("SysvarRecentB1ockhashes11111111111111111111")
    val SYSVAR_INSTRUCTIONS = Pubkey.fromBase58("Sysvar1nstructions1111111111111111111111111")
    val SYSVAR_STAKE_HISTORY = Pubkey.fromBase58("SysvarStakeHistory1111111111111111111111111")
}
