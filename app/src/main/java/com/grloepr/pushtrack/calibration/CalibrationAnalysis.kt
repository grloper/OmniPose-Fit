package com.grloepr.pushtrack.calibration

import android.content.Context
import android.util.Log
import com.grloepr.pushtrack.analysis.ExerciseDetector
import com.grloepr.pushtrack.data.*
import com.grloepr.pushtrack.utils.TerminalLogger
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import kotlin.math.*

/**
 * Analysis result from calibration data
 */
data class CalibrationAnalysis(
    val upThreshold: Double?,
    val downThreshold: Double?,
    val transitionBuffer: Double,
    val transitionSpeed: Double?,
    val averageRepDuration: Double?,
    val standardDeviation: Double?,
    val confidenceScore: Float?,
    val falsePositiveRate: Float?,
    val falseNegativeRate: Float?,
    val minRepDuration: Long,
    val maxRepDuration: Long,
    val speedSensitivity: Float,
    val qualityMetrics: QualityMetrics
)

/**
 * Quality metrics for calibration analysis
 */
data class QualityMetrics(
    val averageConfidence: Float,
    val landmarkStability: Float,
    val motionConsistency: Float,
    val repVariability: Float,
    val dataQualityScore: Float
)

/**
 * Extension functions for CalibrationManager
 */

/**
 * Calculate optimal thresholds from collected data
 */
fun CalibrationManager.calculateOptimalThresholds(
    startPositionData: List<PoseDataEntity>,
    endPositionData: List<PoseDataEntity>
): CalibrationAnalysis {
    
    try {
        TerminalLogger.c("CalibrationAnalysis", "🧮 Calculating optimal thresholds...")
        TerminalLogger.table("CalibrationAnalysis", "Input Data", mapOf(
            "Start position data" to "${startPositionData.size} points",
            "End position data" to "${endPositionData.size} points"
        ))
        
        // Extract metrics
        val startMetrics = startPositionData.mapNotNull { it.primaryMetric }
        val endMetrics = endPositionData.mapNotNull { it.primaryMetric }
        
        if (startMetrics.isEmpty() || endMetrics.isEmpty()) {
            TerminalLogger.w("CalibrationAnalysis", "⚠️ Insufficient data for analysis")
            return createDefaultAnalysis()
        }
        
        // Statistical analysis
        val startStats = calculateStatistics(startMetrics)
        val endStats = calculateStatistics(endMetrics)
        
        TerminalLogger.table("Analysis", "Summary Statistics", mapOf(
            "Start position mean" to startStats.mean,
            "Start position SD" to startStats.standardDeviation,
            "End position mean" to endStats.mean,
            "End position SD" to endStats.standardDeviation
        ))
        
        // Calculate optimal thresholds with safety margins
        val upThreshold = calculateOptimalUpThreshold(startStats)
        val downThreshold = calculateOptimalDownThreshold(endStats)
        val transitionBuffer = calculateTransitionBuffer(startStats, endStats)
        
        // Calculate timing metrics
        val repDurations = calculateRepDurations(startPositionData, endPositionData)
        val averageRepDuration = repDurations.average()
        val repStandardDeviation = calculateStandardDeviation(repDurations)
        
        // Calculate speed sensitivity
        val speedSensitivity = calculateSpeedSensitivity(startPositionData, endPositionData)
        
        // Quality assessment
        val qualityMetrics = calculateQualityMetrics(startPositionData + endPositionData)
        
        // Performance metrics
        val (falsePositiveRate, falseNegativeRate) = calculateErrorRates(
            startPositionData, endPositionData, upThreshold, downThreshold
        )
        
        // Overall confidence score
        val confidenceScore = calculateOverallConfidence(
            qualityMetrics, falsePositiveRate, falseNegativeRate, startStats, endStats
        )
        
        TerminalLogger.table("Analysis", "Optimal Thresholds", mapOf(
            "Up threshold" to upThreshold,
            "Down threshold" to downThreshold,
            "Transition buffer" to transitionBuffer
        ))
        
        return CalibrationAnalysis(
            upThreshold = upThreshold,
            downThreshold = downThreshold,
            transitionBuffer = transitionBuffer,
            transitionSpeed = speedSensitivity.toDouble(),
            averageRepDuration = averageRepDuration,
            standardDeviation = repStandardDeviation,
            confidenceScore = confidenceScore,
            falsePositiveRate = falsePositiveRate,
            falseNegativeRate = falseNegativeRate,
            minRepDuration = (averageRepDuration - 2 * repStandardDeviation).toLong().coerceAtLeast(500),
            maxRepDuration = (averageRepDuration + 3 * repStandardDeviation).toLong().coerceAtMost(30000),
            speedSensitivity = speedSensitivity,
            qualityMetrics = qualityMetrics
        )
        
    } catch (e: Exception) {
        TerminalLogger.e("CalibrationAnalysis", "❌ Error calculating thresholds: ${e.message}")
        return createDefaultAnalysis()
    }
}

/**
 * Calculate statistics for a list of values
 */
private data class Statistics(
    val mean: Double,
    val median: Double,
    val standardDeviation: Double,
    val min: Double,
    val max: Double,
    val percentile25: Double,
    val percentile75: Double
)

private fun calculateStatistics(values: List<Double>): Statistics {
    val sorted = values.sorted()
    val mean = values.average()
    val variance = values.map { (it - mean).pow(2) }.average()
    val standardDeviation = sqrt(variance)
    
    return Statistics(
        mean = mean,
        median = sorted[sorted.size / 2],
        standardDeviation = standardDeviation,
        min = sorted.first(),
        max = sorted.last(),
        percentile25 = sorted[(sorted.size * 0.25).toInt()],
        percentile75 = sorted[(sorted.size * 0.75).toInt()]
    )
}

/**
 * Calculate optimal up threshold (more conservative)
 */
private fun calculateOptimalUpThreshold(startStats: Statistics): Double {
    // Use percentile-based approach for robustness
    // Take 25th percentile minus 1 standard deviation for safety
    return (startStats.percentile25 - startStats.standardDeviation).coerceAtLeast(startStats.min)
}

/**
 * Calculate optimal down threshold (more conservative)
 */
private fun calculateOptimalDownThreshold(endStats: Statistics): Double {
    // Use 75th percentile plus 1 standard deviation for safety
    return (endStats.percentile75 + endStats.standardDeviation).coerceAtMost(endStats.max)
}

/**
 * Calculate transition buffer to prevent jitter
 */
private fun calculateTransitionBuffer(startStats: Statistics, endStats: Statistics): Double {
    val combinedStd = (startStats.standardDeviation + endStats.standardDeviation) / 2
    return combinedStd.coerceIn(5.0, 20.0) // Reasonable bounds
}

/**
 * Calculate rep durations from position transitions
 */
private fun calculateRepDurations(
    startData: List<PoseDataEntity>,
    endData: List<PoseDataEntity>
): List<Double> {
    val durations = mutableListOf<Double>()
    
    // Group by rep number and calculate duration for each rep
    val repGroups = (startData + endData).groupBy { it.repNumberInSession }
    
    repGroups.forEach { (repNumber, repData) ->
        if (repNumber != null && repData.size >= 2) {
            val sortedData = repData.sortedBy { it.timestamp }
            val startTime = sortedData.first().timestamp
            val endTime = sortedData.last().timestamp
            durations.add((endTime - startTime).toDouble())
        }
    }
    
    return durations.ifEmpty { listOf(3000.0) } // Default 3 seconds
}

/**
 * Calculate standard deviation
 */
private fun calculateStandardDeviation(values: List<Double>): Double {
    if (values.isEmpty()) return 1000.0
    val mean = values.average()
    val variance = values.map { (it - mean).pow(2) }.average()
    return sqrt(variance)
}

/**
 * Calculate speed sensitivity based on movement patterns
 */
private fun calculateSpeedSensitivity(
    startData: List<PoseDataEntity>,
    endData: List<PoseDataEntity>
): Float {
    // Analyze the rate of change in primary metric
    val allData = (startData + endData).sortedBy { it.timestamp }
    
    if (allData.size < 2) return 1.0f
    
    val speedChanges = mutableListOf<Double>()
    
    for (i in 1 until allData.size) {
        val current = allData[i]
        val previous = allData[i - 1]
        
        if (current.primaryMetric != null && previous.primaryMetric != null) {
            val timeDiff = (current.timestamp - previous.timestamp).toDouble()
            val metricDiff = abs(current.primaryMetric!! - previous.primaryMetric!!)
            
            if (timeDiff > 0) {
                speedChanges.add(metricDiff / timeDiff)
            }
        }
    }
    
    // Calculate sensitivity based on movement variability
    return if (speedChanges.isNotEmpty()) {
        val avgSpeed = speedChanges.average()
        val speedStd = calculateStandardDeviation(speedChanges)
        
        // Higher variability = lower sensitivity needed
        (1.0 - (speedStd / avgSpeed).coerceIn(0.0, 0.5)).toFloat()
    } else {
        1.0f
    }
}

/**
 * Calculate quality metrics for the collected data
 */
private fun calculateQualityMetrics(allData: List<PoseDataEntity>): QualityMetrics {
    val validData = allData.filter { it.isValidPose }
    
    if (validData.isEmpty()) {
        return QualityMetrics(0f, 0f, 0f, 0f, 0f)
    }
    
    // Average confidence
    val averageConfidence = validData.map { it.detectionConfidence }.average().toFloat()
    
    // Landmark stability (consistency of landmark positions)
    val landmarkStability = calculateLandmarkStability(validData)
    
    // Motion consistency (smoothness of primary metric changes)
    val motionConsistency = calculateMotionConsistency(validData)
    
    // Rep variability (consistency across different reps)
    val repVariability = calculateRepVariability(validData)
    
    // Overall data quality score
    val dataQualityScore = (averageConfidence + landmarkStability + motionConsistency + (1f - repVariability)) / 4f
    
    return QualityMetrics(
        averageConfidence = averageConfidence,
        landmarkStability = landmarkStability,
        motionConsistency = motionConsistency,
        repVariability = repVariability,
        dataQualityScore = dataQualityScore
    )
}

/**
 * Calculate landmark stability
 */
private fun calculateLandmarkStability(data: List<PoseDataEntity>): Float {
    // Analyze position variance of key landmarks
    val leftShoulderPositions = data.mapNotNull { 
        if (it.leftShoulderX != null && it.leftShoulderY != null) 
            Pair(it.leftShoulderX!!, it.leftShoulderY!!) 
        else null 
    }
    
    if (leftShoulderPositions.size < 2) return 0f
    
    val xVariance = calculateStandardDeviation(leftShoulderPositions.map { it.first.toDouble() })
    val yVariance = calculateStandardDeviation(leftShoulderPositions.map { it.second.toDouble() })
    
    // Lower variance = higher stability
    val maxExpectedVariance = 50.0 // pixels
    val normalizedVariance = ((xVariance + yVariance) / 2) / maxExpectedVariance
    
    return (1.0 - normalizedVariance.coerceIn(0.0, 1.0)).toFloat()
}

/**
 * Calculate motion consistency
 */
private fun calculateMotionConsistency(data: List<PoseDataEntity>): Float {
    val metrics = data.mapNotNull { it.primaryMetric }.sorted()
    
    if (metrics.size < 3) return 0f
    
    // Calculate smoothness by analyzing second derivatives
    val firstDerivatives = mutableListOf<Double>()
    for (i in 1 until metrics.size) {
        firstDerivatives.add(metrics[i] - metrics[i - 1])
    }
    
    val secondDerivatives = mutableListOf<Double>()
    for (i in 1 until firstDerivatives.size) {
        secondDerivatives.add(abs(firstDerivatives[i] - firstDerivatives[i - 1]))
    }
    
    val averageJerk = secondDerivatives.average()
    val maxExpectedJerk = 10.0 // degrees per frame^2
    
    return (1.0 - (averageJerk / maxExpectedJerk).coerceIn(0.0, 1.0)).toFloat()
}

/**
 * Calculate rep variability
 */
private fun calculateRepVariability(data: List<PoseDataEntity>): Float {
    val repGroups = data.groupBy { it.repNumberInSession }.values.filter { it.size > 5 }
    
    if (repGroups.size < 2) return 0f
    
    val repMetrics = repGroups.map { rep ->
        rep.mapNotNull { it.primaryMetric }.average()
    }
    
    val repStandardDeviation = calculateStandardDeviation(repMetrics)
    val repMean = repMetrics.average()
    
    // Coefficient of variation as variability measure
    return if (repMean > 0) {
        (repStandardDeviation / repMean).toFloat().coerceIn(0f, 1f)
    } else {
        1f
    }
}

/**
 * Calculate error rates for threshold validation
 */
private fun calculateErrorRates(
    startData: List<PoseDataEntity>,
    endData: List<PoseDataEntity>,
    upThreshold: Double,
    downThreshold: Double
): Pair<Float, Float> {
    
    // False positives: end position data classified as start position
    val falsePositives = endData.count { it.primaryMetric != null && it.primaryMetric!! > upThreshold }
    val falsePositiveRate = if (endData.isNotEmpty()) falsePositives.toFloat() / endData.size else 0f
    
    // False negatives: start position data classified as end position
    val falseNegatives = startData.count { it.primaryMetric != null && it.primaryMetric!! < downThreshold }
    val falseNegativeRate = if (startData.isNotEmpty()) falseNegatives.toFloat() / startData.size else 0f
    
    return Pair(falsePositiveRate, falseNegativeRate)
}

/**
 * Calculate overall confidence score
 */
private fun calculateOverallConfidence(
    qualityMetrics: QualityMetrics,
    falsePositiveRate: Float,
    falseNegativeRate: Float,
    startStats: Statistics,
    endStats: Statistics
): Float {
    
    // Data quality component (40%)
    val qualityScore = qualityMetrics.dataQualityScore * 0.4f
    
    // Error rate component (30%)
    val errorScore = (1f - (falsePositiveRate + falseNegativeRate) / 2f) * 0.3f
    
    // Separation component (20%) - how well separated are the positions
    val separation = abs(startStats.mean - endStats.mean)
    val combinedStd = (startStats.standardDeviation + endStats.standardDeviation) / 2
    val separationScore = if (combinedStd > 0) {
        ((separation / combinedStd) / 4.0).coerceIn(0.0, 1.0).toFloat() * 0.2f
    } else 0f
    
    // Sample size component (10%)
    val sampleSizeScore = ((startStats.min * endStats.min) / 100.0).coerceIn(0.0, 1.0).toFloat() * 0.1f
    
    return (qualityScore + errorScore + separationScore + sampleSizeScore).coerceIn(0f, 1f)
}

/**
 * Create default analysis when data is insufficient
 */
private fun createDefaultAnalysis(): CalibrationAnalysis {
    return CalibrationAnalysis(
        upThreshold = null,
        downThreshold = null,
        transitionBuffer = 10.0,
        transitionSpeed = null,
        averageRepDuration = null,
        standardDeviation = null,
        confidenceScore = 0f,
        falsePositiveRate = 0f,
        falseNegativeRate = 0f,
        minRepDuration = 1000L,
        maxRepDuration = 10000L,
        speedSensitivity = 1.0f,
        qualityMetrics = QualityMetrics(0f, 0f, 0f, 0f, 0f)
    )
}

/**
 * Apply calculated thresholds to detector
 */
fun CalibrationManager.applyThresholds(thresholds: ExerciseThresholds) {
    // This would need to be implemented in each detector class
    // For now, we'll log the application
    TerminalLogger.c("CalibrationManager", "🔧 Applying thresholds: ${thresholds.exerciseType}")
    TerminalLogger.table("CalibrationManager", "Applied Thresholds", mapOf(
        "Up threshold" to thresholds.upThreshold,
        "Down threshold" to thresholds.downThreshold,
        "Transition buffer" to thresholds.transitionBuffer,
        "Min rep duration" to "${thresholds.minRepDuration}ms",
        "Max rep duration" to "${thresholds.maxRepDuration}ms"
    ))
    
    // TODO: Add method to ExerciseDetector to update thresholds
    // detector.updateThresholds(thresholds.upThreshold, thresholds.downThreshold)
}

/**
 * Export calibration data for external analysis
 */
fun CalibrationManager.exportCalibrationData(
    sessionId: String,
    analysis: CalibrationAnalysis
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Get all data for session
            val sessionData = poseDataDao.getPoseDataForSession(sessionId)
            val session = sessionDao.getSession(sessionId)
            
            // Create export directory
            val exportDir = File(context.getExternalFilesDir(null), "calibration_exports")
            exportDir.mkdirs()
            
            // Export as JSON
            val jsonFile = File(exportDir, "calibration_$sessionId.json")
            val exportData = mapOf(
                "session" to session,
                "data" to sessionData,
                "analysis" to analysis,
                "exportTime" to System.currentTimeMillis()
            )
            
            jsonFile.writeText(Json.encodeToString(exportData))
            
            // Export as CSV for easy analysis
            val csvFile = File(exportDir, "calibration_${sessionId}_data.csv")
            exportToCsv(sessionData, csvFile)
            
            TerminalLogger.s("CalibrationManager", "📄 Data exported to: ${exportDir.absolutePath}")
            TerminalLogger.i("CalibrationManager", "📁 Files created:")
            TerminalLogger.i("CalibrationManager", "   • ${jsonFile.name} (JSON format)")
            TerminalLogger.i("CalibrationManager", "   • ${csvFile.name} (CSV format)")
            
        } catch (e: Exception) {
            TerminalLogger.e("CalibrationManager", "❌ Error exporting calibration data: ${e.message}")
        }
    }
}

/**
 * Export pose data to CSV format (manual implementation)
 */
private fun exportToCsv(data: List<PoseDataEntity>, file: File) {
    file.writeText(buildString {
        // Header
        appendLine("timestamp,frameNumber,primaryMetric,exerciseState,repCount," +
                "leftShoulderX,leftShoulderY,rightShoulderX,rightShoulderY," +
                "leftElbowX,leftElbowY,rightElbowX,rightElbowY," +
                "leftWristX,leftWristY,rightWristX,rightWristY," +
                "leftArmAngle,rightArmAngle,detectionConfidence,isValidPose")
        
        // Data rows
        data.forEach { pose ->
            appendLine("${pose.timestamp},${pose.frameNumber},${pose.primaryMetric},${pose.exerciseState},${pose.repCount}," +
                    "${pose.leftShoulderX},${pose.leftShoulderY},${pose.rightShoulderX},${pose.rightShoulderY}," +
                    "${pose.leftElbowX},${pose.leftElbowY},${pose.rightElbowX},${pose.rightElbowY}," +
                    "${pose.leftWristX},${pose.leftWristY},${pose.rightWristX},${pose.rightWristY}," +
                    "${pose.leftArmAngle},${pose.rightArmAngle},${pose.detectionConfidence},${pose.isValidPose}")
        }
    })
}
