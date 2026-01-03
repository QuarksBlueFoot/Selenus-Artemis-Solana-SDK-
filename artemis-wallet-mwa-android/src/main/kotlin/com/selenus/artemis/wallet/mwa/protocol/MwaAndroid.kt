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

  fun launchWallet(activity: Activity, associationUri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, associationUri)
    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    activity.startActivity(intent)
  }

  fun randomEphemeralPort(): Int {
    // For local ws connections wallets expect a port we choose.
    // Use the dynamic/private range and avoid common ports.
    return 20000 + rng.nextInt(20000)
  }
}
