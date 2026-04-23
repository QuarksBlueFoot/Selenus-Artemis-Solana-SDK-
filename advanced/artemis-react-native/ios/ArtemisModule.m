/*
 * RN bridge declarations for the Swift ArtemisModule.
 *
 * iOS ships only the cross-platform utility layer (Base58, Base58Check,
 * SHA-256, Ed25519). Mobile Wallet Adapter and Seed Vault are Android-
 * only contracts; this file intentionally omits them so the RN bridge
 * does not advertise native methods that do not exist on iOS.
 */
#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(ArtemisModule, NSObject)

// Base58 encoding

RCT_EXTERN_METHOD(base64ToBase58:(NSString *)base64
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(base58ToBase64:(NSString *)base58
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(isValidBase58:(NSString *)input
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(isValidSolanaPubkey:(NSString *)input
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(isValidSolanaSignature:(NSString *)input
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

// Base58Check

RCT_EXTERN_METHOD(base58EncodeCheck:(NSString *)base64
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(base58DecodeCheck:(NSString *)input
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

// SHA-256

RCT_EXTERN_METHOD(sha256:(NSString *)base64Data
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

// Ed25519

RCT_EXTERN_METHOD(cryptoGenerateKeypair:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(cryptoSign:(NSString *)messageBase64
                  secretKey:(NSString *)secretKeyBase64
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(cryptoVerify:(NSString *)signatureBase64
                  message:(NSString *)messageBase64
                  publicKey:(NSString *)publicKeyBase64
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end
