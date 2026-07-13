package com.grloepr.pushtrack.progression

import com.grloepr.pushtrack.anatomy.MuscleGroup

/** Visual/semantic lane a skill lives in on the progression map. */
enum class SkillBranch(val label: String, val lane: Int) {
    PUSH("Push", 0),
    BALANCE("Balance", 1),
    PULL("Pull", 2),
    LEVER("Lever", 3),
    LEGS("Legs", 4)
}

/** Icon flavor for a node; resolved to vector icons in the UI layer. */
enum class SkillIcon { PUSH, PULL, LEGS, BALANCE, LEVER, HANG, CAPSTONE }

enum class SkillStatus { LOCKED, AVAILABLE, MASTERED }

/**
 * One node of the calisthenics progression DAG.
 *
 * @param schemaId id of the exercise schema powering AI rep tracking for this
 *                 skill, or null when camera tracking isn't available yet.
 */
data class SkillNode(
    val id: String,
    val title: String,
    val tagline: String,
    val description: String,
    val branch: SkillBranch,
    val tier: Int,
    val prerequisites: List<String>,
    val schemaId: String?,
    val targetMuscles: Set<MuscleGroup>,
    val difficulty: Int,
    val masteryReps: Int,
    val icon: SkillIcon
) {
    val xpReward: Int get() = difficulty * 60
}

/**
 * The built-in progression graph — a DAG whose edges are prerequisite
 * relations. Kept as data so a remote-config/JSON source can replace it later.
 */
object CalisthenicsSkillGraph {

    val nodes: List<SkillNode> = listOf(
        // ── PUSH ─────────────────────────────────────────────────────────────
        SkillNode(
            id = "wall_pushup", title = "Wall Push-Up",
            tagline = "Groove the pressing pattern",
            description = "Hands on a wall, body in one line. Build the shoulder and elbow mechanics that every push skill inherits.",
            branch = SkillBranch.PUSH, tier = 0, prerequisites = emptyList(),
            schemaId = "pushup",
            targetMuscles = setOf(MuscleGroup.PECTORALS, MuscleGroup.TRICEPS, MuscleGroup.DELTOIDS),
            difficulty = 1, masteryReps = 12, icon = SkillIcon.PUSH
        ),
        SkillNode(
            id = "pushup", title = "Push-Up",
            tagline = "The foundation of all pressing",
            description = "Full push-up with a rigid plank line. Chest tracks to the floor, elbows lock out at the top — the gateway to the entire push tree.",
            branch = SkillBranch.PUSH, tier = 1, prerequisites = listOf("wall_pushup"),
            schemaId = "pushup",
            targetMuscles = setOf(MuscleGroup.PECTORALS, MuscleGroup.TRICEPS, MuscleGroup.DELTOIDS, MuscleGroup.CORE),
            difficulty = 2, masteryReps = 10, icon = SkillIcon.PUSH
        ),
        SkillNode(
            id = "diamond_pushup", title = "Diamond Push-Up",
            tagline = "Triceps-dominant pressing power",
            description = "Hands form a diamond under the chest. Shifts load onto the triceps and prepares the elbows for straight-arm skills.",
            branch = SkillBranch.PUSH, tier = 2, prerequisites = listOf("pushup"),
            schemaId = "pushup",
            targetMuscles = setOf(MuscleGroup.TRICEPS, MuscleGroup.PECTORALS, MuscleGroup.CORE),
            difficulty = 3, masteryReps = 8, icon = SkillIcon.PUSH
        ),
        SkillNode(
            id = "archer_pushup", title = "Archer Push-Up",
            tagline = "Shift the load to one side",
            description = "One arm bends while the other stays straight and wide. Unilateral strength on the road to the one-arm push-up.",
            branch = SkillBranch.PUSH, tier = 3, prerequisites = listOf("diamond_pushup"),
            schemaId = "pushup",
            targetMuscles = setOf(MuscleGroup.PECTORALS, MuscleGroup.TRICEPS, MuscleGroup.OBLIQUES),
            difficulty = 4, masteryReps = 6, icon = SkillIcon.PUSH
        ),
        SkillNode(
            id = "one_arm_pushup", title = "One-Arm Push-Up",
            tagline = "Elite unilateral pressing",
            description = "Full-range push-up on a single arm with square hips. A benchmark of total-body tension and pressing strength.",
            branch = SkillBranch.PUSH, tier = 4, prerequisites = listOf("archer_pushup"),
            schemaId = "pushup",
            targetMuscles = setOf(MuscleGroup.PECTORALS, MuscleGroup.TRICEPS, MuscleGroup.CORE, MuscleGroup.OBLIQUES),
            difficulty = 5, masteryReps = 3, icon = SkillIcon.CAPSTONE
        ),

        // ── BALANCE ──────────────────────────────────────────────────────────
        SkillNode(
            id = "frog_stand", title = "Frog Stand",
            tagline = "First taste of hand balancing",
            description = "Knees perch on bent elbows, toes float. Trains wrists, balance reflexes and the confidence to be upside-down-ish.",
            branch = SkillBranch.BALANCE, tier = 3, prerequisites = listOf("diamond_pushup"),
            schemaId = null,
            targetMuscles = setOf(MuscleGroup.FOREARMS, MuscleGroup.DELTOIDS, MuscleGroup.CORE),
            difficulty = 3, masteryReps = 1, icon = SkillIcon.BALANCE
        ),
        SkillNode(
            id = "wall_handstand", title = "Wall Handstand",
            tagline = "Own the inverted line",
            description = "Chest-to-wall handstand hold. Stacks shoulders over wrists and builds the overhead endurance a freestanding handstand needs.",
            branch = SkillBranch.BALANCE, tier = 4, prerequisites = listOf("frog_stand"),
            schemaId = null,
            targetMuscles = setOf(MuscleGroup.DELTOIDS, MuscleGroup.TRAPEZIUS, MuscleGroup.CORE, MuscleGroup.FOREARMS),
            difficulty = 4, masteryReps = 1, icon = SkillIcon.BALANCE
        ),
        SkillNode(
            id = "handstand", title = "Handstand",
            tagline = "The freestanding crown jewel",
            description = "Balance on two hands, body in one silent line. The signature calisthenics skill — equal parts strength, alignment and calm.",
            branch = SkillBranch.BALANCE, tier = 5, prerequisites = listOf("wall_handstand"),
            schemaId = null,
            targetMuscles = setOf(MuscleGroup.DELTOIDS, MuscleGroup.TRAPEZIUS, MuscleGroup.CORE, MuscleGroup.FOREARMS),
            difficulty = 5, masteryReps = 1, icon = SkillIcon.CAPSTONE
        ),

        // ── PULL ─────────────────────────────────────────────────────────────
        SkillNode(
            id = "dead_hang", title = "Dead Hang",
            tagline = "Grip is the gateway",
            description = "Passive hang from the bar. Decompresses the spine and forges the grip endurance every pull skill hangs on.",
            branch = SkillBranch.PULL, tier = 0, prerequisites = emptyList(),
            schemaId = null,
            targetMuscles = setOf(MuscleGroup.FOREARMS, MuscleGroup.LATS),
            difficulty = 1, masteryReps = 1, icon = SkillIcon.HANG
        ),
        SkillNode(
            id = "scapular_pulls", title = "Scapular Pulls",
            tagline = "Wake up the shoulder blades",
            description = "From a dead hang, pull the shoulder blades down without bending the elbows. The hidden first inch of every pull-up.",
            branch = SkillBranch.PULL, tier = 1, prerequisites = listOf("dead_hang"),
            schemaId = null,
            targetMuscles = setOf(MuscleGroup.TRAPEZIUS, MuscleGroup.LATS),
            difficulty = 2, masteryReps = 8, icon = SkillIcon.PULL
        ),
        SkillNode(
            id = "pullup", title = "Pull-Up",
            tagline = "Chin over bar, full stop",
            description = "Dead hang to chin-over-bar with no swing. The king of upper-body pulling and the root of the lever tree.",
            branch = SkillBranch.PULL, tier = 2, prerequisites = listOf("scapular_pulls"),
            schemaId = "pullup",
            targetMuscles = setOf(MuscleGroup.LATS, MuscleGroup.BICEPS, MuscleGroup.FOREARMS),
            difficulty = 3, masteryReps = 5, icon = SkillIcon.PULL
        ),
        SkillNode(
            id = "archer_pullup", title = "Archer Pull-Up",
            tagline = "One side does the heavy lifting",
            description = "Pull toward one hand while the other arm stays straight along the bar. Bridges the gap toward the one-arm pull-up and muscle-up strength.",
            branch = SkillBranch.PULL, tier = 3, prerequisites = listOf("pullup"),
            schemaId = "pullup",
            targetMuscles = setOf(MuscleGroup.LATS, MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.CORE),
            difficulty = 4, masteryReps = 4, icon = SkillIcon.PULL
        ),
        SkillNode(
            id = "muscle_up", title = "Muscle-Up",
            tagline = "Over the bar, not just to it",
            description = "Explosive pull-up that transitions into a dip above the bar. The rite of passage from pulling to total bar ownership.",
            branch = SkillBranch.PULL, tier = 4, prerequisites = listOf("archer_pullup"),
            schemaId = "pullup",
            targetMuscles = setOf(MuscleGroup.LATS, MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.PECTORALS),
            difficulty = 5, masteryReps = 2, icon = SkillIcon.CAPSTONE
        ),

        // ── LEVER ────────────────────────────────────────────────────────────
        SkillNode(
            id = "tuck_front_lever", title = "Tuck Front Lever",
            tagline = "Horizontal begins here",
            description = "Hang, tuck the knees, pull the body flat under the bar. Teaches the straight-arm lat pressure the full lever is made of.",
            branch = SkillBranch.LEVER, tier = 3, prerequisites = listOf("pullup"),
            schemaId = null,
            targetMuscles = setOf(MuscleGroup.LATS, MuscleGroup.CORE, MuscleGroup.LOWER_BACK),
            difficulty = 4, masteryReps = 1, icon = SkillIcon.LEVER
        ),
        SkillNode(
            id = "front_lever", title = "Front Lever",
            tagline = "The horizontal masterpiece",
            description = "Body board-flat under the bar, arms straight. A world-class display of lat and core strength — one of calisthenics' great summits.",
            branch = SkillBranch.LEVER, tier = 5, prerequisites = listOf("tuck_front_lever", "frog_stand"),
            schemaId = null,
            targetMuscles = setOf(MuscleGroup.LATS, MuscleGroup.CORE, MuscleGroup.LOWER_BACK, MuscleGroup.GLUTES),
            difficulty = 5, masteryReps = 1, icon = SkillIcon.CAPSTONE
        ),

        // ── LEGS ─────────────────────────────────────────────────────────────
        SkillNode(
            id = "air_squat", title = "Air Squat",
            tagline = "Sit down, stand up, repeat",
            description = "Bodyweight squat to parallel with heels planted. The mobility and motor pattern everything below the waist builds on.",
            branch = SkillBranch.LEGS, tier = 0, prerequisites = emptyList(),
            schemaId = "squat",
            targetMuscles = setOf(MuscleGroup.QUADRICEPS, MuscleGroup.GLUTES),
            difficulty = 1, masteryReps = 12, icon = SkillIcon.LEGS
        ),
        SkillNode(
            id = "deep_squat", title = "Deep Squat",
            tagline = "Full range, full control",
            description = "Hips below knees with an upright chest. Unlocks ankle and hip mobility while loading the quads and glutes through their full range.",
            branch = SkillBranch.LEGS, tier = 1, prerequisites = listOf("air_squat"),
            schemaId = "squat",
            targetMuscles = setOf(MuscleGroup.QUADRICEPS, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            difficulty = 2, masteryReps = 10, icon = SkillIcon.LEGS
        ),
        SkillNode(
            id = "split_squat", title = "Split Squat",
            tagline = "Two legs, one at a time",
            description = "Rear foot elevated, front leg does the work. Builds the single-leg stability and strength the pistol squat demands.",
            branch = SkillBranch.LEGS, tier = 2, prerequisites = listOf("deep_squat"),
            schemaId = "squat",
            targetMuscles = setOf(MuscleGroup.QUADRICEPS, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES),
            difficulty = 3, masteryReps = 8, icon = SkillIcon.LEGS
        ),
        SkillNode(
            id = "pistol_squat", title = "Pistol Squat",
            tagline = "Single-leg mastery",
            description = "Full-depth squat on one leg, the other held straight out front. Strength, mobility and balance in a single unforgiving rep.",
            branch = SkillBranch.LEGS, tier = 4, prerequisites = listOf("split_squat"),
            schemaId = "squat",
            targetMuscles = setOf(MuscleGroup.QUADRICEPS, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.CORE),
            difficulty = 5, masteryReps = 5, icon = SkillIcon.CAPSTONE
        ),
    )

    private val nodesById: Map<String, SkillNode> = nodes.associateBy { it.id }

    /** Prerequisite edges as (fromId → toId) pairs. */
    val edges: List<Pair<String, String>> =
        nodes.flatMap { node -> node.prerequisites.map { prereq -> prereq to node.id } }

    val maxTier: Int = nodes.maxOf { it.tier }

    fun byId(id: String): SkillNode? = nodesById[id]

    fun statusOf(node: SkillNode, mastered: Set<String>): SkillStatus = when {
        node.id in mastered -> SkillStatus.MASTERED
        node.prerequisites.all { it in mastered } -> SkillStatus.AVAILABLE
        else -> SkillStatus.LOCKED
    }

    init {
        // Fail fast in development if the graph isn't a valid DAG:
        // every prerequisite must exist and sit on a strictly lower tier.
        nodes.forEach { node ->
            node.prerequisites.forEach { prereq ->
                val parent = requireNotNull(nodesById[prereq]) {
                    "Skill '${node.id}' references unknown prerequisite '$prereq'"
                }
                require(parent.tier < node.tier) {
                    "Skill '${node.id}' (tier ${node.tier}) must sit below prerequisite '$prereq' (tier ${parent.tier})"
                }
            }
        }
    }
}
