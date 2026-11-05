package com.quietdlp.app.data

import java.net.URLEncoder

/** Lo que el usuario quiere contar. La etiqueta es una de las que ya existen en el repositorio de GitHub. */
enum class FeedbackKind(val label: String, val titlePrefix: String) {
    BUG("bug", "Bug: "),
    SUGGESTION("enhancement", "Suggestion: "),
}

/** Los únicos datos que viajan en un informe: nada de rutas, contactos ni enlaces descargados. */
data class DiagnosticInfo(val appVersion: String, val android: String, val device: String, val language: String) {
    fun text(): String = "App version: $appVersion\nAndroid: $android\nDevice: $device\nLanguage: $language"
}

/** Prepara el issue de GitHub ya rellenado. No hay servidor ni claves: el navegador lo envía el propio usuario. */
object Feedback {
    const val NEW_ISSUE_URL = "https://github.com/Quiet115/QuietDLP/issues/new"

    /** Límite práctico de longitud de una URL; si el cuerpo no cabe se recorta en vez de fallar. */
    const val MAX_URL_LENGTH = 2000

    fun body(kind: FeedbackKind, info: DiagnosticInfo): String {
        val template = when (kind) {
            FeedbackKind.BUG -> "**What happened**\n\n\n**What you expected**\n\n\n**Steps to reproduce**\n1. \n"
            FeedbackKind.SUGGESTION -> "**Your suggestion**\n\n\n**How it would help**\n\n"
        }
        // La información va primero: si hay que recortar, se pierde la plantilla y no los datos.
        return "**Diagnostic info** (added by the app)\n${info.text()}\n\n$template"
    }

    fun url(kind: FeedbackKind, info: DiagnosticInfo, maxLength: Int = MAX_URL_LENGTH): String {
        val head = "$NEW_ISSUE_URL?title=${encode(kind.titlePrefix)}&labels=${encode(kind.label)}&body="
        var body = body(kind, info)
        while (true) {
            val url = head + encode(body)
            if (url.length <= maxLength || body.isEmpty()) return url
            body = body.dropLast(maxOf(20, body.length / 10))
        }
    }

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
