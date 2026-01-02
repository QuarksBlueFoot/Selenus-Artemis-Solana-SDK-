package com.selenus.artemis.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.selenus.artemis.runtime.Pubkey

@Composable
fun WalletConnectButton(
    connectedPubkey: Pubkey?,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            if (connectedPubkey != null) onDisconnect() else onConnect()
        },
        enabled = !isConnecting,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (connectedPubkey != null) Color(0xFF14F195) else Color(0xFF9945FF),
            contentColor = if (connectedPubkey != null) Color.Black else Color.White
        )
    ) {
        if (isConnecting) {
            Text("Connecting...")
        } else if (connectedPubkey != null) {
            Text(text = connectedPubkey.toBase58().take(4) + "..." + connectedPubkey.toBase58().takeLast(4))
        } else {
            Text("Connect Wallet")
        }
    }
}
