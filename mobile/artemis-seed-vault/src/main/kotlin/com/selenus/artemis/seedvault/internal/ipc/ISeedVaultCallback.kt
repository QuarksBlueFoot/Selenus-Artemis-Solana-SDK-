package com.selenus.artemis.seedvault.internal.ipc

import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

interface ISeedVaultCallback : IInterface {
    fun onResponse(response: Bundle)
    fun onError(error: Bundle)

    abstract class Stub : Binder(), ISeedVaultCallback {
        
        init {
            attachInterface(this, DESCRIPTOR)
        }

        companion object {
            const val DESCRIPTOR = "com.solanamobile.seedvault.ISeedVaultCallback"
            const val TRANSACTION_onResponse = IBinder.FIRST_CALL_TRANSACTION + 0
            const val TRANSACTION_onError = IBinder.FIRST_CALL_TRANSACTION + 1

            fun asInterface(obj: IBinder?): ISeedVaultCallback? {
                if (obj == null) return null
                val iin = obj.queryLocalInterface(DESCRIPTOR)
                if (iin != null && iin is ISeedVaultCallback) return iin
                return Proxy(obj)
            }
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }
                TRANSACTION_onResponse -> {
                    data.enforceInterface(DESCRIPTOR)
                    val bundle = if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data) else Bundle.EMPTY
                    onResponse(bundle)
                    return true
                }
                TRANSACTION_onError -> {
                    data.enforceInterface(DESCRIPTOR)
                    val bundle = if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data) else Bundle.EMPTY
                    onError(bundle)
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }

        private class Proxy(private val mRemote: IBinder) : ISeedVaultCallback {
            override fun asBinder(): IBinder = mRemote

            override fun onResponse(response: Bundle) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    if (response != null) {
                        data.writeInt(1)
                        response.writeToParcel(data, 0)
                    } else {
                        data.writeInt(0)
                    }
                    mRemote.transact(TRANSACTION_onResponse, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            override fun onError(error: Bundle) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    if (error != null) {
                        data.writeInt(1)
                        error.writeToParcel(data, 0)
                    } else {
                        data.writeInt(0)
                    }
                    mRemote.transact(TRANSACTION_onError, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }
        }
    }
}
