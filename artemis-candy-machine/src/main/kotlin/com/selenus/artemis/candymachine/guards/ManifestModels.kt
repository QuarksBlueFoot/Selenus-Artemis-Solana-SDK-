package com.selenus.artemis.candymachine.guards

import com.selenus.artemis.runtime.Pubkey

data class GuardRequirements(
  val requiresSolPayment: Boolean = false,
  val requiresTokenPayment: Boolean = false,
  val requiresAllowList: Boolean = false,
  val requiresGatekeeper: Boolean = false,
  val requiresNftBurn: Boolean = false,
  val requiresNftGate: Boolean = false,
  val requiresTokenGate: Boolean = false,
  val requiresMintLimitArgs: Boolean = false,
  val requiresAllocationArgs: Boolean = false,
)

sealed interface ArgRequirement {
  val guard: GuardType

  data class AllowListProof(override val guard: GuardType = GuardType.allowList) : ArgRequirement
  data class MintLimitId(override val guard: GuardType = GuardType.mintLimit) : ArgRequirement
  data class AllocationId(override val guard: GuardType = GuardType.allocation) : ArgRequirement
  data class GatekeeperToken(override val guard: GuardType = GuardType.gatekeeper) : ArgRequirement
}

/**
 * A deterministic rule describing which remaining accounts must be appended for a mint.
 */
sealed interface RemainingAccountRule {
  val guard: GuardType

  /**
   * pNFT minting requires token + tokenRecord accounts.
   */
  data class PnftTokenRecord(override val guard: GuardType = GuardType.unknown) : RemainingAccountRule

  /**
   * Placeholder for guards that require additional accounts (rare on mobile).
   *
   * We keep it typed and explicit so the planner can fail early if unsupported.
   */
  data class Custom(
    override val guard: GuardType,
    val description: String,
  ) : RemainingAccountRule
}

data class CandyGuardManifest(
  val enabledGuards: Set<GuardType>,
  val requirements: GuardRequirements,
  val argSchema: List<ArgRequirement>,
  val remainingAccountRules: List<RemainingAccountRule>,
  val isPnft: Boolean,
  val solPaymentLamports: Long? = null,
  val tokenPaymentMint: Pubkey? = null,
  val tokenPaymentAmount: Long? = null,
  /** Destination token account (ATA) for token payments, if parsed from guard config. */
  val tokenPaymentDestinationAta: Pubkey? = null,
  /** Guard identifiers that were detected but could not be parsed by this SDK version. */
  val unknownGuards: Set<String> = emptySet(),
)

data class GuardSummaryItem(
  val type: GuardType,
  val required: Boolean,
  val details: String? = null,
  val paymentMint: Pubkey? = null,
  val paymentAmount: Long? = null,
)

data class Price(
  val mint: Pubkey?, // null = SOL
  val amount: Long,
)

data class CandyMachineState(
  val itemsAvailable: Long,
  val itemsRedeemed: Long,
  val isSoldOut: Boolean,
  val currentPrice: Price?,
  val guardSummary: List<GuardSummaryItem>,
)

data class CandyGuardState(
  val manifest: CandyGuardManifest,
  val guardSummary: List<GuardSummaryItem>,
)
