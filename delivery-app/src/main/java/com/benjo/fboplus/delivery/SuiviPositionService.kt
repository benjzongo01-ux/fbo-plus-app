package com.benjo.fboplus.delivery

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Partage la position du livreur pendant une livraison, même écran éteint ou application en arrière-plan.
 * Une notification permanente le signale, comme l'exige Android. Le service s'arrête de lui-même
 * dès que la livraison n'est plus « en_livraison » (livrée, retournée, annulée).
 */
class SuiviPositionService : Service() {

    private val depot = Depot()
    private var missionId: String? = null
    private var ecoute: ListenerRegistration? = null
    private val localisation by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val rappel = object : LocationCallback() {
        override fun onLocationResult(resultat: LocationResult) {
            val id = missionId ?: return
            resultat.lastLocation?.let { depot.enregistrerPosition(id, it.latitude, it.longitude) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_MISSION)
        if (id == null || !permissionAccordee(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        )
        if (id == missionId) return START_REDELIVER_INTENT
        arreterSuivi()
        missionId = id

        localisation.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVALLE_MS)
                .setMinUpdateIntervalMillis(INTERVALLE_MS / 2)
                .build(),
            rappel,
            Looper.getMainLooper()
        )

        // Fin de la livraison ou mission retirée : on cesse de partager la position.
        ecoute = FirebaseFirestore.getInstance().document("deliveries/$id")
            .addSnapshotListener { doc, erreur ->
                if (erreur != null || doc?.getString("statut") != "en_livraison") stopSelf()
            }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        arreterSuivi()
        super.onDestroy()
    }

    private fun arreterSuivi() {
        localisation.removeLocationUpdates(rappel)
        ecoute?.remove()
        ecoute = null
    }

    private fun notification() = NotificationCompat.Builder(this, CANAL).also {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL, "Livraison en cours", NotificationManager.IMPORTANCE_LOW)
        )
    }
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Livraison en cours")
        .setContentText("Votre position est partagée jusqu'à la fin de la livraison.")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    companion object {
        private const val CANAL = "suivi_position"
        private const val NOTIFICATION_ID = 42
        private const val INTERVALLE_MS = 30_000L
        private const val EXTRA_MISSION = "mission"

        fun permissionAccordee(context: Context) =
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

        fun demarrer(context: Context, missionId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SuiviPositionService::class.java).putExtra(EXTRA_MISSION, missionId)
            )
        }

        fun arreter(context: Context) {
            context.stopService(Intent(context, SuiviPositionService::class.java))
        }
    }
}
