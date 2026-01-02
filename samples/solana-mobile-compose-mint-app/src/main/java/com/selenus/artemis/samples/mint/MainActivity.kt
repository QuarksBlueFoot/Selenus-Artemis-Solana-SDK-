package com.selenus.artemis.samples.mint

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.selenus.artemis.candymachine.presets.CandyMachineMintPresets
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.wallet.mwa.DataStoreAuthTokenStore
import com.selenus.artemis.wallet.mwa.MwaWalletAdapter
import kotlinx.coroutines.launch

/**
 * v64 sample app
 *
 * Purpose:
 * - show how Artemis plugs into Solana Mobile (MWA) without glue code
 * - demonstrate the one-call Candy Machine preset: mintNewWithSeed
 *
 * Notes:
 * - this sample is intentionally minimal and is not included in the default repo build
 *   (enable with -PenableAndroidSamples=true)
 */
class MainActivity : AppCompatActivity() {

  private lateinit var status: TextView

  private var adapter: MwaWalletAdapter? = null
  private var connectedPk: Pubkey? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val inputRpc = findViewById<EditText>(R.id.inputRpc)
    val inputCandyMachine = findViewById<EditText>(R.id.inputCandyMachine)
    val inputCandyGuard = findViewById<EditText>(R.id.inputCandyGuard)
    val inputGroup = findViewById<EditText>(R.id.inputGroup)
    val inputSeed = findViewById<EditText>(R.id.inputSeed)
    val checkForcePnft = findViewById<CheckBox>(R.id.checkForcePnft)
    val btnConnect = findViewById<Button>(R.id.btnConnect)
    val btnMint = findViewById<Button>(R.id.btnMint)
    status = findViewById(R.id.textStatus)

    btnConnect.setOnClickListener {
      lifecycleScope.launch {
        try {
          setStatus("Connecting wallet…")
          val a = MwaWalletAdapter(
            activity = this@MainActivity,
            identityUri = Uri.parse("https://canarymessenger.com"),
            iconPath = "favicon.ico",
            identityName = "Artemis Mint Sample",
            authStore = DataStoreAuthTokenStore.from(this@MainActivity)
          )
          val pk = a.connect()
          adapter = a
          connectedPk = pk
          setStatus("Connected: ${pk.toString()}")
        } catch (t: Throwable) {
          setStatus("Connect error: ${t.message}")
        }
      }
    }

    btnMint.setOnClickListener {
      lifecycleScope.launch {
        try {
          val a = adapter ?: throw IllegalStateException("Connect a wallet first")
          val endpoint = inputRpc.text.toString().trim()
          val candyMachine = Pubkey.fromBase58(inputCandyMachine.text.toString().trim())
          val candyGuard = Pubkey.fromBase58(inputCandyGuard.text.toString().trim())
          val group = inputGroup.text.toString().trim().ifEmpty { null }
          val seed = inputSeed.text.toString().trim().ifEmpty { "mint-1" }
          val forcePnft = checkForcePnft.isChecked

          setStatus("Planning + minting…")
          val rpc = RpcApi(JsonRpcClient(endpoint))

          val res = CandyMachineMintPresets.mintNewWithSeed(
            rpc = rpc,
            adapter = a,
            candyGuard = candyGuard,
            candyMachine = candyMachine,
            seed = seed,
            group = group,
            guardArgs = null,
            forcePnft = forcePnft,
          )

          val sig = res.signature
          setStatus("Minted! Signature: $sig\nMint: ${res.mintedMint}")
        } catch (t: Throwable) {
          setStatus("Mint error: ${t.message}")
        }
      }
    }
  }

  private fun setStatus(text: String) {
    status.text = "Status: $text"
  }
}
