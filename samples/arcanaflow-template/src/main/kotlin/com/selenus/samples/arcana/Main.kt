package com.selenus.samples.arcana
import com.selenus.artemis.logging.Log
import com.selenus.artemis.gaming.*
import com.selenus.artemis.rpc.RpcClient
import com.selenus.artemis.runtime.Keypair
import kotlinx.coroutines.*
import java.security.SecureRandom
/**
 * ArcanaFlow template runner.
 *
 * Replace buildGameActionIx with real program instructions and wire your RPC endpoint.
 */
fun main() = runBlocking {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  // 1) Build your RPC client
  val rpc = RpcClient("https://YOUR_RPC_HTTP_URL")
  // 2) Create a test fee payer (DO NOT use mainnet keys here)
  val feePayerKp = Keypair.generate(SecureRandom())
  val feePayer = feePayerKp
  // 3) Configure ArcanaFlow
  val lane = ArcanaFlow(scope, frameWindowMs = 60)
  lane.start()
  val cache = AltSessionCache()
  val builder = AltSessionBuilder(maxAddresses = 256)
  val oracle = PriorityFeeOracle(scope)
  val composer = ArcanaFlowFrameComposer(
    programId = "YourGameProgramIdBase58",
    tier = ComputeBudgetPresets.Tier.COMPETITIVE,
    oracle = oracle
  )
  // Sample known LUT list. Add real LUT addresses once created.
  val knownLuts = emptyList<com.selenus.artemis.runtime.Pubkey>()
  val job = scope.launch {
    lane.frames.collect { frame ->
      val proposal = ArcanaFlowTxHelper.collectForAltPlanning(frame, cache, builder)
      val plan = composer.compose(
        frame = frame,
        knownLookupTables = knownLuts,
        proposal = proposal
      )
      // You need a real recent blockhash to compile. This is a template call site.
      val bh = "RecentBlockhashBase58"
      // In a real app:
      // - fetch blockhash via RPC
      // - sign with fee payer + session signer
      // - send
      // - recordOutcome into oracle
      // val tx = ArcanaFlowV0Compiler.compileFrame(
      //   rpc = rpc,
      //   feePayer = feePayer,
      //   additionalSigners = emptyList(),
      //   recentBlockhash = bh,
      //   plan = plan
      // )
      Log.get("ArcanaTemplate").info("frame: actions=${frame.instructions.size} proposalSize=${proposal.addresses.size} suggestedFee=${plan.suggestedMicroLamports}")
    }
  }
  // Enqueue some fake actions
  repeat(10) {
    lane.enqueue(buildGameActionIx())
    delay(10)
  }
  delay(500)
  job.cancelAndJoin()
  lane.stop()
  scope.cancel()
}
private fun buildGameActionIx(): com.selenus.artemis.tx.Instruction {
  // Replace with real instruction builder for your program
  return ComputeBudgetPresets.setComputeUnitLimit(200_000)
}