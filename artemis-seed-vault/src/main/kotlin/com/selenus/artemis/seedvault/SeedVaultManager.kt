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
import com.selenus.artemis.seedvault.internal.ipc.ISeedVaultService
import com.selenus.artemis.seedvault.internal.ipc.ISeedVaultCallback

/**
 * SeedVaultManager
 *
 * A modern, Coroutine-first client for the Solana Seed Vault.
 * Handles service binding, lifecycle, and IPC with the system service.
 */
class SeedVaultManager(private val context: Context) {
    
    private var serviceBinder: IBinder? = null
    private var service: ISeedVaultService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceBinder = binder
            if (binder != null) {
                service = ISeedVaultService.Stub.asInterface(binder)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
            service = null
            isBound = false
        }
    }

    companion object {
        private const val ACTION_BIND_SEED_VAULT = SeedVaultConstants.ACTION_BIND_SEED_VAULT

        /**
         * Resolves the component for the Intent using Artemis internal check logic.
         * Ensures we target the valid System Seed Vault.
         */
        fun resolveComponent(context: Context, intent: Intent) {
             com.selenus.artemis.seedvault.internal.SeedVaultCheck.resolveComponentForIntent(context, intent)
        }
    }

    /**
     * Connects to the Seed Vault system service.
     * This must be called before performing any privileged actions.
     */
    fun connect() {
        if (isBound) return
        val intent = Intent(ACTION_BIND_SEED_VAULT).apply {
            setPackage(SeedVaultConstants.PACKAGE_SEED_VAULT)
        }
        resolveComponent(context, intent)
        isBound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Disconnects from the Seed Vault service.
     * Should be called when the manager is no longer needed (e.g. onDestroy).
     */
    fun disconnect() {
        if (isBound) {
            context.unbindService(connection)
            isBound = false
            service = null
            serviceBinder = null
        }
    }
    
    /**
     * Creates an Intent to authorize the app to access the Seed Vault.
     * Use this with startActivityForResult or ActivityResultCaller.
     */
    fun buildAuthorizeIntent(purpose: String = "sign_transaction"): Intent {
        val intent = Intent(ACTION_AUTHORIZE_SEED_ACCESS).apply {
            setPackage(SERVICE_PACKAGE)
            putExtra("purpose", purpose.toIntOrNull() ?: SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION)
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
            putExtra("purpose", purpose.toIntOrNull() ?: SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION)
        }
        resolveComponent(context, intent)
        return intent
    }

    /**
     * Creates an Intent to import a seed into the Seed Vault.
     */
    fun buildImportSeedIntent(purpose: String = "sign_transaction"): Intent {
        val intent = Intent(ACTION_IMPORT_SEED).apply {
           putExtra("purpose", purpose.toIntOrNull() ?: SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION)
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
        
        // Try to parse basic account info if available in extras, otherwise use placeholder
        val accountId = data.getLongExtra(SeedVaultConstants.EXTRA_ACCOUNT_ID, 0L)
        
        // Account details might be fetched separately via getAccounts if not present
        // Provide a dummy key for now until we fetch the real one. 
        val dummyKey = Pubkey(ByteArray(32))
        return SeedVaultAuthorization(token.toString(), SeedVaultAccount(accountId, "Parsed Account", dummyKey)) 
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
    
    suspend fun signTransactions(authToken: String, transactions: List<ByteArray>): List<ByteArray> {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toLongOrNull() ?: -1L)
            putSerializable(SeedVaultConstants.KEY_PAYLOADS, ArrayList(transactions))
        }
        
        val response = performAction("signTransactions", params)
        
        val sigs = response.getSerializable(SeedVaultConstants.KEY_SIGNATURES) as? ArrayList<ByteArray>
            ?: throw SeedVaultException.Unknown("No signatures returned")
            
        return sigs
    }

    suspend fun signMessages(authToken: String, messages: List<ByteArray>): List<ByteArray> {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toLongOrNull() ?: -1L)
            putSerializable(SeedVaultConstants.KEY_PAYLOADS, ArrayList(messages))
        }
        val response = performAction("signMessages", params)
        
        val sigs = response.getSerializable(SeedVaultConstants.KEY_SIGNATURES) as? ArrayList<ByteArray>
            ?: throw SeedVaultException.Unknown("No signatures returned")
            
        return sigs
    }

    suspend fun deauthorize(authToken: String) {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toLongOrNull() ?: -1L)
        }
        performAction("deauthorize", params)
    }

    /**
     * Request public keys for specific derivation paths.
     * 
     * This is the Seed Vault equivalent of HD wallet key derivation.
     * @param authToken The authorization token from authorize/createSeed/importSeed
     * @param derivationPaths List of BIP32/BIP44 derivation paths (e.g. "m/44'/501'/0'/0'")
     * @return List of public keys corresponding to each derivation path
     */
    suspend fun requestPublicKeys(authToken: String, derivationPaths: List<String>): List<Pubkey> {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toLongOrNull() ?: -1L)
            putStringArrayList("derivation_paths", ArrayList(derivationPaths))
        }
        val response = performAction("requestPublicKeys", params)
        
        val keys = response.getSerializable("public_keys") as? ArrayList<ByteArray>
            ?: throw SeedVaultException.Unknown("No public keys returned")
            
        return keys.map { Pubkey(it) }
    }

    /**
     * Sign payloads using a specific derivation path.
     *
     * This allows signing with keys derived from arbitrary BIP32 paths,
     * not just the default account.
     * @param authToken The authorization token
     * @param derivationPath The BIP32 derivation path for the signing key
     * @param payloads List of messages/transactions to sign
     * @return List of signatures
     */
    suspend fun signWithDerivationPath(
        authToken: String, 
        derivationPath: String, 
        payloads: List<ByteArray>
    ): List<ByteArray> {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toLongOrNull() ?: -1L)
            putString("derivation_path", derivationPath)
            putSerializable(SeedVaultConstants.KEY_PAYLOADS, ArrayList(payloads))
        }
        
        val response = performAction("signWithDerivationPath", params)
        
        val sigs = response.getSerializable(SeedVaultConstants.KEY_SIGNATURES) as? ArrayList<ByteArray>
            ?: throw SeedVaultException.Unknown("No signatures returned")
            
        return sigs
    }

    private suspend fun performAction(method: String, params: Bundle): Bundle = suspendCancellableCoroutine { cont ->
        try {
            checkConnected()
            
            // Define the callback
            val callback = object : ISeedVaultCallback.Stub() {
                override fun onResponse(response: Bundle) {
                    if (cont.isActive) cont.resume(response)
                }
                override fun onError(error: Bundle) {
                    if (cont.isActive) cont.resumeWithException(SeedVaultException.fromBundle(error))
                }
            }
            
            when (method) {
                "authorize" -> service!!.authorize(params, callback)
                "getAccounts" -> service!!.getAccounts(params, callback)
                "signTransactions" -> service!!.signTransactions(params, callback)
                "signMessages" -> service!!.signMessages(params, callback)
                "deauthorize" -> service!!.deauthorize(params, callback)
                else -> {
                    if (cont.isActive) cont.resumeWithException(IllegalArgumentException("Unknown method: $method"))
                }
            }
        } catch (e: RemoteException) {
            if (cont.isActive) {
                cont.resumeWithException(SeedVaultException.InternalError("Remote exception: ${e.message}"))
            }
        } catch (e: Exception) {
             if (cont.isActive) {
                cont.resumeWithException(SeedVaultException.InternalError("Unexpected exception: ${e.message}"))
            }
        }
    }

    private fun checkConnected() {
        if (service == null) throw IllegalStateException("Seed Vault not connected. Call connect() first.")
    }
}

