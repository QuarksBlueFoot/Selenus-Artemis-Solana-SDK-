package com.selenus.artemis.candymachine.guards

/**
 * Candy Guard guard types.
 *
 * Names mirror the on-chain identifiers for developer clarity.
 */
enum class GuardType {
  botTax,
  solPayment,
  tokenPayment,
  startDate,
  endDate,
  thirdPartySigner,
  tokenGate,
  gatekeeper,
  allowList,
  mintLimit,
  nftBurn,
  nftGate,
  tokenBurn,
  programGate,
  redeemedAmount,
  addressGate,
  allocation,
  freezeSolPayment,
  freezeTokenPayment,
  nftPayment,
  token2022Payment,
  unknown,
}
