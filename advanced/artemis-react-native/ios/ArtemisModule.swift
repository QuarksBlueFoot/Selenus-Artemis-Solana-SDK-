import Foundation
import CryptoKit

/**
 * Artemis Solana SDK iOS native module.
 *
 * Exposes the cross-platform utility layer (Base58, Base58Check, SHA-256,
 * Ed25519) that the JS `Base58` and `Crypto` helpers call. Every method
 * is an instance method taking RN `resolve`/`reject` blocks so the
 * bridge in ArtemisModule.m registers them correctly. Mobile Wallet
 * Adapter and Seed Vault are Android-only contracts; there is no iOS
 * equivalent, so this file intentionally omits those surfaces.
 */
@objc(ArtemisModule)
final class ArtemisModule: NSObject {

    // Required so React Native knows we are not main-queue dependent.
    @objc static func requiresMainQueueSetup() -> Bool { false }

    // MARK: Base58 alphabet

    private static let base58Alphabet = Array(
        "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    )
    private static let base58Index: [Character: Int] = {
        var map: [Character: Int] = [:]
        for (i, c) in base58Alphabet.enumerated() { map[c] = i }
        return map
    }()

    private static func base58Encode(_ bytes: [UInt8]) -> String {
        if bytes.isEmpty { return "" }
        var work = bytes
        var leadingZeros = 0
        while leadingZeros < work.count && work[leadingZeros] == 0 { leadingZeros += 1 }

        var encoded: [Character] = []
        var start = leadingZeros
        while start < work.count {
            var carry = 0
            for i in start..<work.count {
                let digit = Int(work[i])
                let combined = carry * 256 + digit
                work[i] = UInt8(combined / 58)
                carry = combined % 58
            }
            encoded.append(base58Alphabet[carry])
            while start < work.count && work[start] == 0 { start += 1 }
        }
        for _ in 0..<leadingZeros { encoded.append("1") }
        return String(encoded.reversed())
    }

    private static func base58Decode(_ input: String) throws -> [UInt8] {
        if input.isEmpty { return [] }
        var digits: [Int] = []
        digits.reserveCapacity(input.count)
        for c in input {
            guard let idx = base58Index[c] else {
                throw NSError(
                    domain: "ArtemisModule",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "Invalid Base58 character: \(c)"]
                )
            }
            digits.append(idx)
        }
        var leadingOnes = 0
        while leadingOnes < digits.count && digits[leadingOnes] == 0 { leadingOnes += 1 }

        var decoded: [UInt8] = []
        var start = leadingOnes
        while start < digits.count {
            var carry = 0
            for i in start..<digits.count {
                let combined = carry * 58 + digits[i]
                digits[i] = combined / 256
                carry = combined % 256
            }
            decoded.append(UInt8(carry))
            while start < digits.count && digits[start] == 0 { start += 1 }
        }
        var out = [UInt8](repeating: 0, count: leadingOnes)
        out.append(contentsOf: decoded.reversed())
        return out
    }

    private static func doubleSha256(_ bytes: [UInt8]) -> [UInt8] {
        let first = SHA256.hash(data: Data(bytes))
        let second = SHA256.hash(data: Data(first))
        return Array(second)
    }

    // MARK: - Base58 bridge methods

    @objc(base64ToBase58:resolver:rejecter:)
    func base64ToBase58(
        _ base64: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let data = Data(base64Encoded: base64) else {
            reject("BASE58_ERROR", "Invalid base64 input", nil); return
        }
        resolve(ArtemisModule.base58Encode(Array(data)))
    }

    @objc(base58ToBase64:resolver:rejecter:)
    func base58ToBase64(
        _ base58: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        do {
            let bytes = try ArtemisModule.base58Decode(base58)
            resolve(Data(bytes).base64EncodedString())
        } catch {
            reject("BASE58_ERROR", error.localizedDescription, error)
        }
    }

    @objc(isValidBase58:resolver:rejecter:)
    func isValidBase58(
        _ input: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        let ok = (try? ArtemisModule.base58Decode(input)) != nil
        resolve(ok)
    }

    @objc(isValidSolanaPubkey:resolver:rejecter:)
    func isValidSolanaPubkey(
        _ input: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        let ok: Bool = {
            guard let decoded = try? ArtemisModule.base58Decode(input) else { return false }
            return decoded.count == 32
        }()
        resolve(ok)
    }

    @objc(isValidSolanaSignature:resolver:rejecter:)
    func isValidSolanaSignature(
        _ input: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        let ok: Bool = {
            guard let decoded = try? ArtemisModule.base58Decode(input) else { return false }
            return decoded.count == 64
        }()
        resolve(ok)
    }

    @objc(base58EncodeCheck:resolver:rejecter:)
    func base58EncodeCheck(
        _ base64: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let payload = Data(base64Encoded: base64) else {
            reject("BASE58_CHECK_ERROR", "Invalid base64 payload", nil); return
        }
        let bytes = Array(payload)
        let checksum = Array(ArtemisModule.doubleSha256(bytes).prefix(4))
        resolve(ArtemisModule.base58Encode(bytes + checksum))
    }

    @objc(base58DecodeCheck:resolver:rejecter:)
    func base58DecodeCheck(
        _ input: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        do {
            let decoded = try ArtemisModule.base58Decode(input)
            guard decoded.count >= 4 else {
                throw NSError(
                    domain: "ArtemisModule",
                    code: -2,
                    userInfo: [NSLocalizedDescriptionKey: "Base58Check input too short"]
                )
            }
            let payload = Array(decoded.prefix(decoded.count - 4))
            let checksum = Array(decoded.suffix(4))
            let expected = Array(ArtemisModule.doubleSha256(payload).prefix(4))
            guard checksum == expected else {
                throw NSError(
                    domain: "ArtemisModule",
                    code: -3,
                    userInfo: [NSLocalizedDescriptionKey: "Base58Check checksum mismatch"]
                )
            }
            resolve(Data(payload).base64EncodedString())
        } catch {
            reject("BASE58_CHECK_ERROR", error.localizedDescription, error)
        }
    }

    // MARK: - SHA-256

    @objc(sha256:resolver:rejecter:)
    func sha256(
        _ base64Data: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let data = Data(base64Encoded: base64Data) else {
            reject("SHA256_ERROR", "Invalid base64 input", nil); return
        }
        let digest = SHA256.hash(data: data)
        resolve(Data(digest).base64EncodedString())
    }

    // MARK: - Ed25519

    @objc(cryptoGenerateKeypair:rejecter:)
    func cryptoGenerateKeypair(
        _ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        guard #available(iOS 13.0, *) else {
            reject("CRYPTO_KEYGEN_ERROR", "Ed25519 keygen requires iOS 13+", nil); return
        }
        let privateKey = Curve25519.Signing.PrivateKey()
        let publicKey = privateKey.publicKey
        resolve([
            "publicKey": ArtemisModule.base58Encode(Array(publicKey.rawRepresentation)),
            "secretKey": ArtemisModule.base58Encode(Array(privateKey.rawRepresentation)),
        ])
    }

    @objc(cryptoSign:secretKey:resolver:rejecter:)
    func cryptoSign(
        _ messageBase64: String,
        secretKey secretKeyBase64: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        guard #available(iOS 13.0, *) else {
            reject("CRYPTO_SIGN_ERROR", "Ed25519 signing requires iOS 13+", nil); return
        }
        guard let msg = Data(base64Encoded: messageBase64),
              let seed = Data(base64Encoded: secretKeyBase64)
        else {
            reject("CRYPTO_SIGN_ERROR", "Invalid base64 input", nil); return
        }
        guard seed.count == 32 else {
            reject("CRYPTO_SIGN_ERROR", "Secret key must be a 32-byte Ed25519 seed", nil); return
        }
        do {
            let key = try Curve25519.Signing.PrivateKey(rawRepresentation: seed)
            let sig = try key.signature(for: msg)
            resolve(sig.base64EncodedString())
        } catch {
            reject("CRYPTO_SIGN_ERROR", error.localizedDescription, error)
        }
    }

    @objc(cryptoVerify:message:publicKey:resolver:rejecter:)
    func cryptoVerify(
        _ signatureBase64: String,
        message messageBase64: String,
        publicKey publicKeyBase64: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        guard #available(iOS 13.0, *) else {
            reject("CRYPTO_VERIFY_ERROR", "Ed25519 verify requires iOS 13+", nil); return
        }
        guard
            let sig = Data(base64Encoded: signatureBase64),
            let msg = Data(base64Encoded: messageBase64),
            let pk = Data(base64Encoded: publicKeyBase64)
        else {
            reject("CRYPTO_VERIFY_ERROR", "Invalid base64 input", nil); return
        }
        guard pk.count == 32, sig.count == 64 else {
            resolve(false); return
        }
        do {
            let pub = try Curve25519.Signing.PublicKey(rawRepresentation: pk)
            resolve(pub.isValidSignature(sig, for: msg))
        } catch {
            resolve(false)
        }
    }
}
