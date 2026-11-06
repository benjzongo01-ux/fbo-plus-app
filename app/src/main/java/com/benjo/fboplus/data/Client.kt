package com.benjo.fboplus.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Client(
    val id: String = "",
    val nom: String = "",
    val telephone: String = "",
    val adresse: String = "",
    val notes: String = ""
)

object ClientsRepository {

    private val db get() = FirebaseFirestore.getInstance()
    private val collection get() = db.collection("customers")

    private val _clients = MutableStateFlow<List<Client>>(emptyList())
    val clients: StateFlow<List<Client>> = _clients

    private var ecoute: ListenerRegistration? = null
    private var uid: String? = null

    fun demarrer(uid: String) {
        this.uid = uid
        ecoute?.remove()
        ecoute = collection.whereEqualTo("ownerId", uid).addSnapshotListener { docs, _ ->
            if (docs == null) return@addSnapshotListener
            _clients.value = docs.documents.map { d ->
                Client(
                    id = d.id,
                    nom = d.getString("nom").orEmpty(),
                    telephone = d.getString("telephone").orEmpty(),
                    adresse = d.getString("adresse").orEmpty(),
                    notes = d.getString("notes").orEmpty()
                )
            }.sortedBy { it.nom.lowercase() }
        }
    }

    fun arreter() {
        ecoute?.remove()
        ecoute = null
        uid = null
        _clients.value = emptyList()
    }

    /** Écrit dans le cache Firestore : fonctionne aussi hors ligne. */
    fun enregistrer(client: Client) {
        val proprietaire = uid ?: return
        val donnees = mapOf(
            "ownerId" to proprietaire,
            "nom" to client.nom.trim(),
            "telephone" to client.telephone.trim(),
            "adresse" to client.adresse.trim(),
            "notes" to client.notes.trim()
        )
        if (client.id.isEmpty()) collection.document().set(donnees)
        else collection.document(client.id).set(donnees)
    }

    fun supprimer(id: String) {
        collection.document(id).delete()
    }
}
