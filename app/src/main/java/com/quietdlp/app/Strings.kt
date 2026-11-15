package com.quietdlp.app

import android.content.Context
import androidx.annotation.StringRes
import com.quietdlp.app.ui.Language
import com.quietdlp.app.ui.LanguageStore
import com.quietdlp.app.ui.withLanguage

// QuietApp inicia el contexto usado también por servicios y notificaciones.
object Strings {
    private lateinit var app: Context
    private var cached: Pair<Language, Context>? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    private fun localized(): Context {
        val language = LanguageStore(app).load()
        cached?.takeIf { it.first == language }?.let { return it.second }
        return app.withLanguage(language).also { cached = language to it }
    }

    // Los datos conservan el inglés para sus pruebas. Los textos desconocidos se devuelven sin traducir.
    fun tr(english: String): String {
        if (!::app.isInitialized) return english
        TextMap.ids[english]?.let { return get(it) }
        if (english.startsWith("Video ") && english.length > 6) return get(R.string.tm_video) + " " + english.removePrefix("Video ")
        if (english.startsWith("Audio only: ")) return get(R.string.tm_audio_only) + ": " + tr(english.removePrefix("Audio only: "))
        return english
    }

    private var english: Context? = null

    // El buscador de ajustes consulta también los textos en inglés.
    fun getEnglish(@StringRes id: Int): String {
        val ctx = english ?: app.withLanguage(Language.ENGLISH).also { english = it }
        return ctx.getString(id)
    }

    fun get(@StringRes id: Int, vararg args: Any?): String =
        if (args.isEmpty()) localized().getString(id) else localized().getString(id, *args)
}
