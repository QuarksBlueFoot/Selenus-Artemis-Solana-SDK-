/*
 * Drop-in source compatibility for the upstream nested exception
 * `com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.InsecureWalletEndpointUriException`.
 *
 * Upstream declares this as a nested runtime exception to signal that the
 * wallet URI the app received violates the MWA spec's https-only requirement
 * (no cleartext HTTP, no custom schemes outside `solana-wallet`). Apps that
 * catch it by FQN keep compiling with Artemis.
 */
package com.solana.mobilewalletadapter.clientlib.protocol

/**
 * Raised when a wallet-provided endpoint URI is not safe to connect to.
 *
 * The spec requires https for remote endpoints; cleartext or otherwise
 * insecure URIs raise this instead of silently continuing.
 */
class InsecureWalletEndpointUriException(message: String) : RuntimeException(message)
