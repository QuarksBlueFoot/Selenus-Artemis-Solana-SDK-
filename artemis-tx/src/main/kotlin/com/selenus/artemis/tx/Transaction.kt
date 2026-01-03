package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import java.io.ByteArrayOutputStream
import java.util.Base64

class Transaction(
  private val feePayer: Pubkey,
  private val recentBlockhash: String,
  private val instructions: List<Instruction>
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
