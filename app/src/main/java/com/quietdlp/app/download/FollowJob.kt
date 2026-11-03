package com.quietdlp.app.download

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.quietdlp.app.data.FollowInterval
import com.quietdlp.app.data.Prefs

/**
 * La revisión de las páginas seguidas con la app cerrada. Android NO deja arrancar una descarga en primer plano desde una
 * tarea en segundo plano: aquí solo se revisa y se apunta lo nuevo; si no deja bajarlo, queda para cuando se abra la app.
 */
class FollowJobService : JobService() {
    @Volatile
    private var stopped = false

    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        Thread {
            try {
                FollowChecker.runDue(applicationContext) { stopped }
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        return false
    }
}

object FollowScheduler {
    private const val JOB_ID = 5001

    /** Pone, cambia o quita la tarea según las páginas que haya y el ajuste de Wi-Fi. Se puede llamar las veces que haga falta. */
    fun update(ctx: Context) {
        val app = ctx.applicationContext
        FollowStore.init(app)
        val js = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val pages = FollowStore.pages.value
        if (pages.isEmpty()) {
            js.cancel(JOB_ID)
            return
        }
        val period = FollowInterval.jobPeriodMs(pages)
        val network = if (Prefs(app).followWifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
        val current = js.getPendingJob(JOB_ID)
        if (current != null && current.intervalMillis == period && current.networkType == network) return
        runCatching {
            js.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(app, FollowJobService::class.java))
                    .setPeriodic(period)
                    .setRequiredNetworkType(network)
                    .setRequiresBatteryNotLow(true)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}
