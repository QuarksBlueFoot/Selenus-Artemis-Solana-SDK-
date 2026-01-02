package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.ByteArrayBuilder
import com.selenus.artemis.tx.Instruction

object StakeProgram {
    val PROGRAM_ID = Pubkey.fromBase58("Stake11111111111111111111111111111111111111")
    val SYSVAR_RENT = Pubkey.fromBase58("SysvarRent111111111111111111111111111111111")

    fun initialize(
        stakeAccount: Pubkey,
        authorized: Authorized,
        lockup: Lockup = Lockup()
    ): Instruction {
        val data = ByteArrayBuilder(4 + 32 + 32 + 8 + 8 + 32)
            .putIntLE(0) // Initialize
            .write(authorized.staker.bytes)
            .write(authorized.withdrawer.bytes)
            .putLongLE(lockup.unixTimestamp)
            .putLongLE(lockup.epoch)
            .write(lockup.custodian.bytes)
            .toByteArray()

        return Instruction(
            PROGRAM_ID,
            listOf(
                AccountMeta(stakeAccount, isSigner = false, isWritable = true),
                AccountMeta(SYSVAR_RENT, isSigner = false, isWritable = false)
            ),
            data
        )
    }

    fun delegate(
        stakeAccount: Pubkey,
        voteAccount: Pubkey,
        clockSysvar: Pubkey = Pubkey.fromBase58("SysvarC1ock11111111111111111111111111111111"),
        stakeHistorySysvar: Pubkey = Pubkey.fromBase58("SysvarStakeHistory1111111111111111111111111"),
        configSysvar: Pubkey = Pubkey.fromBase58("StakeConfig11111111111111111111111111111111"),
        authorizedStaker: Pubkey
    ): Instruction {
        val data = ByteArrayBuilder(4)
            .putIntLE(2) // Delegate
            .toByteArray()

        return Instruction(
            PROGRAM_ID,
            listOf(
                AccountMeta(stakeAccount, isSigner = false, isWritable = true),
                AccountMeta(voteAccount, isSigner = false, isWritable = false),
                AccountMeta(clockSysvar, isSigner = false, isWritable = false),
                AccountMeta(stakeHistorySysvar, isSigner = false, isWritable = false),
                AccountMeta(configSysvar, isSigner = false, isWritable = false),
                AccountMeta(authorizedStaker, isSigner = true, isWritable = false)
            ),
            data
        )
    }
    
    fun deactivate(
        stakeAccount: Pubkey,
        clockSysvar: Pubkey = Pubkey.fromBase58("SysvarC1ock11111111111111111111111111111111"),
        authorizedStaker: Pubkey
    ): Instruction {
        val data = ByteArrayBuilder(4)
            .putIntLE(5) // Deactivate
            .toByteArray()

        return Instruction(
            PROGRAM_ID,
            listOf(
                AccountMeta(stakeAccount, isSigner = false, isWritable = true),
                AccountMeta(clockSysvar, isSigner = false, isWritable = false),
                AccountMeta(authorizedStaker, isSigner = true, isWritable = false)
            ),
            data
        )
    }

    data class Authorized(
        val staker: Pubkey,
        val withdrawer: Pubkey
    )

    data class Lockup(
        val unixTimestamp: Long = 0,
        val epoch: Long = 0,
        val custodian: Pubkey = Pubkey(ByteArray(32))
    )

    fun withdraw(
        stakeAccount: Pubkey,
        withdrawAuthority: Pubkey,
        to: Pubkey,
        lamports: Long,
        clockSysvar: Pubkey = Pubkey.fromBase58("SysvarC1ock11111111111111111111111111111111"),
        stakeHistorySysvar: Pubkey = Pubkey.fromBase58("SysvarStakeHistory1111111111111111111111111")
    ): Instruction {
        val data = ByteArrayBuilder(4 + 8)
            .putIntLE(4) // Withdraw
            .putLongLE(lamports)
            .toByteArray()

        return Instruction(
            PROGRAM_ID,
            listOf(
                AccountMeta(stakeAccount, isSigner = false, isWritable = true),
                AccountMeta(to, isSigner = false, isWritable = true),
                AccountMeta(clockSysvar, isSigner = false, isWritable = false),
                AccountMeta(stakeHistorySysvar, isSigner = false, isWritable = false),
                AccountMeta(withdrawAuthority, isSigner = true, isWritable = false)
            ),
            data
        )
    }

    fun merge(
        destination: Pubkey,
        source: Pubkey,
        authorizedStaker: Pubkey,
        clockSysvar: Pubkey = Pubkey.fromBase58("SysvarC1ock11111111111111111111111111111111"),
        stakeHistorySysvar: Pubkey = Pubkey.fromBase58("SysvarStakeHistory1111111111111111111111111")
    ): Instruction {
        val data = ByteArrayBuilder(4)
            .putIntLE(7) // Merge
            .toByteArray()

        return Instruction(
            PROGRAM_ID,
            listOf(
                AccountMeta(destination, isSigner = false, isWritable = true),
                AccountMeta(source, isSigner = false, isWritable = true),
                AccountMeta(clockSysvar, isSigner = false, isWritable = false),
                AccountMeta(stakeHistorySysvar, isSigner = false, isWritable = false),
                AccountMeta(authorizedStaker, isSigner = true, isWritable = false)
            ),
            data
        )
    }
}
