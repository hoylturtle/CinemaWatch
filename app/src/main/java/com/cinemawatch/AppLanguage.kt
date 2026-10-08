package com.cinemawatch

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/** Services and the application-scoped scanner need the same explicit locale as the activity. */
object AppLanguage {
    fun context(context: Context): Context {
        val locale = AppCompatDelegate.getApplicationLocales()[0] ?: return context
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration)
    }
}
