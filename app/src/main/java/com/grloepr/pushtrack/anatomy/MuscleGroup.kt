package com.grloepr.pushtrack.anatomy

/**
 * Muscle groups renderable on the anatomy canvas. Exercise schemas reference
 * muscles by free-form snake_case names; [fromSchemaName] maps the common
 * aliases onto canvas regions.
 */
enum class MuscleGroup(val displayName: String) {
    TRAPEZIUS("Trapezius"),
    DELTOIDS("Deltoids"),
    PECTORALS("Pectorals"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    CORE("Core / Abs"),
    OBLIQUES("Obliques"),
    LATS("Lats"),
    LOWER_BACK("Lower Back"),
    GLUTES("Glutes"),
    QUADRICEPS("Quadriceps"),
    HAMSTRINGS("Hamstrings"),
    CALVES("Calves");

    companion object {
        fun fromSchemaName(raw: String): MuscleGroup? = when (raw.trim().lowercase()) {
            "trapezius", "traps", "upper_back" -> TRAPEZIUS
            "deltoids", "delts", "shoulders", "anterior_deltoid" -> DELTOIDS
            "pectorals", "pecs", "chest", "pectoralis_major" -> PECTORALS
            "biceps", "biceps_brachii" -> BICEPS
            "triceps", "triceps_brachii" -> TRICEPS
            "forearms", "grip", "brachioradialis" -> FOREARMS
            "core", "abs", "abdominals", "rectus_abdominis" -> CORE
            "obliques" -> OBLIQUES
            "lats", "latissimus_dorsi", "back" -> LATS
            "lower_back", "erector_spinae", "erectors" -> LOWER_BACK
            "glutes", "gluteus_maximus", "gluteus" -> GLUTES
            "quadriceps", "quads" -> QUADRICEPS
            "hamstrings", "hams" -> HAMSTRINGS
            "calves", "gastrocnemius", "soleus" -> CALVES
            else -> null
        }

        fun fromSchemaNames(raw: Collection<String>): Set<MuscleGroup> =
            raw.mapNotNull(::fromSchemaName).toSet()
    }
}
