package com.quietdlp.app.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object Connectivity {
    /** true si el móvil tiene una red con internet que Android ya comprobó que funciona. */
    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
