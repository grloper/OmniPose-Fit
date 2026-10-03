package com.grloepr.pushtrack

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Captures runtime pixels only; filenames distinguish synthetic event presentation. */
fun captureRuntimeEvidence(name: String) {
    require(name.matches(Regex("[a-z0-9-]+")))
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("evidence"))
    directory.mkdirs()
    instrumentation.waitForIdleSync()
    // Continuous detector timestamps can keep accessibility events flowing.
    // Allow a bounded display-frame interval; semantic assertions govern state.
    android.os.SystemClock.sleep(250L)
    val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Screenshot unavailable" }
    try {
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    } finally { bitmap.recycle() }
}
