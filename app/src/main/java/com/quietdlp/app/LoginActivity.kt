package com.quietdlp.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.quietdlp.app.download.CookieStore

/**
 * Pantalla de inicio de sesión: abre la web oficial del sitio dentro de la app. El usuario escribe sus datos
 * directamente en esa web (QuietDLP nunca ve la contraseña); al terminar, la app solo recoge las cookies
 * de sesión y las guarda para yt-dlp.
 */
class LoginActivity : Activity() {

    companion object {
        /** Dominio donde iniciar sesión, p. ej. "ejemplo.com". */
        const val EXTRA_HOST = "host"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = CookieStore.normalizeHost(intent.getStringExtra(EXTRA_HOST).orEmpty())
        if (host == null) {
            finish()
            return
        }

        val pad = (16 * resources.displayMetrics.density).toInt()

        val hint = TextView(this).apply {
            text = "Sign in to $host, then tap “I'm signed in”. " +
                "If it offers to save your login, choose “Not now”."
            setPadding(pad, pad, pad, pad)
            textSize = 14f
            // Fondo blanco fijo: el texto debe ser oscuro también en modo oscuro del sistema.
            setTextColor(Color.BLACK)
        }

        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.savePassword = false
            @Suppress("DEPRECATION")
            settings.saveFormData = false
            // Evita que el gestor de contraseñas del móvil (o el autorrelleno) ofrezca guardar o rellenar
            // la cuenta dentro de este navegador integrado.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
            webViewClient = WebViewClient()
        }

        val done = Button(this).apply {
            text = "I'm signed in"
            setOnClickListener {
                if (CookieStore.saveFromWebView(this@LoginActivity, host, web.url)) {
                    // Limpia el WebView: la sesión queda solo en el archivo privado de la app.
                    CookieManager.getInstance().removeAllCookies(null)
                    WebStorage.getInstance().deleteAllData()
                    web.clearFormData()
                    web.clearCache(true)
                    web.clearHistory()
                    Toast.makeText(this@LoginActivity, "Session for $host saved", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "No session yet. Sign in, then tap again.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(hint)
            addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(done, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Android 15 dibuja bajo las barras del sistema: dejar margen para que no tapen el contenido.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
        web.loadUrl("https://$host")
    }
}
