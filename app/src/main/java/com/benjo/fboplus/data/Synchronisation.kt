package com.benjo.fboplus.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/** Branche les données Firestore au compte connecté, pour toute la durée de l'application. */
object Synchronisation {

    fun demarrer(context: Context) {
        val appli = context.applicationContext
        var uidActuel: String? = null

        ProductRepository.demarrer()
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid == uidActuel) return@addAuthStateListener
            uidActuel = uid

            if (uid != null) {
                VentesRepository.demarrer(appli, uid)
                ClientsRepository.demarrer(uid)
                ProfilRepository.demarrer(appli, uid)
                NotificationsRepository.demarrer(uid)
            } else {
                VentesRepository.arreter()
                ClientsRepository.arreter()
                ProfilRepository.arreter()
                NotificationsRepository.arreter()
            }
        }
    }
}
