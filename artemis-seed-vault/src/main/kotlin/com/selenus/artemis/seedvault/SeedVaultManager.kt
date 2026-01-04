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

/**
 * SeedVaultManager
 *
 * A modern, Coroutine-first client for the Solana Seed Vault.
 * Handles service binding, lifecycle, and IPC with the system service.
 */
class SeedVaultManager(private val context: Context) {

    companion object {
        private const val SERVICE_PACKAGE = "com.solanamobile.seedvault"
        private const val SERVICE_CLASS = "com.solanamobile.seedvault.SeedVaultService"
        private const val ACTION_BIND_SEED_VAULT = "com.solanamobile.seedvault.BIND_SEED_VAULT"
    }

    // We use a raw IBinder here because we can't compile the AIDL in this environment.
    // In a real Android build, this would be ISeedVaultService.Stub.asInterface(service)
    private var serviceBinder: IBinder? = null

    // Placeholder for the generated AIDL interface
    // private var service: ISeedVaultService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service
            // service = ISeedVaultService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
            // service = null
        }
    }

    suspend fun connect() = suspendCancellableCoroutine<Unit> { cont ->
        val intent = Intent(ACTION_BIND_SEED_VAULT).apply {
            setPackage(SERVICE_PACKAGE)
        }
        
        val bound = context.bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                serviceBinder = service
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceBinder = null
            }
        }, Context.BIND_AUTO_CREATE)

        if (!bound) {
            cont.resumeWithException(IllegalStateException("Could not bind to Seed Vault Service"))
        }
        
        cont.invokeOnCancellation {
            context.unbindService(connection)
        }
    }

    fun disconnect() {
        try {
            context.unbindService(connection)
        } catch (e: Exception) {
            // Ignore if already unbound
        }
        serviceBinder = null
    }

    /**
     * Authorize the app to access the Seed Vault.
     */
    suspend fun authorize(purpose: String = "sign_transaction"): SeedVaultAuthorization {
        val params = Bundle().apply {
            putString("purpose", purpose)
        }
        val response = performAction("authorize", params)
        
        val token = response.getString(SeedVaultKeys.AUTH_TOKEN) 
            ?: throw SeedVaultException.Unknown("No auth token returned")
        val accountBundle = response.getParcelable<Bundle>("account") 
            ?: throw SeedVaultException.Unknown("No account returned")
            
        return SeedVaultAuthorization(token, SeedVaultAccount.fromBundle(accountBundle))
    }

    suspend fun getAccounts(authToken: String): List<SeedVaultAccount> {
        val params = Bundle().apply {
            putString(SeedVaultKeys.AUTH_TOKEN, authToken)
        }
        val response = performAction("getAccounts", params)
        
        val list = response.getParcelableArrayList<Bundle>(SeedVaultKeys.ACCOUNTS) 
            ?: return emptyList()
            
        return list.map { SeedVaultAccount.fromBundle(it) }
    }

    suspend fun signTransactions(authToken: String, transactions: List<ByteArray>): List<ByteArray> {
        val params = Bundle().apply {
            putString(SeedVaultKeys.AUTH_TOKEN, authToken)
            putSerializable(SeedVaultKeys.PAYLOADS, ArrayList(transactions))
        }
        val response = performAction("signTransactions", params)
        
        val sigs = response.getSerializable(SeedVaultKeys.SIGNATURES) as? ArrayList<ByteArray>
            ?: throw SeedVaultException.Unknown("No signatures returned")
            
        return sigs
    }

    suspend fun signMessages(authToken: String, messages: List<ByteArray>): List<ByteArray> {
        val params = Bundle().apply {
            putString(SeedVaultKeys.AUTH_TOKEN, authToken)
            putSerializable(SeedVaultKeys.PAYLOADS, ArrayList(messages))
        }
        val response = performAction("signMessages", params)
        
        val sigs = response.getSerializable(SeedVaultKeys.SIGNATURES) as? ArrayList<ByteArray>
            ?: throw SeedVaultException.Unknown("No signatures returned")
            
        return sigs
    }

    suspend fun deauthorize(authToken: String) {
        val params = Bundle().apply {
            putString(SeedVaultKeys.AUTH_TOKEN, authToken)
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

