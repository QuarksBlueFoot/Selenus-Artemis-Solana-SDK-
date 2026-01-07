package com.selenus.artemis.candymachine

import com.selenus.artemis.candymachine.guards.*
import com.selenus.artemis.candymachine.state.CandyMachineStateReader
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Candy Machine Intelligence Layer.
 *
 * Pure account reads. No indexer. No vendor services.
 */
object CandyMachineIntelligence {

  suspend fun readCandyGuardState(rpc: RpcApi, candyGuard: Pubkey): CandyGuardState {
    val guardData = fetchAccountBase64(rpc, candyGuard) ?: throw IllegalArgumentException("Candy Guard account not found")
    val manifest = CandyGuardManifestReader.read(guardData)
    if (manifest.unknownGuards.isNotEmpty()) {
      // Fail closed to avoid footguns.
      throw IllegalArgumentException("Candy Guard has unsupported/unknown guards: " + manifest.unknownGuards.joinToString(", "))
    }
    val summary = buildGuardSummary(manifest)
    return CandyGuardState(manifest = manifest, guardSummary = summary)
  }

  suspend fun readCandyMachineState(
    rpc: RpcApi,
    candyMachine: Pubkey,
    candyGuard: Pubkey? = null,
  ): CandyMachineState {
    val guardState = if (candyGuard == null) null else readCandyGuardState(rpc, candyGuard)
    val price = guardState?.manifest?.let { manifestToPrice(it) }
    val cmData = fetchAccountBase64(rpc, candyMachine) ?: throw IllegalArgumentException("Candy Machine account not found")
    return CandyMachineStateReader.toState(
      accountData = cmData,
      currentPrice = price,
      guardSummary = guardState?.guardSummary ?: emptyList(),
    )
  }

  private fun manifestToPrice(m: CandyGuardManifest): Price? {
    if (m.solPaymentLamports != null) return Price(mint = null, amount = m.solPaymentLamports)
    if (m.tokenPaymentMint != null && m.tokenPaymentAmount != null) return Price(mint = m.tokenPaymentMint, amount = m.tokenPaymentAmount)
    return null
  }

  private fun buildGuardSummary(m: CandyGuardManifest): List<GuardSummaryItem> = buildList {
    if (m.solPaymentLamports != null) add(
      GuardSummaryItem(type = GuardType.solPayment, required = true, details = "SOL payment", paymentMint = null, paymentAmount = m.solPaymentLamports)
    )
    if (m.tokenPaymentMint != null && m.tokenPaymentAmount != null) add(
      GuardSummaryItem(type = GuardType.tokenPayment, required = true, details = "SPL token payment", paymentMint = m.tokenPaymentMint, paymentAmount = m.tokenPaymentAmount)
    )
    if (m.requirements.requiresAllowList) add(GuardSummaryItem(type = GuardType.allowList, required = true, details = "Allowlist proof required"))
    if (m.requirements.requiresGatekeeper) add(GuardSummaryItem(type = GuardType.gatekeeper, required = true, details = "Gatekeeper"))
    if (m.requirements.requiresMintLimitArgs) add(GuardSummaryItem(type = GuardType.mintLimit, required = true, details = "Mint limit id required"))
    if (m.requirements.requiresAllocationArgs) add(GuardSummaryItem(type = GuardType.allocation, required = true, details = "Allocation id required"))
    if (m.requirements.requiresNftBurn) add(GuardSummaryItem(type = GuardType.nftBurn, required = true, details = "NFT burn"))
    if (m.requirements.requiresNftGate) add(GuardSummaryItem(type = GuardType.nftGate, required = true, details = "NFT gate"))
    if (m.requirements.requiresTokenGate) add(GuardSummaryItem(type = GuardType.tokenGate, required = true, details = "Token gate"))
  }

  private suspend fun fetchAccountBase64(rpc: RpcApi, pubkey: Pubkey): ByteArray? {
    val rsp = rpc.getAccountInfo(pubkey.toBase58(), commitment = "confirmed", encoding = "base64")
    val value = rsp["value"] ?: return null
    if (value.toString() == "null") return null
    val dataArr = value.jsonObject["data"]?.jsonArray ?: return null
    if (dataArr.isEmpty()) return null
    val b64 = dataArr[0].jsonPrimitive.content
    return java.util.Base64.getDecoder().decode(b64)
  }
}
