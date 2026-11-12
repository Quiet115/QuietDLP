package com.quietdlp.app

import android.content.Context
import androidx.annotation.StringRes
import com.quietdlp.app.ui.Language
import com.quietdlp.app.ui.LanguageStore
import com.quietdlp.app.ui.withLanguage

/**
 * Los textos de la app en el idioma elegido en Ajustes (inglés de serie). Sirve en cualquier sitio: pantallas, servicio,
 * notificaciones y mensajes. Se inicia en [QuietApp].
 */
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

    /**
     * Traduce un texto en inglés de la capa de datos (formatos, calidades, modos) al mostrarlo. Esa capa sigue en inglés
     * para poder probarla; lo que no está en [TextMap] se devuelve igual.
     */
    fun tr(english: String): String {
        if (!::app.isInitialized) return english
        TextMap.ids[english]?.let { return get(it) }
        if (english.startsWith("Video ") && english.length > 6) return get(R.string.tm_video) + " " + english.removePrefix("Video ")
        if (english.startsWith("Audio only: ")) return get(R.string.tm_audio_only) + ": " + tr(english.removePrefix("Audio only: "))
        return english
    }

    private var english: Context? = null

    /** Un texto en inglés, sea cual sea el idioma elegido (el buscador de Ajustes busca en los dos). */
    fun getEnglish(@StringRes id: Int): String {
        val ctx = english ?: app.withLanguage(Language.ENGLISH).also { english = it }
        return ctx.getString(id)
    }

    fun get(@StringRes id: Int, vararg args: Any?): String =
        if (args.isEmpty()) localized().getString(id) else localized().getString(id, *args)
}
