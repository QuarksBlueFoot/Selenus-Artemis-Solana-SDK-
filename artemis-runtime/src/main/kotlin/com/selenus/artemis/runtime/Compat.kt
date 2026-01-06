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
 * Factory function to mimic Account(secretKey) constructor.
 */
fun Account(secretKey: ByteArray): Account = Keypair.fromSecretKey(secretKey)

/**
 * Factory function to mimic Account(base58String) constructor (less common but supported in some forks).
 */
fun Account(base58SecretKey: String): Account = Keypair.fromSecretKey(Base58.decode(base58SecretKey))

/**
 * Extension property to expose secretKey as ByteArray, matching solana-kt.
 */
val Account.secretKey: ByteArray get() = this.secretKeyBytes()

