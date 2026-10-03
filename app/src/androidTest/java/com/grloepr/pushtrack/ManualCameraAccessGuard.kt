package com.grloepr.pushtrack

import android.Manifest
import android.app.AppOpsManager
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/** Own-UID live camera activity observation, not a comparison of relative app-op timestamps. */
internal fun assertManualJourneyDoesNotUseCamera(journey: () -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val uid = context.applicationInfo.uid
    val appOps = requireNotNull(context.getSystemService(AppOpsManager::class.java))
    val permissionBefore = context.checkSelfPermission(Manifest.permission.CAMERA)
    val modeBefore = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_CAMERA, uid, context.packageName)
    val activeTransitions = AtomicInteger()
    val listener = AppOpsManager.OnOpActiveChangedListener { op, changedUid, _, active ->
        if (op == AppOpsManager.OPSTR_CAMERA && changedUid == uid && active)
            activeTransitions.incrementAndGet()
    }
    // SDK 29+ permits watching one's own UID without adopting WATCH_APPOPS privileges.
    appOps.startWatchingActive(arrayOf(AppOpsManager.OPSTR_CAMERA), Executor { it.run() }, listener)
    try {
        // Public SDK has no active-state getter. The own-package shell query is only the
        // initial/final running-state check; the registered observer detects intervening use.
        fun assertInactive() {
            val snapshot = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("appops get ${context.packageName} CAMERA"))
                .bufferedReader().use { it.readText() }
            assertTrue("App-op state query returned no evidence", snapshot.isNotBlank())
            assertFalse("App-op state query failed: $snapshot", snapshot.contains("Error", ignoreCase = true))
            assertFalse("Own camera operation is active: $snapshot",
                Regex("\\brunning\\b", RegexOption.IGNORE_CASE).containsMatchIn(snapshot))
        }
        assertInactive()
        journey()
        instrumentation.waitForIdleSync()
        assertInactive()
        assertEquals("Manual route started own-UID camera activity", 0, activeTransitions.get())
        assertEquals(permissionBefore, context.checkSelfPermission(Manifest.permission.CAMERA))
        assertEquals(modeBefore, appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_CAMERA, uid, context.packageName))
    } finally {
        appOps.stopWatchingActive(listener)
    }
}
