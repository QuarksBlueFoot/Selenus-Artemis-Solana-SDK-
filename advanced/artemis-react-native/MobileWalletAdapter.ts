import {
    BaseWalletAdapter,
    WalletName,
    WalletReadyState,
    WalletConnectionError,
    WalletSignTransactionError,
    WalletSendTransactionError,
    SendTransactionOptions,
} from '@solana/wallet-adapter-base';
import {
    PublicKey,
    Transaction,
    VersionedTransaction,
    Connection,
    TransactionSignature,
} from '@solana/web3.js';
import { NativeModules, Platform } from 'react-native';
import { Buffer } from 'buffer';

const { ArtemisModule } = NativeModules;

// ============================================================================
// MWA 2.0 types (structured bridge contract)
//
// Every shape below matches what the native Android bridge emits via
// WritableMap / WritableArray. The bridge never hands the JS layer an
// opaque JSON string. All wire data is already a plain JS object by the
// time it reaches this file. That removes the double-parse trap the
// earlier revision had and keeps the React Native debugger usable.
// ============================================================================

export interface MobileWalletAdapterConfig {
    identityUri: string;
    iconPath: string;
    identityName: string;
    /** Chain identifier in CAIP-2 form, e.g. "solana:mainnet". */
    chain?: string;
}

export interface MwaCapabilities {
    maxTransactionsPerRequest: number;
    maxMessagesPerRequest: number;
    supportedTransactionVersions: Array<'legacy' | number>;
    features: string[];
    supportsSignAndSendTransactions: boolean;
    supportsCloneAuthorization: boolean;
    supportsSignTransactions: boolean;
    supportsSignIn: boolean;
    supportsLegacyTransactions: boolean;
    supportsVersionedTransactions: boolean;
}

/**
 * Account returned by the wallet. `address` is the upstream-native
 * base64-encoded public-key bytes; `addressBase58` is the same material
 * rendered in the Solana-idiomatic form. Both are always present so
 * callers pick the encoding that fits their downstream library without
 * re-encoding themselves.
 */
export interface MwaAccount {
    address: string;
    addressBase58: string;
    label?: string;
    icon?: string;
    displayAddress?: string;
    displayAddressFormat?: string;
    chains?: string[];
    features?: string[];
}

export interface AuthorizationResult {
    authToken: string;
    /** Primary account's base58 public key, convenience for single-account apps. */
    address: string;
    accounts: MwaAccount[];
    walletUriBase?: string;
    walletIcon?: string;
    capabilities: MwaCapabilities;
    /** Present only when the authorize call carried a SIWS payload. */
    signInResult?: SignInResult;
}

export interface SignInPayload {
    domain: string;
    uri?: string;
    statement?: string;
    resources?: string[];
    version?: string;
    chainId?: string;
    nonce?: string;
    issuedAt?: string;
    expirationTime?: string;
    notBefore?: string;
    requestId?: string;
}

export interface SignInResult {
    /** Base64-encoded public-key bytes (matches upstream). */
    address: string;
    /** Base64-encoded signed message bytes. */
    signedMessage: string;
    /** Base64-encoded signature bytes. */
    signature: string;
    signatureType?: string;
}

export interface MwaSendOptions {
    minContextSlot?: number;
    commitment?: 'processed' | 'confirmed' | 'finalized';
    skipPreflight?: boolean;
    maxRetries?: number;
    preflightCommitment?: 'processed' | 'confirmed' | 'finalized';
    waitForConfirmation?: boolean;
    confirmationTimeout?: number;
    waitForCommitmentToSendNextTransaction?: boolean;
}

/**
 * Per-transaction status for a batched sign-and-send call. Mirrors the
 * Artemis-native `SendTransactionResult` invariant:
 *   - `isSuccess` is true iff `signature` is non-empty and no error set
 *   - `isFailure` is true iff `error` is set
 *   - `isSignedButNotBroadcast` is true iff the wallet signed but did
 *     not broadcast, and the raw signed bytes are returned in
 *     `signedRaw` so callers can submit through their own RPC.
 * Exactly one of the three flags is true for every well-formed entry.
 */
export interface TransactionSendResult {
    index: number;
    signature: string;
    confirmed: boolean;
    slot?: number;
    error?: string;
    signedRaw?: string;
    isSuccess: boolean;
    isFailure: boolean;
    isSignedButNotBroadcast: boolean;
}

export interface BatchSendResult {
    results: TransactionSendResult[];
    successCount: number;
    failureCount: number;
}

export const MWA_FEATURES = {
    SIGN_AND_SEND_TRANSACTIONS: 'solana:signAndSendTransaction',
    SIGN_TRANSACTIONS: 'solana:signTransactions',
    SIGN_MESSAGES: 'solana:signMessages',
    SIGN_IN_WITH_SOLANA: 'solana:signInWithSolana',
    CLONE_AUTHORIZATION: 'solana:cloneAuthorization',
} as const;

export const MobileWalletName = 'Mobile Wallet Adapter' as WalletName<'Mobile Wallet Adapter'>;

/**
 * MobileWalletAdapter. Drop-in compatible with
 * `@solana-mobile/wallet-adapter-mobile` on Android, surface-disabled
 * on every other platform.
 *
 * Lifecycle:
 *   1. `new MobileWalletAdapter(config)`: initializes the native module.
 *   2. `connect()` or `connectWithSignIn(payload)`: opens the wallet,
 *      runs authorize, and caches accounts + capabilities + auth token.
 *   3. `signTransaction / signAndSendTransaction / signMessage / ...`:
 *      every call uses the cached auth token under the hood.
 *   4. `disconnect()`: deauthorize and clear cached state.
 */
export class MobileWalletAdapter extends BaseWalletAdapter {
    name = MobileWalletName;
    url = 'https://github.com/solana-mobile/mobile-wallet-adapter';
    icon = 'data:image/svg+xml;base64,';
    supportedTransactionVersions = new Set(['legacy', 0] as const);

    private _publicKey: PublicKey | null = null;
    private _connecting = false;
    private _authToken: string | null = null;
    private _config: MobileWalletAdapterConfig;
    private _capabilities: MwaCapabilities | null = null;
    private _accounts: MwaAccount[] = [];
    private _walletUriBase: string | null = null;

    constructor(config: MobileWalletAdapterConfig) {
        super();
        if (!config.iconPath.startsWith('https://') || config.iconPath.length <= 'https://'.length) {
            throw new WalletConnectionError(
                'MWA identity iconPath must be an absolute HTTPS URI, e.g. https://myapp.example.com/favicon.ico',
            );
        }
        this._config = config;
        if (Platform.OS === 'android' && ArtemisModule) {
            ArtemisModule.initialize(
                config.identityUri,
                config.iconPath,
                config.identityName,
                config.chain || 'solana:mainnet',
            );
        }
    }

    get publicKey(): PublicKey | null {
        return this._publicKey;
    }

    get connecting(): boolean {
        return this._connecting;
    }

    /**
     * MWA is an Android-only path. iOS returns `Unsupported` so the
     * wallet-adapter UI hides the entry point; missing native module
     * on Android falls back to `NotDetected` so dapps don't surface
     * a "connect" button that would always error out.
     */
    get readyState(): WalletReadyState {
        if (Platform.OS !== 'android') return WalletReadyState.Unsupported;
        if (!ArtemisModule) return WalletReadyState.NotDetected;
        return WalletReadyState.Installed;
    }

    get accounts(): MwaAccount[] {
        return this._accounts;
    }

    get capabilities(): MwaCapabilities | null {
        return this._capabilities;
    }

    get authToken(): string | null {
        return this._authToken;
    }

    get walletUriBase(): string | null {
        return this._walletUriBase;
    }

    async connect(): Promise<void> {
        if (this.connected || this.connecting) return;
        this._connecting = true;
        try {
            const result: AuthorizationResult = await this.mwaModule().connect();
            this.applyAuthorization(result);
            this.emit('connect', this._publicKey!);
        } catch (error: any) {
            throw new WalletConnectionError(error?.message ?? 'MWA connect failed', error);
        } finally {
            this._connecting = false;
        }
    }

    /**
     * Authorize the session and, if the wallet supports SIWS, return the
     * signed sign-in payload alongside the full authorization result.
     * Throws instead of returning an empty object; prior revisions
     * silently returned `{}` on already-connected state, which callers
     * couldn't distinguish from a real sign-in.
     */
    async connectWithSignIn(payload: SignInPayload): Promise<AuthorizationResult> {
        if (this._connecting) {
            throw new WalletConnectionError('Wallet is already connecting');
        }
        if (this.connected) {
            throw new WalletConnectionError(
                'Wallet already connected. Call disconnect() before connectWithSignIn().',
            );
        }
        this._connecting = true;
        try {
            const result: AuthorizationResult = await this.mwaModule().connectWithSignIn(payload);
            this.applyAuthorization(result);
            this.emit('connect', this._publicKey!);
            return result;
        } catch (error: any) {
            throw new WalletConnectionError(error?.message ?? 'MWA sign-in failed', error);
        } finally {
            this._connecting = false;
        }
    }

    /**
     * Silent reauthorize: uses the stored auth token to refresh the
     * session without re-prompting the user. Populates the same fields
     * `connect()` does.
     */
    async reauthorize(): Promise<AuthorizationResult> {
        try {
            const result: AuthorizationResult = await this.mwaModule().reauthorize();
            this.applyAuthorization(result);
            return result;
        } catch (error: any) {
            throw new WalletConnectionError(error?.message ?? 'MWA reauthorize failed', error);
        }
    }

    async disconnect(): Promise<void> {
        try {
            if (Platform.OS === 'android' && ArtemisModule) {
                await ArtemisModule.deauthorize();
            }
        } catch {
            // Best effort; the wallet side may have already rotated.
        }
        this._publicKey = null;
        this._authToken = null;
        this._accounts = [];
        this._capabilities = null;
        this._walletUriBase = null;
        this.emit('disconnect');
    }

    async getCapabilities(): Promise<MwaCapabilities> {
        if (this._capabilities) return this._capabilities;
        const caps: MwaCapabilities = await this.mwaModule().getCapabilities();
        this._capabilities = caps;
        return caps;
    }

    /**
     * Clone the current authorization. MWA 2.0 optional feature; throws
     * when the wallet does not advertise `solana:cloneAuthorization`.
     */
    async cloneAuthorization(): Promise<string> {
        const caps = await this.getCapabilities();
        if (!caps.supportsCloneAuthorization) {
            throw new Error('Wallet does not support clone_authorization');
        }
        return await this.mwaModule().cloneAuthorization();
    }

    async signTransaction<T extends Transaction | VersionedTransaction>(transaction: T): Promise<T> {
        try {
            const serialized = transaction.serialize({ requireAllSignatures: false });
            const base64Tx = Buffer.from(serialized).toString('base64');
            const signedBase64: string = await this.mwaModule().signTransaction(base64Tx);
            const signedArray = new Uint8Array(Buffer.from(signedBase64, 'base64'));
            if (transaction instanceof VersionedTransaction) {
                return VersionedTransaction.deserialize(signedArray) as T;
            }
            return Transaction.from(signedArray) as T;
        } catch (error: any) {
            throw new WalletSignTransactionError(error?.message ?? 'signTransaction failed', error);
        }
    }

    async signAllTransactions<T extends Transaction | VersionedTransaction>(
        transactions: T[],
    ): Promise<T[]> {
        try {
            const base64Array = transactions.map((tx) =>
                Buffer.from(tx.serialize({ requireAllSignatures: false })).toString('base64'),
            );
            const signedList: string[] = await this.mwaModule().signTransactions(base64Array);
            return signedList.map((signedBase64, i) => {
                const bytes = new Uint8Array(Buffer.from(signedBase64, 'base64'));
                const original = transactions[i];
                if (original instanceof VersionedTransaction) {
                    return VersionedTransaction.deserialize(bytes) as T;
                }
                return Transaction.from(bytes) as T;
            });
        } catch (error: any) {
            throw new WalletSignTransactionError(error?.message ?? 'signTransactions failed', error);
        }
    }

    /**
     * Sign-and-send a single transaction through the wallet's own RPC
     * submission path. Returns the Solana signature (base58) or a
     * signed-raw payload when the wallet does not implement
     * sign_and_send; `isSignedButNotBroadcast` tells the caller which
     * state they got.
     */
    async signAndSendTransaction(
        transaction: Transaction | VersionedTransaction,
        options?: MwaSendOptions,
    ): Promise<TransactionSendResult> {
        try {
            const serialized = transaction.serialize({ requireAllSignatures: false });
            const base64Tx = Buffer.from(serialized).toString('base64');
            const result: TransactionSendResult = await this.mwaModule().signAndSendTransaction(
                base64Tx,
                options ?? null,
            );
            return result;
        } catch (error: any) {
            throw new WalletSendTransactionError(
                error?.message ?? 'signAndSendTransaction failed',
                error,
            );
        }
    }

    /**
     * Sign-and-send a batch. Every input slot maps to a
     * [TransactionSendResult] in the same position; partial failure
     * does not collapse the batch.
     */
    async signAndSendTransactions(
        transactions: Array<Transaction | VersionedTransaction>,
        options?: MwaSendOptions,
    ): Promise<BatchSendResult> {
        try {
            const base64Array = transactions.map((tx) =>
                Buffer.from(tx.serialize({ requireAllSignatures: false })).toString('base64'),
            );
            const batch: BatchSendResult = await this.mwaModule().signAndSendTransactions(
                base64Array,
                options ?? null,
            );
            return batch;
        } catch (error: any) {
            throw new WalletSendTransactionError(
                error?.message ?? 'signAndSendTransactions failed',
                error,
            );
        }
    }

    /**
     * `sendTransaction` overload required by the wallet-adapter base
     * class. Uses the wallet's sign-and-send path when supported;
     * otherwise signs locally and broadcasts through the caller's
     * [Connection].
     */
    async sendTransaction(
        transaction: Transaction | VersionedTransaction,
        connection: Connection,
        options: SendTransactionOptions = {},
    ): Promise<TransactionSignature> {
        const caps = await this.getCapabilities();
        if (caps.supportsSignAndSendTransactions) {
            const result = await this.signAndSendTransaction(transaction, {
                skipPreflight: options.skipPreflight,
                maxRetries: options.maxRetries,
                preflightCommitment: options.preflightCommitment as any,
            });
            if (result.isSuccess) return result.signature;
            if (result.isSignedButNotBroadcast && result.signedRaw) {
                const raw = Buffer.from(result.signedRaw, 'base64');
                return await connection.sendRawTransaction(raw, options);
            }
            throw new WalletSendTransactionError(result.error ?? 'Transaction failed');
        }

        try {
            const signed = await this.signTransaction(transaction);
            const raw = signed.serialize();
            return await connection.sendRawTransaction(raw, options);
        } catch (error: any) {
            throw new WalletSendTransactionError(error?.message ?? 'sendTransaction failed', error);
        }
    }

    async signMessage(message: Uint8Array): Promise<Uint8Array> {
        try {
            const base64Msg = Buffer.from(message).toString('base64');
            const signatureBase64: string = await this.mwaModule().signMessage(base64Msg);
            return new Uint8Array(Buffer.from(signatureBase64, 'base64'));
        } catch (error: any) {
            throw new WalletSignTransactionError(error?.message ?? 'signMessage failed', error);
        }
    }

    /**
     * Sign each message separately and return detached signatures. The
     * native bridge returns `{ messages, signatures }` as arrays of
     * base64 strings; we decode into `Uint8Array` for caller ergonomics.
     */
    async signMessagesDetached(
        messages: Uint8Array[],
    ): Promise<{ messages: Uint8Array[]; signatures: Uint8Array[] }> {
        try {
            const base64Messages = messages.map((m) => Buffer.from(m).toString('base64'));
            const result: { messages: string[]; signatures: string[] } =
                await this.mwaModule().signMessagesDetached(base64Messages);
            return {
                messages: result.messages.map((m) => new Uint8Array(Buffer.from(m, 'base64'))),
                signatures: result.signatures.map((s) => new Uint8Array(Buffer.from(s, 'base64'))),
            };
        } catch (error: any) {
            throw new WalletSignTransactionError(
                error?.message ?? 'signMessagesDetached failed',
                error,
            );
        }
    }

    // ─── internals ─────────────────────────────────────────────────────────

    private mwaModule(): any {
        if (Platform.OS !== 'android') {
            throw new WalletConnectionError('Mobile Wallet Adapter is Android-only in React Native. Use an iOS-compatible wallet path on this platform.');
        }
        if (!ArtemisModule) {
            throw new WalletConnectionError('Artemis native module is not linked. Use a custom React Native dev build; Expo Go cannot load this package.');
        }
        return ArtemisModule;
    }

    private applyAuthorization(result: AuthorizationResult): void {
        this._authToken = result.authToken;
        this._accounts = result.accounts;
        this._capabilities = result.capabilities;
        this._walletUriBase = result.walletUriBase ?? null;
        const primary = result.accounts[0];
        const base58 = primary?.addressBase58 ?? result.address;
        this._publicKey = base58 ? new PublicKey(base58) : null;
    }
}

/**
 * Upstream-parity `transact(wallet, block)` wrapper. Opens an
 * authorized session, runs [block] with the same wallet instance, and
 * cleanly deauthorizes even if [block] threw. Mirrors the upstream
 * `@solana-mobile/mobile-wallet-adapter-protocol-mobile` shape so a
 * dapp migrating from the official SDK can keep its call sites.
 *
 * Pattern:
 *
 * ```ts
 * const signature = await transact(wallet, async (w) => {
 *   const result = await w.signAndSendTransaction(tx);
 *   return result.signature;
 * });
 * ```
 *
 * If [block] throws, the wrapper still calls `disconnect()` so the
 * wallet-side session is not left dangling, and re-raises the original
 * error. Callers that want to keep the session open after the block
 * should call methods on the wallet directly without going through
 * `transact`.
 */
export async function transact<T>(
    wallet: MobileWalletAdapter,
    block: (wallet: MobileWalletAdapter) => Promise<T>,
): Promise<T> {
    if (!wallet.connected) {
        await wallet.connect();
    }
    try {
        return await block(wallet);
    } finally {
        try {
            await wallet.disconnect();
        } catch {
            // Best-effort teardown; the block's outcome still dominates.
        }
    }
}
