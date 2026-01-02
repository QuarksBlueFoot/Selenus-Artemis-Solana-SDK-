package com.selenus.artemis.wallet

/**
 * WalletAdapterSignAndSend
 *
 * Optional capability interface for wallets that can sign and submit transactions directly.
 * This is useful on mobile when the wallet can broadcast with its own RPC routing.
 * 
 * Provides full parity with Solana Mobile MWA 2.0 signAndSendTransactions API,
 * with additional Artemis enhancements.
 */
interface WalletAdapterSignAndSend {
    /**
     * Signs and sends a single transaction.
     * 
     * @param transaction The serialized transaction bytes
     * @param options Options controlling submission and confirmation behavior
     * @return The result containing signature and confirmation status
     */
    suspend fun signAndSendTransaction(
        transaction: ByteArray,
        options: SendTransactionOptions = SendTransactionOptions.Default
    ): SendTransactionResult
    
    /**
     * Signs and sends multiple transactions.
     * 
     * When options.waitForCommitmentToSendNextTransaction is true, each transaction
     * will wait for the specified commitment level before the next is sent. This is
     * critical for dependent transactions that must be processed in order.
     * 
     * @param transactions List of serialized transaction bytes
     * @param options Options controlling submission and confirmation behavior
     * @return Batch result containing results for each transaction
     */
    suspend fun signAndSendTransactions(
        transactions: List<ByteArray>,
        options: SendTransactionOptions = SendTransactionOptions.Default
    ): BatchSendResult
    
    /**
     * Legacy compatibility method.
     * @deprecated Use signAndSendTransactions with SendTransactionOptions instead.
     */
    @Deprecated(
        message = "Use signAndSendTransactions with SendTransactionOptions instead",
        replaceWith = ReplaceWith("signAndSendTransactions(transactions, SendTransactionOptions.Default)")
    )
    suspend fun signAndSendTransactions(
        transactions: List<ByteArray>,
        request: WalletRequest = SignTxRequest(purpose = "signAndSend")
    ): List<String> {
        val result = signAndSendTransactions(transactions, SendTransactionOptions.Default)
        return result.signatures
    }
}

