package com.quietdlp.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.Quality
import com.quietdlp.app.download.DownloadRepository
import com.quietdlp.app.ui.MainScreen
import com.quietdlp.app.ui.QuietTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** Enlace a analizar (cuando se comparte con la descarga directa desactivada). */
        const val EXTRA_URL = "extra_url"

        /** Una descarga falló porque la web pide iniciar sesión: dominio donde hacerlo + datos para reintentar. */
        const val EXTRA_LOGIN_HOST = "extra_login_host"
        const val EXTRA_RETRY_URL = "extra_retry_url"
        const val EXTRA_RETRY_ID = "extra_retry_id"
        const val EXTRA_KIND = "extra_kind"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_AUDIO_FMT = "extra_audio_fmt"
        const val EXTRA_AUDIO_BR = "extra_audio_br"
    }

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DownloadRepository.init(this)
        setContent { QuietTheme(vm.paletteId, vm.customPalettes) { MainScreen(vm) } }
        if (savedInstanceState == null) handleIntent(intent)
    }

    // Android solo deja leer el portapapeles cuando la app tiene el foco.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) vm.checkClipboard()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return

        // Venimos del aviso "pide iniciar sesión": mostrar la explicación.
        val loginHost = intent.getStringExtra(EXTRA_LOGIN_HOST)
        val retryUrl = intent.getStringExtra(EXTRA_RETRY_URL)
        if (loginHost != null && retryUrl != null) {
            val kind = runCatching { Kind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "") }
                .getOrDefault(Kind.VIDEO)
            val quality = Quality(
                kind = kind,
                maxHeight = intent.getIntExtra(EXTRA_HEIGHT, 0),
                audioFormat = intent.getStringExtra(EXTRA_AUDIO_FMT) ?: "m4a",
                audioBitrate = intent.getIntExtra(EXTRA_AUDIO_BR, 0),
            )
            vm.promptLogin(
                LoginPrompt(
                    host = loginHost,
                    url = retryUrl,
                    quality = quality,
                    oldItemId = intent.getStringExtra(EXTRA_RETRY_ID),
                ),
            )
            listOf(
                EXTRA_LOGIN_HOST, EXTRA_RETRY_URL, EXTRA_RETRY_ID,
                EXTRA_KIND, EXTRA_HEIGHT, EXTRA_AUDIO_FMT, EXTRA_AUDIO_BR,
            ).forEach(intent::removeExtra)
            return
        }

        // Llegó un enlace compartido (descarga directa desactivada).
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        intent.removeExtra(EXTRA_URL)
        vm.url = url
        vm.analyze(url)
    }
}
