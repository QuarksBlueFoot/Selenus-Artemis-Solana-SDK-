/**
 * Artemis Enhanced SDK for React Native
 * 
 * Includes versioned transactions, compute optimization, cNFT, Solana Pay, 
 * transaction preview, and account detection features.
 */

import { NativeModules, Platform } from 'react-native';

const { ArtemisEnhancedModule } = NativeModules;

/**
 * Versioned Transaction Configuration
 */
export interface VersionedTxConfig {
  feePayer: string;
  blockhash: string;
  computeUnits?: number;
  priorityFee?: number;
  lookupTableKeys?: string[];
}

/**
 * Transaction Size Estimate
 */
export interface TxSizeEstimate {
  estimatedBytes: number;
  accountKeyCount: number;
  staticKeyCount: number;
  instructionCount: number;
  fits1232Limit: boolean;
  recommendAlt: boolean;
  programCount: number;
}

/**
 * Compute Budget Configuration
 */
export interface ComputeBudget {
  computeUnits: number;
  microLamportsPerUnit: number;
  estimatedTotalLamports: number;
  source: 'PROFILED' | 'SIMULATED' | 'ESTIMATED' | 'DEFAULT';
  confidence: number;
}

/**
 * Compute Presets
 */
export type ComputePreset = 
  | 'TRANSFER'
  | 'TOKEN_TRANSFER'
  | 'NFT_MINT'
  | 'NFT_TRANSFER'
  | 'SWAP'
  | 'STAKE'
  | 'GAMING_ACTION'
  | 'GAMING_BATCH'
  | 'COMPLEX_TX'
  | 'MAX';

/**
 * cNFT Asset
 */
export interface CnftAsset {
  id: string;
  owner: string;
  delegate?: string;
  merkleTree: string;
  leafIndex: number;
  dataHash: string;  // Base64
  creatorHash: string;  // Base64
  name?: string;
  symbol?: string;
  uri?: string;
}

/**
 * cNFT Metadata
 */
export interface CnftMetadata {
  name: string;
  symbol: string;
  uri: string;
  sellerFeeBasisPoints: number;
  creators: Array<{
    address: string;
    verified: boolean;
    share: number;
  }>;
  collection?: {
    verified: boolean;
    key: string;
  };
  attributes?: Array<{
    traitType: string;
    value: string;
  }>;
}

/**
 * Solana Pay Payment Session
 */
export interface PaymentSession {
  sessionId: string;
  recipient: string;
  amount?: string;
  splToken?: string;
  reference: string;
  label?: string;
  message?: string;
  status: 'PENDING' | 'DETECTED' | 'CONFIRMED' | 'FINALIZED' | 'EXPIRED' | 'FAILED';
  signature?: string;
}

/**
 * Simulation Result
 */
export interface SimulationResult {
  success: boolean;
  computeUnitsConsumed: number;
  logs: string[];
  error?: {
    code?: number;
    message: string;
    category: string;
    suggestion?: string;
  };
  balanceChanges: Array<{
    pubkey: string;
    before: number;
    after: number;
    delta: number;
  }>;
}

/**
 * Transaction Preview
 */
export interface TransactionPreview {
  title: string;
  description: string;
  estimatedFee: {
    networkFee: number;
    priorityFee: number;
    totalFee: number;
    estimatedCU: number;
  };
  warnings: Array<{
    message: string;
    severity: 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  }>;
  riskLevel: 'SAFE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  instructionSummaries: Array<{
    index: number;
    programName?: string;
    action: string;
  }>;
}

/**
 * Account Type
 */
export interface AccountType {
  programId?: string;
  accountName: string;
  category: 'SYSTEM' | 'TOKEN' | 'TOKEN_2022' | 'NFT' | 'METADATA' | 'DEFI' | 'GAMING' | 'CUSTOM' | 'UNKNOWN';
  confidence: number;
}

/**
 * VersionedTx - Versioned Transaction Builder
 */
export const VersionedTx = {
  /**
   * Build a versioned transaction
   * @param config - Transaction configuration
   * @param instructions - Array of instruction data (base64)
   */
  build: async (
    config: VersionedTxConfig,
    instructions: string[]
  ): Promise<string> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.buildVersionedTransaction(config, instructions);
  },

  /**
   * Estimate transaction size
   * @param instructions - Array of instruction data (base64)
   * @param signerCount - Number of signers
   * @param useAlt - Whether to use Address Lookup Tables
   */
  estimateSize: async (
    instructions: string[],
    signerCount?: number,
    useAlt?: boolean
  ): Promise<TxSizeEstimate> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.estimateTransactionSize(instructions, signerCount || 1, useAlt || false);
  },

  /**
   * Analyze a transaction
   * @param base64Tx - Base64 encoded transaction
   */
  analyze: async (base64Tx: string): Promise<{
    signatureCount: number;
    accountKeyCount: number;
    instructionCount: number;
    usesAlt: boolean;
    sizeBytes: number;
    hints: string[];
  }> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.analyzeTransaction(base64Tx);
  },
};

/**
 * ComputeOptimizer - Compute budget optimization
 */
export const ComputeOptimizer = {
  /**
   * Estimate compute units for instructions
   * @param instructions - Array of instruction data (base64)
   * @param priorityFee - Priority fee in microLamports
   */
  estimate: async (
    instructions: string[],
    priorityFee?: number
  ): Promise<ComputeBudget> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.estimateComputeUnits(instructions, priorityFee || 0);
  },

  /**
   * Get compute budget preset
   * @param preset - Preset type
   */
  getPreset: async (preset: ComputePreset): Promise<ComputeBudget> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.getComputePreset(preset);
  },

  /**
   * Build compute budget instructions
   * @param computeUnits - Compute unit limit
   * @param microLamports - Priority fee
   */
  buildInstructions: async (
    computeUnits: number,
    microLamports: number
  ): Promise<string[]> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.buildComputeBudgetInstructions(computeUnits, microLamports);
  },

  /**
   * Record actual usage for learning
   * @param programId - Program ID
   * @param actualCU - Actual compute units used
   */
  recordUsage: async (programId: string, actualCU: number): Promise<void> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.recordComputeUsage(programId, actualCU);
  },
};

/**
 * Cnft - Compressed NFT operations
 */
export const Cnft = {
  /**
   * Get cNFT asset by ID
   * @param assetId - Asset ID
   */
  getAsset: async (assetId: string): Promise<CnftAsset | null> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.getCnftAsset(assetId);
  },

  /**
   * Get assets by owner
   * @param owner - Owner public key
   * @param merkleTree - Optional merkle tree filter
   */
  getAssetsByOwner: async (owner: string, merkleTree?: string): Promise<CnftAsset[]> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.getCnftAssetsByOwner(owner, merkleTree);
  },

  /**
   * Get merkle proof for an asset
   * @param assetId - Asset ID
   */
  getProof: async (assetId: string): Promise<string[]> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.getCnftProof(assetId);
  },

  /**
   * Verify a merkle proof
   * @param proof - Array of proof nodes (base64)
   * @param root - Root hash (base64)
   * @param leaf - Leaf hash (base64)
   * @param leafIndex - Leaf index
   */
  verifyProof: async (
    proof: string[],
    root: string,
    leaf: string,
    leafIndex: number
  ): Promise<boolean> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.verifyCnftProof(proof, root, leaf, leafIndex);
  },

  /**
   * Build transfer instruction
   * @param assetId - Asset ID
   * @param from - Current owner
   * @param to - New owner
   */
  buildTransfer: async (
    assetId: string,
    from: string,
    to: string
  ): Promise<string> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.buildCnftTransfer(assetId, from, to);
  },
};

/**
 * SolanaPay - Solana Pay integration
 */
export const SolanaPay = {
  /**
   * Create a payment session
   * @param recipient - Recipient public key
   * @param amount - Amount (optional)
   * @param options - Additional options
   */
  createSession: async (
    recipient: string,
    amount?: string,
    options?: {
      splToken?: string;
      label?: string;
      message?: string;
      memo?: string;
    }
  ): Promise<PaymentSession> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.createPaymentSession(recipient, amount, options || {});
  },

  /**
   * Create transfer URI (no session tracking)
   * @param recipient - Recipient public key
   * @param amount - Amount (optional)
   * @param options - Additional options
   */
  createTransferUri: async (
    recipient: string,
    amount?: string,
    options?: {
      splToken?: string;
      label?: string;
      message?: string;
      memo?: string;
    }
  ): Promise<string> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.createSolanaPayUri(recipient, amount, options || {});
  },

  /**
   * Parse a Solana Pay URI
   * @param uri - Solana Pay URI
   */
  parseUri: async (uri: string): Promise<{
    recipient: string;
    amount?: string;
    splToken?: string;
    reference?: string[];
    label?: string;
    message?: string;
    memo?: string;
  }> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.parseSolanaPayUri(uri);
  },

  /**
   * Verify a payment
   * @param sessionId - Session ID
   */
  verifyPayment: async (sessionId: string): Promise<PaymentSession> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.verifyPayment(sessionId);
  },

  /**
   * Get session status
   * @param sessionId - Session ID
   */
  getSession: async (sessionId: string): Promise<PaymentSession | null> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.getPaymentSession(sessionId);
  },

  /**
   * Cancel a payment session
   * @param sessionId - Session ID
   */
  cancelSession: async (sessionId: string): Promise<void> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.cancelPaymentSession(sessionId);
  },
};

/**
 * TransactionPreview - Transaction simulation and preview
 */
export const TransactionPreview = {
  /**
   * Simulate a transaction
   * @param base64Tx - Base64 encoded transaction
   */
  simulate: async (base64Tx: string): Promise<SimulationResult> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.simulateTransaction(base64Tx);
  },

  /**
   * Get transaction preview
   * @param base64Tx - Base64 encoded transaction
   */
  preview: async (base64Tx: string): Promise<TransactionPreview> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.previewTransaction(base64Tx);
  },

  /**
   * Estimate compute units
   * @param base64Tx - Base64 encoded transaction
   */
  estimateComputeUnits: async (base64Tx: string): Promise<number> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.estimateTxComputeUnits(base64Tx);
  },

  /**
   * Decode simulation error
   * @param errorCode - Error code
   * @param errorMessage - Error message
   */
  decodeError: async (
    errorCode: number,
    errorMessage: string
  ): Promise<{
    title: string;
    explanation: string;
    suggestions: string[];
  }> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.decodeSimulationError(errorCode, errorMessage);
  },
};

/**
 * AccountDetector - Account type detection
 */
export const AccountDetector = {
  /**
   * Detect account type from data
   * @param accountData - Base64 encoded account data
   * @param programId - Optional program ID
   */
  detect: async (accountData: string, programId?: string): Promise<AccountType> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.detectAccountType(accountData, programId);
  },

  /**
   * Check if account matches expected type
   * @param accountData - Base64 encoded account data
   * @param expectedType - Expected account type name
   */
  matches: async (accountData: string, expectedType: string): Promise<boolean> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.accountMatches(accountData, expectedType);
  },

  /**
   * Get discriminator for an account name
   * @param accountName - Account name (Anchor format)
   */
  getDiscriminator: async (accountName: string): Promise<string> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.getAccountDiscriminator(accountName);
  },

  /**
   * Register custom program accounts
   * @param programId - Program ID
   * @param accounts - Account definitions
   */
  registerProgram: async (
    programId: string,
    accounts: Array<{ name: string; category: AccountType['category'] }>
  ): Promise<void> => {
    if (!ArtemisEnhancedModule) throw new Error('ArtemisEnhancedModule not available');
    return ArtemisEnhancedModule.registerProgramAccounts(programId, accounts);
  },
};

export default {
  VersionedTx,
  ComputeOptimizer,
  Cnft,
  SolanaPay,
  TransactionPreview,
  AccountDetector,
};
