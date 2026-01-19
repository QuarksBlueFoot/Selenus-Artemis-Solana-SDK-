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

/**
 * Extension property to get the secret key as a Base58 string.
 * 
 * Useful for serialization/display of secret keys.
 */
val Keypair.secretKeyBase58: String get() = this.secretKeyBytes().toBase58()

/**
 * Extension property to get the 64-byte full secret key (seed + pubkey) as Base58.
 * 
 * Matches the format used by Solana CLI's keypair files.
 */
val Keypair.fullSecretKeyBase58: String get() = (this.secretKeyBytes() + this.publicKey.bytes).toBase58()

/**
 * Creates a Keypair from a Base58-encoded 32-byte seed.
 */
fun Keypair.Companion.fromSeedBase58(seedBase58: String): Keypair = fromSeed(Base58.decode(seedBase58))

/**
 * Creates a Keypair from a Base58-encoded 64-byte secret key.
 */
fun Keypair.Companion.fromSecretKeyBase58(secretKeyBase58: String): Keypair = fromSecretKey(Base58.decode(secretKeyBase58))

