/**
 * Contract tests for the Artemis RN bridge.
 *
 * These tests exercise the wrapper behavior the JS side owns:
 *   - new bridge shape (structured objects, not JSON strings)
 *   - auth token typed as string end-to-end
 *   - batch results with per-slot success / failure state
 *   - transact(wallet, block) teardown semantics
 *   - cross-platform Base58 / Crypto wrappers calling the right native
 *     method names
 *
 * The native module is stubbed with strict response shapes that mirror
 * what the Android Kotlin bridge emits. Every test asserts on the
 * object the native side would actually return, so a regression on
 * either side fails here first.
 */
import {
    MobileWalletAdapter,
    transact,
    type AuthorizationResult,
    type BatchSendResult,
    type MwaCapabilities,
} from '../MobileWalletAdapter';
import Artemis from '../index';
import { Base58, Crypto } from '../Base58';

const ADDRESS_BASE58 = '11111111111111111111111111111111';
const ADDRESS_BASE64 = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=';

const FULL_CAPS: MwaCapabilities = {
    maxTransactionsPerRequest: 10,
    maxMessagesPerRequest: 10,
    supportedTransactionVersions: ['legacy', 0],
    features: [
        'solana:signAndSendTransaction',
        'solana:signTransactions',
        'solana:signMessages',
        'solana:signInWithSolana',
        'solana:cloneAuthorization',
    ],
    supportsSignAndSendTransactions: true,
    supportsCloneAuthorization: true,
    supportsSignTransactions: true,
    supportsSignIn: true,
    supportsLegacyTransactions: true,
    supportsVersionedTransactions: true,
};

const AUTH_RESULT: AuthorizationResult = {
    authToken: 'token-ABC',
    address: ADDRESS_BASE58,
    accounts: [
        {
            address: ADDRESS_BASE64,
            addressBase58: ADDRESS_BASE58,
            label: 'Main',
            chains: ['solana:mainnet'],
            features: ['solana:signAndSendTransaction'],
        },
    ],
    walletUriBase: 'https://wallet.example.com',
    capabilities: FULL_CAPS,
};

jest.mock('react-native', () => ({
    NativeModules: {
        ArtemisModule: {
            initialize: jest.fn(),
            setRpcUrl: jest.fn(),

            // MWA
            connect: jest.fn(),
            connectWithFeatures: jest.fn(),
            connectWithSignIn: jest.fn(),
            reauthorize: jest.fn(),
            deauthorize: jest.fn().mockResolvedValue(undefined),
            getCapabilities: jest.fn(),
            cloneAuthorization: jest.fn(),
            signTransaction: jest.fn(),
            signTransactions: jest.fn(),
            signAndSendTransaction: jest.fn(),
            signAndSendTransactions: jest.fn(),
            signMessage: jest.fn(),
            signMessages: jest.fn(),
            signMessagesDetached: jest.fn(),

            // RPC
            getBalance: jest.fn().mockResolvedValue('1000000000'),
            getLatestBlockhash: jest.fn().mockResolvedValue('blockhash'),
            buildTransferTransaction: jest.fn().mockResolvedValue('base64tx'),

            // Seed Vault (auth tokens are STRINGS end-to-end)
            seedVaultAuthorize: jest
                .fn()
                .mockResolvedValue({ authToken: 'sv-token-1', accountId: 0 }),
            seedVaultCreateSeed: jest
                .fn()
                .mockResolvedValue({ authToken: 'sv-token-2', accountId: 0 }),
            seedVaultImportSeed: jest
                .fn()
                .mockResolvedValue({ authToken: 'sv-token-3', accountId: 0 }),
            seedVaultGetAccounts: jest.fn().mockResolvedValue([
                { id: 0, name: 'Main', publicKey: ADDRESS_BASE58 },
            ]),
            seedVaultSignMessages: jest.fn().mockResolvedValue(['c2ln']),
            seedVaultSignTransactions: jest.fn().mockResolvedValue(['c2lnMQ==']),
            seedVaultRequestPublicKeys: jest.fn().mockResolvedValue([ADDRESS_BASE58]),
            seedVaultSignWithDerivationPath: jest.fn().mockResolvedValue(['c2ln']),
            seedVaultDeauthorize: jest.fn().mockResolvedValue(undefined),

            // DePIN / Solana Pay / Gaming
            generateDeviceIdentity: jest.fn().mockResolvedValue('devicePubkey'),
            signLocationProof: jest.fn().mockResolvedValue('proofSignature'),
            buildSolanaPayUri: jest.fn().mockResolvedValue('solana:pay'),
            parseSolanaPayUri: jest
                .fn()
                .mockResolvedValue({ recipient: 'recipient', amount: '1.0' }),
            verifyMerkleProof: jest.fn().mockResolvedValue(true),

            // Cross-platform Base58 + Crypto
            base64ToBase58: jest.fn().mockResolvedValue('BASE58'),
            base58ToBase64: jest.fn().mockResolvedValue('BASE64='),
            isValidBase58: jest.fn().mockResolvedValue(true),
            isValidSolanaPubkey: jest.fn().mockResolvedValue(true),
            isValidSolanaSignature: jest.fn().mockResolvedValue(true),
            base58EncodeCheck: jest.fn().mockResolvedValue('BASE58CHECK'),
            base58DecodeCheck: jest.fn().mockResolvedValue('BASE64='),
            sha256: jest.fn().mockResolvedValue('SHA='),
            cryptoGenerateKeypair: jest
                .fn()
                .mockResolvedValue({ publicKey: 'pk58', secretKey: 'sk58' }),
            cryptoSign: jest.fn().mockResolvedValue('c2ln'),
            cryptoVerify: jest.fn().mockResolvedValue(true),
        },
    },
    Platform: { OS: 'android', select: jest.fn((o) => o.android) },
}));

// ============================================================================
// MWA adapter
// ============================================================================

describe('MobileWalletAdapter bridge contract', () => {
    let wallet: MobileWalletAdapter;
    let native: any;

    beforeEach(() => {
        native = require('react-native').NativeModules.ArtemisModule;
        Object.values(native).forEach((fn: any) => fn?.mockClear?.());
        native.connect.mockResolvedValue(AUTH_RESULT);
        native.connectWithFeatures.mockResolvedValue(AUTH_RESULT);
        native.connectWithSignIn.mockResolvedValue({
            ...AUTH_RESULT,
            signInResult: {
                address: ADDRESS_BASE64,
                signedMessage: 'bXNn',
                signature: 'c2ln',
                signatureType: 'ed25519',
            },
        });
        native.reauthorize.mockResolvedValue(AUTH_RESULT);
        native.getCapabilities.mockResolvedValue(FULL_CAPS);
        native.cloneAuthorization.mockResolvedValue('cloned-token');

        wallet = new MobileWalletAdapter({
            identityUri: 'https://myapp.example.com',
            iconPath: 'https://myapp.example.com/favicon.ico',
            identityName: 'MyApp',
            chain: 'solana:mainnet',
        });
    });

    it('connect() caches auth token, accounts, capabilities', async () => {
        await wallet.connect();
        expect(wallet.authToken).toBe('token-ABC');
        expect(wallet.accounts).toHaveLength(1);
        expect(wallet.capabilities).toEqual(FULL_CAPS);
        expect(wallet.publicKey?.toBase58()).toBe(ADDRESS_BASE58);
    });

    it('connectWithSignIn returns SIWS result alongside the authorization', async () => {
        const auth = await wallet.connectWithSignIn({ domain: 'myapp.example.com' });
        expect(auth.signInResult?.signatureType).toBe('ed25519');
        expect(auth.signInResult?.signature).toBe('c2ln');
    });

    it('reauthorize() refreshes without re-prompting', async () => {
        await wallet.connect();
        await wallet.reauthorize();
        expect(native.reauthorize).toHaveBeenCalledTimes(1);
    });

    it('getCapabilities() is cached after the first call', async () => {
        await wallet.connect();
        const a = await wallet.getCapabilities();
        const b = await wallet.getCapabilities();
        expect(a).toBe(b);
        // getCapabilities on the native module fires at most once through
        // the adapter (connect hoists it into cache, so the second
        // getCapabilities() returns the cached instance without hitting
        // the bridge again).
        expect(native.getCapabilities).toHaveBeenCalledTimes(0);
    });

    it('cloneAuthorization throws when wallet does not advertise the feature', async () => {
        const capsNoClone: MwaCapabilities = { ...FULL_CAPS, supportsCloneAuthorization: false };
        native.connect.mockResolvedValue({ ...AUTH_RESULT, capabilities: capsNoClone });
        await wallet.connect();
        await expect(wallet.cloneAuthorization()).rejects.toThrow(/clone_authorization/);
    });

    it('signAndSendTransactions returns a per-slot BatchSendResult', async () => {
        const batch: BatchSendResult = {
            results: [
                {
                    index: 0,
                    signature: 'SIG1',
                    confirmed: true,
                    isSuccess: true,
                    isFailure: false,
                    isSignedButNotBroadcast: false,
                },
                {
                    index: 1,
                    signature: '',
                    confirmed: false,
                    error: 'wallet rejected',
                    isSuccess: false,
                    isFailure: true,
                    isSignedButNotBroadcast: false,
                },
            ],
            successCount: 1,
            failureCount: 1,
        };
        native.signAndSendTransactions.mockResolvedValue(batch);
        await wallet.connect();
        const result = await wallet.signAndSendTransactions([
            { serialize: () => new Uint8Array([0]) } as any,
            { serialize: () => new Uint8Array([1]) } as any,
        ]);
        expect(result.results[0].isSuccess).toBe(true);
        expect(result.results[1].isFailure).toBe(true);
        expect(result.successCount).toBe(1);
        expect(result.failureCount).toBe(1);
    });

    it('transact(wallet, block) runs block, disconnects on success', async () => {
        const spy = jest.spyOn(wallet, 'disconnect');
        const out = await transact(wallet, async () => 'payload');
        expect(out).toBe('payload');
        expect(spy).toHaveBeenCalled();
    });

    it('transact(wallet, block) still disconnects on block throw', async () => {
        const spy = jest.spyOn(wallet, 'disconnect');
        await expect(
            transact(wallet, async () => {
                throw new Error('block error');
            }),
        ).rejects.toThrow('block error');
        expect(spy).toHaveBeenCalled();
    });
});

// ============================================================================
// Seed Vault contract
// ============================================================================

describe('Seed Vault auth tokens are strings end to end', () => {
    it('seedVaultAuthorize returns a string authToken', async () => {
        const auth = await Artemis.seedVaultAuthorize('sign_transaction');
        expect(typeof auth.authToken).toBe('string');
        expect(auth.authToken).toBe('sv-token-1');
    });

    it('seedVaultGetAccounts consumes a string token and returns typed accounts', async () => {
        const accounts = await Artemis.seedVaultGetAccounts('sv-token-1');
        expect(accounts[0]).toEqual({ id: 0, name: 'Main', publicKey: ADDRESS_BASE58 });
    });

    it('seedVaultSignTransactions forwards the string token unchanged', async () => {
        const native = require('react-native').NativeModules.ArtemisModule;
        await Artemis.seedVaultSignTransactions('sv-token-2', ['ZmFrZQ==']);
        expect(native.seedVaultSignTransactions).toHaveBeenCalledWith('sv-token-2', ['ZmFrZQ==']);
    });
});

// ============================================================================
// Cross-platform Base58 / Crypto
// ============================================================================

describe('Base58 wrapper calls the right native method names', () => {
    it('Base58.encode uses base64ToBase58 bridge method', async () => {
        const native = require('react-native').NativeModules.ArtemisModule;
        native.base64ToBase58.mockResolvedValueOnce('ENC');
        const out = await Base58.encode(new Uint8Array([1, 2, 3]));
        expect(out).toBe('ENC');
        expect(native.base64ToBase58).toHaveBeenCalled();
    });

    it('Crypto.sign routes through cryptoSign, not signMessage', async () => {
        const native = require('react-native').NativeModules.ArtemisModule;
        await Crypto.sign(new Uint8Array([1]), new Uint8Array(32));
        expect(native.cryptoSign).toHaveBeenCalled();
        // Must NOT collide with MWA signMessage.
        expect(native.signMessage).not.toHaveBeenCalled();
    });

    it('Crypto.generateKeypair returns the base58 shape from the bridge', async () => {
        const kp = await Crypto.generateKeypair();
        expect(kp.publicKey).toBe('pk58');
        expect(kp.secretKey).toBe('sk58');
    });
});
