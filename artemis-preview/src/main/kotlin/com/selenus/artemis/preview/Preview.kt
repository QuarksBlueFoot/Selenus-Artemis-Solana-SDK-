package com.selenus.artemis.preview

import com.selenus.artemis.metaplex.MetaplexClient
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey

fun main() {
  val rpc = RpcApi(JsonRpcClient("https://api.mainnet-beta.solana.com"))
  val metaplex = MetaplexClient(rpc)

  // Example: fetch metadata for a mint (replace with your mint)
  val mint = Pubkey.fromBase58("So11111111111111111111111111111111111111112") // wSOL (not an NFT; will return null)
  val md = metaplex.getMetadata(mint)
  println(md ?: "No metadata account for mint")
}


// ALT quick sanity: derive + instruction bytes (extension in artemis-programs)
val (createAltIx, altAddress) = com.selenus.artemis.programs.AddressLookupTableProgram.createLookupTable(
  authority = mint, // just for demo; use a real signer pubkey
  payer = mint,
  recentSlot = 0L
)
println("Derived ALT address (demo): ${altAddress.toString()} createIxDataLen=${createAltIx.data.size}")

// Fetch + decode ALT account data (real ALT accounts only)
val altData = rpc.getAccountInfoBase64(altAddress.toString(), commitment = "finalized")
if (altData != null) {
  val alt = com.selenus.artemis.vtx.AddressLookupTableAccount.decode(altAddress, altData)
  println("ALT decoded addresses=${alt.addresses.size}")
} else {
  println("ALT account not found (expected in demo).")
}


// ALT Toolkit sizing demo (no network dependency for the optimizer itself)
val dummyBlockhash = "11111111111111111111111111111111" // 32-byte zero base58
val feePayerKey = mint
val dummyFeePayer = object : com.selenus.artemis.runtime.Signer {
  override val publicKey = feePayerKey
  override fun sign(message: ByteArray): ByteArray = ByteArray(64)
}

val ix = com.selenus.artemis.tx.Instruction(
  programId = Pubkey.fromBase58("11111111111111111111111111111111"),
  accounts = listOf(com.selenus.artemis.tx.AccountMeta(mint, isSigner = false, isWritable = true)),
  data = byteArrayOf()
)

val opt = com.selenus.artemis.vtx.AltOptimizer.optimize(
  feePayerKey = dummyFeePayer.publicKey,
  recentBlockhash = dummyBlockhash,
  instructions = listOf(ix),
  candidates = emptyList()
)
println("ALT optimizer baseline size=${opt.messageSizeBytes} loaded=${opt.loadedAddressCount}")

// For compute-aware ALT selection, use AltToolkit with optimizeMode=AltOptimizer.Mode.SIZE_AND_COMPUTE

// Token-2022 + cNFT primitives are available via :artemis-token2022 and :artemis-cnft
