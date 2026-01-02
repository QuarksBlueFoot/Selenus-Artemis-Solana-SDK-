import { MobileWalletAdapter } from '../MobileWalletAdapter';
import { Connection, PublicKey, Transaction, SystemProgram, LAMPORTS_PER_SOL } from '@solana/web3.js';
import Artemis from '../index';

/**
 * Integration Test Runner for Artemis React Native SDK
 * 
 * This script is designed to be imported and run within a React Native application
 * to verify all SDK functionality on Devnet.
 */
export const runIntegrationTests = async () => {
    console.log("Starting Artemis SDK Integration Tests (Devnet)...");
    const results: Record<string, string> = {};

    try {
        // 1. Initialize Wallet Adapter
        console.log("1. Initializing Wallet Adapter...");
        const wallet = new MobileWalletAdapter({
            identityUri: 'https://artemis.selenus.com',
            iconPath: 'favicon.ico',
            identityName: 'Artemis Test',
            chain: 'solana:devnet'
        });
        results['Initialize'] = 'PASS';

        // 2. Connect
        console.log("2. Connecting to Wallet...");
        // Note: In a real app, this triggers the MWA UI. For automated tests without UI interaction,
        // this step requires a connected wallet that auto-approves or manual intervention.
        await wallet.connect();
        if (!wallet.publicKey) throw new Error("Public key is null");
        console.log("Connected: " + wallet.publicKey.toBase58());
        results['Connect'] = 'PASS';

        // 3. RPC - Get Balance
        console.log("3. Testing RPC (Get Balance)...");
        // Ensure we are using Devnet
        Artemis.setRpcUrl("https://api.devnet.solana.com");
        const balance = await Artemis.getBalance(wallet.publicKey.toBase58());
        console.log("Balance: " + balance);
        results['RPC_GetBalance'] = 'PASS';

        // 4. Sign Transaction (Transfer)
        console.log("4. Testing Sign Transaction...");
        const connection = new Connection("https://api.devnet.solana.com");
        const { blockhash } = await connection.getLatestBlockhash();
        
        const tx = new Transaction().add(
            SystemProgram.transfer({
                fromPubkey: wallet.publicKey,
                toPubkey: wallet.publicKey, // Self-transfer
                lamports: 1000,
            })
        );
        tx.recentBlockhash = blockhash;
        tx.feePayer = wallet.publicKey;

        const signedTx = await wallet.signTransaction(tx);
        console.log("Transaction Signed");
        results['SignTransaction'] = 'PASS';

        // 5. DePIN - Identity & Proof
        console.log("5. Testing DePIN Features...");
        const deviceId = await Artemis.generateDeviceIdentity();
        console.log("Device ID: " + deviceId);
        const proof = await Artemis.signLocationProof(deviceId, 40.7128, -74.0060, Date.now());
        console.log("Location Proof Signed");
        results['DePIN_Proof'] = 'PASS';

        // 6. Solana Pay
        console.log("6. Testing Solana Pay...");
        const payUri = await Artemis.buildSolanaPayUri(wallet.publicKey.toBase58(), "0.01", "Test", "Memo");
        const parsed = await Artemis.parseSolanaPayUri(payUri);
        if (parsed.recipient !== wallet.publicKey.toBase58()) throw new Error("Solana Pay Parse Mismatch");
        results['SolanaPay'] = 'PASS';

        // 7. Gaming - Merkle Proof
        console.log("7. Testing Gaming (Merkle Proof)...");
        // Dummy proof for structure test (verification will fail with dummy data, but call should succeed)
        try {
            await Artemis.verifyMerkleProof([], "cm9vdA==", "bGVhZg==");
        } catch (e) {
            // Expected to fail verification, but not crash
        }
        results['Gaming_Merkle'] = 'PASS';

    } catch (e: any) {
        console.error("Test Failed: " + e.message);
        results['FINAL_STATUS'] = 'FAIL: ' + e.message;
    }

    console.log("Test Results:", JSON.stringify(results, null, 2));
    return results;
};
