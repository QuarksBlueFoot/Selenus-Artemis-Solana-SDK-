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
    WalletSendTransactionError,
    SendTransactionOptions,
} from '@solana/wallet-adapter-base';
import { PublicKey, Transaction, VersionedTransaction, Connection, TransactionSignature } from '@solana/web3.js';
import { NativeModules } from 'react-native';
import { Buffer } from 'buffer';

const { ArtemisModule } = NativeModules;

// ============================================================================
// Types - Full MWA 2.0 Parity
// ============================================================================

export interface MobileWalletAdapterConfig {
    identityUri: string;
    iconPath: string;
    identityName: string;
    chain?: string;
}

/**
 * MWA 2.0 Capabilities returned by get_capabilities
 */
export interface MwaCapabilities {
    maxTransactionsPerRequest: number;
    maxMessagesPerRequest: number;
    supportedTransactionVersions: Array<'legacy' | number>;
    features: string[];
    supportsSignAndSendTransactions: boolean;
    supportsCloneAuthorization: boolean;
}

/**
 * Account returned by authorize
 */
export interface MwaAccount {
    address: string; // base58 encoded
    label?: string;
    icon?: string;
    chains?: string[];
    features?: string[];
}

/**
 * Sign In With Solana (SIWS) payload
 */
export interface SignInPayload {
    domain?: string;
    address?: string;
    statement?: string;
    uri?: string;
    version?: string;
    chainId?: string;
    nonce?: string;
    issuedAt?: string;
    expirationTime?: string;
    notBefore?: string;
    requestId?: string;
    resources?: string[];
}

/**
 * Sign In With Solana result
 */
export interface SignInResult {
    address: string;
    signedMessage: Uint8Array;
    signature: Uint8Array;
    signatureType?: string;
}

/**
 * Options for sign_and_send_transactions (MWA 2.0)
 */
export interface MwaSendOptions {
    minContextSlot?: number;
    commitment?: 'processed' | 'confirmed' | 'finalized';
    skipPreflight?: boolean;
    maxRetries?: number;
    preflightCommitment?: 'processed' | 'confirmed' | 'finalized';
    waitForCommitmentToSendNextTransaction?: boolean;
}

// MWA 2.0 Feature Identifiers
export const MWA_FEATURES = {
    SIGN_AND_SEND_TRANSACTIONS: 'solana:signAndSendTransaction',
    SIGN_TRANSACTIONS: 'solana:signTransactions',
    SIGN_MESSAGES: 'solana:signMessages',
    SIGN_IN_WITH_SOLANA: 'solana:signInWithSolana',
    CLONE_AUTHORIZATION: 'solana:cloneAuthorization',
} as const;

export const MobileWalletName = 'Mobile Wallet Adapter' as WalletName<'Mobile Wallet Adapter'>;

/**
 * MobileWalletAdapter - Full MWA 2.0 Implementation
 * 
 * Drop-in compatible with @solana-mobile/wallet-adapter-mobile.
 * Provides all MWA 2.0 features:
 * - authorize / reauthorize / deauthorize / cloneAuthorization
 * - signTransactions / signAndSendTransactions
 * - signMessages / signMessagesDetached
 * - Sign In With Solana (SIWS)
 * - Multi-account support
 * - Transaction version detection
 */
export class MobileWalletAdapter extends BaseWalletAdapter {
    name = MobileWalletName;
    url = 'https://github.com/solana-mobile/mobile-wallet-adapter';
    icon = 'data:image/svg+xml;base64,...'; // Placeholder
    supportedTransactionVersions = new Set(['legacy', 0] as const);

    private _publicKey: PublicKey | null = null;
    private _connecting: boolean = false;
    private _config: MobileWalletAdapterConfig;
    private _capabilities: MwaCapabilities | null = null;
    private _accounts: MwaAccount[] = [];

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

    /** Get all connected accounts (multi-account support) */
    get accounts(): MwaAccount[] {
        return this._accounts;
    }

    /** Get cached capabilities */
    get capabilities(): MwaCapabilities | null {
        return this._capabilities;
    }

    async connect(): Promise<void> {
        try {
            if (this.connected || this.connecting) return;
            this._connecting = true;

            const result = await ArtemisModule.connect();
            const parsed = JSON.parse(result);
            
            this._publicKey = new PublicKey(parsed.address);
            this._accounts = parsed.accounts || [{ address: parsed.address }];
            this._capabilities = parsed.capabilities;
            
            this.emit('connect', this._publicKey);
        } catch (error: any) {
            throw new WalletConnectionError(error?.message, error);
        } finally {
            this._connecting = false;
        }
    }

    /**
     * Connect with Sign In With Solana (SIWS)
     */
    async connectWithSignIn(payload: SignInPayload): Promise<SignInResult> {
        try {
            if (this.connected || this.connecting) return {} as SignInResult;
            this._connecting = true;

            const result = await ArtemisModule.connectWithSignIn(JSON.stringify(payload));
            const parsed = JSON.parse(result);
            
            this._publicKey = new PublicKey(parsed.address);
            this._accounts = parsed.accounts || [{ address: parsed.address }];
            this._capabilities = parsed.capabilities;
            
            this.emit('connect', this._publicKey);
            
            return {
                address: parsed.address,
                signedMessage: new Uint8Array(Buffer.from(parsed.signedMessage, 'base64')),
                signature: new Uint8Array(Buffer.from(parsed.signature, 'base64')),
                signatureType: parsed.signatureType,
            };
        } catch (error: any) {
            throw new WalletConnectionError(error?.message, error);
        } finally {
            this._connecting = false;
        }
    }

    async disconnect(): Promise<void> {
        try {
            await ArtemisModule.deauthorize();
        } catch {
            // Best effort
        }
        this._publicKey = null;
        this._accounts = [];
        this._capabilities = null;
        this.emit('disconnect');
    }

    /**
     * Get wallet capabilities (MWA 2.0)
     */
    async getCapabilities(): Promise<MwaCapabilities> {
        if (this._capabilities) return this._capabilities;
        
        const result = await ArtemisModule.getCapabilities();
        this._capabilities = JSON.parse(result);
        return this._capabilities!;
    }

    /**
     * Clone the current authorization (MWA 2.0 optional feature)
     */
    async cloneAuthorization(): Promise<string> {
        const caps = await this.getCapabilities();
        if (!caps.supportsCloneAuthorization) {
            throw new Error('Wallet does not support clone_authorization');
        }
        return await ArtemisModule.cloneAuthorization();
    }

    async signTransaction<T extends Transaction | VersionedTransaction>(transaction: T): Promise<T> {
        try {
            const serialized = transaction.serialize({ requireAllSignatures: false });
            const base64Tx = Buffer.from(serialized).toString('base64');
            
            const signedBase64 = await ArtemisModule.signTransaction(base64Tx);
            const signedBytes = Buffer.from(signedBase64, 'base64');
            const signedArray = new Uint8Array(signedBytes);

            if (transaction instanceof VersionedTransaction) {
                return VersionedTransaction.deserialize(signedArray) as T;
            } else {
                return Transaction.from(signedArray) as T;
            }
        } catch (error: any) {
            throw new WalletSignTransactionError(error?.message, error);
        }
    }

    /**
     * Sign and send transaction with MWA 2.0 options
     */
    async signAndSendTransaction(
        transaction: Transaction | VersionedTransaction,
        options?: MwaSendOptions
    ): Promise<TransactionSignature> {
        try {
            const serialized = transaction.serialize({ requireAllSignatures: false });
            const base64Tx = Buffer.from(serialized).toString('base64');
            
            const signature = await ArtemisModule.signAndSendTransaction(
                base64Tx,
                JSON.stringify(options || {})
            );
            return signature;
        } catch (error: any) {
            throw new WalletSendTransactionError(error?.message, error);
        }
    }

    async sendTransaction(
        transaction: Transaction | VersionedTransaction,
        connection: Connection,
        options: SendTransactionOptions = {}
    ): Promise<TransactionSignature> {
        // Try to use wallet's sign-and-send if supported
        const caps = await this.getCapabilities();
        if (caps.supportsSignAndSendTransactions) {
            return await this.signAndSendTransaction(transaction, {
                skipPreflight: options.skipPreflight,
                maxRetries: options.maxRetries,
                preflightCommitment: options.preflightCommitment as any,
            });
        }
        
        // Fall back to sign then send via connection
        try {
            const signedTransaction = await this.signTransaction(transaction);
            const rawTransaction = signedTransaction.serialize();
            return await connection.sendRawTransaction(rawTransaction, options);
        } catch (error: any) {
            throw new WalletSendTransactionError(error?.message, error);
        }
    }

    async signAllTransactions<T extends Transaction | VersionedTransaction>(transactions: T[]): Promise<T[]> {
        try {
            const serialized = transactions.map(tx => 
                Buffer.from(tx.serialize({ requireAllSignatures: false })).toString('base64')
            );
            
            const signedBase64Array = await ArtemisModule.signTransactions(JSON.stringify(serialized));
            const signedList = JSON.parse(signedBase64Array) as string[];
            
            return signedList.map((signedBase64, index) => {
                const signedBytes = Buffer.from(signedBase64, 'base64');
                const signedArray = new Uint8Array(signedBytes);
                const originalTx = transactions[index];
                
                if (originalTx instanceof VersionedTransaction) {
                    return VersionedTransaction.deserialize(signedArray) as T;
                } else {
                    return Transaction.from(signedArray) as T;
                }
            });
        } catch (error: any) {
            throw new WalletSignTransactionError(error?.message, error);
        }
    }

    /**
     * Sign and send multiple transactions with MWA 2.0 options
     * Supports waitForCommitmentToSendNextTransaction for dependent transactions
     */
    async signAndSendTransactions(
        transactions: Array<Transaction | VersionedTransaction>,
        options?: MwaSendOptions
    ): Promise<TransactionSignature[]> {
        try {
            const serialized = transactions.map(tx => 
                Buffer.from(tx.serialize({ requireAllSignatures: false })).toString('base64')
            );
            
            const signaturesJson = await ArtemisModule.signAndSendTransactions(
                JSON.stringify(serialized),
                JSON.stringify(options || {})
            );
            return JSON.parse(signaturesJson) as string[];
        } catch (error: any) {
            throw new WalletSendTransactionError(error?.message, error);
        }
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

    /**
     * Sign messages with detached signatures (improved MWA 2.0 API)
     * Returns signatures separately from the original messages
     */
    async signMessagesDetached(messages: Uint8Array[]): Promise<{messages: Uint8Array[], signatures: Uint8Array[]}> {
        try {
            const base64Messages = messages.map(m => Buffer.from(m).toString('base64'));
            const resultJson = await ArtemisModule.signMessagesDetached(JSON.stringify(base64Messages));
            const result = JSON.parse(resultJson);
            
            return {
                messages: result.messages.map((m: string) => new Uint8Array(Buffer.from(m, 'base64'))),
                signatures: result.signatures.map((s: string) => new Uint8Array(Buffer.from(s, 'base64'))),
            };
        } catch (error: any) {
            throw new WalletSignTransactionError(error?.message, error);
        }
    }
}
