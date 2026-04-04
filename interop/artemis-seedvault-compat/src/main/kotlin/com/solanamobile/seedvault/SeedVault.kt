/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Drop-in replacement for com.solanamobile.seedvault.SeedVault.
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
    const val SEEDVAULT_MIN_API_INT = SeedVaultCheck.MIN_API_FOR_SEED_VAULT_PRIVILEGED

    enum class AccessType {
        NONE,
        STANDARD,
        PRIVILEGED;

        fun isGranted(): Boolean = this == STANDARD || this == PRIVILEGED
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
