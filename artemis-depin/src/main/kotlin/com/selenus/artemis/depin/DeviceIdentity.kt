package com.selenus.artemis.depin

import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Crypto
import java.security.SecureRandom
import java.util.Base64

/**
 * DeviceIdentity
 *
 * Represents a cryptographic identity for a physical device (Edge Node).
 * This is distinct from a user wallet. It is used to sign telemetry,
 * prove location, or authenticate the device to a DePIN network.
 *
 * In a real Android implementation, this should wrap the Android Keystore system.
 * For this SDK core, we provide a pure Kotlin implementation that can be backed by file storage.
 */
class DeviceIdentity(
  val keypair: Keypair
) {

  val publicKey: Pubkey get() = keypair.publicKey

  /**
   * Sign a telemetry payload.
   * Returns the signature as a Base64 string.
   */
  fun signTelemetry(payload: ByteArray): String {
    val sig = keypair.sign(payload)
    return Base64.getEncoder().encodeToString(sig)
  }

  /**
   * Create a "Proof of Location" payload.
   *
   * @param lat Latitude (e.g. 37.7749)
   * @param lng Longitude (e.g. -122.4194)
   * @param timestamp Epoch seconds
   */
  fun createLocationProof(lat: Double, lng: Double, timestamp: Long): LocationProof {
    // Canonical serialization: "lat:lng:timestamp"
    val message = "$lat:$lng:$timestamp"
    val signature = signTelemetry(message.encodeToByteArray())
    return LocationProof(
      device = publicKey,
      latitude = lat,
      longitude = lng,
      timestamp = timestamp,
      signature = signature
    )
  }

  companion object {
    fun generate(): DeviceIdentity {
      return DeviceIdentity(Keypair.generate())
    }

    fun fromSecretKey(secretKey: ByteArray): DeviceIdentity {
      return DeviceIdentity(Keypair.fromSeed(secretKey))
    }
  }
}

data class LocationProof(
  val device: Pubkey,
  val latitude: Double,
  val longitude: Double,
  val timestamp: Long,
  val signature: String
)
