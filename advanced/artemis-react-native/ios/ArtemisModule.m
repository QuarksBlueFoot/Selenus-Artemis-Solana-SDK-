#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(ArtemisModule, NSObject)

// Base58 encoding/decoding
RCT_EXTERN_METHOD(base58Encode:(NSData *)data
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(base58Decode:(NSString *)input
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
RCT_EXTERN_METHOD(base58EncodeCheck:(NSData *)data
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(base58DecodeCheck:(NSString *)input
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

// Base64 conversion
RCT_EXTERN_METHOD(base64ToBase58:(NSString *)base64
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(base58ToBase64:(NSString *)base58
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

// Crypto utilities
RCT_EXTERN_METHOD(sha256:(NSData *)data
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(generateKeypair:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(signMessage:(NSData *)message
                  secretKey:(NSData *)secretKey
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(verifySignature:(NSData *)signature
                  message:(NSData *)message
                  publicKey:(NSData *)publicKey
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

// Note: MWA-specific methods (connect, signTransaction, etc.) are Android-only
// as they rely on Solana Mobile Stack which is Android-exclusive.
// iOS apps should use WalletConnect or other iOS-compatible wallet protocols.

@end
