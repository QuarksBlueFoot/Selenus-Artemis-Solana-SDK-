package com.selenus.artemis.samples.mwa

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private lateinit var status: TextView
  private lateinit var sender: ActivityResultSender
  private val compatFlow = CompatMigrationFlow()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    sender = ActivityResultSender(this)

    val connectCompat = Button(this).apply { text = "Authorize with compat imports" }
    val signMessage = Button(this).apply { text = "Sign message with compat imports" }
    val connectNative = Button(this).apply { text = "Connect with native Artemis" }
    status = TextView(this).apply {
      textSize = 16f
      text = "Ready"
    }

    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      setPadding(40, 56, 40, 40)
      addView(connectCompat, buttonParams())
      addView(signMessage, buttonParams())
      addView(connectNative, buttonParams())
      addView(status, textParams())
    }
    setContentView(container)

    connectCompat.setOnClickListener {
      lifecycleScope.launch {
        setStatus("Opening wallet through com.solana.mobilewalletadapter imports...")
        setStatus(describe(compatFlow.authorize(sender)) { auth ->
          val account = auth.accounts.firstOrNull()
          "Compat authorized ${account?.displayAddress ?: account?.label ?: "account"} on ${account?.chains?.joinToString() ?: "unknown chain"}"
        })
      }
    }

    signMessage.setOnClickListener {
      lifecycleScope.launch {
        setStatus("Requesting detached message signature...")
        setStatus(describe(compatFlow.signMessage(sender, "Artemis migration check".encodeToByteArray())) { count ->
          "Wallet returned $count detached signature(s)"
        })
      }
    }

    connectNative.setOnClickListener {
      lifecycleScope.launch {
        runCatching { NativeMigrationFlow.connect(this@MainActivity) }
          .onSuccess { setStatus("Native Artemis connected ${it.toBase58()}") }
          .onFailure { setStatus("Native connect failed: ${it.message}") }
      }
    }
  }

  private fun setStatus(message: String) {
    status.text = message
  }

  private fun buttonParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.WRAP_CONTENT
  ).apply { bottomMargin = 20 }

  private fun textParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.WRAP_CONTENT
  ).apply { topMargin = 16 }

  private fun <T> describe(result: TransactionResult<T>, onSuccess: (T) -> String): String = when (result) {
    is TransactionResult.Success -> onSuccess(result.payload)
    is TransactionResult.Failure -> "Wallet flow failed: ${result.message}"
    is TransactionResult.NoWalletFound -> "No wallet found: ${result.message}"
  }
}