package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pda
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.ByteArrayBuilder
import com.selenus.artemis.tx.Instruction

object AddressLookupTableProgram {
  // Program ID per Solana docs
  val PROGRAM_ID = Pubkey.fromBase58("AddressLookupTab1e1111111111111111111111111")
  private val SYSTEM_PROGRAM_ID = Pubkey.fromBase58("11111111111111111111111111111111")

  /**
   * Derive lookup table address: findProgramAddress([authority, recentSlotLE], PROGRAM_ID)
   */
  fun deriveLookupTableAddress(authority: Pubkey, recentSlot: Long): Pair<Pubkey, Int> {
    val slotBytes = ByteArrayBuilder(8).putLongLE(recentSlot).toByteArray()
    val res = Pda.findProgramAddress(listOf(authority.bytes, slotBytes), PROGRAM_ID)
    return Pair(res.address, res.bump)
  }

  /**
   * ProgramInstruction::CreateLookupTable { recent_slot: u64, bump_seed: u8 }
   * bincode enum tag is u32 LE where Create=0
   */
  fun createLookupTable(authority: Pubkey, payer: Pubkey, recentSlot: Long): Pair<Instruction, Pubkey> {
    val (table, bump) = deriveLookupTableAddress(authority, recentSlot)
    val data = ByteArrayBuilder(4 + 8 + 1)
      .putIntLE(0) // enum variant: CreateLookupTable
      .putLongLE(recentSlot)
      .write(bump)
      .toByteArray()

    val accounts = listOf(
      AccountMeta(table, isSigner = false, isWritable = true),
      AccountMeta(authority, isSigner = true, isWritable = false),
      AccountMeta(payer, isSigner = true, isWritable = true),
      AccountMeta(SYSTEM_PROGRAM_ID, isSigner = false, isWritable = false)
    )
    return Instruction(PROGRAM_ID, accounts, data) to table
  }

  /**
   * ProgramInstruction::ExtendLookupTable { new_addresses: Vec<Pubkey> }
   * bincode enum tag u32 LE where Extend=2, followed by vec length (u64 LE) then addresses.
   */
  fun extendLookupTable(
    lookupTable: Pubkey,
    authority: Pubkey,
    payer: Pubkey? = null,
    newAddresses: List<Pubkey>
  ): Instruction {
    val vecLen = newAddresses.size.toLong()
    val builder = ByteArrayBuilder(4 + 8 + newAddresses.size * 32)
      .putIntLE(2) // enum variant: ExtendLookupTable
      .putLongLE(vecLen)
    newAddresses.forEach { builder.write(it.bytes) }

    val accounts = mutableListOf(
      AccountMeta(lookupTable, isSigner = false, isWritable = true),
      AccountMeta(authority, isSigner = true, isWritable = false),
    )
    if (payer != null) {
      accounts.add(AccountMeta(payer, isSigner = true, isWritable = true))
      accounts.add(AccountMeta(SYSTEM_PROGRAM_ID, isSigner = false, isWritable = false))
    }
    return Instruction(PROGRAM_ID, accounts, builder.toByteArray())
  }

  /**
   * ProgramInstruction::FreezeLookupTable
   * bincode enum tag u32 LE where Freeze=1
   */
  fun freezeLookupTable(lookupTable: Pubkey, authority: Pubkey): Instruction {
    val data = ByteArrayBuilder(4).putIntLE(1).toByteArray()

    val accounts = listOf(
      AccountMeta(lookupTable, isSigner = false, isWritable = true),
      AccountMeta(authority, isSigner = true, isWritable = false)
    )
    return Instruction(PROGRAM_ID, accounts, data)
  }

  /**
   * ProgramInstruction::DeactivateLookupTable
   * bincode enum tag u32 LE where Deactivate=3
   */
  fun deactivateLookupTable(lookupTable: Pubkey, authority: Pubkey): Instruction {
    val data = ByteArrayBuilder(4).putIntLE(3).toByteArray()

    val accounts = listOf(
      AccountMeta(lookupTable, isSigner = false, isWritable = true),
      AccountMeta(authority, isSigner = true, isWritable = false)
    )
    return Instruction(PROGRAM_ID, accounts, data)
  }

  /**
   * ProgramInstruction::CloseLookupTable
   * bincode enum tag u32 LE where Close=4
   */
  fun closeLookupTable(lookupTable: Pubkey, authority: Pubkey, recipient: Pubkey): Instruction {
    val data = ByteArrayBuilder(4).putIntLE(4).toByteArray()

    val accounts = listOf(
      AccountMeta(lookupTable, isSigner = false, isWritable = true),
      AccountMeta(authority, isSigner = true, isWritable = false),
      AccountMeta(recipient, isSigner = false, isWritable = true)
    )
    return Instruction(PROGRAM_ID, accounts, data)
  }
}
