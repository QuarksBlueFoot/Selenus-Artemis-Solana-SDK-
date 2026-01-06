package com.solanamobile.seedvault

import android.content.Context
import android.content.Intent
import com.selenus.artemis.seedvault.internal.SeedVaultCheck

object SeedVault {
    // Delegated to Artemis implementation
    const val MIN_API_FOR_SEED_VAULT_PRIVILEGED = SeedVaultCheck.MIN_API_FOR_SEED_VAULT_PRIVILEGED

    enum class AccessType {
        NONE,
        STANDARD,
        PRIVILEGED; // Matches internal enum structure implicitly 

        fun isGranted(): Boolean = this == STANDARD || this == PRIVILEGED
    }

    @JvmStatic
    @JvmOverloads
    fun isAvailable(context: Context, allowSimulated: Boolean = false): Boolean {
        return SeedVaultCheck.isAvailable(context, allowSimulated)
    }

    @JvmStatic
    fun getAccessType(context: Context): AccessType {
        // Map Artemis internal type to Compat type
        return when(SeedVaultCheck.getAccessType(context)) {
             SeedVaultCheck.AccessType.PRIVILEGED -> AccessType.PRIVILEGED
             SeedVaultCheck.AccessType.STANDARD -> AccessType.STANDARD
             else -> AccessType.NONE
        }
    }

    @JvmStatic
    fun resolveComponentForIntent(context: Context, intent: Intent) {
        SeedVaultCheck.resolveComponentForIntent(context, intent)
    }
}
