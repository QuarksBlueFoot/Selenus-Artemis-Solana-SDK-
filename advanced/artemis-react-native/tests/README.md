# Artemis React Native Integration Tests

This folder contains integration tests for the Artemis React Native SDK.

## Running Tests

Since the SDK relies on native Android modules, these tests must be run within a React Native application on an Android device or emulator.

1.  Import `runIntegrationTests` in your `App.tsx` or a button handler:

    ```typescript
    import { runIntegrationTests } from 'artemis-solana-sdk/IntegrationTest';

    // ... inside your component
    <Button title="Run Tests" onPress={async () => {
        const results = await runIntegrationTests();
        console.log(results);
    }} />
    ```

2.  Run your app on Android:
    ```bash
    npx react-native run-android
    ```

3.  Check the Metro bundler console or Logcat for test output.

## Test Coverage

The `IntegrationTest.ts` script verifies:
*   **Wallet Adapter:** Initialization, Connection, Signing.
*   **RPC:** `getBalance` against Devnet.
*   **DePIN:** Device Identity generation and Location Proof signing.
*   **Solana Pay:** URI building and parsing.
*   **Gaming:** Merkle Proof verification.
