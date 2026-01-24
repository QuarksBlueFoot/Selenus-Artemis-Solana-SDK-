package com.selenus.artemis.rpc

import com.selenus.artemis.runtime.Pubkey
import kotlinx.serialization.json.*
import java.util.Base64

/**
 * AccountInfo - Typed wrapper for Solana account information.
 * 
 * Provides easy access to account properties returned by getAccountInfo RPC.
 * 
 * Usage:
 * ```kotlin
 * val rpc = RpcApi(client)
 * val accountInfo = rpc.getAccountInfoParsed(pubkey)
 * 
 * println("Owner: ${accountInfo.owner}")
 * println("Lamports: ${accountInfo.lamports}")
 * println("Data size: ${accountInfo.data.size}")
 * ```
 */
data class AccountInfo(
    /** The program that owns this account */
    val owner: Pubkey,
    /** Account balance in lamports */
    val lamports: Long,
    /** Raw account data as bytes */
    val data: ByteArray,
    /** Whether this account is executable (program) */
    val executable: Boolean,
    /** Epoch at which this account will next owe rent */
    val rentEpoch: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccountInfo) return false
        return owner == other.owner && lamports == other.lamports
    }
    
    override fun hashCode(): Int = owner.hashCode() + lamports.hashCode()
    
    /**
     * Check if account is owned by the System Program.
     */
    fun isSystemOwned(): Boolean = owner.toBase58() == "11111111111111111111111111111111"
    
    /**
     * Check if account is owned by the Token Program.
     */
    fun isTokenOwned(): Boolean = owner.toBase58() == "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    
    /**
     * Check if account is owned by the Token-2022 Program.
     */
    fun isToken2022Owned(): Boolean = owner.toBase58() == "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
    
    companion object {
        /**
         * Parse AccountInfo from RPC JSON response.
         */
        fun fromJson(json: JsonObject): AccountInfo? {
            val value = json["value"]
            if (value == null || value is JsonNull) return null
            
            val obj = value.jsonObject
            val ownerStr = obj["owner"]?.jsonPrimitive?.content ?: return null
            val lamports = obj["lamports"]?.jsonPrimitive?.long ?: 0L
            val executable = obj["executable"]?.jsonPrimitive?.boolean ?: false
            val rentEpoch = obj["rentEpoch"]?.jsonPrimitive?.long ?: 0L
            
            // Parse data - can be base64 array or parsed
            val data = parseData(obj["data"])
            
            return AccountInfo(
                owner = Pubkey.fromBase58(ownerStr),
                lamports = lamports,
                data = data,
                executable = executable,
                rentEpoch = rentEpoch
            )
        }
        
        private fun parseData(dataElement: JsonElement?): ByteArray {
            if (dataElement == null || dataElement is JsonNull) return ByteArray(0)
            
            return when (dataElement) {
                is JsonArray -> {
                    if (dataElement.isEmpty()) ByteArray(0)
                    else {
                        val b64 = dataElement[0].jsonPrimitive.content
                        Base64.getDecoder().decode(b64)
                    }
                }
                is JsonPrimitive -> {
                    Base64.getDecoder().decode(dataElement.content)
                }
                else -> ByteArray(0)
            }
        }
    }
}

/**
 * TokenAccountInfo - Typed wrapper for SPL Token account information.
 */
data class TokenAccountInfo(
    val mint: Pubkey,
    val owner: Pubkey,
    val amount: Long,
    val decimals: Int,
    val delegate: Pubkey?,
    val delegatedAmount: Long,
    val state: String,
    val isNative: Boolean,
    val closeAuthority: Pubkey?
) {
    companion object {
        /**
         * Parse from jsonParsed RPC response.
         */
        fun fromParsedJson(json: JsonObject): TokenAccountInfo? {
            val value = json["value"]
            if (value == null || value is JsonNull) return null
            
            val obj = value.jsonObject
            val data = obj["data"]?.jsonObject ?: return null
            val parsed = data["parsed"]?.jsonObject ?: return null
            val info = parsed["info"]?.jsonObject ?: return null
            
            val mintStr = info["mint"]?.jsonPrimitive?.content ?: return null
            val ownerStr = info["owner"]?.jsonPrimitive?.content ?: return null
            val tokenAmount = info["tokenAmount"]?.jsonObject
            val amount = tokenAmount?.get("amount")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val decimals = tokenAmount?.get("decimals")?.jsonPrimitive?.int ?: 0
            val state = info["state"]?.jsonPrimitive?.content ?: "initialized"
            val isNative = info["isNative"]?.jsonPrimitive?.boolean ?: false
            
            val delegateStr = info["delegate"]?.jsonPrimitive?.contentOrNull
            val delegatedAmountObj = info["delegatedAmount"]?.jsonObject
            val delegatedAmount = delegatedAmountObj?.get("amount")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val closeAuthorityStr = info["closeAuthority"]?.jsonPrimitive?.contentOrNull
            
            return TokenAccountInfo(
                mint = Pubkey.fromBase58(mintStr),
                owner = Pubkey.fromBase58(ownerStr),
                amount = amount,
                decimals = decimals,
                delegate = delegateStr?.let { Pubkey.fromBase58(it) },
                delegatedAmount = delegatedAmount,
                state = state,
                isNative = isNative,
                closeAuthority = closeAuthorityStr?.let { Pubkey.fromBase58(it) }
            )
        }
    }
}

/**
 * MintInfo - Typed wrapper for SPL Token mint information.
 */
data class MintInfo(
    val mintAuthority: Pubkey?,
    val supply: Long,
    val decimals: Int,
    val isInitialized: Boolean,
    val freezeAuthority: Pubkey?
) {
    companion object {
        /**
         * Parse from jsonParsed RPC response.
         */
        fun fromParsedJson(json: JsonObject): MintInfo? {
            val value = json["value"]
            if (value == null || value is JsonNull) return null
            
            val obj = value.jsonObject
            val data = obj["data"]?.jsonObject ?: return null
            val parsed = data["parsed"]?.jsonObject ?: return null
            val info = parsed["info"]?.jsonObject ?: return null
            
            val mintAuthorityStr = info["mintAuthority"]?.jsonPrimitive?.contentOrNull
            val supplyStr = info["supply"]?.jsonPrimitive?.content ?: "0"
            val decimals = info["decimals"]?.jsonPrimitive?.int ?: 0
            val isInitialized = info["isInitialized"]?.jsonPrimitive?.boolean ?: false
            val freezeAuthorityStr = info["freezeAuthority"]?.jsonPrimitive?.contentOrNull
            
            return MintInfo(
                mintAuthority = mintAuthorityStr?.let { Pubkey.fromBase58(it) },
                supply = supplyStr.toLongOrNull() ?: 0L,
                decimals = decimals,
                isInitialized = isInitialized,
                freezeAuthority = freezeAuthorityStr?.let { Pubkey.fromBase58(it) }
            )
        }
    }
}
