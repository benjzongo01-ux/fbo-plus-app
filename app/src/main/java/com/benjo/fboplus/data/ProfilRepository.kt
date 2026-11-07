package com.benjo.fboplus.data

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Le profil vit dans users/{uid}. La copie locale sert de cache hors ligne,
 * et reste la seule à contenir les réglages propres au téléphone (rappel).
 */
object ProfilRepository {

    private const val PREFS_NAME = "fboplus_profil"
    private const val KEY_PROFIL = "profil"
    private val gson = Gson()
    private val db get() = FirebaseFirestore.getInstance()

    private val _profil = MutableStateFlow<Profil?>(null)
    val profil: StateFlow<Profil?> = _profil

    private var ecoute: ListenerRegistration? = null
    private var uid: String? = null

    fun demarrer(context: Context, uid: String) {
        this.uid = uid
        ecoute?.remove()
        ecoute = db.document("users/$uid").addSnapshotListener { doc, _ ->
            if (doc == null || !doc.exists()) return@addSnapshotListener
            val local = getProfil(context)

            if (doc.getString("prenom").isNullOrBlank()) return@addSnapshotListener
            val fusionne = depuisFirestore(doc, local)
            enregistrerLocal(context, fusionne)
            _profil.value = fusionne
        }
    }

    fun envoyerProfilLocal(context: Context) {
        val local = getProfil(context)
        if (local.prenom.isNotBlank()) envoyer(local)
    }

    /** Ce compte a refusé l'import : il repart d'un profil vide. */
    fun oublierProfilAffiche() {
        _profil.value = Profil()
    }

    fun arreter() {
        ecoute?.remove()
        ecoute = null
        uid = null
    }

    fun sauvegarderProfil(context: Context, profil: Profil) {
        enregistrerLocal(context, profil)
        _profil.value = profil
        envoyer(profil)
    }

    fun getProfil(context: Context): Profil {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROFIL, null) ?: return Profil()
        return try {
            gson.fromJson(json, Profil::class.java)
        } catch (e: Exception) {
            Profil()
        }
    }

    private fun enregistrerLocal(context: Context, profil: Profil) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PROFIL, gson.toJson(profil)).apply()
    }

    /** Firestore met cette écriture en attente si le téléphone est hors ligne. */
    private fun envoyer(profil: Profil) {
        val id = uid ?: return
        db.document("users/$id").update(
            mapOf(
                "prenom" to profil.prenom,
                "nom" to profil.nom,
                "pays" to profil.pays,
                "ville" to profil.ville,
                "gradeDemande" to profil.grade,
                "indicatifTelephone" to profil.indicatifTelephone,
                "telephone" to profil.telephone,
                "indicatifWhatsapp" to profil.indicatifWhatsapp,
                "whatsapp" to profil.whatsapp,
                "idDistributeur" to profil.idDistributeur,
                "objectifCC" to profil.objectifCC,
                "objectifCA" to profil.objectifCA
            )
        )
    }

    private fun depuisFirestore(doc: DocumentSnapshot, local: Profil) = local.copy(
        prenom = doc.getString("prenom").orEmpty(),
        nom = doc.getString("nom").orEmpty(),
        pays = doc.getString("pays").orEmpty(),
        ville = doc.getString("ville").orEmpty(),
        grade = doc.getString("gradeDemande") ?: doc.getString("grade") ?: local.grade,
        gradeValide = doc.getString("grade").orEmpty(),
        indicatifTelephone = doc.getString("indicatifTelephone").orEmpty(),
        telephone = doc.getString("telephone")?.takeIf { !it.startsWith("+") } ?: local.telephone,
        indicatifWhatsapp = doc.getString("indicatifWhatsapp").orEmpty(),
        whatsapp = doc.getString("whatsapp").orEmpty(),
        idDistributeur = doc.getString("idDistributeur").orEmpty(),
        objectifCC = doc.getDouble("objectifCC") ?: 0.0,
        objectifCA = doc.getLong("objectifCA")?.toInt() ?: 0
    )
}
