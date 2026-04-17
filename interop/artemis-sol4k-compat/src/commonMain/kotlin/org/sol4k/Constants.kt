/*
 * Drop-in source compatibility with org.sol4k.Constants.
 *
 * Every field here mirrors the sol4k 0.7.x constant at the same name, backed
 * by the Artemis ProgramIds registry so both the constant VALUE and the
 * constant NAME match byte-for-byte.
 */
package org.sol4k

import com.selenus.artemis.programs.ProgramIds

object Constants {
    @JvmField val SYSTEM_PROGRAM: PublicKey = PublicKey(ProgramIds.SYSTEM_PROGRAM.bytes)
    @JvmField val TOKEN_PROGRAM_ID: PublicKey = PublicKey(ProgramIds.TOKEN_PROGRAM.bytes)
    @JvmField val TOKEN_2022_PROGRAM_ID: PublicKey = PublicKey(ProgramIds.TOKEN_2022_PROGRAM.bytes)
    @JvmField val ASSOCIATED_TOKEN_PROGRAM_ID: PublicKey = PublicKey(ProgramIds.ASSOCIATED_TOKEN_PROGRAM.bytes)
    @JvmField val COMPUTE_BUDGET_PROGRAM_ID: PublicKey = PublicKey("ComputeBudget111111111111111111111111111111")
    @JvmField val SYSVAR_RENT_ADDRESS: PublicKey = PublicKey(ProgramIds.RENT_SYSVAR.bytes)
    const val PUBLIC_KEY_LENGTH: Int = 32
    const val SIGNATURE_LENGTH: Int = 64
}
