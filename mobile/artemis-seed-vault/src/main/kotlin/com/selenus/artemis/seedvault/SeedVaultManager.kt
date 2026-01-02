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
     * 
     * @param purpose One of the PURPOSE_* constants from SeedVaultConstants
     */
    fun buildAuthorizeIntent(purpose: Int = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION): Intent {
        val intent = Intent(ACTION_AUTHORIZE_SEED_ACCESS).apply {
            setPackage(SERVICE_PACKAGE)
            putExtra(SeedVaultConstants.EXTRA_PURPOSE, purpose)
        }
        resolveComponent(context, intent)
        return intent
    }

    /**
     * Creates an Intent to create a new seed in the Seed Vault.
     * NOTE: Uses implicit Intent (no package) per upstream contract.
     * 
     * @param purpose One of the PURPOSE_* constants from SeedVaultConstants
     */
    fun buildCreateSeedIntent(purpose: Int = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION): Intent {
        val intent = Intent(ACTION_CREATE_SEED).apply {
            putExtra(SeedVaultConstants.EXTRA_PURPOSE, purpose)
        }
        resolveComponent(context, intent)
        return intent
    }

    /**
     * Creates an Intent to import a seed into the Seed Vault.
     * NOTE: Uses implicit Intent (no package) per upstream contract.
     * 
     * @param purpose One of the PURPOSE_* constants from SeedVaultConstants
     */
    fun buildImportSeedIntent(purpose: Int = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION): Intent {
        val intent = Intent(ACTION_IMPORT_SEED).apply {
            putExtra(SeedVaultConstants.EXTRA_PURPOSE, purpose)
        }
        resolveComponent(context, intent)
        return intent
    }

    /**
     * Creates an Intent to sign transactions with the Seed Vault.
     * 
     * @param authToken The authorization token from authorize/createSeed/importSeed
     * @param signingRequests The list of signing requests
     */
    fun buildSignTransactionsIntent(
        authToken: Long,
        signingRequests: ArrayList<android.os.Parcelable>
    ): Intent {
        val intent = Intent(SeedVaultConstants.ACTION_SIGN_TRANSACTION).apply {
            putExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken)
            putParcelableArrayListExtra(SeedVaultConstants.EXTRA_SIGNING_REQUEST, signingRequests)
        }
        resolveComponent(context, intent)
        return intent
    }

    /**
     * Creates an Intent to sign messages with the Seed Vault.
     */
    fun buildSignMessagesIntent(
        authToken: Long,
        signingRequests: ArrayList<android.os.Parcelable>
    ): Intent {
        val intent = Intent(SeedVaultConstants.ACTION_SIGN_MESSAGE).apply {
            putExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken)
            putParcelableArrayListExtra(SeedVaultConstants.EXTRA_SIGNING_REQUEST, signingRequests)
        }
        resolveComponent(context, intent)
        return intent
    }

    /**
     * Creates an Intent to request public keys for derivation paths.
     */
    fun buildGetPublicKeysIntent(
        authToken: Long,
        derivationPaths: ArrayList<android.net.Uri>
    ): Intent {
        val intent = Intent(SeedVaultConstants.ACTION_GET_PUBLIC_KEY).apply {
            putExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken)
            putParcelableArrayListExtra(SeedVaultConstants.EXTRA_DERIVATION_PATH, derivationPaths)
        }
        resolveComponent(context, intent)
        return intent
    }

    /**
     * Creates an Intent to show seed settings. Requires privileged access.
     */
    fun buildSeedSettingsIntent(authToken: Long): Intent {
        val intent = Intent(SeedVaultConstants.ACTION_SEED_SETTINGS).apply {
            putExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken)
        }
        resolveComponent(context, intent)
        return intent
    }

    /**
     * Parses the result Intent from an Authorize/Create/Import action.
     * Returns the auth token and basic account info.
     */
    fun parseAuthorizationResult(data: Intent): SeedVaultAuthorization {
        val token = data.getLongExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, -1L)
        if (token == -1L) throw SeedVaultException.Unknown("Invalid auth token in result")
        
        val accountId = data.getLongExtra(SeedVaultConstants.EXTRA_ACCOUNT_ID, 0L)
        
        // Account details should be fetched separately via getAccounts() after authorization
        val dummyKey = Pubkey(ByteArray(32))
        return SeedVaultAuthorization(token.toString(), SeedVaultAccount(accountId, "Authorized Account", dummyKey)) 
    }

    /**
     * Parses signing response from an onActivityResult for sign transactions/messages.
     * @return List of SigningResponse objects
     */
    fun parseSigningResult(data: Intent): List<SigningResponse> {
        val responses = data.getParcelableArrayListExtra<SigningResponse>(SeedVaultConstants.EXTRA_SIGNING_RESPONSE)
        return responses ?: emptyList()
    }

    /**
     * Parses public key response from an onActivityResult for get public key.
     * @return List of PublicKeyResponse objects
     */
    fun parsePublicKeyResult(data: Intent): List<PublicKeyResponse> {
        val keys = data.getParcelableArrayListExtra<PublicKeyResponse>(SeedVaultConstants.EXTRA_PUBLIC_KEY)
        return keys ?: emptyList()
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
     * Request public keys for specific derivation paths via IPC.
     * 
     * This is the Seed Vault equivalent of HD wallet key derivation.
     * @param authToken The authorization token from authorize/createSeed/importSeed
     * @param derivationPaths List of BIP32/BIP44 derivation path URIs
     * @return List of public keys corresponding to each derivation path
     */
    suspend fun requestPublicKeys(authToken: String, derivationPaths: List<android.net.Uri>): List<Pubkey> {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toLongOrNull() ?: -1L)
            putParcelableArrayList(SeedVaultConstants.EXTRA_DERIVATION_PATH, ArrayList(derivationPaths))
        }
        val response = performAction("requestPublicKeys", params)
        
        val keys = response.getParcelableArrayList<Bundle>(SeedVaultConstants.EXTRA_PUBLIC_KEY)
            ?: throw SeedVaultException.Unknown("No public keys returned")
            
        return keys.map { bundle ->
            val raw = bundle.getByteArray("public_key_raw")
                ?: throw SeedVaultException.Unknown("Missing public key bytes")
            Pubkey(raw)
        }
    }

    /**
     * Sign payloads using a specific derivation path via IPC.
     *
     * This allows signing with keys derived from arbitrary BIP32 paths,
     * not just the default account.
     * @param authToken The authorization token
     * @param derivationPath The BIP32 derivation path URI for the signing key
     * @param payloads List of messages/transactions to sign
     * @return List of signatures
     */
    suspend fun signWithDerivationPath(
        authToken: String, 
        derivationPath: android.net.Uri, 
        payloads: List<ByteArray>
    ): List<ByteArray> {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toLongOrNull() ?: -1L)
            putParcelable(SeedVaultConstants.EXTRA_DERIVATION_PATH, derivationPath)
            putSerializable(SeedVaultConstants.KEY_PAYLOADS, ArrayList(payloads))
        }
        
        val response = performAction("signWithDerivationPath", params)
        
        val sigs = response.getSerializable(SeedVaultConstants.KEY_SIGNATURES) as? ArrayList<ByteArray>
            ?: throw SeedVaultException.Unknown("No signatures returned")
            
        return sigs
    }

    /**
     * Resolve a BIP32 derivation path for a specific auth token.
     * This uses the content provider's call() method per the upstream SDK contract.
     * 
     * @param authToken The authorization token
     * @param derivationPath The BIP32 derivation path URI to resolve
     * @return The resolved public key
     */
    suspend fun resolveDerivationPath(authToken: String, derivationPath: android.net.Uri): Pubkey {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toLongOrNull() ?: -1L)
            putParcelable(SeedVaultConstants.EXTRA_DERIVATION_PATH, derivationPath)
        }
        
        val response = performAction("resolveDerivationPath", params)
        
        val keyBytes = response.getByteArray(SeedVaultConstants.EXTRA_PUBLIC_KEY)
            ?: throw SeedVaultException.Unknown("No public key returned from resolve")
            
        return Pubkey(keyBytes)
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
                "createSeed" -> service!!.createSeed(params, callback)
                "importSeed" -> service!!.importSeed(params, callback)
                "updateSeed" -> service!!.updateSeed(params, callback)
                "getAccounts" -> service!!.getAccounts(params, callback)
                "resolveDerivationPath" -> service!!.resolveDerivationPath(params, callback)
                "signTransactions" -> service!!.signTransactions(params, callback)
                "signMessages" -> service!!.signMessages(params, callback)
                "deauthorize" -> service!!.deauthorize(params, callback)
                "requestPublicKeys" -> service!!.resolveDerivationPath(params, callback)
                "signWithDerivationPath" -> service!!.signTransactions(params, callback)
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

