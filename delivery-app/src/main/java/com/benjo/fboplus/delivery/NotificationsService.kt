package com.benjo.fboplus.delivery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

private fun enregistrerJeton(jeton: String) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    FirebaseFirestore.getInstance().document("fcmTokens/$jeton").set(mapOf("userId" to uid))
}

/** FCM exige un vrai projet : il n'y a pas de jeton à obtenir sur les émulateurs. */
fun enregistrerAppareil() {
    if (BuildConfig.EMULATEUR_FIREBASE) return
    FirebaseMessaging.getInstance().token.addOnSuccessListener(::enregistrerJeton)
}

class NotificationsService : FirebaseMessagingService() {

    override fun onNewToken(token: String) = enregistrerJeton(token)

    override fun onMessageReceived(message: RemoteMessage) {
        val contenu = message.notification ?: return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL, "Missions", NotificationManager.IMPORTANCE_HIGH)
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
        const val CANAL = "missions"
    }
}
