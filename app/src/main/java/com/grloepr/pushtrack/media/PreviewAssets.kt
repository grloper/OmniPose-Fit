package com.grloepr.pushtrack.media

import android.content.Context

/**
 * Resolves the bundled demonstration video for a skill. Videos live in
 * `assets/previews/` named `{skillNodeId}.mp4` with a fallback to
 * `{exerciseSchemaId}.mp4`, so one clip can serve a whole family of skill
 * variants until each gets its own footage. Files are produced by
 * `tools/generate_previews.py` — dropping in a new MP4 requires no code change.
 */
object PreviewAssets {
    private const val DIR = "previews"

    @Volatile
    private var cached: Set<String>? = null

    private fun available(context: Context): Set<String> =
        cached ?: synchronized(this) {
            cached ?: (context.applicationContext.assets.list(DIR)?.toSet() ?: emptySet())
                .also { cached = it }
        }

    /** Asset URI for a skill's demo clip, preferring a node-specific file; null when absent. */
    fun uriFor(context: Context, nodeId: String, schemaId: String?): String? {
        val files = available(context)
        val fileName = listOfNotNull("$nodeId.mp4", schemaId?.let { "$it.mp4" })
            .firstOrNull { it in files }
        return fileName?.let { "asset:///$DIR/$it" }
    }
}
