package com.levanyon.meditakip.ui

import android.app.Application
import com.levanyon.meditakip.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The version shown in Ayarlar must follow `versionName` in build.gradle.kts (RELEASE.md, version policy). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VersionLabelTest {
    @Test fun settingsLabelMatchesVersionName() {
        val context = RuntimeEnvironment.getApplication()
        // Debug builds add "-debug" (build.gradle.kts); the release name is what the label shows.
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName!!.removeSuffix("-debug")
        assertEquals("MediTakip $versionName", context.getString(R.string.version_label))
    }
}
