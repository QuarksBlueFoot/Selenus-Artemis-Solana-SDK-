package com.selenus.artemis.reactnative

import android.net.Uri
import com.facebook.react.bridge.*
import com.selenus.artemis.wallet.mwa.MwaWalletAdapter
import com.selenus.artemis.wallet.mwa.InMemoryAuthTokenStore
import com.selenus.artemis.wallet.WalletRequest
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcClientConfig
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.programs.SystemProgram
import com.selenus.artemis.tx.Transaction
import com.selenus.artemis.depin.DeviceIdentity
import com.selenus.artemis.solanapay.SolanaPayUri
import com.selenus.artemis.gaming.MerkleDistributor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Base64
import java.math.BigDecimal

import com.selenus.artemis.wallet.mwa.protocol.MwaSignInPayload

import android.app.Activity
import android.content.Intent
import com.facebook.react.bridge.*
import com.selenus.artemis.seedvault.SeedVaultManager
import com.selenus.artemis.seedvault.SeedVaultAuthorization

class ArtemisModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), ActivityEventListener {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private var adapter: MwaWalletAdapter? = null
    private var rpcClient: JsonRpcClient? = null
    
    // Seed Vault
    private var seedVaultManager: SeedVaultManager? = null
    private var seedVaultPromise: Promise? = null
    private val SEED_VAULT_REQUEST_CODE = 8675 // Jenny

    init {
        reactContext.addActivityEventListener(this)
        seedVaultManager = SeedVaultManager(reactContext)
    }

    override fun onActivityResult(activity: Activity?, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SEED_VAULT_REQUEST_CODE) {
            val promise = seedVaultPromise ?: return
            seedVaultPromise = null // Consume
            
            if (resultCode == Activity.RESULT_OK && data != null) {
                try {
                    val result = seedVaultManager?.parseAuthorizationResult(data)
                    val map = Arguments.createMap()
                    map.putString("authToken", result?.authToken)
                    promise.resolve(map)
                } catch (e: Exception) {
                    promise.reject("SEED_VAULT_ERROR", e.message, e)
                }
            } else {
                promise.reject("SEED_VAULT_CANCELLED", "User cancelled or operation failed")
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        // No-op
    }
    
    // Store DePIN identities in memory
    private val deviceIdentities = mutableMapOf<String, DeviceIdentity>()

    override fun getName(): String {
        return "ArtemisModule"
    }

    @ReactMethod
    fun initialize(identityUri: String, iconPath: String, identityName: String, chain: String) {
        val activity = currentActivity ?: return
        adapter = MwaWalletAdapter(
            activity = activity,
            identityUri = Uri.parse(identityUri),
            iconPath = iconPath,
            identityName = identityName,
            chain = chain,
            authStore = InMemoryAuthTokenStore()
        )
    }

    @ReactMethod
    fun setRpcUrl(url: String) {
        rpcClient = JsonRpcClient(RpcClientConfig(url))
    }

    @ReactMethod
    fun connect(promise: Promise) {
        scope.launch {
            mutex.withLock {
                try {
                    val adapter = adapter ?: throw IllegalStateException("Not initialized")
                    val pubkey = adapter.connect()
                    promise.resolve(pubkey.toBase58())
                } catch (e: Exception) {
                    promise.reject("CONNECT_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun signTransaction(base64Tx: String, promise: Promise) {
        scope.launch {
            mutex.withLock {
                try {
                    val adapter = adapter ?: throw IllegalStateException("Not initialized")
                    val txBytes = Base64.getDecoder().decode(base64Tx)
                    val signed = adapter.signMessage(txBytes, WalletRequest())
                    promise.resolve(Base64.getEncoder().encodeToString(signed))
                } catch (e: Exception) {
                    promise.reject("SIGN_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun signAndSendTransaction(base64Tx: String, promise: Promise) {
        scope.launch {
            mutex.withLock {
                try {
                    val adapter = adapter ?: throw IllegalStateException("Not initialized")
                    val txBytes = Base64.getDecoder().decode(base64Tx)
                    val sigs = adapter.signAndSendTransactions(listOf(txBytes), WalletRequest())
                    if (sigs.isNotEmpty()) {
                        promise.resolve(sigs[0])
                    } else {
                        promise.reject("SEND_ERROR", "No signature returned")
                    }
                } catch (e: Exception) {
                    promise.reject("SEND_ERROR", e.message, e)
                }
            }
        }
    }
    
    @ReactMethod
    fun signMessage(base64Msg: String, promise: Promise) {
        scope.launch {
            mutex.withLock {
                try {
                    val adapter = adapter ?: throw IllegalStateException("Not initialized")
                    val msgBytes = Base64.getDecoder().decode(base64Msg)
                    val signed = adapter.signArbitraryMessage(msgBytes, WalletRequest())
                    promise.resolve(Base64.getEncoder().encodeToString(signed))
                } catch (e: Exception) {
                    promise.reject("SIGN_MSG_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun connectWithSignIn(payload: ReadableMap, promise: Promise) {
        scope.launch {
            mutex.withLock {
                try {
                    val adapter = adapter ?: throw IllegalStateException("Not initialized")
                    
                    // Parse ReadableMap to MwaSignInPayload
                    val signInPayload = MwaSignInPayload(
                        domain = payload.getString("domain")!!,
                        uri = if (payload.hasKey("uri")) payload.getString("uri") else null,
                        statement = if (payload.hasKey("statement")) payload.getString("statement") else null,
                        resources = if (payload.hasKey("resources")) {
                            val list = payload.getArray("resources")!!
                            (0 until list.size()).map { list.getString(it) }
                        } else null,
                        chainId = if (payload.hasKey("chainId")) payload.getString("chainId") else null
                    )

                    val result = adapter.connectWithSignIn(signInPayload)
                    val map = Arguments.createMap()
                    map.putString("address", result.address)
                    map.putString("signedMessage", result.signedMessage)
                    map.putString("signature", result.signature)
                    map.putString("signatureType", result.signatureType)
                    
                    promise.resolve(map)
                } catch (e: Exception) {
                    promise.reject("SIGN_IN_ERROR", e.message, e)
                }
            }
        }
    }

    // --- RPC Methods ---

    @ReactMethod
    fun getBalance(pubkeyStr: String, promise: Promise) {
        scope.launch {
            try {
                val client = rpcClient ?: JsonRpcClient(RpcClientConfig("https://api.mainnet-beta.solana.com"))
                val balance = client.getBalance(Pubkey.fromBase58(pubkeyStr))
                promise.resolve(balance.toString())
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getLatestBlockhash(promise: Promise) {
        scope.launch {
            try {
                val client = rpcClient ?: JsonRpcClient(RpcClientConfig("https://api.mainnet-beta.solana.com"))
                val blockhash = client.getLatestBlockhash()
                promise.resolve(blockhash)
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    // --- Program Methods ---

    @ReactMethod
    fun buildTransferTransaction(fromStr: String, toStr: String, lamports: String, blockhash: String, promise: Promise) {
        try {
            val from = Pubkey.fromBase58(fromStr)
            val to = Pubkey.fromBase58(toStr)
            val amount = lamports.toLong()
            
            val ix = SystemProgram.transfer(from, to, amount)
            val tx = Transaction(from, blockhash, listOf(ix))
            val msg = tx.compileMessage()
            val bytes = msg.serialize()
            
            promise.resolve(Base64.getEncoder().encodeToString(bytes))
        } catch (e: Exception) {
            promise.reject("BUILD_TX_ERROR", e.message, e)
        }
    }

    // --- DePIN Methods ---

    @ReactMethod
    fun generateDeviceIdentity(promise: Promise) {
        try {
            val device = DeviceIdentity.generate()
            val pubkey = device.publicKey.toBase58()
            deviceIdentities[pubkey] = device
            promise.resolve(pubkey)
        } catch (e: Exception) {
            promise.reject("DEPIN_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun signLocationProof(devicePubkey: String, lat: Double, lng: Double, timestamp: Double, promise: Promise) {
        try {
            val device = deviceIdentities[devicePubkey] ?: throw IllegalArgumentException("Device not found")
            val proof = device.createLocationProof(lat, lng, timestamp.toLong())
            promise.resolve(proof.signature)
        } catch (e: Exception) {
            promise.reject("DEPIN_ERROR", e.message, e)
        }
    }

    // --- Solana Pay Methods ---

    @ReactMethod
    fun buildSolanaPayUri(recipientStr: String, amountStr: String, label: String, message: String, promise: Promise) {
        try {
            val recipient = Pubkey.fromBase58(recipientStr)
            val amount = BigDecimal(amountStr)
            
            val uri = SolanaPayUri(
                recipient = recipient,
                amount = amount,
                label = label,
                message = message
            )
            promise.resolve(uri.toString())
        } catch (e: Exception) {
            promise.reject("SOLANA_PAY_ERROR", e.message, e)
        }
    }

    // --- Seed Vault Methods (Wallet App Only) ---

    @ReactMethod
    fun seedVaultAuthorize(purpose: String, promise: Promise) {
        val activity = currentActivity
        if (activity == null) {
            promise.reject("ACTIVITY_ERROR", "Activity not available")
            return
        }
        if (seedVaultPromise != null) {
            promise.reject("CONCURRENT_ERROR", "Action already in progress")
            return
        }
        
        seedVaultPromise = promise
        try {
            val intent = seedVaultManager?.buildAuthorizeIntent(purpose)
            activity.startActivityForResult(intent, SEED_VAULT_REQUEST_CODE)
        } catch (e: Exception) {
            seedVaultPromise = null
            promise.reject("LAUNCH_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun seedVaultCreateSeed(purpose: String, promise: Promise) {
        val activity = currentActivity
        if (activity == null) {
            promise.reject("ACTIVITY_ERROR", "Activity not available")
            return
        }
        if (seedVaultPromise != null) {
            promise.reject("CONCURRENT_ERROR", "Action already in progress")
            return
        }
        
        seedVaultPromise = promise
        try {
            val intent = seedVaultManager?.buildCreateSeedIntent(purpose)
            activity.startActivityForResult(intent, SEED_VAULT_REQUEST_CODE)
        } catch (e: Exception) {
            seedVaultPromise = null
            promise.reject("LAUNCH_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun seedVaultImportSeed(purpose: String, promise: Promise) {
        val activity = currentActivity
        if (activity == null) {
            promise.reject("ACTIVITY_ERROR", "Activity not available")
            return
        }
        if (seedVaultPromise != null) {
            promise.reject("CONCURRENT_ERROR", "Action already in progress")
            return
        }
        
        seedVaultPromise = promise
        try {
            val intent = seedVaultManager?.buildImportSeedIntent(purpose)
            activity.startActivityForResult(intent, SEED_VAULT_REQUEST_CODE)
        } catch (e: Exception) {
            seedVaultPromise = null
            promise.reject("LAUNCH_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun seedVaultGetAccounts(authToken: String, promise: Promise) {
        scope.launch {
            try {
                val accounts = seedVaultManager?.getAccounts(authToken) ?: emptyList()
                val array = Arguments.createArray()
                accounts.forEach { 
                    val map = Arguments.createMap()
                    map.putInt("accountId", it.accountId.toInt())
                    map.putString("name", it.name)
                    array.pushMap(map)
                }
                promise.resolve(array)
            } catch (e: Exception) {
                promise.reject("SEED_VAULT_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun seedVaultSignMessages(authToken: String, messages: ReadableArray, promise: Promise) {
         scope.launch {
            try {
                val msgs = ArrayList<ByteArray>()
                for (i in 0 until messages.size()) {
                    val b64 = messages.getString(i)
                    msgs.add(Base64.getDecoder().decode(b64))
                }
                
                val sigs = seedVaultManager?.signMessages(authToken, msgs) ?: emptyList()
                
                val array = Arguments.createArray()
                sigs.forEach { 
                    array.pushString(Base64.getEncoder().encodeToString(it))
                }
                promise.resolve(array)
            } catch (e: Exception) {
                promise.reject("SEED_VAULT_ERROR", e.message, e)
            }
        }
    }
            val req = SolanaPayUri.Request(
                recipient = Pubkey.fromBase58(recipientStr),
                amount = BigDecimal(amountStr),
                label = label,
                message = message
            )
            val uri = SolanaPayUri.build(req)
            promise.resolve(uri)
        } catch (e: Exception) {
            promise.reject("SOLANA_PAY_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun parseSolanaPayUri(uri: String, promise: Promise) {
        try {
            val req = SolanaPayUri.parse(uri)
            val map = Arguments.createMap()
            map.putString("recipient", req.recipient.toBase58())
            req.amount?.let { map.putString("amount", it.toPlainString()) }
            req.label?.let { map.putString("label", it) }
            req.message?.let { map.putString("message", it) }
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("SOLANA_PAY_ERROR", e.message, e)
        }
    }

    // --- Gaming Methods ---

    @ReactMethod
    fun verifyMerkleProof(proofArr: ReadableArray, rootStr: String, leafStr: String, promise: Promise) {
        try {
            val proof = ArrayList<ByteArray>()
            for (i in 0 until proofArr.size()) {
                val b64 = proofArr.getString(i) ?: continue
                proof.add(Base64.getDecoder().decode(b64))
            }
            
            val root = Base64.getDecoder().decode(rootStr)
            val leaf = Base64.getDecoder().decode(leafStr)
            
            val valid = MerkleDistributor.verify(proof, root, leaf)
            promise.resolve(valid)
        } catch (e: Exception) {
            promise.reject("GAMING_ERROR", e.message, e)
        }
    }
}
