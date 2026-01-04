package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import java.nio.ByteBuffer
import java.nio.ByteOrder

object StakeProgram {
    val PROGRAM_ID = Pubkey.fromBase58("Stake11111111111111111111111111111111111111")
    val SYSVAR_RENT = Pubkey.fromBase58("SysvarRent111111111111111111111111111111111")

    fun initialize(
        stakeAccount: Pubkey,
        authorized: Authorized,
        lockup: Lockup = Lockup()
    ): Instruction {
        val data = ByteBuffer.allocate(4 + 32 + 32 + 8 + 8 + 32)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0) // Initialize
            .put(authorized.staker.bytes)
            .put(authorized.withdrawer.bytes)
            .putLong(lockup.unixTimestamp)
            .putLong(lockup.epoch)
            .put(lockup.custodian.bytes)
            .array()

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
        val data = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(2) // Delegate
            .array()

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
        val data = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(5) // Deactivate
            .array()

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
}
