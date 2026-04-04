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
sealed class Blockchain(
    val name: String,
    val cluster: String
) {
    val fullName: String get() = "$name:$cluster"

    override fun toString(): String = fullName
}

/**
 * Solana blockchain variants.
 */
sealed class Solana {
    object Mainnet : Blockchain("solana", "mainnet")
    object Devnet : Blockchain("solana", "devnet")
    object Testnet : Blockchain("solana", "testnet")
}

/**
 * Deprecated RPC cluster designation. Use [Blockchain] / [Solana] instead.
 */
@Deprecated("Use Blockchain / Solana instead", ReplaceWith("Solana.Mainnet"))
sealed class RpcCluster(val name: String) {
    object MainnetBeta : RpcCluster("mainnet-beta")
    object Devnet : RpcCluster("devnet")
    object Testnet : RpcCluster("testnet")
    class Custom(name: String) : RpcCluster(name)

    fun toBlockchain(): Blockchain = when (this) {
        is MainnetBeta -> Solana.Mainnet
        is Devnet -> Solana.Devnet
        is Testnet -> Solana.Testnet
        is Custom -> Solana.Mainnet // fallback
    }
}
