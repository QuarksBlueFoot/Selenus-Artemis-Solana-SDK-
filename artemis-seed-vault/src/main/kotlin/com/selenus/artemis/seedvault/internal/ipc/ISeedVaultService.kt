package com.selenus.artemis.seedvault.internal.ipc

import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

interface ISeedVaultService : IInterface {
    
    // Core methods matching AIDL definition
    fun authorize(params: Bundle, callback: ISeedVaultCallback)
    fun createSeed(params: Bundle, callback: ISeedVaultCallback)
    fun importSeed(params: Bundle, callback: ISeedVaultCallback)
    fun updateSeed(params: Bundle, callback: ISeedVaultCallback)
    fun getAccounts(params: Bundle, callback: ISeedVaultCallback)
    fun resolveDerivationPath(params: Bundle, callback: ISeedVaultCallback)
    fun signTransactions(params: Bundle, callback: ISeedVaultCallback)
    fun signMessages(params: Bundle, callback: ISeedVaultCallback)
    fun deauthorize(params: Bundle, callback: ISeedVaultCallback)

    abstract class Stub : Binder(), ISeedVaultService {
        companion object {
            const val DESCRIPTOR = "com.solanamobile.seedvault.ISeedVaultService"
            
            // Transaction IDs (Must match AIDL generated order)
            const val TRANSACT_authorize = IBinder.FIRST_CALL_TRANSACTION + 0
            const val TRANSACT_createSeed = IBinder.FIRST_CALL_TRANSACTION + 1
            const val TRANSACT_importSeed = IBinder.FIRST_CALL_TRANSACTION + 2
            const val TRANSACT_updateSeed = IBinder.FIRST_CALL_TRANSACTION + 3
            const val TRANSACT_getAccounts = IBinder.FIRST_CALL_TRANSACTION + 4
            const val TRANSACT_resolveDerivationPath = IBinder.FIRST_CALL_TRANSACTION + 5
            const val TRANSACT_signTransactions = IBinder.FIRST_CALL_TRANSACTION + 6
            const val TRANSACT_signMessages = IBinder.FIRST_CALL_TRANSACTION + 7
            const val TRANSACT_deauthorize = IBinder.FIRST_CALL_TRANSACTION + 8

            fun asInterface(obj: IBinder?): ISeedVaultService? {
                if (obj == null) return null
                val iin = obj.queryLocalInterface(DESCRIPTOR)
                if (iin != null && iin is ISeedVaultService) return iin
                return Proxy(obj)
            }
        }

        override fun asBinder(): IBinder = this
        
        // We only implement Proxy as we consume this service, we don't host it.
        // But Stub is needed for asInterface.
        
        private class Proxy(private val mRemote: IBinder) : ISeedVaultService {
            override fun asBinder(): IBinder = mRemote

            private fun callVoidMethod(code: Int, params: Bundle, callback: ISeedVaultCallback) {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    if (params != null) {
                        data.writeInt(1)
                        params.writeToParcel(data, 0)
                    } else {
                        data.writeInt(0)
                    }
                    data.writeStrongBinder(callback.asBinder())
                    mRemote.transact(code, data, reply, 0)
                    reply.readException()
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun authorize(params: Bundle, callback: ISeedVaultCallback) {
                callVoidMethod(TRANSACT_authorize, params, callback)
            }

            override fun createSeed(params: Bundle, callback: ISeedVaultCallback) {
                callVoidMethod(TRANSACT_createSeed, params, callback)
            }

            override fun importSeed(params: Bundle, callback: ISeedVaultCallback) {
                callVoidMethod(TRANSACT_importSeed, params, callback)
            }

            override fun updateSeed(params: Bundle, callback: ISeedVaultCallback) {
                callVoidMethod(TRANSACT_updateSeed, params, callback)
            }

            override fun getAccounts(params: Bundle, callback: ISeedVaultCallback) {
                callVoidMethod(TRANSACT_getAccounts, params, callback)
            }

            override fun resolveDerivationPath(params: Bundle, callback: ISeedVaultCallback) {
               callVoidMethod(TRANSACT_resolveDerivationPath, params, callback)
            }

            override fun signTransactions(params: Bundle, callback: ISeedVaultCallback) {
               callVoidMethod(TRANSACT_signTransactions, params, callback)
            }

            override fun signMessages(params: Bundle, callback: ISeedVaultCallback) {
                callVoidMethod(TRANSACT_signMessages, params, callback)
            }

            override fun deauthorize(params: Bundle, callback: ISeedVaultCallback) {
                callVoidMethod(TRANSACT_deauthorize, params, callback)
            }
        }
    }
}
