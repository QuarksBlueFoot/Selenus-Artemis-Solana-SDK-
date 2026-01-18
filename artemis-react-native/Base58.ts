import { NativeModules, Platform } from 'react-native';

const { ArtemisModule } = NativeModules;

/**
 * Artemis Base58 utilities for React Native
 * 
 * Cross-platform Base58 encoding/decoding with Solana-specific validation.
 * Works on both iOS and Android with native implementations for performance.
 */
export const Base58 = {
  /**
   * Encode bytes to Base58 string
   * @param bytes - Uint8Array or base64-encoded string
   * @returns Base58-encoded string
   */
  encode: async (bytes: Uint8Array | string): Promise<string> => {
    const base64 = typeof bytes === 'string' 
      ? bytes 
      : Buffer.from(bytes).toString('base64');
    return ArtemisModule.base64ToBase58(base64);
  },

  /**
   * Decode Base58 string to bytes
   * @param input - Base58 string
   * @returns Uint8Array of decoded bytes
   */
  decode: async (input: string): Promise<Uint8Array> => {
    const base64 = await ArtemisModule.base58ToBase64(input);
    return new Uint8Array(Buffer.from(base64, 'base64'));
  },

  /**
   * Safely decode Base58, returns null on invalid input
   */
  decodeOrNull: async (input: string): Promise<Uint8Array | null> => {
    try {
      return await Base58.decode(input);
    } catch {
      return null;
    }
  },

  /**
   * Check if string is valid Base58
   */
  isValid: async (input: string): Promise<boolean> => {
    return ArtemisModule.isValidBase58(input);
  },

  /**
   * Check if string is a valid Solana public key (32 bytes)
   */
  isValidPubkey: async (input: string): Promise<boolean> => {
    return ArtemisModule.isValidSolanaPubkey(input);
  },

  /**
   * Check if string is a valid Solana signature (64 bytes)
   */
  isValidSignature: async (input: string): Promise<boolean> => {
    return ArtemisModule.isValidSolanaSignature(input);
  },

  /**
   * Convert Base64 to Base58
   */
  fromBase64: async (base64: string): Promise<string> => {
    return ArtemisModule.base64ToBase58(base64);
  },

  /**
   * Convert Base58 to Base64
   */
  toBase64: async (base58: string): Promise<string> => {
    return ArtemisModule.base58ToBase64(base58);
  },
};

/**
 * Base58Check utilities (with checksum)
 */
export const Base58Check = {
  /**
   * Encode with double-SHA256 checksum
   */
  encode: async (bytes: Uint8Array | string): Promise<string> => {
    const base64 = typeof bytes === 'string' 
      ? bytes 
      : Buffer.from(bytes).toString('base64');
    // Convert to data then encode with check
    const data = await ArtemisModule.base58DecodeCheck ? 
      ArtemisModule.base58EncodeCheck(base64) : 
      Promise.reject('Base58Check not available');
    return data;
  },

  /**
   * Decode and verify checksum
   */
  decode: async (input: string): Promise<Uint8Array> => {
    const base64 = await ArtemisModule.base58DecodeCheck(input);
    return new Uint8Array(Buffer.from(base64, 'base64'));
  },
};

/**
 * Crypto utilities
 */
export const Crypto = {
  /**
   * Generate Ed25519 keypair
   * @returns Object with publicKey and secretKey as Base58 strings
   */
  generateKeypair: async (): Promise<{ publicKey: string; secretKey: string }> => {
    return ArtemisModule.generateKeypair();
  },

  /**
   * SHA256 hash
   * @param data - Uint8Array or base64 string
   * @returns Base64-encoded hash
   */
  sha256: async (data: Uint8Array | string): Promise<string> => {
    const base64 = typeof data === 'string' 
      ? data 
      : Buffer.from(data).toString('base64');
    return ArtemisModule.sha256(base64);
  },

  /**
   * Sign message with Ed25519
   * @param message - Message bytes
   * @param secretKey - 32-byte secret key
   * @returns 64-byte signature
   */
  sign: async (message: Uint8Array, secretKey: Uint8Array): Promise<Uint8Array> => {
    const msgBase64 = Buffer.from(message).toString('base64');
    const keyBase64 = Buffer.from(secretKey).toString('base64');
    const sigBase64 = await ArtemisModule.signMessage(msgBase64, keyBase64);
    return new Uint8Array(Buffer.from(sigBase64, 'base64'));
  },

  /**
   * Verify Ed25519 signature
   */
  verify: async (
    signature: Uint8Array, 
    message: Uint8Array, 
    publicKey: Uint8Array
  ): Promise<boolean> => {
    const sigBase64 = Buffer.from(signature).toString('base64');
    const msgBase64 = Buffer.from(message).toString('base64');
    const keyBase64 = Buffer.from(publicKey).toString('base64');
    return ArtemisModule.verifySignature(sigBase64, msgBase64, keyBase64);
  },
};

/**
 * Platform info for conditional features
 */
export const ArtemisPlatform = {
  /**
   * Whether Mobile Wallet Adapter is available (Android only)
   */
  hasMWA: Platform.OS === 'android',
  
  /**
   * Whether Seed Vault is available (Android with Saga/etc.)
   */
  hasSeedVault: Platform.OS === 'android',
  
  /**
   * Current platform
   */
  os: Platform.OS,
};

export default { Base58, Base58Check, Crypto, ArtemisPlatform };
