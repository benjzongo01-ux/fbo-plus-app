package com.benjo.fboplus.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/** Branche les données Firestore au compte connecté, pour toute la durée de l'application. */
object Synchronisation {

    fun demarrer(context: Context) {
        val appli = context.applicationContext
        var cleActuelle: String? = null

        fun appliquer(uid: String?, role: String?) {
            val cle = if (uid != null && role != null) "$uid/$role" else null
            if (cle == cleActuelle) return
            cleActuelle = cle

            VentesRepository.arreter()
            ClientsRepository.arreter()
            ProfilRepository.arreter()
            NotificationsRepository.arreter()

            if (cle != null && uid != null) {
                VentesRepository.demarrer(appli, uid)
                ClientsRepository.demarrer(uid)
                ProfilRepository.demarrer(appli, uid)
                NotificationsRepository.demarrer(uid)
            }
        }

        ProductRepository.demarrer()

        // Les règles exigent le rôle, attribué juste après la première connexion : on suit donc
        // le jeton et non le compte, car un écouteur Firestore refusé ne redémarre jamais seul.
        FirebaseAuth.getInstance().addIdTokenListener(
            FirebaseAuth.IdTokenListener { auth ->
                val utilisateur = auth.currentUser
                if (utilisateur == null) {
                    appliquer(null, null)
                } else {
                    utilisateur.getIdToken(false).addOnSuccessListener { jeton ->
                        appliquer(utilisateur.uid, jeton.claims["role"] as? String)
                    }
                }
            }
        )
    }
}
