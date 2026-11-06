package com.benjo.fboplus

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.benjo.fboplus.data.NotificationsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class NotificationsService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FirebaseAuth.getInstance().currentUser?.uid?.let {
            NotificationsRepository.enregistrerJeton(it, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val contenu = message.notification ?: return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL, "Suivi des commandes", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val ouverture = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(this).notify(
            message.messageId.hashCode(),
            NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(contenu.title)
                .setContentText(contenu.body)
                .setContentIntent(ouverture)
                .setAutoCancel(true)
                .build()
        )
    }

    private companion object {
        const val CANAL = "commandes"
    }
}
