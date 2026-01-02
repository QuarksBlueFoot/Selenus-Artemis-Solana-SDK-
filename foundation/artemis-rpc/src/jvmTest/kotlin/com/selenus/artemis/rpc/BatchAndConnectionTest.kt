package com.selenus.artemis.rpc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive

/**
 * Tests for JSON-RPC batch builder and Connection commitment overloads.
 */
class BatchAndConnectionTest {

    @Test
    fun testBatchRequestBuilderEmpty() {
        val builder = RpcApi.BatchRequestBuilder()
        assertTrue(builder.requests.isEmpty())
    }

    @Test
    fun testBatchRequestBuilderAddRaw() {
        val builder = RpcApi.BatchRequestBuilder()
        builder.add("getHealth")
        builder.add("getVersion")
        assertEquals(2, builder.requests.size)
        assertEquals("getHealth", builder.requests[0].first)
        assertEquals("getVersion", builder.requests[1].first)
    }

    @Test
    fun testBatchRequestBuilderWithParams() {
        val builder = RpcApi.BatchRequestBuilder()
        val params = buildJsonArray { add(JsonPrimitive("test")) }
        builder.add("getBalance", params)
        assertEquals(1, builder.requests.size)
        assertEquals("getBalance", builder.requests[0].first)
        assertEquals(params, builder.requests[0].second)
    }

    @Test
    fun testBatchBuilderConvenienceMethods() {
        val builder = RpcApi.BatchRequestBuilder()
        builder.getBalance("11111111111111111111111111111111")
        builder.getSlot()
        builder.getHealth()
        builder.getVersion()
        builder.getEpochInfo()
        builder.getBlockHeight()
        builder.getTransactionCount()
        builder.getLatestBlockhash()
        assertEquals(8, builder.requests.size)
        assertEquals("getBalance", builder.requests[0].first)
        assertEquals("getSlot", builder.requests[1].first)
        assertEquals("getHealth", builder.requests[2].first)
        assertEquals("getVersion", builder.requests[3].first)
        assertEquals("getEpochInfo", builder.requests[4].first)
        assertEquals("getBlockHeight", builder.requests[5].first)
        assertEquals("getTransactionCount", builder.requests[6].first)
        assertEquals("getLatestBlockhash", builder.requests[7].first)
    }

    @Test
    fun testBatchBuilderGetAccountInfo() {
        val builder = RpcApi.BatchRequestBuilder()
        builder.getAccountInfo("11111111111111111111111111111111", "finalized", "jsonParsed")
        assertEquals(1, builder.requests.size)
        assertEquals("getAccountInfo", builder.requests[0].first)
    }

    @Test
    fun testBatchBuilderTokenMethods() {
        val builder = RpcApi.BatchRequestBuilder()
        builder.getTokenAccountBalance("11111111111111111111111111111111")
        builder.getMinimumBalanceForRentExemption(165L)
        assertEquals(2, builder.requests.size)
        assertEquals("getTokenAccountBalance", builder.requests[0].first)
        assertEquals("getMinimumBalanceForRentExemption", builder.requests[1].first)
    }

    @Test
    fun testBatchBuilderSignatureStatuses() {
        val builder = RpcApi.BatchRequestBuilder()
        builder.getSignatureStatuses(listOf("sig1", "sig2"), searchTransactionHistory = false)
        assertEquals(1, builder.requests.size)
        assertEquals("getSignatureStatuses", builder.requests[0].first)
    }

    // ════════════════════════════════════════════════════════════════════════
    // COMMITMENT ENUM TESTS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testCommitmentEnum() {
        assertEquals("processed", Commitment.PROCESSED.value)
        assertEquals("confirmed", Commitment.CONFIRMED.value)
        assertEquals("finalized", Commitment.FINALIZED.value)
    }

    @Test
    fun testCommitmentFromValue() {
        assertEquals(Commitment.PROCESSED, Commitment.fromValue("processed"))
        assertEquals(Commitment.CONFIRMED, Commitment.fromValue("Confirmed"))
        assertEquals(Commitment.FINALIZED, Commitment.fromValue("FINALIZED"))
    }

    @Test
    fun testCommitmentToString() {
        assertEquals("confirmed", Commitment.CONFIRMED.toString())
    }

    @Test
    fun testSolanaClusterEndpoints() {
        assertTrue(SolanaCluster.MAINNET_BETA.endpoint.contains("mainnet"))
        assertTrue(SolanaCluster.DEVNET.endpoint.contains("devnet"))
        assertTrue(SolanaCluster.TESTNET.endpoint.contains("testnet"))
    }

    @Test
    fun testConnectionDefaultCommitment() {
        val conn = Connection("https://api.devnet.solana.com")
        assertEquals(Commitment.FINALIZED, conn.defaultCommitment)
    }

    @Test
    fun testConnectionCustomCommitment() {
        val conn = Connection("https://api.devnet.solana.com", Commitment.CONFIRMED)
        assertEquals(Commitment.CONFIRMED, conn.defaultCommitment)
    }

    @Test
    fun testConnectionFromCluster() {
        val conn = Connection(SolanaCluster.DEVNET, Commitment.PROCESSED)
        assertEquals(Commitment.PROCESSED, conn.defaultCommitment)
    }
}
