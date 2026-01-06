package com.solana.mobilewalletadapter.common.signin

import android.net.Uri

object SignInWithSolana {
    data class Payload(
        val domain: String,
        val uri: Uri? = null,
        val statement: String? = null,
        val resources: List<String>? = null,
        val version: String? = "1",
        val chainId: String? = "solana:mainnet",
        val nonce: String? = null,
        val issuedAt: String? = null,
        val expirationTime: String? = null,
        val notBefore: String? = null,
        val requestId: String? = null
    )
}
