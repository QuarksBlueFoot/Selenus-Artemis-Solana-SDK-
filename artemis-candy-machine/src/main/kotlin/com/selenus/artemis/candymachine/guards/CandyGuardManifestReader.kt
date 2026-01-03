package com.selenus.artemis.candymachine.guards

import com.selenus.artemis.candymachine.internal.BorshReader

/**
 * Reads a Candy Guard account and produces a machine-readable manifest.
 *
 * Notes:
 * - This parser is intentionally "capability" oriented. It reads enough to plan and validate mints.
 * - If the account layout changes and parsing becomes ambiguous, we fail closed (unknown guard set).
 */
object CandyGuardManifestReader {

  private const val ANCHOR_DISCRIMINATOR_LEN = 8

  /**
   * Parse a Candy Guard account's raw data (base64-decoded bytes).
   */
  fun read(accountData: ByteArray): CandyGuardManifest {
    if (accountData.size <= ANCHOR_DISCRIMINATOR_LEN) {
      return CandyGuardManifest(
        enabledGuards = emptySet(),
        requirements = GuardRequirements(),
        argSchema = emptyList(),
        remainingAccountRules = emptyList(),
        isPnft = false,
        unknownGuards = setOf("CandyGuard:account-data-too-short"),
      )
    }

    val r = BorshReader(accountData, ANCHOR_DISCRIMINATOR_LEN)

    // CandyGuard fields (Anchor/Borsh):
    // authority: Pubkey
    // (optional) base / bump fields may exist across versions; we keep this resilient by skipping pubkeys/bump-like bytes
    // default: GuardSet
    // groups: Vec<Group>
    //
    // We do not need group parsing for mobile deterministic planning.

    // Attempt to skip 32-byte authority.
    if (r.remaining() < 32) {
      return CandyGuardManifest(
        enabledGuards = emptySet(),
        requirements = GuardRequirements(),
        argSchema = emptyList(),
        remainingAccountRules = emptyList(),
        isPnft = false,
        unknownGuards = setOf("CandyGuard:missing-authority"),
      )
    }
    r.pubkey32()

    // Some versions include a base pubkey and bump before guardSet. If present, skip conservatively:
    // - if next bytes look like an option tag (0/1) for the first guard, we stop.
    // - otherwise, we try skipping a pubkey and a u8 bump.
    //
    // This heuristic keeps the parser tolerant across minor account revisions.
    val peek = runCatching { accountData[ANCHOR_DISCRIMINATOR_LEN + 32].toInt() and 0xFF }.getOrNull()
    if (peek != null && peek !in 0..1 && r.remaining() >= 33) {
      r.pubkey32()
      r.u8() // bump
    }

    val guardSetResult = parseGuardSet(r)

    // Groups vector exists after default guard set. We don't parse it yet; we just ensure we didn't underflow.
    // If the account has groups, the remaining bytes should still be valid Borsh. We ignore.

    return guardSetResult
  }

  private data class GuardSetParse(
    val enabled: MutableSet<GuardType> = linkedSetOf(),
    var solPayment: Boolean = false,
    var tokenPayment: Boolean = false,
    var allowList: Boolean = false,
    var gatekeeper: Boolean = false,
    var nftBurn: Boolean = false,
    var nftGate: Boolean = false,
    var tokenGate: Boolean = false,
    var pnft: Boolean = false,
    var solPaymentLamports: Long? = null,
    var tokenPaymentAmount: Long? = null,
    var tokenPaymentMint: ByteArray? = null,
    var tokenPaymentDestinationAta: ByteArray? = null,
    val unknown: MutableSet<String> = linkedSetOf(),
  )

  private fun parseGuardSet(r: BorshReader): CandyGuardManifest {
    // Candy Guard's GuardSet is a Borsh struct of many Option<Guard> fields.
    // We parse known guards in a stable order used by mpl-candy-guard v3+.
    val s = GuardSetParse()

    fun optGuard(type: GuardType, parse: (BorshReader) -> Unit) {
      val tag = r.u8() // 0 = None, 1 = Some
      if (tag == 0) return
      if (tag != 1) {
        s.unknown += "guard:${type.name}:invalid-option-tag:$tag"
        return
      }
      s.enabled += type
      runCatching { parse(r) }.onFailure {
        s.unknown += "guard:${type.name}:parse-failed"
      }
    }

    // Guard structs (sizes based on public IDL for mpl-candy-guard; we only read what we need and skip the rest).
    optGuard(GuardType.botTax) {
      // BotTax { lamports: u64, lastInstruction: bool }
      it.u64(); it.bool()
    }
    optGuard(GuardType.solPayment) {
      // SolPayment { lamports: u64, destination: Pubkey }
      val lamports = it.u64(); it.pubkey32(); s.solPayment = true; s.solPaymentLamports = lamports
    }
    optGuard(GuardType.tokenPayment) {
      // TokenPayment { amount: u64, mint: Pubkey, destinationAta: Pubkey }
      val amount = it.u64(); val mint = it.pubkey32(); val dst = it.pubkey32();
      s.tokenPayment = true
      s.tokenPaymentAmount = amount
      s.tokenPaymentMint = mint
      s.tokenPaymentDestinationAta = dst
    }
    optGuard(GuardType.startDate) {
      // StartDate { date: i64/u64 (unix) }
      it.u64()
    }
    optGuard(GuardType.thirdPartySigner) {
      // ThirdPartySigner { signerKey: Pubkey }
      it.pubkey32()
    }
    optGuard(GuardType.tokenGate) {
      // TokenGate { amount: u64, mint: Pubkey }
      it.u64(); it.pubkey32(); s.tokenGate = true
    }
    optGuard(GuardType.gatekeeper) {
      // Gatekeeper { network: Pubkey, expireOnUse: bool }
      it.pubkey32(); it.bool(); s.gatekeeper = true
    }
    optGuard(GuardType.endDate) {
      it.u64()
    }
    optGuard(GuardType.allowList) {
      // AllowList { merkleRoot: [u8;32] }
      it.bytes(32); s.allowList = true
    }
    optGuard(GuardType.mintLimit) {
      // MintLimit { id: u8, limit: u16 }
      it.u8(); it.u16()
    }
    optGuard(GuardType.nftPayment) {
      // NftPayment { requiredCollection: Pubkey?, destination: Pubkey }
      it.optionPubkey32(); it.pubkey32()
    }
    optGuard(GuardType.redeemedAmount) {
      // RedeemedAmount { amount: u64 }
      it.u64()
    }
    optGuard(GuardType.addressGate) {
      // AddressGate { address: Pubkey }
      it.pubkey32()
    }
    optGuard(GuardType.nftGate) {
      // NftGate { requiredCollection: Pubkey? }
      it.optionPubkey32(); s.nftGate = true
    }
    optGuard(GuardType.nftBurn) {
      // NftBurn { requiredCollection: Pubkey? }
      it.optionPubkey32(); s.nftBurn = true
    }
    optGuard(GuardType.tokenBurn) {
      // TokenBurn { amount: u64, mint: Pubkey }
      it.u64(); it.pubkey32()
    }
    optGuard(GuardType.freezeSolPayment) {
      // FreezeSolPayment { lamports: u64, destination: Pubkey }
      it.u64(); it.pubkey32()
    }
    optGuard(GuardType.freezeTokenPayment) {
      // FreezeTokenPayment { amount: u64, mint: Pubkey, destinationAta: Pubkey }
      it.u64(); it.pubkey32(); it.pubkey32()
    }
    optGuard(GuardType.programGate) {
      // ProgramGate { additional: Pubkey }
      it.pubkey32()
    }
    optGuard(GuardType.allocation) {
      // Allocation { id: u8, limit: u32 }
      it.u8(); it.u32()
    }
    optGuard(GuardType.token2022Payment) {
      // Token2022Payment { amount: u64, mint: Pubkey, destinationAta: Pubkey }
      val amount = it.u64(); val mint = it.pubkey32(); val dst = it.pubkey32();
      // v60: fail closed for token-2022 payments until token-2022 program is wired into mint_v2 builder.
      s.unknown += "guard:token2022Payment:not-supported"
      s.tokenPayment = true
      s.tokenPaymentAmount = amount
      s.tokenPaymentMint = mint
      s.tokenPaymentDestinationAta = dst
    }

    // Build requirements and arg schema
    val req = GuardRequirements(
      requiresSolPayment = s.solPayment,
      requiresTokenPayment = s.tokenPayment,
      requiresAllowList = s.allowList,
      requiresGatekeeper = s.gatekeeper,
      requiresNftBurn = s.nftBurn,
      requiresNftGate = s.nftGate,
      requiresTokenGate = s.tokenGate,
      requiresMintLimitArgs = s.enabled.contains(GuardType.mintLimit),
      requiresAllocationArgs = s.enabled.contains(GuardType.allocation),
    )

    val args = buildList {
      if (s.allowList) add(ArgRequirement.AllowListProof())
      if (s.enabled.contains(GuardType.mintLimit)) add(ArgRequirement.MintLimitId())
      if (s.enabled.contains(GuardType.allocation)) add(ArgRequirement.AllocationId())
      if (s.gatekeeper) add(ArgRequirement.GatekeeperToken())
    }

    val remaining = buildList {
      if (s.pnft) add(RemainingAccountRule.PnftTokenRecord())
    }

    return CandyGuardManifest(
      enabledGuards = s.enabled,
      requirements = req,
      argSchema = args,
      remainingAccountRules = remaining,
      isPnft = s.pnft,
      solPaymentLamports = s.solPaymentLamports,
      tokenPaymentMint = s.tokenPaymentMint?.let { com.selenus.artemis.runtime.Pubkey(it) },
      tokenPaymentAmount = s.tokenPaymentAmount,
      tokenPaymentDestinationAta = s.tokenPaymentDestinationAta?.let { com.selenus.artemis.runtime.Pubkey(it) },
      unknownGuards = s.unknown,
    )
  }
}
