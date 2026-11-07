package com.benjo.fboplus.delivery

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

data class LigneMission(val nom: String, val quantite: Int)

data class Mission(
    val id: String,
    val statut: String,
    val clientNom: String,
    val clientTelephone: String,
    val clientTelephone2: String,
    val adresse: String,
    val lignes: List<LigneMission>,
    val montant: Int,
    val commission: Int,
    val dateMillis: Long,
    val motifRetour: String
) {
    val enCours: Boolean get() = statut == "assignee" || statut == "en_livraison"
}

class Depot {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance(REGION_FUNCTIONS)
    private val stockage = FirebaseStorage.getInstance()

    val uid: String? get() = auth.currentUser?.uid

    fun missions(uid: String): Flow<List<Mission>> = callbackFlow {
        val ecoute = db.collection("deliveries")
            .whereEqualTo("livreurId", uid)
            .addSnapshotListener { docs, _ ->
                if (docs != null) trySend(docs.documents.map(::versMission))
            }
        awaitClose { ecoute.remove() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun versMission(doc: DocumentSnapshot): Mission {
        val client = doc.get("client") as? Map<String, Any?>
        val lignes = (doc.get("lignes") as? List<Map<String, Any?>>).orEmpty()
        return Mission(
            id = doc.id,
            statut = doc.getString("statut").orEmpty(),
            clientNom = client?.get("nom") as? String ?: "",
            clientTelephone = client?.get("telephone") as? String ?: "",
            clientTelephone2 = client?.get("telephone2") as? String ?: "",
            adresse = doc.getString("adresse").orEmpty(),
            lignes = lignes.map {
                LigneMission(it["nom"] as? String ?: "", (it["quantite"] as? Number)?.toInt() ?: 0)
            },
            montant = doc.getLong("montantAEncaisser")?.toInt() ?: 0,
            commission = doc.getLong("commission")?.toInt() ?: 0,
            dateMillis = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            motifRetour = doc.getString("motifRetour").orEmpty()
        )
    }

    suspend fun demarrer(id: String) {
        appeler("demarrerLivraison", mapOf("id" to id))
    }

    suspend fun signalerRetour(id: String, motif: String) {
        appeler("signalerRetour", mapOf("id" to id, "motif" to motif))
    }

    /** Le nom est unique : une preuve déposée ne peut pas être remplacée, donc un nouvel essai crée un autre fichier. */
    suspend fun terminer(id: String, montant: Int, photo: ByteArray, signature: ByteArray) {
        val livreur = uid ?: error("Session expirée")
        val horodatage = System.currentTimeMillis()
        val cheminPhoto = "deliveries/$id/$livreur/photo-$horodatage.jpg"
        val cheminSignature = "deliveries/$id/$livreur/signature-$horodatage.png"
        envoyer(cheminPhoto, photo, "image/jpeg")
        envoyer(cheminSignature, signature, "image/png")
        appeler(
            "terminerLivraison",
            mapOf(
                "id" to id,
                "montantEncaisse" to montant,
                "preuvePhoto" to cheminPhoto,
                "signature" to cheminSignature
            )
        )
    }

    private suspend fun envoyer(chemin: String, octets: ByteArray, type: String) {
        stockage.reference.child(chemin)
            .putBytes(octets, StorageMetadata.Builder().setContentType(type).build())
            .await()
    }

    fun enregistrerPosition(id: String, lat: Double, lng: Double) {
        db.document("deliveries/$id").update(
            mapOf(
                "position" to mapOf("lat" to lat, "lng" to lng),
                "positionUpdatedAt" to FieldValue.serverTimestamp()
            )
        )
    }

    private suspend fun appeler(nom: String, donnees: Map<String, Any>) {
        functions.getHttpsCallable(nom).call(donnees).await()
    }
}

class DepotAuth {
    private val auth = FirebaseAuth.getInstance()

    fun estConnecte() = auth.currentUser != null

    suspend fun envoyerCode(activity: Activity, telephone: String): EnvoiCode =
        suspendCancellableCoroutine { suite ->
            val rappels = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(identifiant: PhoneAuthCredential) {
                    if (suite.isActive) suite.resume(EnvoiCode.Automatique(identifiant))
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (suite.isActive) suite.resumeWithException(e)
                }

                override fun onCodeSent(id: String, jeton: PhoneAuthProvider.ForceResendingToken) {
                    if (suite.isActive) suite.resume(EnvoiCode.Envoye(id))
                }
            }
            PhoneAuthProvider.verifyPhoneNumber(
                PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(telephone)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(rappels)
                    .build()
            )
        }

    fun identifiant(id: String, code: String) = PhoneAuthProvider.getCredential(id, code)

    /**
     * Pas d'auto-inscription : un numéro inconnu n'a aucun compte livreur, et le compte que
     * Firebase vient de créer pour lui est supprimé aussitôt.
     */
    suspend fun connecterLivreur(identifiant: PhoneAuthCredential): Boolean {
        val resultat = auth.signInWithCredential(identifiant).await()
        if (resultat.additionalUserInfo?.isNewUser == true) {
            auth.currentUser?.delete()?.await()
            auth.signOut()
            return false
        }
        val role = auth.currentUser?.getIdToken(true)?.await()?.claims?.get("role")
        if (role != "livreur") {
            auth.signOut()
            return false
        }
        return true
    }

    fun deconnecter() = auth.signOut()
}

sealed interface EnvoiCode {
    data class Envoye(val id: String) : EnvoiCode
    data class Automatique(val identifiant: PhoneAuthCredential) : EnvoiCode
}

/** Réduit la photo sous la limite de 5 Mo fixée par les règles Storage. */
fun photoCompressee(context: Context, uri: Uri, coteMax: Int = 1600): ByteArray {
    val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bornes) }

    var echelle = 1
    while (max(bornes.outWidth, bornes.outHeight) / echelle > coteMax) echelle *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = echelle }
    val image = context.contentResolver.openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: error("Photo illisible")

    return ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
}
