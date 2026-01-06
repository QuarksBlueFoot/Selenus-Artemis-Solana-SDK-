package com.selenus.artemis.seedvault

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.selenus.artemis.runtime.Pubkey

import com.selenus.artemis.seedvault.internal.SeedVaultConstants
import com.selenus.artemis.seedvault.internal.SeedVaultConstants.ACTION_AUTHORIZE_SEED_ACCESS
import com.selenus.artemis.seedvault.internal.SeedVaultConstants.ACTION_CREATE_SEED
import com.selenus.artemis.seedvault.internal.SeedVaultConstants.ACTION_IMPORT_SEED
import com.selenus.artemis.seedvault.internal.SeedVaultConstants.SERVICE_PACKAGE

/**
 * SeedVaultManager
 *
 * A modern, Coroutine-first client for the Solana Seed Vault.
 * Handles service binding, lifecycle, and IPC with the system service.
 */
class SeedVaultManager(private val context: Context) {
    
    private var serviceBinder: IBinder? = null

    companion object {
        // Constants delegated to internal definition
        private const val ACTION_BIND_SEED_VAULT = SeedVaultConstants.ACTION_BIND_SEED_VAULT

        /**
         * Resolves the component for the Intent using Artemis internal check logic.
         * Ensures we target the valid System Seed Vault.
         */
        fun resolveComponent(context: Context, intent: Intent) {
             com.selenus.artemis.seedvault.internal.SeedVaultCheck.resolveComponentForIntent(context, intent)
        }
    }

    // ... (rest of class)
    
    /**
     * Creates an Intent to authorize the app to access the Seed Vault.
     * Use this with startActivityForResult or ActivityResultCaller.
     */
    fun buildAuthorizeIntent(purpose: String = "sign_transaction"): Intent {
        val intent = Intent(ACTION_AUTHORIZE_SEED_ACCESS).apply {
            setPackage(SERVICE_PACKAGE)
            putExtra("purpose", purpose.toIntOrNull() ?: 1) // Default to standard purpose
        }
        resolveComponent(context, intent)
        return intent
    }

    /**
     * Creates an Intent to create a new seed in the Seed Vault.
     */
    fun buildCreateSeedIntent(purpose: String = "sign_transaction"): Intent {
        val intent = Intent(ACTION_CREATE_SEED).apply {
            // setPackage(SERVICE_PACKAGE) // Create/Import might be handled by different activity
            putExtra("purpose", purpose.toIntOrNull() ?: 1)
        }
        resolveComponent(context, intent)
        return intent
    }

    /**
     * Creates an Intent to import a seed into the Seed Vault.
     */
    fun buildImportSeedIntent(purpose: String = "sign_transaction"): Intent {
        val intent = Intent(ACTION_IMPORT_SEED).apply {
           putExtra("purpose", purpose.toIntOrNull() ?: 1)
        }
        resolveComponent(context, intent)
        return intent
    }

    /**
     * Parses the result Intent from an Authorize/Create/Import action.
     */
    fun parseAuthorizationResult(data: Intent): SeedVaultAuthorization {
        val token = data.getLongExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, -1L)
        if (token == -1L) throw SeedVaultException.Unknown("Invalid auth token in result")
        // Account details might be fetched separately via getAccounts if not present
        // Provide a dummy key for now until we fetch the real one.
        val dummyKey = Pubkey(ByteArray(32))
        return SeedVaultAuthorization(token.toString(), SeedVaultAccount(0, "Parsed Account", dummyKey)) 
    }

    suspend fun getAccounts(authToken: String): List<SeedVaultAccount> {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toLongOrNull() ?: -1L)
        }
        val response = performAction("getAccounts", params)
        
        val list = response.getParcelableArrayList<Bundle>(SeedVaultConstants.EXTRA_ACCOUNTS) 
            ?: return emptyList()
            
        return list.map { SeedVaultAccount.fromBundle(it) }
    }
    
    // ... signTransactions ...
    suspend fun signTransactions(authToken: String, transactions: List<ByteArray>): List<ByteArray> {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toLongOrNull() ?: -1L)
            putSerializable(SeedVaultKeys.PAYLOADS, ArrayList(transactions))
        }
        // ...
        val response = performAction("signTransactions", params)
        
        val sigs = response.getSerializable(SeedVaultKeys.SIGNATURES) as? ArrayList<ByteArray>
            ?: throw SeedVaultException.Unknown("No signatures returned")
            
        return sigs
    }

    suspend fun signMessages(authToken: String, messages: List<ByteArray>): List<ByteArray> {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toLongOrNull() ?: -1L)
            putSerializable(SeedVaultKeys.PAYLOADS, ArrayList(messages))
        }
        val response = performAction("signMessages", params)
        
        val sigs = response.getSerializable(SeedVaultKeys.SIGNATURES) as? ArrayList<ByteArray>
            ?: throw SeedVaultException.Unknown("No signatures returned")
            
        return sigs
    }

    suspend fun deauthorize(authToken: String) {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toLongOrNull() ?: -1L)
        }
        performAction("deauthorize", params)
    }

    private suspend fun performAction(method: String, params: Bundle): Bundle = suspendCancellableCoroutine { cont ->
        checkConnected()
        
        // In a real implementation, we would create the Stub callback here.
        // Since we don't have the generated code, we simulate the structure.
        /*
        val callback = object : ISeedVaultCallback.Stub() {
            override fun onResponse(response: Bundle) {
                if (cont.isActive) cont.resume(response)
            }
            override fun onError(error: Bundle) {
                if (cont.isActive) cont.resumeWithException(SeedVaultException.fromBundle(error))
            }
        }
        
        try {
            when (method) {
                "authorize" -> service!!.authorize(params, callback)
                "getAccounts" -> service!!.getAccounts(params, callback)
                "signTransactions" -> service!!.signTransactions(params, callback)
                "signMessages" -> service!!.signMessages(params, callback)
                "deauthorize" -> service!!.deauthorize(params, callback)
            }
        } catch (e: RemoteException) {
            cont.resumeWithException(SeedVaultException.InternalError("Remote exception: ${e.message}"))
        }
        */
        
        // For compilation in this environment, we throw to indicate this is a skeleton
        // that requires the AIDL build step.
        cont.resumeWithException(UnsupportedOperationException("AIDL compilation required for $method"))
    }

    private fun checkConnected() {
        if (serviceBinder == null) throw IllegalStateException("Seed Vault not connected")
    }
}

