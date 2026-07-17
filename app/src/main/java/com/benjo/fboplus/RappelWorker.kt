package com.benjo.fboplus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.concurrent.TimeUnit
import java.util.Calendar

class RappelWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        envoyerNotification(applicationContext)
        return Result.success()
    }

    private fun envoyerNotification(context: Context) {
        val channelId = "fboplus_rappel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Créer le canal (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Rappels FBO+",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Rappels quotidiens pour enregistrer vos ventes"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🌿 FBO+ - Rappel ventes")
            .setContentText("N'oubliez pas d'enregistrer vos ventes d'aujourd'hui !")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }
}

object RappelScheduler {

    fun planifierRappelQuotidien(context: Context, heureH: Int = 19, heureM: Int = 0) {
        // Calcule le délai jusqu'à l'heure choisie
        val maintenant = Calendar.getInstance()
        val cible = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, heureH)
            set(Calendar.MINUTE, heureM)
            set(Calendar.SECOND, 0)
            if (before(maintenant)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val delaiMs = cible.timeInMillis - maintenant.timeInMillis

        val request = PeriodicWorkRequestBuilder<RappelWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delaiMs, TimeUnit.MILLISECONDS)
            .addTag("rappel_ventes")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "rappel_ventes",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun annulerRappel(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("rappel_ventes")
    }
}