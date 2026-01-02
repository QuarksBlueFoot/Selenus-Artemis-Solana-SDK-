import { MobileWalletAdapter } from '../MobileWalletAdapter';
import Artemis from '../index';
import { Transaction, PublicKey, VersionedTransaction } from '@solana/web3.js';
import { NativeModules } from 'react-native';

// Mock NativeModules
jest.mock('react-native', () => {
    return {
        NativeModules: {
            ArtemisModule: {
                initialize: jest.fn(),
                connect: jest.fn().mockResolvedValue('11111111111111111111111111111111'),
                signTransaction: jest.fn().mockImplementation((tx) => Promise.resolve(tx)),
                signAndSendTransaction: jest.fn().mockResolvedValue('signature'),
                signMessage: jest.fn().mockResolvedValue('c2lnbmF0dXJl'), // 'signature' in base64
                connectWithSignIn: jest.fn().mockResolvedValue({
                    publicKey: '11111111111111111111111111111111',
                    signature: 'sig',
                    message: 'msg'
                }),
                setRpcUrl: jest.fn(),
                getBalance: jest.fn().mockResolvedValue('1000000000'),
                getLatestBlockhash: jest.fn().mockResolvedValue('blockhash'),
                buildTransferTransaction: jest.fn().mockResolvedValue('base64tx'),
                generateDeviceIdentity: jest.fn().mockResolvedValue('devicePubkey'),
                signLocationProof: jest.fn().mockResolvedValue('proofSignature'),
                buildSolanaPayUri: jest.fn().mockResolvedValue('solana:pay'),
                parseSolanaPayUri: jest.fn().mockResolvedValue({ recipient: 'recipient', amount: '1.0' }),
                verifyMerkleProof: jest.fn().mockResolvedValue(true),
                // Seed Vault Mocks
                seedVaultAuthorize: jest.fn().mockResolvedValue({ authToken: 123 }),
                seedVaultCreateSeed: jest.fn().mockResolvedValue({ authToken: 456 }),
                seedVaultImportSeed: jest.fn().mockResolvedValue({ authToken: 789 }),
                seedVaultGetAccounts: jest.fn().mockResolvedValue([{ accountId: 1, name: "Main" }]),
                seedVaultSignMessages: jest.fn().mockResolvedValue(['sig1', 'sig2']),
                seedVaultSignTransactions: jest.fn().mockResolvedValue(['signedTx1', 'signedTx2']),
                seedVaultRequestPublicKeys: jest.fn().mockResolvedValue(['pubkey1', 'pubkey2']),
                seedVaultSignWithDerivationPath: jest.fn().mockResolvedValue(['sig1']),
                seedVaultDeauthorize: jest.fn().mockResolvedValue(undefined),
            }
        },
        Platform: {
            OS: 'android',
            select: jest.fn((objs) => objs.android)
        }
    };
});

describe('Artemis SDK Integration Tests', () => {
    
    describe('MobileWalletAdapter Wrapper', () => {
        let adapter: MobileWalletAdapter;

        beforeEach(() => {
            jest.clearAllMocks();
            adapter = new MobileWalletAdapter({
                identityUri: 'https://test.com',
                iconPath: 'icon.png',
                identityName: 'Test App',
                chain: 'solana:devnet'
            });
        });

        it('1. initializes correctly', () => {
            expect(NativeModules.ArtemisModule.initialize).toHaveBeenCalledWith(
                'https://test.com',
                'icon.png',
                'Test App',
                'solana:devnet'
            );
        });

        it('2. connects successfully', async () => {
            await adapter.connect();
            expect(NativeModules.ArtemisModule.connect).toHaveBeenCalled();
            expect(adapter.publicKey?.toBase58()).toBe('11111111111111111111111111111111');
            expect(adapter.connected).toBe(true);
        });

        it('3. handles connection failure', async () => {
            (NativeModules.ArtemisModule.connect as jest.Mock).mockRejectedValueOnce(new Error('Auth failed'));
            await expect(adapter.connect()).rejects.toThrow();
            expect(adapter.connected).toBe(false);
        });

        it('4. disconnects correctly', async () => {
            await adapter.connect();
            await adapter.disconnect();
            expect(adapter.publicKey).toBeNull();
            expect(adapter.connected).toBe(false);
        });

        it('5. signs transaction', async () => {
            await adapter.connect();
            const tx = new Transaction();
            tx.recentBlockhash = '11111111111111111111111111111111';
            tx.feePayer = adapter.publicKey!;
            tx.add({
                keys: [],
                programId: new PublicKey('11111111111111111111111111111111'),
                data: Buffer.from([])
            });
            
            await adapter.signTransaction(tx);
            expect(NativeModules.ArtemisModule.signTransaction).toHaveBeenCalled();
        });

        it('6. signs message', async () => {
            await adapter.connect();
            const msg = new Uint8Array([1, 2, 3]);
            const sig = await adapter.signMessage(msg);
            expect(NativeModules.ArtemisModule.signMessage).toHaveBeenCalled();
            expect(sig).toBeDefined();
        });
    });

    describe('Direct Artemis SDK Calls', () => {
        beforeEach(() => {
            jest.clearAllMocks();
        });

        it('7. initialize calls native', () => {
            Artemis.initialize('uri', 'icon', 'name', 'chain');
            expect(NativeModules.ArtemisModule.initialize).toHaveBeenCalledWith('uri', 'icon', 'name', 'chain');
        });

        it('8. connect calls native', async () => {
            await Artemis.connect();
            expect(NativeModules.ArtemisModule.connect).toHaveBeenCalled();
        });

        it('9. signTransaction calls native', async () => {
            await Artemis.signTransaction('txBase64');
            expect(NativeModules.ArtemisModule.signTransaction).toHaveBeenCalledWith('txBase64');
        });

        it('10. signAndSendTransaction calls native', async () => {
            await Artemis.signAndSendTransaction('txBase64');
            expect(NativeModules.ArtemisModule.signAndSendTransaction).toHaveBeenCalledWith('txBase64');
        });

        it('11. signMessage calls native', async () => {
            await Artemis.signMessage('msgBase64');
            expect(NativeModules.ArtemisModule.signMessage).toHaveBeenCalledWith('msgBase64');
        });

        it('12. connectWithSignIn calls native', async () => {
            const payload = { domain: 'test.com', uri: 'https://test.com' };
            await Artemis.connectWithSignIn(payload);
            expect(NativeModules.ArtemisModule.connectWithSignIn).toHaveBeenCalledWith(payload);
        });
    });

    describe('RPC & Program Methods', () => {
        it('13. setRpcUrl calls native', () => {
            Artemis.setRpcUrl('https://api.devnet.solana.com');
            expect(NativeModules.ArtemisModule.setRpcUrl).toHaveBeenCalledWith('https://api.devnet.solana.com');
        });

        it('14. getBalance calls native', async () => {
            const bal = await Artemis.getBalance('pubkey');
            expect(NativeModules.ArtemisModule.getBalance).toHaveBeenCalledWith('pubkey');
            expect(bal).toBe('1000000000');
        });

        it('15. getLatestBlockhash calls native', async () => {
            const blockhash = await Artemis.getLatestBlockhash();
            expect(NativeModules.ArtemisModule.getLatestBlockhash).toHaveBeenCalled();
            expect(blockhash).toBe('blockhash');
        });

        it('16. buildTransferTransaction calls native', async () => {
            await Artemis.buildTransferTransaction('from', 'to', '1000', 'hash');
            expect(NativeModules.ArtemisModule.buildTransferTransaction).toHaveBeenCalledWith('from', 'to', '1000', 'hash');
        });
    });

    describe('DePIN & Solana Pay', () => {
        it('17. generateDeviceIdentity calls native', async () => {
            const key = await Artemis.generateDeviceIdentity();
            expect(NativeModules.ArtemisModule.generateDeviceIdentity).toHaveBeenCalled();
            expect(key).toBe('devicePubkey');
        });

        it('18. signLocationProof calls native', async () => {
            await Artemis.signLocationProof('key', 1.0, 2.0, 1000);
            expect(NativeModules.ArtemisModule.signLocationProof).toHaveBeenCalledWith('key', 1.0, 2.0, 1000);
        });

        it('19. buildSolanaPayUri calls native', async () => {
            await Artemis.buildSolanaPayUri('rec', '1.0', 'lbl', 'msg');
            expect(NativeModules.ArtemisModule.buildSolanaPayUri).toHaveBeenCalledWith('rec', '1.0', 'lbl', 'msg');
        });

        it('20. parseSolanaPayUri calls native', async () => {
            const res = await Artemis.parseSolanaPayUri('solana:pay');
            expect(NativeModules.ArtemisModule.parseSolanaPayUri).toHaveBeenCalledWith('solana:pay');
            expect(res).toEqual({ recipient: 'recipient', amount: '1.0' });
        });
    });

    describe('Gaming & Merkle Proofs', () => {
        it('21. verifyMerkleProof calls native', async () => {
            const valid = await Artemis.verifyMerkleProof(['p1'], 'root', 'leaf');
            expect(NativeModules.ArtemisModule.verifyMerkleProof).toHaveBeenCalledWith(['p1'], 'root', 'leaf');
            expect(valid).toBe(true);
        });
    });

    describe('Seed Vault Integration', () => {
        it('22. seedVaultAuthorize calls native', async () => {
            const res = await Artemis.seedVaultAuthorize('purpose');
            expect(NativeModules.ArtemisModule.seedVaultAuthorize).toHaveBeenCalledWith('purpose');
            expect(res).toEqual({ authToken: 123 });
        });

        it('23. seedVaultCreateSeed calls native', async () => {
            const res = await Artemis.seedVaultCreateSeed('purpose');
            expect(NativeModules.ArtemisModule.seedVaultCreateSeed).toHaveBeenCalledWith('purpose');
            expect(res).toEqual({ authToken: 456 });
        });

        it('24. seedVaultImportSeed calls native', async () => {
            const res = await Artemis.seedVaultImportSeed('purpose');
            expect(NativeModules.ArtemisModule.seedVaultImportSeed).toHaveBeenCalledWith('purpose');
            expect(res).toEqual({ authToken: 789 });
        });

        it('25. seedVaultGetAccounts calls native', async () => {
            const accts = await Artemis.seedVaultGetAccounts(123);
            expect(NativeModules.ArtemisModule.seedVaultGetAccounts).toHaveBeenCalledWith(123);
            expect(accts).toHaveLength(1);
        });

        it('26. seedVaultSignMessages calls native', async () => {
            const sigs = await Artemis.seedVaultSignMessages(123, ['msg1', 'msg2']);
            expect(NativeModules.ArtemisModule.seedVaultSignMessages).toHaveBeenCalledWith(123, ['msg1', 'msg2']);
            expect(sigs).toHaveLength(2);
        });

        it('27. seedVaultSignTransactions calls native', async () => {
            const sigs = await Artemis.seedVaultSignTransactions(123, ['tx1', 'tx2']);
            expect(NativeModules.ArtemisModule.seedVaultSignTransactions).toHaveBeenCalledWith(123, ['tx1', 'tx2']);
            expect(sigs).toHaveLength(2);
        });

        it('28. seedVaultRequestPublicKeys calls native', async () => {
            const keys = await Artemis.seedVaultRequestPublicKeys(123, ["m/44'/501'/0'/0'"]);
            expect(NativeModules.ArtemisModule.seedVaultRequestPublicKeys).toHaveBeenCalledWith(123, ["m/44'/501'/0'/0'"]);
            expect(keys).toHaveLength(2);
        });

        it('29. seedVaultSignWithDerivationPath calls native', async () => {
            const sigs = await Artemis.seedVaultSignWithDerivationPath(123, "m/44'/501'/0'/0'", ['payload1']);
            expect(NativeModules.ArtemisModule.seedVaultSignWithDerivationPath).toHaveBeenCalledWith(123, "m/44'/501'/0'/0'", ['payload1']);
            expect(sigs).toHaveLength(1);
        });

        it('30. seedVaultDeauthorize calls native', async () => {
            await Artemis.seedVaultDeauthorize(123);
            expect(NativeModules.ArtemisModule.seedVaultDeauthorize).toHaveBeenCalledWith(123);
        });
    });
});
