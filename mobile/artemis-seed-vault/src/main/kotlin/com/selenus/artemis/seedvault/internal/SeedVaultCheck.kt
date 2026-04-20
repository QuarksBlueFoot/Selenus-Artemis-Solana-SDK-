package com.selenus.artemis.seedvault.internal

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi

object SeedVaultCheck {
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    const val MIN_API_FOR_SEED_VAULT_PRIVILEGED = Build.VERSION_CODES.TIRAMISU

    enum class AccessType {
        NONE,
        STANDARD,
        PRIVILEGED;

        fun isGranted(): Boolean = this == STANDARD || this == PRIVILEGED
    }

    @JvmStatic
    @JvmOverloads
    fun isAvailable(context: Context, allowSimulated: Boolean = false): Boolean {
        val pm = context.packageManager
        val pi = try {
            pm.getPackageInfo(SeedVaultConstants.PACKAGE_SEED_VAULT, PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES)
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }

        if (allowSimulated) return true

        pi.permissions?.forEach { permission ->
            if (SeedVaultConstants.PERMISSION_SEED_VAULT_IMPL == permission.name) {
                if (!usesSignatureProtection(permission)) return false
                if (hasPrivilegedProtectionFlag(permission)) return true
                
                // Android/Signature check fallback
                val androidPi = try {
                    pm.getPackageInfo("android", PackageManager.GET_SIGNATURES)
                } catch (e: PackageManager.NameNotFoundException) {
                    return false
                }
                
                return androidPi.applicationInfo?.uid == Process.SYSTEM_UID &&
                        pi.signatures?.firstOrNull() == androidPi.signatures?.firstOrNull()
            }
        }
        return false
    }

    @JvmStatic
    fun getAccessType(context: Context): AccessType {
        return if (context.checkSelfPermission(SeedVaultConstants.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED) == PackageManager.PERMISSION_GRANTED) {
            AccessType.PRIVILEGED
        } else if (context.checkSelfPermission(SeedVaultConstants.PERMISSION_ACCESS_SEED_VAULT) == PackageManager.PERMISSION_GRANTED) {
            AccessType.STANDARD
        } else {
            AccessType.NONE
        }
    }

    private fun usesSignatureProtection(permission: PermissionInfo): Boolean {
        return (permission.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_SIGNATURE
    }

    private fun hasPrivilegedProtectionFlag(permission: PermissionInfo): Boolean {
        val privilegedFlag = permission.protectionLevel and 
                PermissionInfo.PROTECTION_MASK_BASE.inv() and 
                PermissionInfo.PROTECTION_FLAG_PRIVILEGED
        return privilegedFlag != 0
    }

    @JvmStatic
    @SuppressLint("InlinedApi")
    fun resolveComponentForIntent(context: Context, intent: Intent) {
        require(intent.component == null) { "component should not be set prior to resolution" }

        val accessType = getAccessType(context)
        val heldPermission = when (accessType) {
            AccessType.STANDARD -> SeedVaultConstants.PERMISSION_ACCESS_SEED_VAULT
            AccessType.PRIVILEGED -> SeedVaultConstants.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED
            else -> throw IllegalStateException("No access to Seed Vault; callers must hold either permission")
        }

        val resolved = context.packageManager.queryIntentActivities(intent, 0)

        for (ri in resolved) {
            if (heldPermission == ri.activityInfo.permission &&
                isTrustedProvider(context, ri.activityInfo.packageName)
            ) {
                intent.setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                return
            }
        }

        throw IllegalStateException("No trusted target activity found for ${intent.action}")
    }

    /**
     * Confirm that [packageName] is a trusted Seed Vault provider. A provider
     * qualifies if it is signed by the platform's `android` package or appears
     * in the explicit [TRUSTED_PROVIDERS] allowlist (simulator + known OEM
     * builds). Any intent resolving to an arbitrary third-party package is
     * rejected even if it happens to declare the right permission.
     */
    @JvmStatic
    @SuppressLint("PackageManagerGetSignatures")
    fun isTrustedProvider(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        if (packageName in TRUSTED_PROVIDERS) return true
        val providerPi = try {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        val androidPi = try {
            pm.getPackageInfo("android", PackageManager.GET_SIGNATURES)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        val providerSig = providerPi.signatures?.firstOrNull() ?: return false
        val platformSig = androidPi.signatures?.firstOrNull() ?: return false
        return providerSig == platformSig
    }

    /**
     * Allowlist of provider packages Artemis trusts without a platform
     * signature match. The official Seed Vault simulator ships as
     * `com.solanamobile.seedvault.simulator` and is not signed with the
     * platform certificate; tests and dev builds need it explicitly.
     */
    @JvmField
    val TRUSTED_PROVIDERS: Set<String> = setOf(
        SeedVaultConstants.PACKAGE_SEED_VAULT,
        "com.solanamobile.seedvault.simulator"
    )
}
