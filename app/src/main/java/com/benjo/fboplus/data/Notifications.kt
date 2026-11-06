package com.benjo.fboplus.data

import android.util.Log
import com.benjo.fboplus.BuildConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Notification(
    val id: String,
    val titre: String,
    val corps: String,
    val commandeId: String,
    val lu: Boolean,
    val dateMillis: Long
)

object NotificationsRepository {

    private val db get() = FirebaseFirestore.getInstance()

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private var ecoute: ListenerRegistration? = null

    fun demarrer(uid: String) {
        ecoute?.remove()
        ecoute = db.collection("notifications")
            .whereEqualTo("userId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { docs, _ ->
                if (docs == null) return@addSnapshotListener
                _notifications.value = docs.documents.map { d ->
                    Notification(
                        id = d.id,
                        titre = d.getString("titre").orEmpty(),
                        corps = d.getString("corps").orEmpty(),
                        commandeId = d.getString("commandeId").orEmpty(),
                        lu = d.getBoolean("lu") ?: false,
                        dateMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )
                }
            }
        enregistrerAppareil(uid)
    }

    fun arreter() {
        ecoute?.remove()
        ecoute = null
        _notifications.value = emptyList()
    }

    fun marquerLue(id: String) {
        db.document("notifications/$id").update("lu", true)
    }

    fun enregistrerJeton(uid: String, jeton: String) {
        db.document("fcmTokens/$jeton").set(mapOf("userId" to uid))
    }

    /** FCM exige un vrai projet : il n'y a pas de jeton à obtenir sur les émulateurs. */
    private fun enregistrerAppareil(uid: String) {
        if (BuildConfig.EMULATEUR_FIREBASE) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { enregistrerJeton(uid, it) }
            .addOnFailureListener { Log.w("Notifications", "Jeton indisponible", it) }
    }
}
