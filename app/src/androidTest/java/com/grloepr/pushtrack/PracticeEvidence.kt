package com.grloepr.pushtrack

import android.graphics.Bitmap
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Catalog IDs use underscores; evidence names have a deliberately smaller safe alphabet. */
internal fun exerciseEvidenceName(prefix: String, exerciseId: String, suffix: String? = null): String {
    require(prefix.matches(Regex("[a-z0-9-]+")))
    require(exerciseId.matches(Regex("[a-z0-9_]+")))
    require(suffix == null || suffix.matches(Regex("[a-z0-9-]+")))
    return listOfNotNull(prefix, exerciseId.replace('_', '-'), suffix).joinToString("-")
}

/** Captures only the synthetic test UI on an emulator, including dialog/IME windows. */
internal fun capturePracticeEvidence(name: String) {
    if (!Build.FINGERPRINT.contains("generic") && !Build.FINGERPRINT.contains("emulator") &&
        !Build.MODEL.contains("sdk", ignoreCase = true) && Build.HARDWARE !in setOf("ranchu", "goldfish")) return
    require(name.matches(Regex("[a-z0-9-]+")))
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "practice-evidence").apply { mkdirs() }
    instrumentation.waitForIdleSync()
    // Compose idle precedes the compositor presenting the final dialog/IME frame.
    android.os.SystemClock.sleep(250)
    instrumentation.waitForIdleSync()
    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    try {
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG encoding failed: $name" }
        }
    } finally { bitmap.recycle() }
    println("Practice screenshot: ${File(directory, "$name.png").absolutePath}")
}
