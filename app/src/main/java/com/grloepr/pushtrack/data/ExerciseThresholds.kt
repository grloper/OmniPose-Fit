package com.grloepr.pushtrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Data class for storing exercise-specific thresholds
 */
@Entity(tableName = "exercise_thresholds")
data class ExerciseThresholds(
    @PrimaryKey
    val exerciseType: String,
    val upThreshold: Double?,
    val downThreshold: Double?,
    val transitionBuffer: Double,
    val minRepDuration: Long,
    val maxRepDuration: Long,
    val speedSensitivity: Float,
    val minConfidence: Float,
    val minLandmarkVisibility: Float,
    val calibrationSessionId: String?,
    val createdAt: Long,
    val lastUpdated: Long,
    val appliedAt: Long = lastUpdated, // default added
    val accuracyScore: Float?,
    val falsePositiveRate: Float?,
    val falseNegativeRate: Float?
)