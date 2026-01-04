import { MobileWalletAdapter } from '../MobileWalletAdapter';
import { Transaction } from '@solana/web3.js';
import { NativeModules } from 'react-native';

// Mock NativeModules
jest.mock('react-native', () => {
    return {
        NativeModules: {
            ArtemisModule: {
                initialize: jest.fn(),
                connect: jest.fn().mockResolvedValue('11111111111111111111111111111111'),
                signTransaction: jest.fn().mockImplementation((tx) => Promise.resolve(tx)),
                signMessage: jest.fn().mockResolvedValue('c2lnbmF0dXJl'),
                setRpcUrl: jest.fn(),
                getBalance: jest.fn().mockResolvedValue('1000000000'),
                getLatestBlockhash: jest.fn().mockResolvedValue('blockhash'),
                buildTransferTransaction: jest.fn().mockResolvedValue('base64tx'),
                generateDeviceIdentity: jest.fn().mockResolvedValue('devicePubkey'),
                signLocationProof: jest.fn().mockResolvedValue('proofSignature'),
                buildSolanaPayUri: jest.fn().mockResolvedValue('solana:pay'),
                parseSolanaPayUri: jest.fn().mockResolvedValue({ recipient: 'recipient', amount: '1.0' }),
                verifyMerkleProof: jest.fn().mockResolvedValue(true),
            }
        }
    };
});

describe('MobileWalletAdapter', () => {
    let adapter: MobileWalletAdapter;

    beforeEach(() => {
        adapter = new MobileWalletAdapter({
            identityUri: 'https://test.com',
            iconPath: 'icon.png',
            identityName: 'Test App',
            chain: 'solana:devnet'
        });
    });

    it('initializes correctly', () => {
        expect(NativeModules.ArtemisModule.initialize).toHaveBeenCalledWith(
            'https://test.com',
            'icon.png',
            'Test App',
            'solana:devnet'
        );
    });

    it('connects successfully', async () => {
        await adapter.connect();
        expect(NativeModules.ArtemisModule.connect).toHaveBeenCalled();
        expect(adapter.publicKey?.toBase58()).toBe('11111111111111111111111111111111');
        expect(adapter.connected).toBe(true);
    });

    it('signs transaction', async () => {
        await adapter.connect();
        const tx = new Transaction();
        tx.recentBlockhash = '11111111111111111111111111111111';
        tx.feePayer = adapter.publicKey!;
        
        await adapter.signTransaction(tx);
        expect(NativeModules.ArtemisModule.signTransaction).toHaveBeenCalled();
    });

    it('signs message', async () => {
        await adapter.connect();
        const msg = new Uint8Array([1, 2, 3]);
        const sig = await adapter.signMessage(msg);
        expect(NativeModules.ArtemisModule.signMessage).toHaveBeenCalled();
        expect(sig).toBeDefined();
    });
});
