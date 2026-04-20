package com.selenus.artemis.wallet.mwa.protocol

import android.app.Activity
import android.content.Intent
import android.net.Uri
import java.security.KeyPair
import java.security.SecureRandom

internal object MwaAndroid {
  private val rng = SecureRandom()

  fun generateAssociationKeypair(): KeyPair = EcP256.generateKeypair()

  fun associationToken(associationKeypair: KeyPair): String {
    // association token is base64url of X9.62 uncompressed public key point (Qa)
    return Base64Url.encode(EcP256.x962Uncompressed(associationKeypair.public))
  }

  fun buildLocalUri(port: Int, associationKeypair: KeyPair, protocolVersionMajor: Int = 2): Uri {
    val assoc = associationToken(associationKeypair)
    return Uri.parse(
      "solana-wallet:/v1/associate/local?association=$assoc&port=$port&v=$protocolVersionMajor"
    )
  }

  /**
   * Build an endpoint-specific association URI. When a wallet returns
   * `wallet_uri_base` from authorize, subsequent sessions can launch that
   * exact wallet (instead of falling through Android's wallet chooser) by
   * appending the association params to the wallet's base URI.
   *
   * Per MWA 2.0 spec, `wallet_uri_base` is an absolute URI. The returned
   * URI preserves its scheme, authority and path segments and appends the
   * `/v1/associate/local` + query params that the wallet knows how to
   * handle.
   */
  fun buildEndpointSpecificUri(
    walletUriBase: String,
    port: Int,
    associationKeypair: KeyPair,
    protocolVersionMajor: Int = 2
  ): Uri {
    val baseUri = Uri.parse(walletUriBase)
    require(baseUri.scheme != null) { "wallet_uri_base must be an absolute URI, got: $walletUriBase" }
    val assoc = associationToken(associationKeypair)
    return baseUri.buildUpon()
      .appendEncodedPath("v1")
      .appendEncodedPath("associate")
      .appendEncodedPath("local")
      .appendQueryParameter("association", assoc)
      .appendQueryParameter("port", port.toString())
      .appendQueryParameter("v", protocolVersionMajor.toString())
      .build()
  }

  fun launchWallet(activity: Activity, associationUri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, associationUri)
    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    activity.startActivity(intent)
  }
}
