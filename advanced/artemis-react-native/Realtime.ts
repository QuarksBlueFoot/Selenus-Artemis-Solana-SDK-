import { NativeEventEmitter, NativeModules } from 'react-native';
import type { EmitterSubscription } from 'react-native';

const { ArtemisModule } = NativeModules;

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

export type RealtimeStateKind =
    | 'Idle'
    | 'Connecting'
    | 'Connected'
    | 'Reconnecting'
    | 'Closed';

export interface RealtimeStateEvent {
    kind: RealtimeStateKind;
    epoch: number;
    endpoint?: string;
    subscriptions?: number;
    attempt?: number;
    nextDelayMs?: number;
    reason?: string;
}

/**
 * Convenience wrapper around the native realtime bridge. Each
 * `onAccountChanged` / `onSignatureConfirmed` call returns a
 * subscription handle; call `.remove()` to detach the JS listener.
 * The underlying native subscription stays registered on the engine
 * until `Realtime.close()` tears the engine down.
 *
 * Wire it up in a single step:
 *
 * ```ts
 * await Realtime.connect();
 * const sub = await Realtime.onAccountChanged(pubkey, (n) => {
 *   console.log('lamports', n.lamports);
 * });
 * // ...
 * sub.remove();
 * ```
 */
export const Realtime = {
    connect(): Promise<void> {
        return ArtemisModule.realtimeConnect();
    },

    close(): Promise<void> {
        return ArtemisModule.realtimeClose();
    },

    /**
     * Observe transport state transitions. The event carries an
     * `epoch` counter that increments with every state transition, so
     * subscribers can distinguish a reconnect that landed back on
     * `Connected` from a stale event that fired during the old
     * connection's lifetime.
     */
    onState(listener: (event: RealtimeStateEvent) => void): EmitterSubscription {
        return new NativeEventEmitter(ArtemisModule).addListener(
            'ArtemisRealtimeState',
            listener,
        );
    },

    async onAccountChanged(
        pubkey: string,
        listener: (event: AccountNotification) => void,
        commitment?: 'processed' | 'confirmed' | 'finalized',
    ): Promise<EmitterSubscription> {
        const eventName: string = await ArtemisModule.subscribeAccount(
            pubkey,
            commitment ?? null,
        );
        return new NativeEventEmitter(ArtemisModule).addListener(eventName, listener);
    },

    async onSignatureConfirmed(
        signature: string,
        listener: (event: SignatureNotification) => void,
        commitment?: 'processed' | 'confirmed' | 'finalized',
    ): Promise<EmitterSubscription> {
        const eventName: string = await ArtemisModule.subscribeSignature(
            signature,
            commitment ?? null,
        );
        return new NativeEventEmitter(ArtemisModule).addListener(eventName, listener);
    },
};

export default Realtime;
