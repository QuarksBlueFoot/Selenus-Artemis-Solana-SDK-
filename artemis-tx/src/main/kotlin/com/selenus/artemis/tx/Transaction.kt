package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import java.io.ByteArrayOutputStream
import java.util.Base64

class Transaction(
  val feePayer: Pubkey,
  val recentBlockhash: String,
  val instructions: List<Instruction>
) {
  fun compileMessage(): Message {
    val metas = LinkedHashMap<String, AccountMeta>()
    fun upsert(meta: AccountMeta) {
      val k = meta.pubkey.toString()
      val existing = metas[k]
      if (existing == null) metas[k] = meta
      else metas[k] = existing.copy(
        isSigner = existing.isSigner || meta.isSigner,
        isWritable = existing.isWritable || meta.isWritable
      )
    }

    // fee payer must be signer + writable
    upsert(AccountMeta(feePayer, isSigner = true, isWritable = true))

    for (ix in instructions) {
      for (m in ix.accounts) upsert(m)
      upsert(AccountMeta(ix.programId, isSigner = false, isWritable = false))
    }

    val accountKeys = metas.values.map { it.pubkey }
    val signerCount = metas.values.count { it.isSigner }
    val readonlySigned = metas.values.take(signerCount).count { it.isSigner && !it.isWritable }
    val readonlyUnsigned = metas.values.drop(signerCount).count { !it.isWritable }

    val header = MessageHeader(signerCount, readonlySigned, readonlyUnsigned)

    val compiledIxs = instructions.map { ix ->
      val programIndex = accountKeys.indexOfFirst { it.bytes.contentEquals(ix.programId.bytes) }
      val accIdx = ix.accounts.map { m ->
        accountKeys.indexOfFirst { it.bytes.contentEquals(m.pubkey.bytes) }.toByte()
      }.toByteArray()
      CompiledInstruction(programIndex, accIdx, ix.data)
    }

    return Message(header, accountKeys, recentBlockhash, compiledIxs)
  }

  fun sign(signers: List<Signer>): SignedTransaction {
    val msg = compileMessage()
    val msgBytes = msg.serialize()
    // signatures in order of first N signer keys in accountKeys
    val signerKeys = msg.accountKeys.take(msg.header.numRequiredSignatures)
    val sigMap = signers.associateBy { it.publicKey.toString() }

    val signatures = signerKeys.map { pk ->
      val s = sigMap[pk.toString()] ?: throw IllegalArgumentException("Missing signer for $pk")
      s.sign(msgBytes)
    }

    return SignedTransaction(signatures, msgBytes)
  }

  companion object {
    fun from(bytes: ByteArray): Transaction {
      var offset = 0

      // 1. Signatures
      val (numSigs, sigLenBytes) = ShortVec.decodeLen(bytes)
      offset += sigLenBytes
      offset += numSigs * 64

      // 2. Message Header
      val numRequiredSignatures = bytes[offset].toInt() and 0xFF
      val numReadonlySigned = bytes[offset + 1].toInt() and 0xFF
      val numReadonlyUnsigned = bytes[offset + 2].toInt() and 0xFF
      offset += 3

      // 3. Account Keys
      val (numKeys, keysLenBytes) = ShortVec.decodeLen(bytes.copyOfRange(offset, bytes.size))
      offset += keysLenBytes

      val accountKeys = ArrayList<Pubkey>()
      for (i in 0 until numKeys) {
        accountKeys.add(Pubkey(bytes.copyOfRange(offset, offset + 32)))
        offset += 32
      }

      // 4. Recent Blockhash
      val recentBlockhash = Base58.encode(bytes.copyOfRange(offset, offset + 32))
      offset += 32

      // 5. Instructions
      val (numIxs, ixsLenBytes) = ShortVec.decodeLen(bytes.copyOfRange(offset, bytes.size))
      offset += ixsLenBytes

      val instructions = ArrayList<Instruction>()
      for (i in 0 until numIxs) {
        val programIdIndex = bytes[offset].toInt() and 0xFF
        offset += 1

        val (numAccounts, accLenBytes) = ShortVec.decodeLen(bytes.copyOfRange(offset, bytes.size))
        offset += accLenBytes

        val accounts = ArrayList<AccountMeta>()
        for (j in 0 until numAccounts) {
          val accountIndex = bytes[offset].toInt() and 0xFF
          offset += 1

          val pubkey = accountKeys[accountIndex]
          val isSigner = accountIndex < numRequiredSignatures
          val isWritable = if (isSigner) {
            accountIndex < (numRequiredSignatures - numReadonlySigned)
          } else {
            accountIndex < (accountKeys.size - numReadonlyUnsigned)
          }
          accounts.add(AccountMeta(pubkey, isSigner, isWritable))
        }

        val (dataLen, dataLenBytes) = ShortVec.decodeLen(bytes.copyOfRange(offset, bytes.size))
        offset += dataLenBytes

        val data = bytes.copyOfRange(offset, offset + dataLen)
        offset += dataLen

        instructions.add(Instruction(accountKeys[programIdIndex], accounts, data))
      }

      val feePayer = accountKeys.firstOrNull() ?: throw IllegalArgumentException("Transaction has no accounts")
      return Transaction(feePayer, recentBlockhash, instructions)
    }
  }
}

data class SignedTransaction(val signatures: List<ByteArray>, val messageBytes: ByteArray) {
  fun serialize(): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(ShortVec.encodeLen(signatures.size))
    for (sig in signatures) out.write(sig)
    out.write(messageBytes)
    return out.toByteArray()
  }

  fun toBase64(): String = Base64.getEncoder().encodeToString(serialize())
}
