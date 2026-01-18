import Foundation
import CryptoKit

/**
 * Artemis Solana SDK - iOS Native Module for React Native
 * 
 * Provides Base58 encoding/decoding, cryptographic operations, and Solana utilities
 * matching the Android Kotlin implementation for cross-platform parity.
 */
@objc(ArtemisModule)
class ArtemisModule: NSObject {
  
  // MARK: - Base58 Encoding/Decoding
  
  private static let base58Alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
  private static let base58AlphabetArray = Array(base58Alphabet)
  private static var charToIndex: [Character: Int] = {
    var map = [Character: Int]()
    for (index, char) in base58Alphabet.enumerated() {
      map[char] = index
    }
    return map
  }()
  
  /// Encode bytes to Base58 string
  @objc
  static func base58Encode(_ data: Data) -> String {
    if data.isEmpty { return "" }
    
    var bytes = [UInt8](data)
    var leadingZeros = 0
    while leadingZeros < bytes.count && bytes[leadingZeros] == 0 {
      leadingZeros += 1
    }
    
    var encoded = [Character]()
    var startAt = leadingZeros
    
    while startAt < bytes.count {
      var carry = 0
      for i in startAt..<bytes.count {
        let digit = Int(bytes[i])
        let combined = carry * 256 + digit
        bytes[i] = UInt8(combined / 58)
        carry = combined % 58
      }
      encoded.append(base58AlphabetArray[carry])
      while startAt < bytes.count && bytes[startAt] == 0 {
        startAt += 1
      }
    }
    
    // Add leading '1's for leading zeros
    for _ in 0..<leadingZeros {
      encoded.append("1")
    }
    
    return String(encoded.reversed())
  }
  
  /// Decode Base58 string to bytes
  @objc
  static func base58Decode(_ input: String) -> Data? {
    if input.isEmpty { return Data() }
    
    var digits = [Int]()
    for char in input {
      guard let index = charToIndex[char] else {
        return nil // Invalid character
      }
      digits.append(index)
    }
    
    var leadingOnes = 0
    while leadingOnes < digits.count && digits[leadingOnes] == 0 {
      leadingOnes += 1
    }
    
    var decoded = [UInt8]()
    var startAt = leadingOnes
    
    while startAt < digits.count {
      var carry = 0
      for i in startAt..<digits.count {
        let combined = carry * 58 + digits[i]
        digits[i] = combined / 256
        carry = combined % 256
      }
      decoded.append(UInt8(carry))
      while startAt < digits.count && digits[startAt] == 0 {
        startAt += 1
      }
    }
    
    // Construct result: leading zeros + decoded bytes (reversed)
    var result = [UInt8](repeating: 0, count: leadingOnes)
    result.append(contentsOf: decoded.reversed())
    return Data(result)
  }
  
  /// Validate if string is valid Base58
  @objc
  static func isValidBase58(_ input: String) -> Bool {
    return input.allSatisfy { charToIndex[$0] != nil }
  }
  
  /// Check if string is a valid Solana pubkey (32 bytes when decoded)
  @objc
  static func isValidSolanaPubkey(_ input: String) -> Bool {
    guard let decoded = base58Decode(input) else { return false }
    return decoded.count == 32
  }
  
  /// Check if string is a valid Solana signature (64 bytes when decoded)
  @objc
  static func isValidSolanaSignature(_ input: String) -> Bool {
    guard let decoded = base58Decode(input) else { return false }
    return decoded.count == 64
  }
  
  // MARK: - Base58Check (with checksum)
  
  /// Encode with double-SHA256 checksum
  @objc
  static func base58EncodeCheck(_ data: Data) -> String {
    let checksum = doubleSha256(data).prefix(4)
    return base58Encode(data + checksum)
  }
  
  /// Decode and verify checksum
  @objc
  static func base58DecodeCheck(_ input: String) -> Data? {
    guard let decoded = base58Decode(input), decoded.count >= 4 else {
      return nil
    }
    
    let payload = decoded.prefix(decoded.count - 4)
    let checksum = decoded.suffix(4)
    let expected = doubleSha256(Data(payload)).prefix(4)
    
    guard checksum.elementsEqual(expected) else {
      return nil // Checksum mismatch
    }
    
    return Data(payload)
  }
  
  // MARK: - Base64 Conversion
  
  /// Convert Base64 to Base58
  @objc
  static func base64ToBase58(_ base64: String) -> String? {
    guard let data = Data(base64Encoded: base64) else { return nil }
    return base58Encode(data)
  }
  
  /// Convert Base58 to Base64
  @objc
  static func base58ToBase64(_ base58: String) -> String? {
    guard let data = base58Decode(base58) else { return nil }
    return data.base64EncodedString()
  }
  
  // MARK: - Cryptographic Utilities
  
  private static func doubleSha256(_ data: Data) -> Data {
    let first = SHA256.hash(data: data)
    let second = SHA256.hash(data: Data(first))
    return Data(second)
  }
  
  /// SHA256 hash
  @objc
  static func sha256(_ data: Data) -> Data {
    return Data(SHA256.hash(data: data))
  }
  
  // MARK: - Ed25519 Key Generation (iOS 13+)
  
  /// Generate a new Ed25519 keypair
  @available(iOS 13.0, *)
  @objc
  static func generateKeypair() -> [String: String]? {
    let privateKey = Curve25519.Signing.PrivateKey()
    let publicKey = privateKey.publicKey
    
    return [
      "publicKey": base58Encode(publicKey.rawRepresentation),
      "secretKey": base58Encode(privateKey.rawRepresentation)
    ]
  }
  
  /// Sign a message with Ed25519
  @available(iOS 13.0, *)
  @objc
  static func signMessage(_ message: Data, secretKey: Data) -> Data? {
    guard secretKey.count == 32 else { return nil }
    
    do {
      let privateKey = try Curve25519.Signing.PrivateKey(rawRepresentation: secretKey)
      return try privateKey.signature(for: message)
    } catch {
      return nil
    }
  }
  
  /// Verify an Ed25519 signature
  @available(iOS 13.0, *)
  @objc
  static func verifySignature(_ signature: Data, message: Data, publicKey: Data) -> Bool {
    guard publicKey.count == 32, signature.count == 64 else { return false }
    
    do {
      let pubKey = try Curve25519.Signing.PublicKey(rawRepresentation: publicKey)
      return pubKey.isValidSignature(signature, for: message)
    } catch {
      return false
    }
  }
  
  // MARK: - React Native Bridge Requirements
  
  @objc
  static func requiresMainQueueSetup() -> Bool {
    return false
  }
}
