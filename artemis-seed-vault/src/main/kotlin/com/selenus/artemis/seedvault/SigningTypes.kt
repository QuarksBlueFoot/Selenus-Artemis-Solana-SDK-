/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * SigningRequest/SigningResponse - Full parity with Solana Mobile Seed Vault SDK v0.4.0.
 * 
 * These Parcelable types match the upstream IPC protocol for transaction/message signing.
 * Each SigningRequest pairs a payload with one or more derivation paths for multi-signature support.
 */
package com.selenus.artemis.seedvault

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

/**
 * A request to sign a payload with one or more derivation paths.
 * 
 * Each signing request contains:
 * - A payload (transaction bytes or message bytes)
 * - A list of derivation path URIs (for multi-signature support)
 * 
 * Compatible with com.solanamobile.seedvault.SigningRequest from the upstream SDK.
 */
data class SigningRequest(
    val payload: ByteArray,
    val requestedSignatures: ArrayList<Uri>
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.createByteArray() ?: byteArrayOf(),
        ArrayList<Uri>().also { parcel.readTypedList(it, Uri.CREATOR) }
    )
    
    /**
     * Create a signing request for a single derivation path.
     */
    constructor(payload: ByteArray, derivationPath: Uri) : this(
        payload,
        arrayListOf(derivationPath)
    )
    
    /**
     * Create a signing request from BipDerivationPath objects.
     */
    constructor(payload: ByteArray, paths: List<BipDerivationPath>) : this(
        payload,
        ArrayList(paths.map { it.toUri() })
    )
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByteArray(payload)
        parcel.writeTypedList(requestedSignatures)
    }
    
    override fun describeContents(): Int = 0
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SigningRequest) return false
        return payload.contentEquals(other.payload) &&
               requestedSignatures == other.requestedSignatures
    }
    
    override fun hashCode(): Int {
        var result = payload.contentHashCode()
        result = 31 * result + requestedSignatures.hashCode()
        return result
    }
    
    companion object CREATOR : Parcelable.Creator<SigningRequest> {
        override fun createFromParcel(parcel: Parcel): SigningRequest = SigningRequest(parcel)
        override fun newArray(size: Int): Array<SigningRequest?> = arrayOfNulls(size)
    }
}

/**
 * Response from a signing operation.
 * 
 * Each signing response contains:
 * - A list of signatures (one per requested derivation path)
 * - The resolved derivation paths (BIP44 paths resolved to BIP32)
 * 
 * Compatible with com.solanamobile.seedvault.SigningResponse from the upstream SDK.
 */
data class SigningResponse(
    val signatures: ArrayList<ByteArray>,
    val resolvedDerivationPaths: ArrayList<Uri>
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        ArrayList<ByteArray>().also {
            val size = parcel.readInt()
            for (i in 0 until size) {
                it.add(parcel.createByteArray() ?: byteArrayOf())
            }
        },
        ArrayList<Uri>().also { parcel.readTypedList(it, Uri.CREATOR) }
    )
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(signatures.size)
        for (sig in signatures) {
            parcel.writeByteArray(sig)
        }
        parcel.writeTypedList(resolvedDerivationPaths)
    }
    
    override fun describeContents(): Int = 0
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SigningResponse) return false
        if (signatures.size != other.signatures.size) return false
        return signatures.indices.all { signatures[it].contentEquals(other.signatures[it]) } &&
               resolvedDerivationPaths == other.resolvedDerivationPaths
    }
    
    override fun hashCode(): Int {
        var result = signatures.fold(0) { acc, sig -> 31 * acc + sig.contentHashCode() }
        result = 31 * result + resolvedDerivationPaths.hashCode()
        return result
    }
    
    companion object CREATOR : Parcelable.Creator<SigningResponse> {
        override fun createFromParcel(parcel: Parcel): SigningResponse = SigningResponse(parcel)
        override fun newArray(size: Int): Array<SigningResponse?> = arrayOfNulls(size)
    }
}

/**
 * Public key response from requestPublicKey(s).
 * 
 * Contains the resolved derivation path and the corresponding public key.
 */
data class PublicKeyResponse(
    val resolvedDerivationPath: Uri,
    val publicKeyRaw: ByteArray,
    val publicKeyEncoded: String
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readParcelable(Uri::class.java.classLoader) ?: Uri.EMPTY,
        parcel.createByteArray() ?: byteArrayOf(),
        parcel.readString() ?: ""
    )
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(resolvedDerivationPath, flags)
        parcel.writeByteArray(publicKeyRaw)
        parcel.writeString(publicKeyEncoded)
    }
    
    override fun describeContents(): Int = 0
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicKeyResponse) return false
        return resolvedDerivationPath == other.resolvedDerivationPath &&
               publicKeyRaw.contentEquals(other.publicKeyRaw) &&
               publicKeyEncoded == other.publicKeyEncoded
    }
    
    override fun hashCode(): Int {
        var result = resolvedDerivationPath.hashCode()
        result = 31 * result + publicKeyRaw.contentHashCode()
        result = 31 * result + publicKeyEncoded.hashCode()
        return result
    }
    
    companion object CREATOR : Parcelable.Creator<PublicKeyResponse> {
        override fun createFromParcel(parcel: Parcel): PublicKeyResponse = PublicKeyResponse(parcel)
        override fun newArray(size: Int): Array<PublicKeyResponse?> = arrayOfNulls(size)
    }
}

/**
 * Seed metadata returned from content provider queries.
 */
data class AuthorizedSeed(
    val authToken: Long,
    val authPurpose: Int,
    val seedName: String
) {
    companion object {
        const val PURPOSE_SIGN_SOLANA_TRANSACTION = 0
    }
}

/**
 * Account metadata from content provider queries.
 */
data class SeedVaultAccountInfo(
    val accountId: Long,
    val bip32DerivationPath: Uri,
    val publicKeyRaw: ByteArray,
    val publicKeyEncoded: String,
    val accountName: String,
    val isUserWallet: Boolean,
    val isValid: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SeedVaultAccountInfo) return false
        return accountId == other.accountId &&
               bip32DerivationPath == other.bip32DerivationPath &&
               publicKeyRaw.contentEquals(other.publicKeyRaw) &&
               publicKeyEncoded == other.publicKeyEncoded &&
               accountName == other.accountName &&
               isUserWallet == other.isUserWallet &&
               isValid == other.isValid
    }
    
    override fun hashCode(): Int {
        var result = accountId.hashCode()
        result = 31 * result + bip32DerivationPath.hashCode()
        result = 31 * result + publicKeyRaw.contentHashCode()
        result = 31 * result + publicKeyEncoded.hashCode()
        result = 31 * result + accountName.hashCode()
        result = 31 * result + isUserWallet.hashCode()
        result = 31 * result + isValid.hashCode()
        return result
    }
}

/**
 * Implementation limits from the Seed Vault provider.
 */
data class ImplementationLimits(
    val maxSigningRequests: Int,
    val maxRequestedSignatures: Int,
    val maxRequestedPublicKeys: Int
) {
    companion object {
        /** Minimum values all implementations must support */
        const val MIN_SIGNING_REQUESTS = 3
        const val MIN_REQUESTED_SIGNATURES = 3
        const val MIN_REQUESTED_PUBLIC_KEYS = 10
        
        val DEFAULT = ImplementationLimits(
            maxSigningRequests = MIN_SIGNING_REQUESTS,
            maxRequestedSignatures = MIN_REQUESTED_SIGNATURES,
            maxRequestedPublicKeys = MIN_REQUESTED_PUBLIC_KEYS
        )
    }
}
