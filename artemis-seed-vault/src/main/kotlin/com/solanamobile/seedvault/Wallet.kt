package com.solanamobile.seedvault

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.selenus.artemis.seedvault.SeedVaultManager

@RequiresApi(api = Build.VERSION_CODES.R)
class Wallet private constructor() {

    class NotModifiedException(message: String) : Exception(message)
    class ActionFailedException(message: String) : Exception(message)

    companion object {
        @JvmStatic
        fun authorizeSeed(context: Context, @WalletContractV1.Purpose purpose: Int): Intent {
            // Uses Artemis SeedVaultManager to construct the correct Intent structure
            return SeedVaultManager(context).buildAuthorizeIntent(purpose.toString())
        }

        @JvmStatic
        @WalletContractV1.AuthToken
        @Throws(ActionFailedException::class)
        fun onAuthorizeSeedResult(resultCode: Int, result: Intent?): Long {
            // ... (Result parsing logic remains standard, but we could wrap it)
            if (resultCode != Activity.RESULT_OK) {
                throw ActionFailedException("authorizeSeed failed with result=$resultCode")
            }
            if (result == null) {
                throw ActionFailedException("authorizeSeed failed to return a result")
            }
            // Use common constant from Contract
            val authToken = result.getLongExtra(WalletContractV1.EXTRA_AUTH_TOKEN, -1)
            if (authToken == -1L) {
                throw ActionFailedException("authorizeSeed returned an invalid AuthToken")
            }
            return authToken
        }

        @JvmStatic
        fun createSeed(context: Context, @WalletContractV1.Purpose purpose: Int): Intent {
            return SeedVaultManager(context).buildCreateSeedIntent(purpose.toString())
        @JvmStatic
        @WalletContractV1.AuthToken
        @Throws(ActionFailedException::class)
        fun onCreateSeedResult(resultCode: Int, result: Intent?): Long {
             if (resultCode != Activity.RESULT_OK) {
                throw ActionFailedException("createSeed failed with result=$resultCode")
            }
            if (result == null) {
                throw ActionFailedException("createSeed failed to return a result")
            }
            
            val authToken = result.getLongExtra(WalletContractV1.EXTRA_AUTH_TOKEN, -1)
            if (authToken == -1L) {
                throw ActionFailedException("createSeed returned an invalid AuthToken")
            }
            return authToken
        }

        @JvmStatic
        fun importSeed(context: Context, @WalletContractV1.Purpose purpose: Int): Intent {
            return SeedVaultManager(context).buildImportSeedIntent(purpose.toString())
        }

        @JvmStatic
        @WalletContractV1.AuthToken
        @Throws(ActionFailedException::class)
        fun onImportSeedResult(resultCode: Int, result: Intent?): Long {
            if (resultCode != Activity.RESULT_OK) {
                throw ActionFailedException("importSeed failed with result=$resultCode")
            }
            if (result == null) {
                throw ActionFailedException("importSeed failed to return a result")
            }
            
            val authToken = result.getLongExtra(WalletContractV1.EXTRA_AUTH_TOKEN, -1)
            if (authToken == -1L) {
                throw ActionFailedException("importSeed returned an invalid AuthToken")
            }
            return authToken
        }
    }
}
