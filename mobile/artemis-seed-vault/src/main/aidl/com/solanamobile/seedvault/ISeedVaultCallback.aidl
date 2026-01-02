package com.solanamobile.seedvault;

import android.os.Bundle;

oneway interface ISeedVaultCallback {
    void onResponse(in Bundle response);
    void onError(in Bundle error);
}
