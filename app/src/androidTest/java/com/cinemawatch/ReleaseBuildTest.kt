package com.cinemawatch

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test

class ReleaseBuildTest {
    @Test fun installedReleaseIsNotDebuggable() {
        assumeFalse("This assertion targets the release variant", BuildConfig.DEBUG)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.cinemawatch", context.packageName)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        assertTrue(context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode > 11)
    }
}
