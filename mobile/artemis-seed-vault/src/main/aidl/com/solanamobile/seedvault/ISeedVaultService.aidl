package com.solanamobile.seedvault;

import android.os.Bundle;

interface ISeedVaultService {
    void authorize(in Bundle params, in IBinder callback);
    void getAccounts(in Bundle params, in IBinder callback);
    void signMessages(in Bundle params, in IBinder callback);
    void signTransactions(in Bundle params, in IBinder callback);
    void deauthorize(in Bundle params, in IBinder callback);
}
