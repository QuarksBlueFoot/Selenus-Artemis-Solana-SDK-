/**
 * Artemis Gaming SDK for React Native
 * 
 * Mobile-first gaming features with coroutine-based architecture.
 * All implementations use modern reactive patterns.
 */

import { NativeModules, Platform, NativeEventEmitter } from 'react-native';

const { ArtemisGamingModule } = NativeModules;
const gamingEmitter = ArtemisGamingModule ? new NativeEventEmitter(ArtemisGamingModule) : null;

/**
 * Game Action Types
 */
export type GameActionType = 
  | 'MOVE'
  | 'ATTACK'
  | 'INTERACT'
  | 'ITEM_USE'
  | 'SKILL'
  | 'TRADE'
  | 'CUSTOM';

/**
 * Action Priority Levels
 */
export type ActionPriority = 
  | 'LOW'
  | 'NORMAL'
  | 'HIGH'
  | 'CRITICAL';

/**
 * Game Action
 */
export interface GameAction {
  id?: string;
  playerId: string;
  actionType: GameActionType;
  payload: string;  // Base64 encoded
  priority?: ActionPriority;
  timestampMs?: number;
}

/**
 * Game Frame - Collection of batched actions
 */
export interface GameFrame {
  frameId: number;
  createdAtMs: number;
  actions: GameAction[];
  stateHash: string;  // Base64 encoded
}

/**
 * Frame Result
 */
export interface FrameResult {
  type: 'success' | 'failure' | 'rollback';
  frameId: number;
  signature?: string;
  slot?: number;
  latencyMs?: number;
  error?: string;
}

/**
 * Session Status
 */
export type SessionStatus = 
  | 'WAITING'
  | 'STARTING'
  | 'ACTIVE'
  | 'PAUSED'
  | 'FINISHING'
  | 'COMPLETED'
  | 'CANCELLED';

/**
 * Game Session
 */
export interface GameSession {
  sessionId: string;
  gameType: string;
  host: string;
  status: SessionStatus;
  playerCount: number;
  maxPlayers: number;
  currentTurn?: number;
  stateHash: string;
}

/**
 * Player in Session
 */
export interface Player {
  pubkey: string;
  name: string;
  status: 'READY' | 'NOT_READY' | 'PLAYING' | 'DISCONNECTED';
  score: number;
}

/**
 * Fee Recommendation
 */
export interface FeeRecommendation {
  computeUnitLimit: number;
  microLamportsPerUnit: number;
  estimatedTotalLamports: number;
  confidence: number;
  congestionLevel: 'LOW' | 'NORMAL' | 'ELEVATED' | 'HIGH' | 'CRITICAL';
  reason: string;
}

/**
 * Gaming Tier Presets
 */
export type GamingTier = 
  | 'CASUAL'
  | 'STANDARD'
  | 'COMPETITIVE'
  | 'ESPORTS'
  | 'BOSS_BATTLE';

/**
 * ArcanaFlow - Mobile-first game action orchestration
 */
export const ArcanaFlow = {
  /**
   * Start the ArcanaFlow processing loop
   * @param config - Configuration options
   * @returns Session ID
   */
  start: async (config?: {
    frameWindowMs?: number;
    maxActionsPerFrame?: number;
    enablePrediction?: boolean;
  }): Promise<string> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.arcanaFlowStart(config || {});
  },

  /**
   * Stop the ArcanaFlow processing
   */
  stop: async (): Promise<void> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.arcanaFlowStop();
  },

  /**
   * Enqueue a game action
   * @param action - The game action to queue
   */
  enqueue: async (action: GameAction): Promise<void> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.arcanaFlowEnqueue(action);
  },

  /**
   * Enqueue multiple actions atomically
   * @param actions - Array of game actions
   */
  enqueueBatch: async (actions: GameAction[]): Promise<void> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.arcanaFlowEnqueueBatch(actions);
  },

  /**
   * Subscribe to frame events
   * @param callback - Callback for new frames
   * @returns Unsubscribe function
   */
  onFrame: (callback: (frame: GameFrame) => void): (() => void) => {
    if (!gamingEmitter) return () => {};
    const subscription = gamingEmitter.addListener('onArcanaFrame', callback);
    return () => subscription.remove();
  },

  /**
   * Subscribe to frame results
   * @param callback - Callback for frame results
   * @returns Unsubscribe function
   */
  onResult: (callback: (result: FrameResult) => void): (() => void) => {
    if (!gamingEmitter) return () => {};
    const subscription = gamingEmitter.addListener('onArcanaResult', callback);
    return () => subscription.remove();
  },

  /**
   * Request rollback to a specific frame
   * @param frameId - Frame ID to rollback to
   */
  rollback: async (frameId: number): Promise<void> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.arcanaFlowRollback(frameId);
  },
};

/**
 * GameSession - Multiplayer session management
 */
export const GameSessionManager = {
  /**
   * Create a new game session
   * @param gameType - Type of game
   * @param hostPubkey - Host's public key
   * @param settings - Session settings
   */
  create: async (
    gameType: string,
    hostPubkey: string,
    settings?: {
      maxPlayers?: number;
      isPrivate?: boolean;
      turnBased?: boolean;
      allowSpectators?: boolean;
    }
  ): Promise<GameSession> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.createGameSession(gameType, hostPubkey, settings || {});
  },

  /**
   * Join an existing session
   * @param sessionId - Session ID to join
   * @param playerPubkey - Player's public key
   * @param playerName - Display name
   * @param asSpectator - Join as spectator
   */
  join: async (
    sessionId: string,
    playerPubkey: string,
    playerName: string,
    asSpectator?: boolean
  ): Promise<GameSession> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.joinGameSession(sessionId, playerPubkey, playerName, asSpectator || false);
  },

  /**
   * Leave a session
   * @param sessionId - Session ID
   * @param playerPubkey - Player's public key
   */
  leave: async (sessionId: string, playerPubkey: string): Promise<void> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.leaveGameSession(sessionId, playerPubkey);
  },

  /**
   * Set player ready status
   * @param sessionId - Session ID
   * @param playerPubkey - Player's public key
   * @param ready - Ready status
   */
  setReady: async (sessionId: string, playerPubkey: string, ready: boolean): Promise<void> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.setPlayerReady(sessionId, playerPubkey, ready);
  },

  /**
   * Start the game
   * @param sessionId - Session ID
   */
  startGame: async (sessionId: string): Promise<void> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.startGame(sessionId);
  },

  /**
   * Submit a player action
   * @param sessionId - Session ID
   * @param action - Player action
   */
  submitAction: async (
    sessionId: string,
    action: {
      player: string;
      actionType: string;
      payload: string;  // Base64
    }
  ): Promise<number> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.submitPlayerAction(sessionId, action);
  },

  /**
   * Get session info
   * @param sessionId - Session ID
   */
  getSession: async (sessionId: string): Promise<GameSession | null> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.getGameSession(sessionId);
  },

  /**
   * List available sessions
   * @param gameType - Optional game type filter
   */
  listSessions: async (gameType?: string): Promise<GameSession[]> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.listGameSessions(gameType);
  },

  /**
   * Subscribe to session events
   * @param sessionId - Session ID
   * @param callback - Event callback
   */
  onSessionEvent: (
    sessionId: string,
    callback: (event: {
      type: string;
      sessionId: string;
      data: any;
    }) => void
  ): (() => void) => {
    if (!gamingEmitter) return () => {};
    const subscription = gamingEmitter.addListener('onSessionEvent', (event) => {
      if (event.sessionId === sessionId) {
        callback(event);
      }
    });
    return () => subscription.remove();
  },
};

/**
 * AdaptiveFee - Intelligent fee optimization
 */
export const AdaptiveFee = {
  /**
   * Get fee recommendation
   * @param options - Recommendation options
   */
  recommend: async (options?: {
    programId?: string;
    priority?: ActionPriority;
    estimatedComputeUnits?: number;
    isRetry?: boolean;
    isTimeSensitive?: boolean;
  }): Promise<FeeRecommendation> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.getAdaptiveFeeRecommendation(options || {});
  },

  /**
   * Get gaming tier preset
   * @param tier - Gaming tier
   */
  getPreset: async (tier: GamingTier): Promise<FeeRecommendation> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.getGamingFeePreset(tier);
  },

  /**
   * Record transaction outcome for learning
   * @param outcome - Transaction outcome data
   */
  recordOutcome: async (outcome: {
    signature: string;
    programId?: string;
    priority: ActionPriority;
    computeUnitsUsed: number;
    microLamportsSpent: number;
    success: boolean;
    confirmationTimeMs: number;
  }): Promise<void> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.recordFeeOutcome(outcome);
  },

  /**
   * Get current congestion level
   */
  getCongestionLevel: async (): Promise<'LOW' | 'NORMAL' | 'ELEVATED' | 'HIGH' | 'CRITICAL'> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.getCongestionLevel();
  },

  /**
   * Get budget status
   */
  getBudgetStatus: async (): Promise<{
    hourlyBudget: number;
    spent: number;
    remaining: number;
    percentUsed: number;
  }> => {
    if (!ArtemisGamingModule) throw new Error('ArtemisGamingModule not available');
    return ArtemisGamingModule.getFeeBudgetStatus();
  },
};

export default {
  ArcanaFlow,
  GameSessionManager,
  AdaptiveFee,
};
