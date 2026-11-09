package com.benjo.fboplus.delivery

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import org.json.JSONObject
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
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

/**
 * Connexion des livreurs : Google (recommandé) ou email. Pas d'auto-inscription : seule une adresse
 * invitée par le super administrateur obtient un compte livreur ; toute autre est refusée par le serveur.
 */
class DepotAuth {
    private val auth = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance(REGION_FUNCTIONS)

    fun estConnecte() = auth.currentUser != null

    val googleConfigure: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    val googleDeTest: Boolean get() = !googleConfigure && BuildConfig.EMULATEUR_FIREBASE

    suspend fun connecterGoogle(activity: Activity) {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val demande = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val identifiant = CredentialManager.create(activity).getCredential(activity, demande).credential
        require(identifiant is CustomCredential && identifiant.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Réponse Google inattendue."
        }
        val jeton = GoogleIdTokenCredential.createFrom(identifiant.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(jeton, null)).await()
    }

    /** Émulateurs uniquement : jeton Google fictif accepté par l'émulateur d'authentification. */
    suspend fun connecterGoogleDeTest(email: String) {
        check(BuildConfig.EMULATEUR_FIREBASE) { "Réservé aux émulateurs." }
        val jeton = JSONObject()
            .put("sub", "test-" + email.lowercase())
            .put("email", email.lowercase())
            .put("email_verified", true)
            .toString()
        auth.signInWithCredential(GoogleAuthProvider.getCredential(jeton, null)).await()
    }

    suspend fun connecterEmail(email: String, motDePasse: String) {
        auth.signInWithEmailAndPassword(email.trim(), motDePasse).await()
    }

    /** Première connexion par email : crée le mot de passe et envoie le lien de confirmation. */
    suspend fun creerMotDePasse(email: String, motDePasse: String) {
        val compte = auth.createUserWithEmailAndPassword(email.trim(), motDePasse).await().user
        compte?.sendEmailVerification()?.await()
        auth.signOut()
    }

    suspend fun reinitialiserMotDePasse(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /**
     * Vérifie le compte auprès du serveur : l'invitation est appliquée, ou l'accès refusé.
     * Retourne null si c'est bien un livreur, sinon le message à afficher.
     */
    suspend fun verifierLivreur(): String? {
        try {
            functions.getHttpsCallable("initialiserProfil").call(mapOf("personnel" to true)).await()
        } catch (e: FirebaseFunctionsException) {
            // Adresse jamais invitée : le compte vide créé par cette connexion est retiré.
            if (e.code == FirebaseFunctionsException.Code.PERMISSION_DENIED) runCatching { auth.currentUser?.delete()?.await() }
            auth.signOut()
            return e.message ?: "Accès refusé."
        }
        val role = auth.currentUser?.getIdToken(true)?.await()?.claims?.get("role")
        if (role != "livreur") {
            auth.signOut()
            return "Ce compte n'est pas un compte livreur. Contactez l'administration."
        }
        return null
    }

    fun deconnecter() = auth.signOut()
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
