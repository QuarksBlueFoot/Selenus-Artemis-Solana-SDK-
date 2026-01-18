package com.selenus.artemis.candymachine.planner

import com.selenus.artemis.candymachine.CandyGuardMintV2
import com.selenus.artemis.candymachine.CandyMachinePdas
import com.selenus.artemis.candymachine.guards.*
import com.selenus.artemis.candymachine.internal.AssociatedTokenAddresses
import com.selenus.artemis.nft.MetadataParser
import com.selenus.artemis.nft.Pdas
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Guard-aware planner that derives required accounts and (when possible) MintArgs.
 *
 * This module is optional. If you already have your accounts and args, call CandyGuardMintV2.build(...) directly.
 */
object CandyGuardAccountPlanner {

  suspend fun planMint(
    rpc: RpcApi,
    candyGuard: Pubkey,
    candyMachine: Pubkey,
    wallet: Pubkey,
    mint: Pubkey,
    group: String? = null,
    guardArgs: GuardArgs? = null,
    forcePnft: Boolean = false,
    mintIsSigner: Boolean = false,
  ): MintPlan {
    val warnings = ArrayList<String>()

    // Read Candy Guard manifest.
    val guardData = fetchAccountBase64(rpc, candyGuard) ?: throw IllegalArgumentException("Candy Guard account not found")
    val manifest = CandyGuardManifestReader.read(guardData)

    if (manifest.unknownGuards.isNotEmpty()) {
      throw IllegalArgumentException(
        "Candy Guard has unsupported/unknown guards: " + manifest.unknownGuards.joinToString(", ")
      )
    }

    // Read Candy Machine core to derive collection mint + authority.
    val cmData = fetchAccountBase64(rpc, candyMachine) ?: throw IllegalArgumentException("Candy Machine account not found")
    val core = com.selenus.artemis.candymachine.state.CandyMachineStateReader.parseCoreFields(cmData)
    val collectionMint = Pubkey(core.collectionMint)
    val candyMachineAuthorityPda = CandyMachinePdas.findCandyMachineAuthorityPda(candyMachine).address

    // Collection metadata -> update authority.
    val collectionMetaPda = Pdas.metadataPda(collectionMint)
    val collectionMetaBytes = fetchAccountBase64(rpc, collectionMetaPda) ?: throw IllegalArgumentException("Collection metadata account not found")
    val collectionMeta = MetadataParser.parse(collectionMint, collectionMetaBytes)
    val collectionUpdateAuthority = collectionMeta.updateAuthority

    val nftMetadata = Pdas.metadataPda(mint)
    val nftMasterEdition = Pdas.masterEditionPda(mint)
    val collectionMasterEdition = Pdas.masterEditionPda(collectionMint)

    val collectionDelegateRecord = Pdas.collectionAuthorityRecordPda(collectionMint, candyMachineAuthorityPda)

    // pNFT support: not all Candy Machines are pNFT. We only inject token/tokenRecord if caller opts in
    // by setting manifest.isPnft (future) or by providing guardArgs.gatekeeperToken etc. For now, we detect
    // pNFT via the presence of sysvar instructions + slot hashes required by mint_v2 and allow caller override.
    // If you know your machine enforces pNFT, set `forcePnft = true` by passing a token arg in guardArgs.
    val token = if (forcePnft) AssociatedTokenAddresses.ata(wallet, mint) else null
    val tokenRecord = if (forcePnft && token != null) Pdas.tokenRecordPda(mint, token) else null

    val mintArgsBorsh = CandyGuardMintArgsSerializer.serialize(guardArgs, manifest)

    // Remaining accounts for common guards.
    val remaining = ArrayList<com.selenus.artemis.tx.AccountMeta>()

    // Token payment requires payer token account + destination token account.
    if (manifest.requirements.requiresTokenPayment) {
      val pm = manifest.tokenPaymentMint
      val dst = manifest.tokenPaymentDestinationAta
      if (pm == null || dst == null) {
        throw IllegalArgumentException("Token payment guard enabled but mint/destination could not be parsed")
      }
      val payerAta = AssociatedTokenAddresses.ata(wallet, pm)
      remaining += com.selenus.artemis.tx.AccountMeta(payerAta, isSigner = false, isWritable = true)
      remaining += com.selenus.artemis.tx.AccountMeta(dst, isSigner = false, isWritable = true)
    }

    // Build guard summary for UI.
    if (manifest.requirements.requiresAllowList && guardArgs?.allowlistProof == null) {
      warnings += "Candy Guard requires allowlist proof; provide GuardArgs.allowlistProof or mint will fail"
    }

    val accounts = CandyGuardMintV2.Accounts(
      candyGuard = candyGuard,
      candyMachine = candyMachine,
      payer = wallet,
      minter = wallet,
      nftMint = mint,
      nftMintAuthority = wallet,
      nftMetadata = nftMetadata,
      nftMasterEdition = nftMasterEdition,
      token = token,
      tokenRecord = tokenRecord,
      collectionDelegateRecord = collectionDelegateRecord,
      collectionMint = collectionMint,
      collectionMetadata = collectionMetaPda,
      collectionMasterEdition = collectionMasterEdition,
      collectionUpdateAuthority = collectionUpdateAuthority!!,
      candyMachineAuthorityPda = candyMachineAuthorityPda,
      remainingAccounts = emptyList(),
      nftMintIsSigner = mintIsSigner,
      nftMintAuthorityIsSigner = true,
    )

    // Merge remaining accounts after base set.
    val finalAccounts = if (remaining.isEmpty()) accounts else accounts.copy(remainingAccounts = remaining)

    return MintPlan(accounts = finalAccounts, mintArgsBorsh = mintArgsBorsh, warnings = warnings)
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
