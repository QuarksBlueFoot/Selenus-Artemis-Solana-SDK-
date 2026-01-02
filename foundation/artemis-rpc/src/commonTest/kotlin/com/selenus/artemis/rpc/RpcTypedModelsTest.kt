package com.selenus.artemis.rpc

import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for typed RPC response models — verifying fromJson parsing.
 */
class RpcTypedModelsTest {

    @Test
    fun testEpochInfoFromJson() {
        val json = buildJsonObject {
            put("absoluteSlot", 166598)
            put("blockHeight", 166500)
            put("epoch", 27)
            put("slotIndex", 2790)
            put("slotsInEpoch", 8192)
            put("transactionCount", 22661093)
        }
        val info = EpochInfo.fromJson(json)
        assertEquals(166598L, info.absoluteSlot)
        assertEquals(27L, info.epoch)
        assertEquals(8192L, info.slotsInEpoch)
        assertEquals(22661093L, info.transactionCount)
    }

    @Test
    fun testEpochScheduleFromJson() {
        val json = buildJsonObject {
            put("slotsPerEpoch", 432000)
            put("leaderScheduleSlotOffset", 432000)
            put("warmup", false)
            put("firstNormalEpoch", 0)
            put("firstNormalSlot", 0)
        }
        val schedule = EpochSchedule.fromJson(json)
        assertEquals(432000L, schedule.slotsPerEpoch)
        assertEquals(false, schedule.warmup)
    }

    @Test
    fun testInflationGovernorFromJson() {
        val json = buildJsonObject {
            put("initial", 0.15)
            put("terminal", 0.015)
            put("taper", 0.15)
            put("foundation", 0.05)
            put("foundationTerm", 7.0)
        }
        val gov = InflationGovernor.fromJson(json)
        assertEquals(0.15, gov.initial, 0.001)
        assertEquals(0.015, gov.terminal, 0.001)
    }

    @Test
    fun testInflationRateFromJson() {
        val json = buildJsonObject {
            put("total", 0.149)
            put("validator", 0.148)
            put("foundation", 0.001)
            put("epoch", 100)
        }
        val rate = InflationRate.fromJson(json)
        assertEquals(0.149, rate.total, 0.001)
        assertEquals(100L, rate.epoch)
    }

    @Test
    fun testSupplyFromJson() {
        val json = buildJsonObject {
            putJsonObject("value") {
                put("total", 590000000000000000)
                put("circulating", 400000000000000000)
                put("nonCirculating", 190000000000000000)
                putJsonArray("nonCirculatingAccounts") {
                    add("FriELggez2Dy3phZeHHAdpcoEXkKQVkv6tx3zDtCVP8T")
                }
            }
        }
        val supply = Supply.fromJson(json)
        assertEquals(590000000000000000L, supply.total)
        assertEquals(1, supply.nonCirculatingAccounts.size)
    }

    @Test
    fun testStakeActivationFromJson() {
        val json = buildJsonObject {
            put("state", "active")
            put("active", 1000000)
            put("inactive", 0)
        }
        val act = StakeActivation.fromJson(json)
        assertEquals("active", act.state)
        assertEquals(1000000L, act.active)
    }

    @Test
    fun testClusterNodeFromJson() {
        val json = buildJsonObject {
            put("pubkey", "9QzsJf7LPLj8GkXbYT3LFDKqsj2hHG7TA3xinJHu8epQ")
            put("gossip", "10.239.6.48:8001")
            put("tpu", "10.239.6.48:8856")
            put("rpc", JsonNull)
            put("version", "1.0.0 c375ce1f")
            put("featureSet", 12345)
            put("shredVersion", 0)
        }
        val node = ClusterNode.fromJson(json)
        assertEquals("9QzsJf7LPLj8GkXbYT3LFDKqsj2hHG7TA3xinJHu8epQ", node.pubkey)
        assertNotNull(node.gossip)
        assertNull(node.rpc)
    }

    @Test
    fun testPerformanceSampleFromJson() {
        val json = buildJsonObject {
            put("slot", 348125)
            put("numTransactions", 126)
            put("numSlots", 126)
            put("samplePeriodSecs", 60)
        }
        val sample = PerformanceSample.fromJson(json)
        assertEquals(348125L, sample.slot)
        assertEquals(60, sample.samplePeriodSecs)
    }

    @Test
    fun testTokenAccountBalanceFromJson() {
        val json = buildJsonObject {
            putJsonObject("value") {
                put("amount", "9864000000")
                put("decimals", 6)
                put("uiAmount", 9864.0)
                put("uiAmountString", "9864")
            }
        }
        val bal = TokenAccountBalance.fromJson(json)
        assertEquals("9864000000", bal.amount)
        assertEquals(6, bal.decimals)
        assertEquals(9864.0, bal.uiAmount)
    }

    @Test
    fun testVoteAccountsFromJson() {
        val json = buildJsonObject {
            putJsonArray("current") {
                addJsonObject {
                    put("votePubkey", "3ZT31jkAGhUaw8jsRFXi1SE1DVJq5BSTyymTPEVb3gFr")
                    put("nodePubkey", "B97CCUW3AEZFGy6uUg6zUdnNYvnVq5VG8PUtb2HayTDD")
                    put("activatedStake", 42)
                    put("epochVoteAccount", true)
                    put("commission", 0)
                    put("lastVote", 147)
                    put("rootSlot", 146)
                }
            }
            putJsonArray("delinquent") {}
        }
        val va = VoteAccounts.fromJson(json)
        assertEquals(1, va.current.size)
        assertEquals(0, va.delinquent.size)
        assertEquals("3ZT31jkAGhUaw8jsRFXi1SE1DVJq5BSTyymTPEVb3gFr", va.current[0].votePubkey)
    }

    @Test
    fun testBlockCommitmentFromJson() {
        val json = buildJsonObject {
            putJsonArray("commitment") { add(0); add(1); add(2) }
            put("totalStake", 42)
        }
        val bc = BlockCommitment.fromJson(json)
        assertEquals(3, bc.commitment?.size)
        assertEquals(42L, bc.totalStake)
    }

    @Test
    fun testSignatureStatusInfoHelpers() {
        val confirmed = SignatureStatusInfo(slot = 100, confirmations = 10, err = null, confirmationStatus = "confirmed")
        assertTrue(confirmed.isConfirmed)
        assertTrue(!confirmed.isFinalized)
        assertTrue(!confirmed.hasError)

        val finalized = SignatureStatusInfo(slot = 100, confirmations = null, err = null, confirmationStatus = "finalized")
        assertTrue(finalized.isConfirmed)
        assertTrue(finalized.isFinalized)
    }

    @Test
    fun testVersionInfoFromJson() {
        val json = buildJsonObject {
            put("solana-core", "1.14.10")
            put("feature-set", 3580551090)
        }
        val version = VersionInfo.fromJson(json)
        assertEquals("1.14.10", version.solanaCore)
        assertEquals(3580551090L, version.featureSet)
    }
}
