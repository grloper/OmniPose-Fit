package com.grloepr.pushtrack.engine

import com.google.mlkit.vision.pose.Pose

/**
 * A single tracked joint in image space with its detection confidence.
 *
 * The engine layer works exclusively on [PoseSnapshot]/[JointPoint] rather than
 * vendor SDK types, keeping every rule in this package pure Kotlin. This is the
 * seam for a future Kotlin Multiplatform `:shared` module — only the
 * [PoseSnapshot.fromMlKit] adapter below binds to Android's ML Kit.
 */
data class JointPoint(
    val x: Float,
    val y: Float,
    val confidence: Float
)

/** An immutable frame of detected joints, keyed by joint id (see [PoseJoints]). */
class PoseSnapshot(private val joints: Map<Int, JointPoint>) {

    operator fun get(jointId: Int): JointPoint? = joints[jointId]

    fun confidence(jointId: Int): Float = joints[jointId]?.confidence ?: 0f

    val isEmpty: Boolean get() = joints.isEmpty()

    companion object {
        val EMPTY = PoseSnapshot(emptyMap())

        /** Android binding: converts an ML Kit [Pose] into the engine's pure representation. */
        fun fromMlKit(pose: Pose): PoseSnapshot = PoseSnapshot(
            pose.allPoseLandmarks.associate { landmark ->
                landmark.landmarkType to JointPoint(
                    x = landmark.position.x,
                    y = landmark.position.y,
                    confidence = landmark.inFrameLikelihood
                )
            }
        )
    }
}
