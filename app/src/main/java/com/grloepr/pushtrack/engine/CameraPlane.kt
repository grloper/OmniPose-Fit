package com.grloepr.pushtrack.engine

/**
 * The anatomical plane the camera should capture for reliable tracking of a movement.
 *
 * SAGITTAL — side profile (squats, push-ups: depth is measured along the body's side).
 * FRONTAL  — facing the camera (pull-ups, jumping jacks: symmetry is measured across the body).
 * ANY      — plane-agnostic movements.
 */
enum class CameraPlane {
    SAGITTAL,
    FRONTAL,
    ANY;

    companion object {
        fun fromId(raw: String?): CameraPlane = when (raw?.trim()?.uppercase()) {
            "SAGITTAL" -> SAGITTAL
            "FRONTAL", "CORONAL" -> FRONTAL
            else -> ANY
        }
    }
}
