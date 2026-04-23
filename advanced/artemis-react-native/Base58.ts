import { NativeModules, Platform } from 'react-native';
import { Buffer } from 'buffer';

const { ArtemisModule } = NativeModules;

/**
 * Cross-platform Base58 helpers. Both iOS and Android implement the
 * same native contract; every method here is a thin wrapper that pins
 * the base64 wire format the bridge uses and decodes to a JS-friendly
 * shape before returning to the caller.
 */
export const Base58 = {
    async encode(bytes: Uint8Array | string): Promise<string> {
        const base64 = typeof bytes === 'string'
            ? bytes
            : Buffer.from(bytes).toString('base64');
        return ArtemisModule.base64ToBase58(base64);
    },

    async decode(input: string): Promise<Uint8Array> {
        const base64: string = await ArtemisModule.base58ToBase64(input);
        return new Uint8Array(Buffer.from(base64, 'base64'));
    },

    async decodeOrNull(input: string): Promise<Uint8Array | null> {
        try {
            return await Base58.decode(input);
        } catch {
            return null;
        }
    },

    isValid(input: string): Promise<boolean> {
        return ArtemisModule.isValidBase58(input);
    },

    isValidPubkey(input: string): Promise<boolean> {
        return ArtemisModule.isValidSolanaPubkey(input);
    },

    isValidSignature(input: string): Promise<boolean> {
        return ArtemisModule.isValidSolanaSignature(input);
    },

    fromBase64(base64: string): Promise<string> {
        return ArtemisModule.base64ToBase58(base64);
    },

    toBase64(base58: string): Promise<string> {
        return ArtemisModule.base58ToBase64(base58);
    },
};

/**
 * Base58Check helpers (payload + 4-byte double-SHA-256 checksum).
 */
export const Base58Check = {
    async encode(bytes: Uint8Array | string): Promise<string> {
        const base64 = typeof bytes === 'string'
            ? bytes
            : Buffer.from(bytes).toString('base64');
        return ArtemisModule.base58EncodeCheck(base64);
    },

    async decode(input: string): Promise<Uint8Array> {
        const base64: string = await ArtemisModule.base58DecodeCheck(input);
        return new Uint8Array(Buffer.from(base64, 'base64'));
    },
};

/**
 * Ed25519 / SHA-256 helpers. The native bridge exposes the crypto
 * primitives as `cryptoGenerateKeypair`, `cryptoSign`, `cryptoVerify`
 * to avoid colliding with the MWA `signMessage` surface. This module
 * wraps them back under the ergonomic `Crypto.*` names.
 */
export const Crypto = {
    async generateKeypair(): Promise<{ publicKey: string; secretKey: string }> {
        return ArtemisModule.cryptoGenerateKeypair();
    },

    async sha256(data: Uint8Array | string): Promise<string> {
        const base64 = typeof data === 'string'
            ? data
            : Buffer.from(data).toString('base64');
        return ArtemisModule.sha256(base64);
    },

    async sign(message: Uint8Array, secretKey: Uint8Array): Promise<Uint8Array> {
        const msgBase64 = Buffer.from(message).toString('base64');
        const keyBase64 = Buffer.from(secretKey).toString('base64');
        const sigBase64: string = await ArtemisModule.cryptoSign(msgBase64, keyBase64);
        return new Uint8Array(Buffer.from(sigBase64, 'base64'));
    },

    async verify(
        signature: Uint8Array,
        message: Uint8Array,
        publicKey: Uint8Array,
    ): Promise<boolean> {
        const sigBase64 = Buffer.from(signature).toString('base64');
        const msgBase64 = Buffer.from(message).toString('base64');
        const pkBase64 = Buffer.from(publicKey).toString('base64');
        return ArtemisModule.cryptoVerify(sigBase64, msgBase64, pkBase64);
    },
};

/**
 * Runtime platform hints for conditional rendering and feature gating.
 */
export const ArtemisPlatform = {
    /** Mobile Wallet Adapter is an Android-only contract. */
    hasMWA: Platform.OS === 'android',
    /** Seed Vault ships on Saga / Seeker; absent on iOS and other OSes. */
    hasSeedVault: Platform.OS === 'android',
    os: Platform.OS,
};

export default { Base58, Base58Check, Crypto, ArtemisPlatform };
