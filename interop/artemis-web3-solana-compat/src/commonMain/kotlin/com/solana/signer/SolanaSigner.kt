/*
 * Drop-in source compatibility with com.solana.signer (web3-solana 0.2.5).
 *
 * web3-solana defines `SolanaSigner` as an abstract class extending a broader
 * `Ed25519Signer` marker. The shim keeps the hierarchy shape so `is SolanaSigner`
 * and `is Ed25519Signer` checks in user code continue to type-check.
 */
package com.solana.signer

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Message
import com.solana.transaction.Transaction

/**
 * Marker interface for anything capable of producing Ed25519 signatures.
 * Upstream exposes this at the same fully qualified name.
 */
interface Ed25519Signer {
    suspend fun sign(message: ByteArray): ByteArray
}

/**
 * web3-solana `SolanaSigner` - the primary wallet / signing abstraction.
 *
 * Concrete instances include keypair-backed signers and MWA adapters. Both
 * `signTransaction(transaction)` and `signTransaction(message)` are provided
 * as base-class concrete methods so subclasses only need to implement
 * `signAndSendTransaction` and the low-level `sign(bytes)`.
 *
 * Two deprecated byte-array entry points exist in upstream that throw
 * `NotImplementedError`. The shim preserves them verbatim - downstream code
 * catches the throw.
 */
abstract class SolanaSigner : Ed25519Signer {

    /** The public key that signatures will be verifiable against. */
    abstract val publicKey: SolanaPublicKey

    /** Sign and submit [transaction] to the connected cluster. Returns the signature. */
    abstract suspend fun signAndSendTransaction(transaction: Transaction): Result<String>

    /**
     * Sign [transaction] and return it with the added signature. Not send -
     * the caller submits it through whatever send path they own.
     */
    open suspend fun signTransaction(transaction: Transaction): Result<Transaction> {
        val msgBytes = transaction.message.serialize()
        val signature = runCatching { sign(msgBytes) }.getOrElse { return Result.failure(it) }
        // Find the correct signature slot by matching the signer's pubkey
        // against the message's account keys. Signature[i] corresponds to
        // accounts[i] for i < numRequiredSignatures. Writing to slot 0
        // unconditionally (previous behaviour) overwrites the fee-payer
        // signature when the signer is actually a co-signer.
        val numRequired = transaction.message.signatureCount.toInt()
        val signerIndex = transaction.message.accounts.asSequence()
            .take(numRequired)
            .indexOfFirst { it.bytes.contentEquals(publicKey.bytes) }
        if (signerIndex < 0) {
            return Result.failure(
                IllegalArgumentException(
                    "Signer pubkey ${publicKey.base58()} is not among the " +
                        "$numRequired required signers of the message"
                )
            )
        }
        val newSigs = transaction.signatures.toMutableList().also { sigs ->
            while (sigs.size <= signerIndex) sigs.add(ByteArray(Transaction.SIGNATURE_LENGTH_BYTES))
            sigs[signerIndex] = signature
        }
        return Result.success(transaction.copy(signatures = newSigs))
    }

    /** Build an unsigned transaction from [transactionMessage] and sign it. */
    open suspend fun signTransaction(transactionMessage: Message): Result<Transaction> {
        val unsigned = Transaction(transactionMessage)
        return signTransaction(unsigned)
    }

    /**
     * Sign an off-chain message. This is the standard path for Sign-In With
     * Solana and similar flows where the payload is not a transaction.
     */
    open suspend fun signOffChainMessage(message: ByteArray): Result<ByteArray> =
        runCatching { sign(message) }

    /** Deprecated sync-sign entry point. Upstream throws; we mirror. */
    @Deprecated(
        "Use sign(ByteArray) suspend function instead",
        level = DeprecationLevel.ERROR
    )
    fun signMessage(message: ByteArray): ByteArray =
        throw NotImplementedError("signMessage is not supported; use the suspending sign() instead")

    /** Deprecated sync-sign entry point. Upstream throws; we mirror. */
    @Deprecated(
        "Use signTransaction(Transaction) suspend function instead",
        level = DeprecationLevel.ERROR
    )
    fun signTransaction(transactionBytes: ByteArray): ByteArray =
        throw NotImplementedError("signTransaction(ByteArray) is not supported; use signTransaction(Transaction)")
}
