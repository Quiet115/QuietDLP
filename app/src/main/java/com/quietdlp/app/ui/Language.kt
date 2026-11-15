package com.quietdlp.app.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

// El idioma inicial es inglés, independientemente del idioma del sistema.
enum class Language(val tag: String) { ENGLISH("en"), SPANISH("es") }

class LanguageStore(context: Context) {
    private val prefs = context.getSharedPreferences("language", Context.MODE_PRIVATE)

    fun load(): Language =
        Language.entries.firstOrNull { it.tag == prefs.getString("tag", null) } ?: Language.ENGLISH

    fun save(language: Language) {
        prefs.edit().putString("tag", language.tag).apply()
    }
}

fun Context.withLanguage(language: Language): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(Locale.forLanguageTag(language.tag))
    return createConfigurationContext(config)
}
