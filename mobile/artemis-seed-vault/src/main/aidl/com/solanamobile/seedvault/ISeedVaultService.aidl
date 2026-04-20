package com.solanamobile.seedvault;

import android.os.Bundle;

/**
 * Binder interface for the system Seed Vault service.
 *
 * Method order matters: each method's transaction code is derived
 * positionally from FIRST_CALL_TRANSACTION. The matching Kotlin proxy at
 * internal/ipc/ISeedVaultService.kt depends on this order and declares
 * TRANSACT_authorize=0, TRANSACT_createSeed=1, ..., TRANSACT_deauthorize=8.
 * Keep the two files in lock-step; reordering or inserting methods here
 * silently breaks every Kotlin call site.
 */
interface ISeedVaultService {
    void authorize(in Bundle params, in IBinder callback);
    void createSeed(in Bundle params, in IBinder callback);
    void importSeed(in Bundle params, in IBinder callback);
    void updateSeed(in Bundle params, in IBinder callback);
    void getAccounts(in Bundle params, in IBinder callback);
    void resolveDerivationPath(in Bundle params, in IBinder callback);
    void signTransactions(in Bundle params, in IBinder callback);
    void signMessages(in Bundle params, in IBinder callback);
    void deauthorize(in Bundle params, in IBinder callback);
}
