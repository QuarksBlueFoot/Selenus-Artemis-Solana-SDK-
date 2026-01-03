package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AddressLookupTableProgram {
  // Program ID per Solana docs
  val PROGRAM_ID = Pubkey.fromBase58("AddressLookupTab1e1111111111111111111111111")
  private val SYSTEM_PROGRAM_ID = Pubkey.fromBase58("11111111111111111111111111111111")

  /**
   * Derive lookup table address: findProgramAddress([authority, recentSlotLE], PROGRAM_ID)
   */
  fun deriveLookupTableAddress(authority: Pubkey, recentSlot: Long): Pair<Pubkey, Int> {
    val slotBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(recentSlot).array()
    return Pubkey.findProgramAddress(listOf(authority.bytes, slotBytes), PROGRAM_ID)
  }

  /**
   * ProgramInstruction::CreateLookupTable { recent_slot: u64, bump_seed: u8 }
   * bincode enum tag is u32 LE where Create=0
   */
  fun createLookupTable(authority: Pubkey, payer: Pubkey, recentSlot: Long): Pair<Instruction, Pubkey> {
    val (table, bump) = deriveLookupTableAddress(authority, recentSlot)
    val data = ByteBuffer.allocate(4 + 8 + 1).order(ByteOrder.LITTLE_ENDIAN)
      .putInt(0) // enum variant: CreateLookupTable
      .putLong(recentSlot)
      .put(bump.toByte())
      .array()

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
    val data = ByteArrayOutputStreamCompat()
    data.writeU32LE(2) // enum variant: ExtendLookupTable
    data.writeU64LE(vecLen)
    newAddresses.forEach { data.writeBytes(it.bytes) }

    val accounts = mutableListOf(
      AccountMeta(lookupTable, isSigner = false, isWritable = true),
      AccountMeta(authority, isSigner = true, isWritable = false),
    )
    if (payer != null) {
      accounts.add(AccountMeta(payer, isSigner = true, isWritable = true))
      accounts.add(AccountMeta(SYSTEM_PROGRAM_ID, isSigner = false, isWritable = false))
    }
    return Instruction(PROGRAM_ID, accounts, data.toByteArray())
  }
}

// Tiny helper to avoid pulling extra deps.
private class ByteArrayOutputStreamCompat {
  private val out = java.io.ByteArrayOutputStream()
  fun writeBytes(b: ByteArray) { out.write(b) }
  fun writeU32LE(v: Int) {
    val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    out.write(bb)
  }
  fun writeU64LE(v: Long) {
    val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
    out.write(bb)
  }
  fun toByteArray(): ByteArray = out.toByteArray()
}
