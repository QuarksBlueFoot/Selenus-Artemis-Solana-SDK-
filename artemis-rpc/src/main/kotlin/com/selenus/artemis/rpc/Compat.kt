package com.selenus.artemis.rpc

/**
 * Compatibility shims for solana-kt enums/constants.
 */
object Commitment {
    const val FINALIZED = "finalized"
    const val CONFIRMED = "confirmed"
    const val PROCESSED = "processed"
}
