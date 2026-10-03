package com.grloepr.pushtrack

/** Catalog IDs use underscores; evidence names have a deliberately smaller safe alphabet. */
fun exerciseEvidenceName(prefix: String, exerciseId: String, suffix: String? = null): String {
    require(prefix.matches(Regex("[a-z0-9-]+")))
    require(exerciseId.matches(Regex("[a-z0-9_]+")))
    require(suffix == null || suffix.matches(Regex("[a-z0-9-]+")))
    return listOfNotNull(prefix, exerciseId.replace('_', '-'), suffix).joinToString("-")
}

