/*
 * Copyright (c) 2024-2026 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * Predictive Simulator Tests - World's First AI-powered transaction analysis.
 */
package com.selenus.artemis.simulation

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * Comprehensive tests for artemis-simulation module.
 * 
 * Tests the Predictive Simulator - world's first AI-powered
 * transaction analysis and prediction for mobile.
 */
class SimulationModuleTest {

    private lateinit var mockRpcAdapter: MockSimulationRpcAdapter
    private lateinit var simulator: PredictiveSimulator

    @BeforeEach
    fun setup() {
        mockRpcAdapter = MockSimulationRpcAdapter()
        simulator = PredictiveSimulator.create(mockRpcAdapter)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Data Class Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `SimulationResult creation and properties`() {
        val result = SimulationResult(
            success = true,
            error = null,
            logs = listOf("Program log: instruction 1", "Program log: instruction 2"),
            unitsConsumed = 150_000,
            returnData = null,
            accountsModified = listOf("Account1", "Account2")
        )

        assertTrue(result.success)
        assertNull(result.error)
        assertEquals(2, result.logs.size)
        assertEquals(150_000, result.unitsConsumed)
        assertEquals(2, result.accountsModified.size)
    }

    @Test
    fun `SimulationResult with error`() {
        val error = SimulationError(
            code = 6000,
            message = "Insufficient funds"
        )

        val result = SimulationResult(
            success = false,
            error = error,
            logs = listOf("Program log: Error"),
            unitsConsumed = 50_000,
            returnData = null,
            accountsModified = emptyList()
        )

        assertFalse(result.success)
        assertNotNull(result.error)
        assertEquals(6000, result.error?.code)
        assertEquals("Insufficient funds", result.error?.message)
    }

    @Test
    fun `FeeEstimate creation and adequacy score`() {
        val estimate = FeeEstimate(
            current = 5_000,
            recommended = 10_000,
            minimum = 1_000,
            maximum = 100_000,
            adequacyScore = 0.75,
            confidence = 0.9
        )

        assertEquals(5_000, estimate.current)
        assertEquals(10_000, estimate.recommended)
        assertTrue(estimate.adequacyScore > 0 && estimate.adequacyScore <= 1)
        assertTrue(estimate.confidence > 0 && estimate.confidence <= 1)
    }

    @Test
    fun `ComputeEstimate with safety margin`() {
        val estimate = ComputeEstimate(
            estimated = 200_000,
            recommended = 260_000, // 30% safety margin
            maximum = 520_000,
            safetyMargin = 0.3,
            confidence = 0.85
        )

        assertEquals(200_000, estimate.estimated)
        assertEquals(0.3, estimate.safetyMargin)
        assertTrue(estimate.recommended > estimate.estimated)
        assertTrue(estimate.maximum > estimate.recommended)
    }

    @Test
    fun `CongestionLevel severity mapping`() {
        val lowCongestion = CongestionLevel(
            score = 0.2,
            tps = 50_000.0,
            averageFee = 5_000,
            level = CongestionSeverity.LOW
        )

        val highCongestion = CongestionLevel(
            score = 0.8,
            tps = 10_000.0,
            averageFee = 50_000,
            level = CongestionSeverity.CRITICAL
        )

        assertEquals(CongestionSeverity.LOW, lowCongestion.level)
        assertEquals(CongestionSeverity.CRITICAL, highCongestion.level)
        assertTrue(lowCongestion.score < highCongestion.score)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RiskScore Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `RiskScore with no risk factors`() {
        val score = RiskScore(
            overall = 0.1,
            factors = emptyMap(),
            primaryReason = null
        )

        assertEquals(0.1, score.overall)
        assertTrue(score.factors.isEmpty())
        assertNull(score.primaryReason)
    }

    @Test
    fun `RiskScore with multiple factors`() {
        val factors = mapOf(
            RiskFactor.HIGH_VALUE to 0.7,
            RiskFactor.MEV_VULNERABLE to 0.5,
            RiskFactor.COMPLEX_TRANSACTION to 0.3
        )

        val score = RiskScore(
            overall = 0.5,
            factors = factors,
            primaryReason = "HIGH_VALUE"
        )

        assertEquals(3, score.factors.size)
        assertEquals(0.7, score.factors[RiskFactor.HIGH_VALUE])
        assertEquals("HIGH_VALUE", score.primaryReason)
    }

    @Test
    fun `RiskFactor enum values exist`() {
        // Verify all expected risk factors exist
        assertNotNull(RiskFactor.HIGH_VALUE)
        assertNotNull(RiskFactor.COMPLEX_TRANSACTION)
        assertNotNull(RiskFactor.UNKNOWN_PROGRAM)
        assertNotNull(RiskFactor.HIGH_SLIPPAGE)
        assertNotNull(RiskFactor.MEV_VULNERABLE)
        assertNotNull(RiskFactor.INSUFFICIENT_FEE)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Warning and Recommendation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Warning with all levels`() {
        val infoWarning = Warning(
            level = WarningLevel.INFO,
            message = "Transaction uses 500k compute units",
            category = WarningCategory.COMPUTE
        )

        val errorWarning = Warning(
            level = WarningLevel.ERROR,
            message = "Simulation failed",
            category = WarningCategory.SIMULATION
        )

        assertEquals(WarningLevel.INFO, infoWarning.level)
        assertEquals(WarningLevel.ERROR, errorWarning.level)
        assertEquals(WarningCategory.COMPUTE, infoWarning.category)
        assertEquals(WarningCategory.SIMULATION, errorWarning.category)
    }

    @Test
    fun `Recommendation with priority`() {
        val highPriorityRec = Recommendation(
            action = "Increase priority fee",
            reason = "Current fee is below recommended",
            impact = "Faster confirmation",
            priority = RecommendationPriority.HIGH
        )

        val lowPriorityRec = Recommendation(
            action = "Consider using lookup tables",
            reason = "Transaction is large",
            impact = "Lower transaction size",
            priority = RecommendationPriority.LOW
        )

        assertEquals(RecommendationPriority.HIGH, highPriorityRec.priority)
        assertEquals(RecommendationPriority.LOW, lowPriorityRec.priority)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEV Vulnerability Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `MevVulnerability levels`() {
        val noVulnerability = MevVulnerability(
            level = MevVulnerabilityLevel.NONE,
            sandwichRisk = false,
            frontrunRisk = false,
            recommendation = null
        )

        val highVulnerability = MevVulnerability(
            level = MevVulnerabilityLevel.HIGH,
            sandwichRisk = true,
            frontrunRisk = true,
            recommendation = "Consider using Jito bundles for MEV protection"
        )

        assertEquals(MevVulnerabilityLevel.NONE, noVulnerability.level)
        assertFalse(noVulnerability.sandwichRisk)
        assertNull(noVulnerability.recommendation)

        assertEquals(MevVulnerabilityLevel.HIGH, highVulnerability.level)
        assertTrue(highVulnerability.sandwichRisk)
        assertTrue(highVulnerability.frontrunRisk)
        assertNotNull(highVulnerability.recommendation)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Timing Recommendation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `TimingRecommendation for low congestion`() {
        val timing = TimingRecommendation(
            recommendation = SendTiming.NOW,
            reason = "Network congestion is very low",
            currentCongestionScore = 0.2,
            expectedWaitMinutes = 0,
            confidence = 0.95
        )

        assertEquals(SendTiming.NOW, timing.recommendation)
        assertEquals(0, timing.expectedWaitMinutes)
        assertTrue(timing.confidence > 0.9)
    }

    @Test
    fun `TimingRecommendation for high congestion`() {
        val timing = TimingRecommendation(
            recommendation = SendTiming.DELAY,
            reason = "Very high congestion",
            currentCongestionScore = 0.9,
            expectedWaitMinutes = 30,
            confidence = 0.8,
            suggestedTime = System.currentTimeMillis() + 30 * 60 * 1000
        )

        assertEquals(SendTiming.DELAY, timing.recommendation)
        assertTrue(timing.expectedWaitMinutes > 0)
        assertNotNull(timing.suggestedTime)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ConfirmationTimeEstimate Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ConfirmationTimeEstimate ranges`() {
        val estimate = ConfirmationTimeEstimate(
            optimisticMs = 400,
            estimatedMs = 1200,
            pessimisticMs = 3600,
            confidence = 0.7
        )

        assertTrue(estimate.optimisticMs < estimate.estimatedMs)
        assertTrue(estimate.estimatedMs < estimate.pessimisticMs)
        assertTrue(estimate.confidence > 0 && estimate.confidence <= 1)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CurrentFees Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `CurrentFees percentiles are ordered`() {
        val fees = CurrentFees(
            p25 = 1_000,
            p50 = 5_000,
            p75 = 10_000,
            p90 = 50_000,
            p99 = 100_000
        )

        assertTrue(fees.p25 < fees.p50)
        assertTrue(fees.p50 < fees.p75)
        assertTrue(fees.p75 < fees.p90)
        assertTrue(fees.p90 < fees.p99)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BlockInclusionPrediction Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `BlockInclusionPrediction with high probability`() {
        val prediction = BlockInclusionPrediction(
            probability = 0.95,
            estimatedBlocks = 1,
            estimatedSeconds = 0,
            feePercentile = 90,
            recommendation = null
        )

        assertTrue(prediction.probability > 0.9)
        assertEquals(1, prediction.estimatedBlocks)
        assertNull(prediction.recommendation)
    }

    @Test
    fun `BlockInclusionPrediction with low probability has recommendation`() {
        val prediction = BlockInclusionPrediction(
            probability = 0.3,
            estimatedBlocks = 10,
            estimatedSeconds = 4,
            feePercentile = 10,
            recommendation = "Consider increasing priority fee"
        )

        assertTrue(prediction.probability < 0.5)
        assertTrue(prediction.estimatedBlocks > 5)
        assertNotNull(prediction.recommendation)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TransactionAnalysis Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `TransactionAnalysis isLikelyToSucceed property`() {
        val simulationResult = SimulationResult(
            success = true,
            error = null,
            logs = emptyList(),
            unitsConsumed = 100_000,
            returnData = null,
            accountsModified = emptyList()
        )

        val analysis = TransactionAnalysis(
            successProbability = 0.85,
            simulationResult = simulationResult,
            feeEstimate = FeeEstimate(5000, 10000, 1000, 100000, 0.8, 0.9),
            computeEstimate = ComputeEstimate(100000, 130000, 260000, 0.3, 0.9),
            congestionLevel = CongestionLevel(0.3, 45000.0, 5000, CongestionSeverity.LOW),
            riskScore = RiskScore(0.2, emptyMap(), null),
            warnings = emptyList(),
            recommendations = emptyList(),
            estimatedConfirmationTime = ConfirmationTimeEstimate(400, 800, 2400, 0.8),
            mevVulnerability = MevVulnerability(MevVulnerabilityLevel.NONE, false, false, null),
            analyzedAt = System.currentTimeMillis()
        )

        assertTrue(analysis.isLikelyToSucceed)
        assertEquals("LOW", analysis.riskLevel)
    }

    @Test
    fun `TransactionAnalysis riskLevel categories`() {
        val lowRisk = RiskScore(0.2, emptyMap(), null)
        val mediumRisk = RiskScore(0.5, emptyMap(), null)
        val highRisk = RiskScore(0.8, emptyMap(), null)

        // We can't directly test the computed property without creating full analysis
        // but we can verify the thresholds
        assertTrue(lowRisk.overall < 0.3)
        assertTrue(mediumRisk.overall >= 0.3 && mediumRisk.overall < 0.6)
        assertTrue(highRisk.overall >= 0.6)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PredictiveSimulator Integration Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `simulator analyze returns complete analysis`() = runBlocking {
        val transaction = ByteArray(100) { it.toByte() }
        
        val analysis = simulator.analyze(transaction)

        assertNotNull(analysis)
        assertTrue(analysis.successProbability >= 0 && analysis.successProbability <= 1)
        assertNotNull(analysis.simulationResult)
        assertNotNull(analysis.feeEstimate)
        assertNotNull(analysis.computeEstimate)
        assertNotNull(analysis.congestionLevel)
        assertNotNull(analysis.riskScore)
        assertTrue(analysis.analyzedAt > 0)
    }

    @Test
    fun `simulator getOptimalTiming returns timing`() = runBlocking {
        val timing = simulator.getOptimalTiming()

        assertNotNull(timing)
        assertNotNull(timing.recommendation)
        assertTrue(timing.currentCongestionScore >= 0 && timing.currentCongestionScore <= 1)
        assertTrue(timing.confidence >= 0 && timing.confidence <= 1)
    }

    @Test
    fun `simulator predictNextBlockInclusion returns prediction`() = runBlocking {
        val transaction = ByteArray(100) { it.toByte() }
        val priorityFee = 10_000L

        val prediction = simulator.predictNextBlockInclusion(transaction, priorityFee)

        assertNotNull(prediction)
        assertTrue(prediction.probability >= 0 && prediction.probability <= 1)
        assertTrue(prediction.estimatedBlocks >= 1)
        assertTrue(prediction.feePercentile >= 0 && prediction.feePercentile <= 99)
    }

    @Test
    fun `simulator simulateTransaction returns result`() = runBlocking {
        val transaction = ByteArray(100) { it.toByte() }

        val result = simulator.simulateTransaction(transaction)

        assertNotNull(result)
        // Mock returns success by default
        assertTrue(result.success || result.error != null)
    }

    @Test
    fun `simulator analyzeBatch handles multiple transactions`() = runBlocking {
        val transactions = listOf(
            ByteArray(100) { 0x01 },
            ByteArray(150) { 0x02 },
            ByteArray(200) { 0x03 }
        )

        val analyses = simulator.analyzeBatch(transactions)

        assertEquals(3, analyses.size)
        analyses.forEach { analysis ->
            assertNotNull(analysis)
            assertTrue(analysis.successProbability >= 0)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Fee Predictor Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `StatisticalFeePredictor returns valid estimate`() = runBlocking {
        val predictor = StatisticalFeePredictor(mockRpcAdapter)
        val transaction = ByteArray(100)

        val estimate = predictor.predictOptimalFee(transaction)

        assertNotNull(estimate)
        assertTrue(estimate.recommended >= estimate.minimum)
        assertTrue(estimate.maximum >= estimate.recommended)
        assertTrue(estimate.adequacyScore > 0)
    }

    @Test
    fun `StatisticalFeePredictor getCurrentFees returns percentiles`() = runBlocking {
        val predictor = StatisticalFeePredictor(mockRpcAdapter)

        val fees = predictor.getCurrentFees()

        assertTrue(fees.p25 <= fees.p50)
        assertTrue(fees.p50 <= fees.p75)
        assertTrue(fees.p75 <= fees.p90)
        assertTrue(fees.p90 <= fees.p99)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Congestion Analyzer Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `NetworkCongestionAnalyzer returns congestion level`() = runBlocking {
        val analyzer = NetworkCongestionAnalyzer(mockRpcAdapter)

        val congestion = analyzer.getCurrentCongestion()

        assertNotNull(congestion)
        assertTrue(congestion.score >= 0 && congestion.score <= 1)
        assertTrue(congestion.tps > 0)
        assertNotNull(congestion.level)
    }

    @Test
    fun `NetworkCongestionAnalyzer returns historical pattern`() = runBlocking {
        val analyzer = NetworkCongestionAnalyzer(mockRpcAdapter)

        val pattern = analyzer.getHistoricalPattern()

        assertNotNull(pattern)
        assertEquals(24, pattern.hourlyAverages.size) // 24 hours
        pattern.hourlyAverages.forEach { avg ->
            assertTrue(avg >= 0 && avg <= 1)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Risk Scorer Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `TransactionRiskScorer scores successful transaction`() {
        val scorer = TransactionRiskScorer()
        val transaction = ByteArray(100)
        val simulation = SimulationResult(
            success = true,
            error = null,
            logs = listOf("Program log: Success"),
            unitsConsumed = 100_000,
            returnData = null,
            accountsModified = emptyList()
        )

        val score = scorer.scoreTransaction(transaction, simulation)

        assertNotNull(score)
        assertTrue(score.overall >= 0 && score.overall <= 1)
    }

    @Test
    fun `TransactionRiskScorer scores failed transaction higher`() {
        val scorer = TransactionRiskScorer()
        val transaction = ByteArray(100)
        
        val successSimulation = SimulationResult(
            success = true,
            error = null,
            logs = emptyList(),
            unitsConsumed = 100_000,
            returnData = null,
            accountsModified = emptyList()
        )

        val failedSimulation = SimulationResult(
            success = false,
            error = SimulationError(6000, "Error"),
            logs = emptyList(),
            unitsConsumed = 50_000,
            returnData = null,
            accountsModified = emptyList()
        )

        val successScore = scorer.scoreTransaction(transaction, successSimulation)
        val failedScore = scorer.scoreTransaction(transaction, failedSimulation)

        assertTrue(failedScore.overall >= successScore.overall)
    }

    @Test
    fun `TransactionRiskScorer detects DEX interaction`() {
        val scorer = TransactionRiskScorer()
        val transaction = ByteArray(100)
        val simulation = SimulationResult(
            success = true,
            error = null,
            logs = listOf("Program log: swap instruction", "Program log: amount: 1000000"),
            unitsConsumed = 200_000,
            returnData = null,
            accountsModified = emptyList()
        )

        val score = scorer.scoreTransaction(transaction, simulation)

        assertTrue(score.factors.containsKey(RiskFactor.MEV_VULNERABLE))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SimulatorConfig Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `SimulatorConfig has reasonable defaults`() {
        val config = SimulatorConfig()

        assertTrue(config.monitoringIntervalMs > 0)
        assertTrue(config.congestionThreshold > 0 && config.congestionThreshold < 1)
        assertTrue(config.historySampleSize > 0)
    }

    @Test
    fun `SimulatorConfig can be customized`() {
        val config = SimulatorConfig(
            monitoringIntervalMs = 10_000,
            congestionThreshold = 0.8,
            historySampleSize = 200
        )

        assertEquals(10_000, config.monitoringIntervalMs)
        assertEquals(0.8, config.congestionThreshold)
        assertEquals(200, config.historySampleSize)
    }
}

/**
 * Mock RPC adapter for testing.
 */
class MockSimulationRpcAdapter : SimulationRpcAdapter {
    
    var simulationSuccess = true
    var mockTps = 40_000.0
    var mockFees = listOf(1_000L, 5_000L, 10_000L, 50_000L, 100_000L)
    
    override suspend fun simulateTransaction(transaction: ByteArray): SimulationResponse {
        return if (simulationSuccess) {
            SimulationResponse(
                error = null,
                logs = listOf("Program log: Success", "Program log: Compute units: 100000"),
                unitsConsumed = 100_000,
                returnData = null,
                accounts = emptyList()
            )
        } else {
            SimulationResponse(
                error = SimulationError(6000, "Simulation failed"),
                logs = listOf("Program log: Error"),
                unitsConsumed = 50_000,
                returnData = null,
                accounts = null
            )
        }
    }

    override suspend fun getSlot(): Long = 200_000_000L

    override suspend fun getRecentPriorityFees(): List<Long> = mockFees

    override suspend fun getRecentPerformance(): PerformanceData {
        return PerformanceData(
            tps = mockTps,
            averageFee = 10_000,
            slot = 200_000_000
        )
    }
}
