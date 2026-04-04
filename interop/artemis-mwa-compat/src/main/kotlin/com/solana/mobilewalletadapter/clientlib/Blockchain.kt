/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.solana.mobilewalletadapter.clientlib

/**
 * Blockchain designation for MWA connections.
 *
 * Matches upstream `com.solana.mobilewalletadapter.clientlib.Blockchain`.
 */
sealed class Blockchain(val cluster: String) {
    override fun toString(): String = cluster
}

/**
 * Solana blockchain variants.
 */
sealed class Solana(cluster: String) : Blockchain(cluster) {
    object MainnetBeta : Solana("solana:mainnet")
    object Devnet : Solana("solana:devnet")
    object Testnet : Solana("solana:testnet")

    class Custom(cluster: String) : Solana(cluster)
}

/**
 * Deprecated RPC cluster designation. Use [Blockchain] / [Solana] instead.
 */
@Deprecated("Use Blockchain / Solana instead", ReplaceWith("Solana.MainnetBeta"))
sealed class RpcCluster(val name: String) {
    object MainnetBeta : RpcCluster("mainnet-beta")
    object Devnet : RpcCluster("devnet")
    object Testnet : RpcCluster("testnet")

    fun toBlockchain(): Blockchain = when (this) {
        MainnetBeta -> Solana.MainnetBeta
        Devnet -> Solana.Devnet
        Testnet -> Solana.Testnet
    }
}
