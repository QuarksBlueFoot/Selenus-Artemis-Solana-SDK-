import {
    BaseWalletAdapter,
    WalletAdapterNetwork,
    WalletName,
    WalletReadyState,
    WalletConnectionError,
    WalletDisconnectedError,
    WalletPublicKeyError,
    WalletSignTransactionError,
    WalletWindowClosedError,
} from '@solana/wallet-adapter-base';
import { PublicKey, Transaction, VersionedTransaction } from '@solana/web3.js';
import { NativeModules } from 'react-native';
import { Buffer } from 'buffer';

const { ArtemisModule } = NativeModules;

export interface MobileWalletAdapterConfig {
    identityUri: string;
    iconPath: string;
    identityName: string;
    chain?: string;
}

export const MobileWalletName = 'Mobile Wallet Adapter' as WalletName<'Mobile Wallet Adapter'>;

export class MobileWalletAdapter extends BaseWalletAdapter {
    name = MobileWalletName;
    url = 'https://github.com/solana-mobile/mobile-wallet-adapter';
    icon = 'data:image/svg+xml;base64,...'; // Placeholder
    supportedTransactionVersions = new Set(['legacy', 0] as const);

    private _publicKey: PublicKey | null = null;
    private _connecting: boolean = false;
    private _config: MobileWalletAdapterConfig;

    constructor(config: MobileWalletAdapterConfig) {
        super();
        this._config = config;
        ArtemisModule.initialize(
            config.identityUri,
            config.iconPath,
            config.identityName,
            config.chain || 'solana:mainnet'
        );
    }

    get publicKey() {
        return this._publicKey;
    }

    get connecting() {
        return this._connecting;
    }

    get readyState() {
        return WalletReadyState.Installed;
    }

    async connect(): Promise<void> {
        try {
            if (this.connected || this.connecting) return;
            this._connecting = true;

            const pubkeyStr = await ArtemisModule.connect();
            this._publicKey = new PublicKey(pubkeyStr);
            
            this.emit('connect', this._publicKey);
        } catch (error: any) {
            throw new WalletConnectionError(error?.message, error);
        } finally {
            this._connecting = false;
        }
    }

    async disconnect(): Promise<void> {
        this._publicKey = null;
        this.emit('disconnect');
    }

    async signTransaction<T extends Transaction | VersionedTransaction>(transaction: T): Promise<T> {
        try {
            const serialized = transaction.serialize({ requireAllSignatures: false });
            const base64Tx = Buffer.from(serialized).toString('base64');
            
            const signedBase64 = await ArtemisModule.signTransaction(base64Tx);
            const signedBytes = Buffer.from(signedBase64, 'base64');

            if (transaction instanceof VersionedTransaction) {
                return VersionedTransaction.deserialize(signedBytes) as T;
            } else {
                return Transaction.from(signedBytes) as T;
            }
        } catch (error: any) {
            throw new WalletSignTransactionError(error?.message, error);
        }
    }

    async signAllTransactions<T extends Transaction | VersionedTransaction>(transactions: T[]): Promise<T[]> {
        // MWA supports batch signing, but for simplicity we iterate here or implement batch in native
        // ArtemisModule.signTransactions is not yet exposed, so we loop.
        // Note: This is inefficient for MWA, ideally we expose signTransactions
        const signed: T[] = [];
        for (const tx of transactions) {
            signed.push(await this.signTransaction(tx));
        }
        return signed;
    }

    async signMessage(message: Uint8Array): Promise<Uint8Array> {
        try {
            const base64Msg = Buffer.from(message).toString('base64');
            const signatureBase64 = await ArtemisModule.signMessage(base64Msg);
            return new Uint8Array(Buffer.from(signatureBase64, 'base64'));
        } catch (error: any) {
            throw new WalletSignTransactionError(error?.message, error);
        }
    }
}
