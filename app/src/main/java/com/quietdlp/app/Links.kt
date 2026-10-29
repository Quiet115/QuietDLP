package com.quietdlp.app

/**
 * Enlaces externos de la app.
 *
 * DONATE_URL: dirección de donaciones (Ko-fi, GitHub Sponsors, Buy Me a Coffee, PayPal.me…).
 *   Mientras esté vacía, el botón "Apoyar el proyecto" no se muestra. Solo abre el navegador.
 *
 * GITHUB_REPO: "usuario/repositorio" donde se publican las versiones (Releases). Mientras esté vacío,
 *   la app no busca actualizaciones de sí misma.
 */
object Links {
    const val DONATE_URL = "https://ko-fi.com/quiet115"
    /** Enlace de PayPal.me (opcional). Mientras esté vacío, el botón de PayPal no se muestra. */
    const val PAYPAL_URL = ""
    const val GITHUB_REPO = "Quiet115/QuietDLP"
    const val SOURCE_URL = "https://github.com/Quiet115/QuietDLP"
}
