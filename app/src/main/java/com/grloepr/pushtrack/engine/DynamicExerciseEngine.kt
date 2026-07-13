package com.grloepr.pushtrack.engine

/**
 * UI-facing phase of the schema state machine.
 *
 * SEARCHING → READY → ECCENTRIC → BOTTOM → CONCENTRIC → (rep++) → READY
 */
enum class EnginePhase(val label: String) {
    SEARCHING("Searching"),
    READY("Start"),
    ECCENTRIC("Descent"),
    BOTTOM("Bottom"),
    CONCENTRIC("Ascent")
}

/** One immutable frame of engine output, emitted after every analysed pose. */
data class EngineFrame(
    val phase: EnginePhase,
    val repCount: Int,
    /** 1 when this frame completed a rep, else 0 — lets the UI fire one-shot effects. */
    val repDelta: Int,
    /** Count of reps that turned back before reaching the inflection point. */
    val partialReps: Int,
    /** 1 when this frame registered a partial rep, else 0. */
    val partialRepDelta: Int,
    /**
     * 0 at the START posture → 1 at the inflection point (movement depth).
     * For hold schemas this becomes hold completion while the hold is live.
     */
    val progress: Float,
    /** Elapsed milliseconds of the current isometric hold; 0 when not holding. */
    val holdMs: Long,
    /** Smoothed value of the schema's primary tracked angle, in degrees. */
    val primaryAngle: Double?,
    val poseVisible: Boolean,
    val alignment: CameraAlignment,
    val lastRepDurationMs: Long?,
    val avgRepDurationMs: Long?,
    val timestampMs: Long
) {
    companion object {
        fun idle(timestampMs: Long = 0L) = EngineFrame(
            phase = EnginePhase.SEARCHING,
            repCount = 0,
            repDelta = 0,
            partialReps = 0,
            partialRepDelta = 0,
            progress = 0f,
            holdMs = 0L,
            primaryAngle = null,
            poseVisible = false,
            alignment = CameraAlignment.searching(),
            lastRepDurationMs = null,
            avgRepDurationMs = null,
            timestampMs = timestampMs
        )
    }
}

/**
 * Generic, schema-driven repetition state machine. Nothing here knows what a
 * "squat" is — the [ExerciseSchema] declares which joint angles matter and the
 * angle windows that define the START, INFLECTION_POINT and END states; the
 * engine walks poses through them and counts complete cycles.
 *
 * Hold schemas ([ExerciseSchema.isHold]) run a reduced machine instead:
 * SEARCHING → READY → BOTTOM (holding), and keeping the INFLECTION_POINT
 * posture for the schema's hold target counts as one rep.
 */
class DynamicExerciseEngine(
    val schema: ExerciseSchema,
    private val onFrame: (EngineFrame) -> Unit
) {
    private val angleSmoothers: Map<String, MeasurementSmoother> =
        schema.trackingAngles.mapValues { MeasurementSmoother(SMOOTHING_WINDOW) }
    private val alignmentMonitor = AlignmentMonitor(schema.optimalPlane)
    private val tempo = TempoTracker()

    private var phase = EnginePhase.SEARCHING
    private var repCount = 0
    private var partialReps = 0
    private var startHeldSince = -1L
    private var lastPhaseChangeAt = 0L
    private var repStartedAt = -1L
    private var holdStartedAt = -1L
    private var holdCounted = false

    fun reset() {
        angleSmoothers.values.forEach { it.clear() }
        alignmentMonitor.reset()
        tempo.reset()
        phase = EnginePhase.SEARCHING
        repCount = 0
        partialReps = 0
        startHeldSince = -1L
        lastPhaseChangeAt = 0L
        repStartedAt = -1L
        holdStartedAt = -1L
        holdCounted = false
        onFrame(EngineFrame.idle(System.currentTimeMillis()))
    }

    fun onPose(pose: PoseSnapshot, timestampMs: Long) {
        val alignment = alignmentMonitor.update(pose, timestampMs)

        if (!isPoseVisible(pose)) {
            angleSmoothers.values.forEach { it.clear() }
            if (phase != EnginePhase.SEARCHING) changePhase(EnginePhase.SEARCHING, timestampMs)
            startHeldSince = -1L
            repStartedAt = -1L
            holdStartedAt = -1L
            holdCounted = false
            emit(
                repDelta = 0, partialDelta = 0, progress = 0f, holdMs = 0L, primaryAngle = null,
                poseVisible = false, alignment = alignment, timestampMs = timestampMs
            )
            return
        }

        val angles: Map<String, Double?> = schema.trackingAngles.mapValues { (name, definition) ->
            angleSmoothers.getValue(name).add(
                calculateAngle(
                    pose[definition.startId],
                    pose[definition.vertexId],
                    pose[definition.endId]
                )
            )
        }
        val primaryAngle = angles[schema.primaryAngleName]

        val startSatisfied = stateSatisfied(SchemaStates.START, angles)
        val inflectionSatisfied = stateSatisfied(SchemaStates.INFLECTION, angles)
        // Schemas may omit END, in which case returning to START completes the cycle.
        val endSatisfied = if (schema.states.containsKey(SchemaStates.END)) {
            stateSatisfied(SchemaStates.END, angles)
        } else {
            startSatisfied
        }

        var repDelta = 0
        var partialDelta = 0

        if (schema.isHold) {
            val target = requireNotNull(schema.holdTargetMs)
            when (phase) {
                EnginePhase.SEARCHING -> {
                    if (startSatisfied) {
                        if (startHeldSince < 0) startHeldSince = timestampMs
                        if (timestampMs - startHeldSince >= START_HOLD_MS) {
                            changePhase(EnginePhase.READY, timestampMs)
                        }
                    } else {
                        startHeldSince = -1L
                    }
                }

                EnginePhase.READY -> {
                    if (inflectionSatisfied) {
                        holdStartedAt = timestampMs
                        holdCounted = false
                        changePhase(EnginePhase.BOTTOM, timestampMs)
                    }
                }

                EnginePhase.BOTTOM -> {
                    if (inflectionSatisfied) {
                        if (!holdCounted && timestampMs - holdStartedAt >= target) {
                            repCount += 1
                            repDelta = 1
                            holdCounted = true
                            tempo.onRepCompleted(timestampMs)
                        }
                    } else if (canChangePhase(timestampMs)) {
                        if (!holdCounted && timestampMs - holdStartedAt >= MIN_PARTIAL_HOLD_MS) {
                            // Broke the position after a real attempt — partial hold.
                            partialReps += 1
                            partialDelta = 1
                        }
                        holdStartedAt = -1L
                        changePhase(EnginePhase.READY, timestampMs)
                    }
                }

                // Rep-cycle phases never occur while a hold schema drives the engine.
                EnginePhase.ECCENTRIC, EnginePhase.CONCENTRIC ->
                    changePhase(EnginePhase.READY, timestampMs)
            }

            val holdMs = if (phase == EnginePhase.BOTTOM && holdStartedAt >= 0) {
                timestampMs - holdStartedAt
            } else {
                0L
            }
            emit(
                repDelta = repDelta,
                partialDelta = partialDelta,
                progress = (holdMs.toDouble() / target).toFloat().coerceIn(0f, 1f),
                holdMs = holdMs,
                primaryAngle = primaryAngle,
                poseVisible = true,
                alignment = alignment,
                timestampMs = timestampMs
            )
            return
        }

        val progress = schema.progressFor(primaryAngle)

        when (phase) {
            EnginePhase.SEARCHING -> {
                if (startSatisfied) {
                    if (startHeldSince < 0) startHeldSince = timestampMs
                    if (timestampMs - startHeldSince >= START_HOLD_MS) {
                        changePhase(EnginePhase.READY, timestampMs)
                    }
                } else {
                    startHeldSince = -1L
                }
            }

            EnginePhase.READY -> {
                if (inflectionSatisfied) {
                    // Explosive rep: skipped straight past the descent window.
                    repStartedAt = timestampMs
                    changePhase(EnginePhase.BOTTOM, timestampMs)
                } else if (!startSatisfied && progress > ECCENTRIC_ENTRY_PROGRESS) {
                    repStartedAt = timestampMs
                    changePhase(EnginePhase.ECCENTRIC, timestampMs)
                }
            }

            EnginePhase.ECCENTRIC -> {
                if (inflectionSatisfied) {
                    changePhase(EnginePhase.BOTTOM, timestampMs)
                } else if (endSatisfied && canChangePhase(timestampMs)) {
                    // Turned around before full depth — count it as a partial.
                    partialReps += 1
                    partialDelta = 1
                    repStartedAt = -1L
                    changePhase(EnginePhase.READY, timestampMs)
                }
            }

            EnginePhase.BOTTOM -> {
                if (!inflectionSatisfied && progress < ASCENT_EXIT_PROGRESS && canChangePhase(timestampMs)) {
                    changePhase(EnginePhase.CONCENTRIC, timestampMs)
                }
            }

            EnginePhase.CONCENTRIC -> {
                if (inflectionSatisfied) {
                    // Bounced back down before completing the ascent.
                    changePhase(EnginePhase.BOTTOM, timestampMs)
                } else if (endSatisfied) {
                    repCount += 1
                    repDelta = 1
                    tempo.onRepCompleted(timestampMs)
                    repStartedAt = -1L
                    changePhase(EnginePhase.READY, timestampMs)
                }
            }
        }

        emit(
            repDelta = repDelta,
            partialDelta = partialDelta,
            progress = progress,
            holdMs = 0L,
            primaryAngle = primaryAngle,
            poseVisible = true,
            alignment = alignment,
            timestampMs = timestampMs
        )
    }

    private fun stateSatisfied(stateName: String, angles: Map<String, Double?>): Boolean {
        val state = schema.states[stateName] ?: return false
        if (state.constraints.isEmpty()) return false
        return state.constraints.all { (angleName, constraint) ->
            angles[angleName]?.let(constraint::isSatisfied) ?: false
        }
    }

    private fun isPoseVisible(pose: PoseSnapshot): Boolean {
        if (pose.isEmpty) return false
        val required = schema.requiredJointsVisible.ifEmpty {
            schema.trackingAngles.values.flatMap { it.jointIds }.distinct()
        }
        val visible = required.count { pose.confidence(it) > VISIBILITY_CONFIDENCE }
        return visible >= required.size * VISIBILITY_QUORUM
    }

    private fun canChangePhase(timestampMs: Long): Boolean =
        timestampMs - lastPhaseChangeAt >= PHASE_DEBOUNCE_MS

    private fun changePhase(newPhase: EnginePhase, timestampMs: Long) {
        phase = newPhase
        lastPhaseChangeAt = timestampMs
        if (newPhase == EnginePhase.SEARCHING) startHeldSince = -1L
    }

    private fun emit(
        repDelta: Int,
        partialDelta: Int,
        progress: Float,
        holdMs: Long,
        primaryAngle: Double?,
        poseVisible: Boolean,
        alignment: CameraAlignment,
        timestampMs: Long
    ) {
        onFrame(
            EngineFrame(
                phase = phase,
                repCount = repCount,
                repDelta = repDelta,
                partialReps = partialReps,
                partialRepDelta = partialDelta,
                progress = progress,
                holdMs = holdMs,
                primaryAngle = primaryAngle,
                poseVisible = poseVisible,
                alignment = alignment,
                lastRepDurationMs = tempo.lastRepDurationMs,
                avgRepDurationMs = tempo.averageRepDurationMs,
                timestampMs = timestampMs
            )
        )
    }

    private companion object {
        const val SMOOTHING_WINDOW = 5
        const val START_HOLD_MS = 350L
        const val PHASE_DEBOUNCE_MS = 180L

        /** A broken hold shorter than this is ignored rather than scored as a partial. */
        const val MIN_PARTIAL_HOLD_MS = 1500L

        /** A joint counts as visible above this in-frame likelihood. */
        const val VISIBILITY_CONFIDENCE = 0.5f

        /** Fraction of required joints that must be visible to keep tracking. */
        const val VISIBILITY_QUORUM = 0.7f

        /** Movement depth needed before READY commits to a descent. */
        const val ECCENTRIC_ENTRY_PROGRESS = 0.12f

        /** Depth the athlete must rise back above before BOTTOM yields to ASCENT. */
        const val ASCENT_EXIT_PROGRESS = 0.92f
    }
}
