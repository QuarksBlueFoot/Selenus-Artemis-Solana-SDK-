import { NativeModules } from 'react-native';
export { MobileWalletAdapter } from './MobileWalletAdapter';

const { ArtemisModule } = NativeModules;

/**
 * Artemis SDK for React Native
 * Provides access to Solana Mobile Wallet Adapter, RPC, DePIN, Gaming, and Solana Pay features.
 */
const Artemis = {
  /**
   * Initialize the wallet adapter.
   * @param {string} identityUri - URI of your app
   * @param {string} iconPath - Path to your app icon
   * @param {string} identityName - Name of your app
   * @param {string} chain - Chain to connect to (e.g., "solana:mainnet")
   */
  initialize: (identityUri, iconPath, identityName, chain) => ArtemisModule.initialize(identityUri, iconPath, identityName, chain),

  /**
   * Connect to a wallet.
   * @returns {Promise<string>} The public key of the connected wallet.
   */
  connect: () => ArtemisModule.connect(),

  /**
   * Sign a transaction.
   * @param {string} base64Tx - The transaction to sign, base64 encoded.
   * @returns {Promise<string>} The signed transaction, base64 encoded.
   */
  signTransaction: (base64Tx) => ArtemisModule.signTransaction(base64Tx),

  /**
   * Sign and send a transaction.
   * @param {string} base64Tx - The transaction to sign and send, base64 encoded.
   * @returns {Promise<string>} The transaction signature.
   */
  signAndSendTransaction: (base64Tx) => ArtemisModule.signAndSendTransaction(base64Tx),

  /**
   * Sign a message.
   * @param {string} base64Msg - The message to sign, base64 encoded.
   * @returns {Promise<string>} The signature, base64 encoded.
   */
  signMessage: (base64Msg) => ArtemisModule.signMessage(base64Msg),
  
  // --- RPC Methods ---

  /**
   * Set the RPC URL for subsequent calls.
   * @param {string} url - The RPC URL.
   */
  setRpcUrl: (url) => ArtemisModule.setRpcUrl(url),

  /**
   * Get the balance of an account.
   * @param {string} pubkey - The public key to check.
   * @returns {Promise<string>} The balance in lamports.
   */
  getBalance: (pubkey) => ArtemisModule.getBalance(pubkey),

  /**
   * Get the latest blockhash.
   * @returns {Promise<string>} The latest blockhash.
   */
  getLatestBlockhash: () => ArtemisModule.getLatestBlockhash(),
  
  // --- Program Methods ---

  /**
   * Build a transfer transaction.
   * @param {string} from - Sender public key.
   * @param {string} to - Recipient public key.
   * @param {string} lamports - Amount in lamports.
   * @param {string} blockhash - Recent blockhash.
   * @returns {Promise<string>} The compiled transaction, base64 encoded.
   */
  buildTransferTransaction: (from, to, lamports, blockhash) => ArtemisModule.buildTransferTransaction(from, to, lamports, blockhash),
  
  // --- DePIN Methods ---

  /**
   * Generate a new device identity for DePIN.
   * The identity is stored in memory on the native side.
   * @returns {Promise<string>} The public key of the device.
   */
  generateDeviceIdentity: () => ArtemisModule.generateDeviceIdentity(),

  /**
   * Sign a location proof using a generated device identity.
   * @param {string} devicePubkey - The public key of the device (returned from generateDeviceIdentity).
   * @param {number} lat - Latitude.
   * @param {number} lng - Longitude.
   * @param {number} timestamp - Timestamp.
   * @returns {Promise<string>} The signature of the proof.
   */
  signLocationProof: (devicePubkey, lat, lng, timestamp) => ArtemisModule.signLocationProof(devicePubkey, lat, lng, timestamp),
  
  // --- Solana Pay Methods ---

  /**
   * Build a Solana Pay URI.
   * @param {string} recipient - Recipient public key.
   * @param {string} amount - Amount in SOL.
   * @param {string} label - Label.
   * @param {string} message - Message.
   * @returns {Promise<string>} The Solana Pay URI.
   */
  buildSolanaPayUri: (recipient, amount, label, message) => ArtemisModule.buildSolanaPayUri(recipient, amount, label, message),

  /**
   * Parse a Solana Pay URI.
   * @param {string} uri - The URI to parse.
   * @returns {Promise<Object>} The parsed request object.
   */
  parseSolanaPayUri: (uri) => ArtemisModule.parseSolanaPayUri(uri),
  
  // --- Gaming Methods ---

  /**
   * Verify a Merkle Proof.
   * @param {string[]} proof - Array of base64 encoded proof elements.
   * @param {string} root - Base64 encoded root.
   * @param {string} leaf - Base64 encoded leaf.
   * @returns {Promise<boolean>} True if valid.
   */
  verifyMerkleProof: (proof, root, leaf) => ArtemisModule.verifyMerkleProof(proof, root, leaf),
};

export default Artemis;
