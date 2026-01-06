package com.solanamobile.seedvault

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringDef
import androidx.annotation.IntDef
import com.selenus.artemis.seedvault.internal.SeedVaultConstants

object WalletContractV1 {
    const val PACKAGE_SEED_VAULT = SeedVaultConstants.PACKAGE_SEED_VAULT

    const val PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED = SeedVaultConstants.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED
    const val PERMISSION_ACCESS_SEED_VAULT = SeedVaultConstants.PERMISSION_ACCESS_SEED_VAULT
    const val PERMISSION_SEED_VAULT_IMPL = SeedVaultConstants.PERMISSION_SEED_VAULT_IMPL

    const val AUTHORITY_WALLET = SeedVaultConstants.AUTHORITY_WALLET

    const val ACTION_AUTHORIZE_SEED_ACCESS = SeedVaultConstants.ACTION_AUTHORIZE_SEED_ACCESS
    const val ACTION_SIGN_TRANSACTION = SeedVaultConstants.ACTION_SIGN_TRANSACTION
    const val ACTION_SIGN_MESSAGE = SeedVaultConstants.ACTION_SIGN_MESSAGE
    const val ACTION_CREATE_SEED = SeedVaultConstants.ACTION_CREATE_SEED
    const val ACTION_IMPORT_SEED = SeedVaultConstants.ACTION_IMPORT_SEED
    const val ACTION_GET_ACCOUNTS = SeedVaultConstants.ACTION_GET_ACCOUNTS

    const val EXTRA_PURPOSE = SeedVaultConstants.EXTRA_PURPOSE
    const val EXTRA_AUTH_TOKEN = SeedVaultConstants.EXTRA_AUTH_TOKEN
    const val EXTRA_ACCOUNT_ID = SeedVaultConstants.EXTRA_ACCOUNT_ID
    const val EXTRA_ACCOUNTS = SeedVaultConstants.EXTRA_ACCOUNTS
    const val EXTRA_SIGNING_REQUEST = SeedVaultConstants.EXTRA_SIGNING_REQUEST
    const val EXTRA_SIGNING_RESPONSE = SeedVaultConstants.EXTRA_SIGNING_RESPONSE

    const val PURPOSE_SIGN_SOLANA_TRANSACTION = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION

    const val RESULT_INVALID_PURPOSE = SeedVaultConstants.RESULT_INVALID_PURPOSE
    const val RESULT_NO_AVAILABLE_SEEDS = SeedVaultConstants.RESULT_NO_AVAILABLE_SEEDS
    const val RESULT_AUTHENTICATION_FAILED = SeedVaultConstants.RESULT_AUTHENTICATION_FAILED
    const val RESULT_INVALID_AUTH_TOKEN = SeedVaultConstants.RESULT_INVALID_AUTH_TOKEN
    const val RESULT_INVALID_PAYLOAD = SeedVaultConstants.RESULT_INVALID_PAYLOAD
    const val RESULT_INVALID_DERIVATION_PATH = SeedVaultConstants.RESULT_INVALID_DERIVATION_PATH
    const val RESULT_IMPLEMENTATION_LIMIT_EXCEEDED = SeedVaultConstants.RESULT_IMPLEMENTATION_LIMIT_EXCEEDED

    const val BIP32_URI_SCHEME = SeedVaultConstants.BIP32_URI_SCHEME
    const val BIP44_URI_SCHEME = SeedVaultConstants.BIP44_URI_SCHEME

    const val PERMISSIONED_BIP44_ACCOUNT = 0
    const val PERMISSIONED_BIP44_CHANGE = 0

    // Typedefs (Optional in Kotlin but good for docs)
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
    annotation class Purpose

    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
    annotation class AuthToken

    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
    annotation class AccountId
}
