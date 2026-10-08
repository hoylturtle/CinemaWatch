package com.cinemawatch

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class LanguageTest {
    private fun text(tag: String, id: Int): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return context.createConfigurationContext(configuration).getString(id)
    }
    @Test fun systemLocalesMatchSupportedScripts() {
        assertEquals("开始巡检", text("zh-CN", R.string.start_inspection))
        assertEquals("開始巡檢", text("zh-TW", R.string.start_inspection))
        assertEquals("開始巡檢", text("zh-HK", R.string.start_inspection))
        assertEquals("Start inspection", text("en-GB", R.string.start_inspection))
    }
    @Test fun unsupportedLocaleFallsBackToSimplifiedChinese() {
        assertEquals("开始巡检", text("fr-FR", R.string.start_inspection))
        assertEquals("导出 CSV", text("ja-JP", R.string.export))
    }
}
