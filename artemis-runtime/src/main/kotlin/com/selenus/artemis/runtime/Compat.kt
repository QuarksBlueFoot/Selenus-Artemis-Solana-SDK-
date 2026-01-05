package com.selenus.artemis.runtime

/**
 * Compatibility aliases for solana-kt users.
 */
typealias PublicKey = Pubkey
typealias Account = Keypair

/**
 * Factory function to mimic Account() constructor (generates new keypair).
 */
fun Account(): Account = Keypair.generate()

/**
 * Extension property to expose secretKey as ByteArray, matching solana-kt.
 */
val Account.secretKey: ByteArray get() = this.secretKeyBytes()

