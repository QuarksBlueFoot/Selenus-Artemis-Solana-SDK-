import { NativeModules } from 'react-native';

export { MobileWalletAdapter, MWA_FEATURES, transact } from './MobileWalletAdapter';
export { Base58, Base58Check, Crypto, ArtemisPlatform } from './Base58';
export { ArcanaFlow, GameSessionManager, AdaptiveFee } from './Gaming';
export {
    VersionedTx,
    ComputeOptimizer,
    Cnft,
    SolanaPay,
    TransactionPreview,
    AccountDetector,
} from './Enhanced';

const { ArtemisModule } = NativeModules;

/**
 * Artemis SDK for React Native.
 *
 * All methods delegate 1:1 to the Android native bridge. No JSON
 * round-tripping: the bridge resolves structured objects which this
 * file forwards untouched. Types are documented in index.d.ts.
 *
 * Android is the only platform with MWA and Seed Vault support. iOS
 * exposes the Base58 / Ed25519 utilities from Base58.ts; the MWA-shaped
 * methods below throw on non-Android if called.
 */
const Artemis = {
    // ─── Initialization ──────────────────────────────────────────────────

    initialize: (identityUri, iconPath, identityName, chain) =>
        ArtemisModule.initialize(identityUri, iconPath, identityName, chain),

    setRpcUrl: (url) => ArtemisModule.setRpcUrl(url),

    // ─── MWA 2.0 ─────────────────────────────────────────────────────────

    connect: () => ArtemisModule.connect(),
    connectWithFeatures: (features, addresses) =>
        ArtemisModule.connectWithFeatures(features ?? null, addresses ?? null),
    connectWithSignIn: (payload) => ArtemisModule.connectWithSignIn(payload),
    reauthorize: () => ArtemisModule.reauthorize(),
    deauthorize: () => ArtemisModule.deauthorize(),
    getCapabilities: () => ArtemisModule.getCapabilities(),
    cloneAuthorization: () => ArtemisModule.cloneAuthorization(),

    signTransaction: (base64Tx) => ArtemisModule.signTransaction(base64Tx),
    signTransactions: (base64Txs) => ArtemisModule.signTransactions(base64Txs),
    signAndSendTransaction: (base64Tx, options) =>
        ArtemisModule.signAndSendTransaction(base64Tx, options ?? null),
    signAndSendTransactions: (base64Txs, options) =>
        ArtemisModule.signAndSendTransactions(base64Txs, options ?? null),

    signMessage: (base64Msg) => ArtemisModule.signMessage(base64Msg),
    signMessages: (base64Msgs) => ArtemisModule.signMessages(base64Msgs),
    signMessagesDetached: (base64Msgs) => ArtemisModule.signMessagesDetached(base64Msgs),

    // ─── RPC ─────────────────────────────────────────────────────────────

    getBalance: (pubkey) => ArtemisModule.getBalance(pubkey),
    getLatestBlockhash: () => ArtemisModule.getLatestBlockhash(),

    // ─── System program helpers ──────────────────────────────────────────

    buildTransferTransaction: (from, to, lamports, blockhash) =>
        ArtemisModule.buildTransferTransaction(from, to, lamports, blockhash),

    // ─── DePIN ───────────────────────────────────────────────────────────

    generateDeviceIdentity: () => ArtemisModule.generateDeviceIdentity(),
    signLocationProof: (devicePubkey, lat, lng, timestamp) =>
        ArtemisModule.signLocationProof(devicePubkey, lat, lng, timestamp),

    // ─── Solana Pay ──────────────────────────────────────────────────────

    buildSolanaPayUri: (recipient, amount, label, message) =>
        ArtemisModule.buildSolanaPayUri(recipient, amount, label, message),
    parseSolanaPayUri: (uri) => ArtemisModule.parseSolanaPayUri(uri),

    // ─── Gaming ──────────────────────────────────────────────────────────

    verifyMerkleProof: (proof, root, leaf) =>
        ArtemisModule.verifyMerkleProof(proof, root, leaf),

    // ─── Seed Vault (Android wallet apps only) ──────────────────────────

    seedVaultAuthorize: (purpose) => ArtemisModule.seedVaultAuthorize(purpose),
    seedVaultCreateSeed: (purpose) => ArtemisModule.seedVaultCreateSeed(purpose),
    seedVaultImportSeed: (purpose) => ArtemisModule.seedVaultImportSeed(purpose),
    seedVaultGetAccounts: (authToken) => ArtemisModule.seedVaultGetAccounts(authToken),
    seedVaultSignMessages: (authToken, base64Messages) =>
        ArtemisModule.seedVaultSignMessages(authToken, base64Messages),
    seedVaultSignTransactions: (authToken, base64Transactions) =>
        ArtemisModule.seedVaultSignTransactions(authToken, base64Transactions),
    seedVaultRequestPublicKeys: (authToken, derivationPaths) =>
        ArtemisModule.seedVaultRequestPublicKeys(authToken, derivationPaths),
    seedVaultSignWithDerivationPath: (authToken, derivationPath, base64Payloads) =>
        ArtemisModule.seedVaultSignWithDerivationPath(authToken, derivationPath, base64Payloads),
    seedVaultDeauthorize: (authToken) => ArtemisModule.seedVaultDeauthorize(authToken),
};

export default Artemis;
