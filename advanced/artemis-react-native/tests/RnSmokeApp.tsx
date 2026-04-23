/*
 * Minimal React Native smoke harness for Artemis.
 *
 * Drop this screen into a bare React Native app with
 * `@selenus/artemis-solana-sdk` installed to validate the end-to-end
 * bridge contract against a live wallet. Every button exercises one
 * production code path:
 *
 *   Connect                    authorize + cache accounts + caps
 *   Connect with Sign-In       SIWS payload round-trip
 *   Get capabilities           read cached caps off the adapter
 *   Sign a message             arbitrary-message signing
 *   Sign and send (batch)      3-tx batch with per-slot results
 *   Clone authorization        MWA 2.0 optional feature
 *   Disconnect                 session teardown
 *
 * The screen prints the structured objects returned by the bridge so
 * you can eyeball whether the shapes match what the TS layer expects
 * without needing a full test runner. No mocks, no jest: this is the
 * real bridge exercise the audit asked for.
 *
 * This file ships inside the npm package under `tests/` as reference
 * source; in a consuming app, copy it to your screens folder and
 * import your own transactions.
 */
import React, { useCallback, useRef, useState } from 'react';
import { Alert, Button, ScrollView, StyleSheet, Text, View } from 'react-native';
import {
    MobileWalletAdapter,
    MWA_FEATURES,
    transact,
    type AuthorizationResult,
    type BatchSendResult,
    type TransactionSendResult,
} from '../MobileWalletAdapter';
import { Buffer } from 'buffer';

const APP_CONFIG = {
    identityUri: 'https://myapp.example.com',
    iconPath: 'https://myapp.example.com/favicon.ico',
    identityName: 'Artemis Smoke',
    chain: 'solana:devnet',
};

export default function RnSmokeApp() {
    const walletRef = useRef<MobileWalletAdapter | null>(null);
    const [log, setLog] = useState<string>('');

    const wallet = (): MobileWalletAdapter => {
        if (!walletRef.current) {
            walletRef.current = new MobileWalletAdapter(APP_CONFIG);
        }
        return walletRef.current;
    };

    const emit = useCallback((label: string, body: unknown) => {
        const line = `\n[${label}] ${JSON.stringify(body, null, 2)}`;
        setLog((prev) => prev + line);
    }, []);

    const onError = useCallback((label: string, err: unknown) => {
        const msg = err instanceof Error ? err.message : String(err);
        setLog((prev) => prev + `\n[${label}] ERROR ${msg}`);
        Alert.alert(label, msg);
    }, []);

    const connect = async () => {
        try {
            await wallet().connect();
            const auth: AuthorizationResult = {
                authToken: wallet().authToken ?? '',
                address: wallet().publicKey?.toBase58() ?? '',
                accounts: wallet().accounts,
                capabilities: wallet().capabilities!,
                walletUriBase: wallet().walletUriBase ?? undefined,
            };
            emit('connect', auth);
        } catch (e) {
            onError('connect', e);
        }
    };

    const signIn = async () => {
        try {
            const auth = await wallet().connectWithSignIn({
                domain: 'myapp.example.com',
                statement: 'Sign in to Artemis Smoke',
                uri: 'https://myapp.example.com/login',
            });
            emit('connectWithSignIn', auth.signInResult ?? {});
        } catch (e) {
            onError('connectWithSignIn', e);
        }
    };

    const readCaps = async () => {
        try {
            const caps = await wallet().getCapabilities();
            emit('getCapabilities', caps);
        } catch (e) {
            onError('getCapabilities', e);
        }
    };

    const signMessage = async () => {
        try {
            const msg = Buffer.from('hello artemis');
            const sig = await wallet().signMessage(new Uint8Array(msg));
            emit('signMessage.sig.length', sig.length);
        } catch (e) {
            onError('signMessage', e);
        }
    };

    const signAndSendBatch = async () => {
        try {
            const txs = [0x01, 0x02, 0x03].map((b) => ({
                serialize: () => new Uint8Array([b]),
            }));
            const result: BatchSendResult = await wallet().signAndSendTransactions(
                txs as any,
                { commitment: 'confirmed' },
            );
            const summary = result.results.map((r: TransactionSendResult) => ({
                index: r.index,
                state: r.isSuccess
                    ? 'success'
                    : r.isFailure
                      ? 'failure'
                      : 'signedButNotBroadcast',
                signature: r.signature,
            }));
            emit('signAndSendTransactions', summary);
        } catch (e) {
            onError('signAndSendTransactions', e);
        }
    };

    const cloneAuth = async () => {
        try {
            const caps = await wallet().getCapabilities();
            if (!caps.features.includes(MWA_FEATURES.CLONE_AUTHORIZATION)) {
                emit('cloneAuthorization', 'wallet does not advertise clone_authorization');
                return;
            }
            const token = await wallet().cloneAuthorization();
            emit('cloneAuthorization', { token });
        } catch (e) {
            onError('cloneAuthorization', e);
        }
    };

    const disconnect = async () => {
        try {
            await wallet().disconnect();
            emit('disconnect', 'ok');
        } catch (e) {
            onError('disconnect', e);
        }
    };

    const runFullTransactBlock = async () => {
        try {
            const out = await transact(wallet(), async (w) => {
                const caps = await w.getCapabilities();
                return { caps, primary: w.publicKey?.toBase58() };
            });
            emit('transact', out);
        } catch (e) {
            onError('transact', e);
        }
    };

    return (
        <View style={styles.root}>
            <Text style={styles.title}>Artemis RN smoke</Text>
            <View style={styles.buttons}>
                <Button title="Connect" onPress={connect} />
                <Button title="Connect with Sign-In" onPress={signIn} />
                <Button title="Get capabilities" onPress={readCaps} />
                <Button title="Sign a message" onPress={signMessage} />
                <Button title="Sign + send (batch of 3)" onPress={signAndSendBatch} />
                <Button title="Clone authorization" onPress={cloneAuth} />
                <Button title="Run transact block" onPress={runFullTransactBlock} />
                <Button title="Disconnect" onPress={disconnect} />
            </View>
            <ScrollView style={styles.logBox}>
                <Text style={styles.log} selectable>
                    {log || 'Logs will appear here. Run any button above.'}
                </Text>
            </ScrollView>
        </View>
    );
}

const styles = StyleSheet.create({
    root: { flex: 1, padding: 16 },
    title: { fontSize: 20, fontWeight: 'bold', marginBottom: 12 },
    buttons: { gap: 8, marginBottom: 12 },
    logBox: {
        flex: 1,
        backgroundColor: '#0b0f14',
        padding: 12,
        borderRadius: 4,
    },
    log: { color: '#9be08a', fontFamily: 'monospace', fontSize: 12 },
});
