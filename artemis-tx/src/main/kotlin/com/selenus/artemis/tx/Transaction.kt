package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import java.io.ByteArrayOutputStream
import java.util.Base64

class Transaction(
  var feePayer: Pubkey,
  var recentBlockhash: String,
  instructions: List<Instruction> = emptyList()
) {
  val instructions = ArrayList(instructions)
  val signatures = LinkedHashMap<Pubkey, ByteArray>()

  fun addInstruction(instruction: Instruction) {
    instructions.add(instruction)
  }

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

    // Sort accounts according to Solana rules:
    // 1. Fee Payer (must be first)
    // 2. Signers, Writable
    // 3. Signers, Read-only
    // 4. Non-signers, Writable
    // 5. Non-signers, Read-only
    val sortedMetas = metas.values.sortedWith(Comparator { a, b ->
      // Fee payer always first
      if (a.pubkey == feePayer) return@Comparator -1
      if (b.pubkey == feePayer) return@Comparator 1

      // Signers before non-signers
      if (a.isSigner != b.isSigner) return@Comparator if (a.isSigner) -1 else 1

      // Writable before read-only
      if (a.isWritable != b.isWritable) return@Comparator if (a.isWritable) -1 else 1

      // Tie-break with pubkey bytes for deterministic ordering (optional but good practice)
      // For now, just keep stable sort or insertion order if equal
      0
    })

    val accountKeys = sortedMetas.map { it.pubkey }
    val signerCount = sortedMetas.count { it.isSigner }
    val readonlySigned = sortedMetas.take(signerCount).count { !it.isWritable }
    val readonlyUnsigned = sortedMetas.drop(signerCount).count { !it.isWritable }

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
    
    for (signer in signers) {
      signatures[signer.publicKey] = signer.sign(msgBytes)
    }

    val signerKeys = msg.accountKeys.take(msg.header.numRequiredSignatures)
    val orderedSignatures = signerKeys.map { pk ->
      signatures[pk] ?: throw IllegalArgumentException("Missing signer for $pk")
    }

    return SignedTransaction(orderedSignatures, msgBytes)
  }

  fun partialSign(signers: List<Signer>) {
    val msg = compileMessage()
    val msgBytes = msg.serialize()
    for (signer in signers) {
      signatures[signer.publicKey] = signer.sign(msgBytes)
    }
  }

  fun addSignature(pubkey: Pubkey, signature: ByteArray) {
    require(signature.size == 64) { "Signature must be 64 bytes" }
    signatures[pubkey] = signature
  }

  fun serialize(): ByteArray {
    val msg = compileMessage()
    val msgBytes = msg.serialize()
    val requiredSigners = msg.accountKeys.take(msg.header.numRequiredSignatures)
    
    val orderedSignatures = requiredSigners.map { pk ->
      signatures[pk] ?: ByteArray(64)
    }
    
    val out = ByteArrayOutputStream()
    out.write(ShortVec.encodeLen(orderedSignatures.size))
    for (sig in orderedSignatures) out.write(sig)
    out.write(msgBytes)
    return out.toByteArray()
  }

  companion object {
    fun from(bytes: ByteArray): Transaction {
      var offset = 0

      val (numSigs, sigLenBytes) = ShortVec.decodeLen(bytes)
      offset += sigLenBytes
      
      val rawSignatures = ArrayList<ByteArray>()
      for (i in 0 until numSigs) {
        rawSignatures.add(bytes.copyOfRange(offset, offset + 64))
        offset += 64
      }

      val numRequiredSignatures = bytes[offset].toInt() and 0xFF
      val numReadonlySigned = bytes[offset + 1].toInt() and 0xFF
      val numReadonlyUnsigned = bytes[offset + 2].toInt() and 0xFF
      offset += 3

      val (numKeys, keysLenBytes) = ShortVec.decodeLen(bytes.copyOfRange(offset, bytes.size))
      offset += keysLenBytes

      val accountKeys = ArrayList<Pubkey>()
      for (i in 0 until numKeys) {
        accountKeys.add(Pubkey(bytes.copyOfRange(offset, offset + 32)))
        offset += 32
      }

      val recentBlockhash = Base58.encode(bytes.copyOfRange(offset, offset + 32))
      offset += 32

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
      val tx = Transaction(feePayer, recentBlockhash, instructions)
      
      for (i in 0 until numSigs) {
        if (i < accountKeys.size) {
          tx.signatures[accountKeys[i]] = rawSignatures[i]
        }
      }
      
      return tx
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
