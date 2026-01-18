package com.selenus.artemis.candymachine.guards

import com.selenus.artemis.candymachine.internal.BorshWriter

/**
 * Serializes Candy Guard MintArgs for `mint_v2`.
 *
 * We implement the small set of mint args that are commonly required for mobile:
 * - allowList proof
 * - mintLimit id
 * - allocation id
 * - gatekeeper token (optional)
 */
object CandyGuardMintArgsSerializer {

  fun serialize(args: GuardArgs?, manifest: CandyGuardManifest?): ByteArray? {
    if (args == null) return null

    // Only serialize if any supported arg is present.
    val hasAny = args.allowlistProof != null || args.mintLimitId != null || args.allocationId != null || args.gatekeeperToken != null
    if (!hasAny) return null

    // Validate against manifest if provided.
    manifest?.let {
      if (it.requirements.requiresAllowList && args.allowlistProof == null) {
        throw IllegalArgumentException("Allowlist proof is required by Candy Guard but was not provided")
      }
      if (it.requirements.requiresMintLimitArgs && args.mintLimitId == null) {
        throw IllegalArgumentException("MintLimit id is required by Candy Guard but was not provided")
      }
      if (it.requirements.requiresAllocationArgs && args.allocationId == null) {
        throw IllegalArgumentException("Allocation id is required by Candy Guard but was not provided")
      }
    }

    val w = BorshWriter()

    // IMPORTANT: Field order must match mpl-candy-guard MintArgs.
    // We include option tags for every field in the struct.
    // For fields we don't support/need, we write None.
    //
    // Order used here mirrors the guard-set order in CandyGuardManifestReader.

    // botTax
    w.option(null as Int?) { _ -> }

    // solPayment
    w.option(null as Int?) { _ -> }

    // tokenPayment
    w.option(null as Int?) { _ -> }

    // startDate
    w.option(null as Int?) { _ -> }

    // thirdPartySigner
    w.option(null as Int?) { _ -> }

    // tokenGate
    w.option(null as Int?) { _ -> }

    // gatekeeper: Option<{ token: Option<Pubkey> }>
    w.option(args.gatekeeperToken) { gk ->
      w.option(gk.token) { t -> w.fixedBytes(t.bytes) }
    }

    // endDate
    w.option(null as Int?) { _ -> }

    // allowList: Option<{ proof: Vec<[u8;32]> }>
    w.option(args.allowlistProof) { al ->
      // validate node size
      al.proof.forEach {
        require(it.size == 32) { "Allowlist proof node must be 32 bytes" }
      }
      w.vec(al.proof) { node -> w.fixedBytes(node) }
    }

    // mintLimit: Option<{ id: u8 }>
    w.option(args.mintLimitId) { id ->
      require(id in 0..255) { "mintLimitId must be 0..255" }
      w.u8(id)
    }

    // nftPayment
    w.option(null as Int?) { _ -> }

    // redeemedAmount
    w.option(null as Int?) { _ -> }

    // addressGate
    w.option(null as Int?) { _ -> }

    // nftGate
    w.option(null as Int?) { _ -> }

    // nftBurn
    w.option(null as Int?) { _ -> }

    // tokenBurn
    w.option(null as Int?) { _ -> }

    // freezeSolPayment
    w.option(null as Int?) { _ -> }

    // freezeTokenPayment
    w.option(null as Int?) { _ -> }

    // programGate
    w.option(null as Int?) { _ -> }

    // allocation: Option<{ id: u8 }>
    w.option(args.allocationId) { id ->
      require(id in 0..255) { "allocationId must be 0..255" }
      w.u8(id)
    }

    // token2022Payment
    w.option(null as Int?) { _ -> }

    return w.bytes()
  }
}
