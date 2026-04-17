/*
 * Drop-in source compatibility re-exports for the public Seed Vault SDK types.
 *
 * The Solana Mobile Seed Vault SDK declares its data classes in the
 * `com.solanamobile.seedvault` package. Artemis uses its own equivalent types
 * under `com.selenus.artemis.seedvault`. This file re-publishes the upstream
 * names as Kotlin typealiases so any existing application can import
 * `com.solanamobile.seedvault.SigningRequest` / `SigningResponse` /
 * `PublicKeyResponse` / `Bip32DerivationPath` / `Bip44DerivationPath`
 * unchanged and still get the Artemis implementation underneath.
 */
package com.solanamobile.seedvault

/** Drop-in alias for the upstream `SigningRequest`. */
typealias SigningRequest = com.selenus.artemis.seedvault.SigningRequest

/** Drop-in alias for the upstream `SigningResponse`. */
typealias SigningResponse = com.selenus.artemis.seedvault.SigningResponse

/** Drop-in alias for the upstream `PublicKeyResponse`. */
typealias PublicKeyResponse = com.selenus.artemis.seedvault.PublicKeyResponse

/** Drop-in alias for the upstream `Bip32DerivationPath`. */
typealias Bip32DerivationPath = com.selenus.artemis.seedvault.Bip32DerivationPath

/** Drop-in alias for the upstream `Bip44DerivationPath`. */
typealias Bip44DerivationPath = com.selenus.artemis.seedvault.Bip44DerivationPath

/** Drop-in alias for the upstream `BipLevel`. */
typealias BipLevel = com.selenus.artemis.seedvault.BipLevel
