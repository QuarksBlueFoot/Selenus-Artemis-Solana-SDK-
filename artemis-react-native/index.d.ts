/**
 * Artemis Solana SDK for React Native - TypeScript Declarations
 */

// Re-export MobileWalletAdapter
export { MobileWalletAdapter, MobileWalletAdapterConfig, MobileWalletName } from './MobileWalletAdapter';

// Gaming exports
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
  AdaptiveFee
} from './Gaming';

// Enhanced exports
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
  AccountDetector
} from './Enhanced';

// Base58 utilities
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

// Main Artemis namespace
declare const Artemis: {
  // Initialization
  initialize(identityUri: string, iconPath: string, identityName: string, chain: string): void;
  
  // Wallet Connection (Android MWA)
  connect(): Promise<string>;
  signTransaction(base64Tx: string): Promise<string>;
  signAndSendTransaction(base64Tx: string): Promise<string>;
  signMessage(base64Msg: string): Promise<string>;
  
  // SignIn With Solana
  connectWithSignIn(payload: {
    domain: string;
    uri?: string;
    statement?: string;
    resources?: string[];
    chainId?: string;
  }): Promise<{
    address: string;
    signature: string;
    message: string;
  }>;
  
  // RPC Methods
  setRpcUrl(url: string): void;
  getBalance(pubkey: string): Promise<string>;
  getLatestBlockhash(): Promise<string>;
  
  // Program Methods
  buildTransferTransaction(from: string, to: string, lamports: string, blockhash: string): Promise<string>;
  
  // DePIN Methods
  generateDeviceIdentity(): Promise<string>;
  signLocationProof(devicePubkey: string, lat: number, lng: number, timestamp: number): Promise<string>;
  
  // Solana Pay Methods
  buildSolanaPayUri(recipient: string, amount: string, label: string, message: string): Promise<string>;
  parseSolanaPayUri(uri: string): Promise<{
    recipient: string;
    amount?: string;
    label?: string;
    message?: string;
    memo?: string;
  }>;
  
  // Gaming Methods
  verifyMerkleProof(proof: string[], root: string, leaf: string): Promise<boolean>;
  
  // Seed Vault Methods (Android Only)
  seedVaultAuthorize(purpose: string): Promise<{ authToken: number }>;
  seedVaultCreateSeed(purpose: string): Promise<{ authToken: number }>;
  seedVaultImportSeed(purpose: string): Promise<{ authToken: number }>;
  seedVaultGetAccounts(authToken: number): Promise<Array<{
    publicKey: string;
    derivationPath: string;
  }>>;
  seedVaultSignMessages(authToken: number, messages: string[]): Promise<string[]>;
  seedVaultSignTransactions(authToken: number, transactions: string[]): Promise<string[]>;
  seedVaultRequestPublicKeys(authToken: number, derivationPaths: string[]): Promise<string[]>;
  seedVaultSignWithDerivationPath(authToken: number, derivationPath: string, payloads: string[]): Promise<string[]>;
  seedVaultDeauthorize(authToken: number): Promise<void>;
};

export default Artemis;
