package com.grloepr.pushtrack.engine

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Parses exercise definition JSON (the files under `assets/exercises`) into [ExerciseSchema].
 *
 * Expected shape:
 * ```json
 * {
 *   "exercise_id": "squat",
 *   "display_name": "Deep Squat",
 *   "target_muscles": ["quadriceps", "gluteus_maximus"],
 *   "tracking_joints": { "primary_angle": ["LEFT_HIP", "LEFT_KNEE", "LEFT_ANKLE"] },
 *   "states": {
 *     "START": { "primary_angle": { "min": 160, "max": 180 } },
 *     "INFLECTION_POINT": { "primary_angle": { "less_than": 90 } },
 *     "END": { "primary_angle": { "min": 160, "max": 180 } }
 *   },
 *   "validation": {
 *     "optimal_camera_plane": "SAGITTAL",
 *     "required_joints_visible": ["LEFT_HIP", "LEFT_KNEE"]
 *   },
 *   "tempo": { "pulse_interval_ms": 3000 },
 *   "mastery_reps": 10
 * }
 * ```
 *
 * Isometric skills add a `hold` block; the INFLECTION_POINT state then describes
 * the hold posture, and keeping it for `target_ms` counts as one rep:
 * ```json
 * "hold": { "target_ms": 20000 }
 * ```
 */
object SchemaParser {

    fun parse(json: JSONObject): ExerciseSchema {
        val id = json.getString("exercise_id")
        val displayName = json.optString("display_name", id)

        val muscles = buildList {
            val arr = json.optJSONArray("target_muscles")
            if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i))
        }

        val trackingJoints = json.getJSONObject("tracking_joints")
        val trackingAngles = buildMap {
            trackingJoints.keys().forEach { angleName ->
                val triple = trackingJoints.getJSONArray(angleName)
                require(triple.length() == 3) {
                    "tracking_joints.$angleName must list exactly 3 joints"
                }
                val names = List(3) { triple.getString(it) }
                put(
                    angleName,
                    JointAngleDefinition(
                        name = angleName,
                        startId = PoseJoints.require(names[0]),
                        vertexId = PoseJoints.require(names[1]),
                        endId = PoseJoints.require(names[2]),
                        startName = names[0],
                        vertexName = names[1],
                        endName = names[2]
                    )
                )
            }
        }
        require(trackingAngles.isNotEmpty()) { "Schema '$id' defines no tracking joints" }

        val statesJson = json.getJSONObject("states")
        val states = buildMap {
            statesJson.keys().forEach { stateName ->
                val stateJson = statesJson.getJSONObject(stateName)
                val constraints = buildMap {
                    stateJson.keys().forEach { angleName ->
                        val c = stateJson.getJSONObject(angleName)
                        put(
                            angleName,
                            AngleConstraint(
                                min = c.optDoubleOrNull("min"),
                                max = c.optDoubleOrNull("max"),
                                lessThan = c.optDoubleOrNull("less_than"),
                                greaterThan = c.optDoubleOrNull("greater_than")
                            )
                        )
                    }
                }
                put(stateName.uppercase(), SchemaState(constraints))
            }
        }
        require(states.containsKey(SchemaStates.START)) { "Schema '$id' is missing START state" }
        require(states.containsKey(SchemaStates.INFLECTION)) { "Schema '$id' is missing INFLECTION_POINT state" }

        val validation = json.optJSONObject("validation")
        val requiredVisible = buildList {
            val arr = validation?.optJSONArray("required_joints_visible")
            if (arr != null) for (i in 0 until arr.length()) add(PoseJoints.require(arr.getString(i)))
        }

        return ExerciseSchema(
            id = id,
            displayName = displayName,
            targetMuscles = muscles,
            trackingAngles = trackingAngles,
            states = states,
            optimalPlane = CameraPlane.fromId(validation?.optString("optimal_camera_plane")),
            requiredJointsVisible = requiredVisible,
            tempoPulseIntervalMs = json.optJSONObject("tempo")
                ?.optLong("pulse_interval_ms", DEFAULT_TEMPO_MS) ?: DEFAULT_TEMPO_MS,
            masteryReps = json.optInt("mastery_reps", DEFAULT_MASTERY_REPS),
            holdTargetMs = json.optJSONObject("hold")
                ?.optLong("target_ms", 0L)
                ?.takeIf { it > 0L }
        )
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key)) optDouble(key).takeUnless { it.isNaN() } else null

    private const val DEFAULT_TEMPO_MS = 3000L
    private const val DEFAULT_MASTERY_REPS = 10
}

/**
 * Loads and caches every exercise schema bundled under `assets/exercises/`.
 * New movements are added by dropping a JSON file in — no code changes.
 */
object ExerciseLibrary {
    private const val TAG = "ExerciseLibrary"
    private const val ASSET_DIR = "exercises"

    private val cache = LinkedHashMap<String, ExerciseSchema>()
    @Volatile
    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val assets = context.applicationContext.assets
        val files = assets.list(ASSET_DIR).orEmpty().filter { it.endsWith(".json") }
        files.forEach { fileName ->
            runCatching {
                val text = assets.open("$ASSET_DIR/$fileName").bufferedReader().use { it.readText() }
                SchemaParser.parse(JSONObject(text))
            }.onSuccess { schema ->
                cache[schema.id] = schema
            }.onFailure { error ->
                Log.e(TAG, "Failed to parse exercise schema $fileName", error)
            }
        }
        loaded = true
    }

    fun get(context: Context, exerciseId: String): ExerciseSchema? {
        load(context)
        return cache[exerciseId]
    }

    fun all(context: Context): List<ExerciseSchema> {
        load(context)
        return cache.values.toList()
    }
}
