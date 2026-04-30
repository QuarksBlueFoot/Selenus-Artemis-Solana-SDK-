/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Source-compatible shim for com.solanamobile.seedvault.SeedVault.
 * Delegates to Artemis SeedVaultCheck for availability and access checks.
 */
package com.solanamobile.seedvault

import android.content.Context
import android.content.Intent
import com.selenus.artemis.seedvault.internal.SeedVaultCheck

/**
 * Compatibility shim for `com.solanamobile.seedvault.SeedVault`.
 *
 * Provides device-level Seed Vault availability and access checks.
 */
object SeedVault {
    /**
     * Minimum Android API level at which the privileged Seed Vault provider
     * is reachable. Upstream publishes this under the exact name
     * `MIN_API_FOR_SEED_VAULT_PRIVILEGED`; an alias is kept for the legacy
     * Artemis name [SEEDVAULT_MIN_API_INT] so both call sites compile.
     */
    const val MIN_API_FOR_SEED_VAULT_PRIVILEGED: Int = SeedVaultCheck.MIN_API_FOR_SEED_VAULT_PRIVILEGED

    /** Legacy alias for [MIN_API_FOR_SEED_VAULT_PRIVILEGED]. */
    const val SEEDVAULT_MIN_API_INT: Int = MIN_API_FOR_SEED_VAULT_PRIVILEGED

    enum class AccessType {
        NONE,
        STANDARD,
        PRIVILEGED,
        /** Seed Vault simulator present on non-SMS devices (dev builds). */
        SIMULATED;

        fun isGranted(): Boolean = this == STANDARD || this == PRIVILEGED || this == SIMULATED
    }

    @JvmStatic
    fun isAvailable(context: Context, allowSimulated: Boolean = false): Boolean =
        SeedVaultCheck.isAvailable(context, allowSimulated)

    @JvmStatic
    fun getAccessType(context: Context): AccessType {
        return when (SeedVaultCheck.getAccessType(context)) {
            SeedVaultCheck.AccessType.PRIVILEGED -> AccessType.PRIVILEGED
            SeedVaultCheck.AccessType.STANDARD -> AccessType.STANDARD
            else -> AccessType.NONE
        }
    }

    @JvmStatic
    fun resolveComponentForIntent(context: Context, intent: Intent) =
        SeedVaultCheck.resolveComponentForIntent(context, intent)
}
