/**
 * Artemis Solana SDK for React Native. TypeScript declarations.
 *
 * Every type below matches the shape the Android native bridge
 * resolves. The JS layer never stringifies/parses JSON across the
 * bridge; `WritableMap` structures are consumed as plain objects.
 */

export {
    Realtime,
    RealtimeStateKind,
} from './Realtime';

// Re-export MobileWalletAdapter + related types.
export {
    MobileWalletAdapter,
    MobileWalletAdapterConfig,
    MobileWalletName,
    MwaCapabilities,
    MwaAccount,
    MwaSendOptions,
    AuthorizationResult,
    SignInPayload,
    SignInResult,
    TransactionSendResult,
    BatchSendResult,
    MWA_FEATURES,
    transact,
} from './MobileWalletAdapter';

// Gaming
export {
    GameAction,
    GameFrame,
    FrameResult,
    ArcanaFlowConfig,
    ArcanaFlow,
    Player,
    SessionEvent,
    GameSession,
    SessionConfig,
    GameSessionManager,
    FeeRecommendation,
    CongestionLevel,
    AdaptiveFee,
} from './Gaming';

// Enhanced helpers
export {
    VersionedTxConfig,
    TxSizeEstimate,
    ComputeBudget,
    ComputePreset,
    CnftAsset,
    CnftMetadata,
    PaymentSession,
    SimulationResult,
    TransactionPreview as TransactionPreviewType,
    AccountType,
    VersionedTx,
    ComputeOptimizer,
    Cnft,
    SolanaPay,
    TransactionPreview,
    AccountDetector,
} from './Enhanced';

// Base58 + crypto utilities (cross-platform)
export declare const Base58: {
    encode(bytes: Uint8Array | string): Promise<string>;
    decode(input: string): Promise<Uint8Array>;
    decodeOrNull(input: string): Promise<Uint8Array | null>;
    isValid(input: string): Promise<boolean>;
    isValidPubkey(input: string): Promise<boolean>;
    isValidSignature(input: string): Promise<boolean>;
    fromBase64(base64: string): Promise<string>;
    toBase64(base58: string): Promise<string>;
};

export declare const Base58Check: {
    encode(bytes: Uint8Array | string): Promise<string>;
    decode(input: string): Promise<Uint8Array>;
};

export declare const Crypto: {
    generateKeypair(): Promise<{ publicKey: string; secretKey: string }>;
    sha256(data: Uint8Array | string): Promise<string>;
    sign(message: Uint8Array, secretKey: Uint8Array): Promise<Uint8Array>;
    verify(signature: Uint8Array, message: Uint8Array, publicKey: Uint8Array): Promise<boolean>;
};

export declare const ArtemisPlatform: {
    hasMWA: boolean;
    hasSeedVault: boolean;
    os: 'ios' | 'android' | 'windows' | 'macos' | 'web';
};

import type {
    AuthorizationResult,
    BatchSendResult,
    MwaCapabilities,
    MwaSendOptions,
    SignInPayload,
    TransactionSendResult,
} from './MobileWalletAdapter';

export interface SeedVaultAuthorization {
    /**
     * Opaque token produced by the Seed Vault system service. Treated
     * as a string everywhere on the bridge. Upstream `AuthToken` is
     * already string-typed, and the native side accepts string input
     * unchanged.
     */
    authToken: string;
    accountId: number;
}

export interface SeedVaultAccount {
    id: number;
    name: string;
    publicKey: string;
    derivationPath?: string;
}

export interface SolanaPayRequest {
    recipient: string;
    amount?: string;
    label?: string;
    message?: string;
    memo?: string;
}

export interface RealtimeStateEvent {
    kind: 'Idle' | 'Connecting' | 'Connected' | 'Reconnecting' | 'Closed';
    epoch: number;
    endpoint?: string;
    subscriptions?: number;
    attempt?: number;
    nextDelayMs?: number;
    reason?: string;
}

export interface AccountNotification {
    pubkey: string;
    lamports: number;
    slot: number;
    data?: string;
    owner?: string;
}

export interface SignatureNotification {
    signature: string;
    confirmed: boolean;
}

export interface DigitalAsset {
    id: string;
    name: string;
    symbol: string;
    uri: string;
    owner: string;
    royaltyBasisPoints: number;
    isCompressed: boolean;
    frozen: boolean;
    collectionAddress?: string;
    collectionVerified: boolean;
}

export interface InstructionShape {
    programId: string;
    accounts: Array<{ pubkey: string; isSigner: boolean; isWritable: boolean }>;
    /** Base64 encoded instruction data. */
    data: string;
}

export interface PdaResult {
    address: string;
    bump: number;
}

declare const Artemis: {
    /** Initialize the MWA adapter. Required before any wallet method. */
    initialize(
        identityUri: string,
        iconPath: string,
        identityName: string,
        chain: string,
    ): void;

    // ─── MWA 2.0 ──────────────────────────────────────────────────────────

    connect(): Promise<AuthorizationResult>;
    connectWithFeatures(
        features?: string[] | null,
        addresses?: string[] | null,
    ): Promise<AuthorizationResult>;
    connectWithSignIn(payload: SignInPayload): Promise<AuthorizationResult>;
    reauthorize(): Promise<AuthorizationResult>;
    deauthorize(): Promise<void>;
    getCapabilities(): Promise<MwaCapabilities>;
    cloneAuthorization(): Promise<string>;

    signTransaction(base64Tx: string): Promise<string>;
    signTransactions(base64Txs: string[]): Promise<string[]>;
    signAndSendTransaction(
        base64Tx: string,
        options?: MwaSendOptions | null,
    ): Promise<TransactionSendResult>;
    signAndSendTransactions(
        base64Txs: string[],
        options?: MwaSendOptions | null,
    ): Promise<BatchSendResult>;

    signMessage(base64Msg: string): Promise<string>;
    signMessages(base64Msgs: string[]): Promise<string[]>;
    signMessagesDetached(
        base64Msgs: string[],
    ): Promise<{ messages: string[]; signatures: string[] }>;

    // ─── RPC ──────────────────────────────────────────────────────────────

    setRpcUrl(url: string): void;
    setWsUrl(url: string): void;
    setDasUrl(url: string): void;

    getBalance(pubkey: string): Promise<string>;
    getLatestBlockhash(): Promise<string>;
    getAccountInfo(
        pubkey: string,
        commitment?: string | null,
        encoding?: string | null,
    ): Promise<string>;
    getMultipleAccounts(pubkeys: string[], commitment?: string | null): Promise<string>;
    getTokenAccountsByOwner(
        owner: string,
        mint?: string | null,
        programId?: string | null,
        commitment?: string | null,
    ): Promise<string>;
    simulateTransaction(
        base64Tx: string,
        sigVerify: boolean,
        replaceRecentBlockhash: boolean,
        commitment?: string | null,
    ): Promise<string>;
    sendRawTransaction(
        base64Tx: string,
        skipPreflight: boolean,
        maxRetries?: number | null,
    ): Promise<string>;
    getSignatureStatuses(
        signatures: string[],
        searchTransactionHistory: boolean,
    ): Promise<string>;
    getSlot(commitment?: string | null): Promise<string>;
    getBlockHeight(commitment?: string | null): Promise<string>;
    getMinimumBalanceForRentExemption(
        dataLength: number,
        commitment?: string | null,
    ): Promise<string>;

    // ─── Realtime (WebSocket subscriptions) ───────────────────────────────

    realtimeConnect(): Promise<void>;
    realtimeClose(): Promise<void>;
    /**
     * Subscribe to account changes. Returns the event name the native
     * side will emit on `DeviceEventEmitter`. Pair with
     * `DeviceEventEmitter.addListener(eventName, handler)` on the JS
     * side; the handler receives an [AccountNotification].
     */
    subscribeAccount(pubkey: string, commitment?: string | null): Promise<string>;
    /**
     * Subscribe to signature confirmation. Returns the event name the
     * native side will emit with a [SignatureNotification] body.
     */
    subscribeSignature(signature: string, commitment?: string | null): Promise<string>;

    // ─── DAS (digital asset standard) ─────────────────────────────────────

    dasAssetsByOwner(owner: string, page: number, limit: number): Promise<DigitalAsset[]>;
    dasAsset(assetId: string): Promise<DigitalAsset | null>;
    dasAssetsByCollection(collectionAddress: string): Promise<DigitalAsset[]>;

    // ─── Compute budget ───────────────────────────────────────────────────

    computeBudgetSetUnitLimit(units: number): Promise<InstructionShape>;
    computeBudgetSetUnitPrice(microLamports: string): Promise<InstructionShape>;

    // ─── PDA / ATA derivation ─────────────────────────────────────────────

    findProgramAddress(seedsBase64: string[], programId: string): Promise<PdaResult>;
    getAssociatedTokenAddress(
        owner: string,
        mint: string,
        tokenProgram?: string | null,
    ): Promise<string>;

    // ─── System program helpers ───────────────────────────────────────────

    buildTransferTransaction(
        from: string,
        to: string,
        lamports: string,
        blockhash: string,
    ): Promise<string>;

    // ─── DePIN ────────────────────────────────────────────────────────────

    generateDeviceIdentity(): Promise<string>;
    signLocationProof(
        devicePubkey: string,
        lat: number,
        lng: number,
        timestamp: number,
    ): Promise<string>;

    // ─── Solana Pay ───────────────────────────────────────────────────────

    buildSolanaPayUri(
        recipient: string,
        amount: string,
        label: string,
        message: string,
    ): Promise<string>;
    parseSolanaPayUri(uri: string): Promise<SolanaPayRequest>;

    // ─── Gaming ───────────────────────────────────────────────────────────

    verifyMerkleProof(proof: string[], root: string, leaf: string): Promise<boolean>;

    // ─── Seed Vault (Android wallet apps only) ───────────────────────────

    seedVaultAuthorize(purpose: string): Promise<SeedVaultAuthorization>;
    seedVaultCreateSeed(purpose: string): Promise<SeedVaultAuthorization>;
    seedVaultImportSeed(purpose: string): Promise<SeedVaultAuthorization>;
    seedVaultGetAccounts(authToken: string): Promise<SeedVaultAccount[]>;
    seedVaultSignMessages(authToken: string, base64Messages: string[]): Promise<string[]>;
    seedVaultSignTransactions(authToken: string, base64Transactions: string[]): Promise<string[]>;
    seedVaultRequestPublicKeys(authToken: string, derivationPaths: string[]): Promise<string[]>;
    seedVaultSignWithDerivationPath(
        authToken: string,
        derivationPath: string,
        base64Payloads: string[],
    ): Promise<string[]>;
    seedVaultDeauthorize(authToken: string): Promise<void>;
};

export default Artemis;
