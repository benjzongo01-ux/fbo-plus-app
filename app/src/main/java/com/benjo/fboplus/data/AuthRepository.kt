package com.benjo.fboplus.data

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.benjo.fboplus.BuildConfig
import com.benjo.fboplus.REGION_FUNCTIONS
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

data class ProfilSession(val role: String, val aPin: Boolean)

data class ResultatPin(val valide: Boolean, val essaisRestants: Int)

/**
 * Connexion par Google (recommandée : gratuite, en un toucher) ou par email et mot de passe.
 * Plus de SMS : chaque SMS est payant, une connexion Google ou email ne coûte rien.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(REGION_FUNCTIONS)
) {

    fun estConnecte(): Boolean = auth.currentUser != null

    /** Vrai quand la connexion Google réelle est configurée (identifiant web du projet Firebase). */
    val googleConfigure: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** Sur les émulateurs, sans configuration Google, un compte Google fictif sert aux essais. */
    val googleDeTest: Boolean get() = !googleConfigure && BuildConfig.EMULATEUR_FIREBASE

    /** Ouvre le sélecteur de comptes Google d'Android, puis connecte à Firebase. */
    suspend fun connecterGoogle(activity: Activity) {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val demande = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val reponse = CredentialManager.create(activity).getCredential(activity, demande)
        val identifiant = reponse.credential
        require(identifiant is CustomCredential && identifiant.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Réponse Google inattendue."
        }
        val jeton = GoogleIdTokenCredential.createFrom(identifiant.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(jeton, null)).await()
    }

    /** Émulateurs uniquement : l'émulateur d'authentification accepte un jeton Google non signé. */
    suspend fun connecterGoogleDeTest(email: String) {
        check(BuildConfig.EMULATEUR_FIREBASE) { "Réservé aux émulateurs." }
        val jeton = JSONObject()
            .put("sub", "test-" + email.lowercase())
            .put("email", email.lowercase())
            .put("email_verified", true)
            .put("name", email.substringBefore('@'))
            .toString()
        auth.signInWithCredential(GoogleAuthProvider.getCredential(jeton, null)).await()
    }

    suspend fun connecterEmail(email: String, motDePasse: String) {
        auth.signInWithEmailAndPassword(email.trim(), motDePasse).await()
    }

    /** Crée le compte et envoie le lien de confirmation de l'adresse. */
    suspend fun creerCompteEmail(email: String, motDePasse: String) {
        val compte = auth.createUserWithEmailAndPassword(email.trim(), motDePasse).await().user
        runCatching { compte?.sendEmailVerification()?.await() }
    }

    suspend fun reinitialiserMotDePasse(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun deconnecter() = auth.signOut()

    suspend fun initialiserProfil(): ProfilSession {
        val data = appeler("initialiserProfil", emptyMap())
        // Récupère le rôle que la fonction vient de placer dans les custom claims.
        auth.currentUser?.getIdToken(true)?.await()
        return ProfilSession(role = data["role"] as String, aPin = data["aPin"] as Boolean)
    }

    suspend fun definirPin(pin: String, pinActuel: String? = null) {
        val donnees = buildMap {
            put("pin", pin)
            pinActuel?.let { put("pinActuel", it) }
        }
        appeler("definirPin", donnees)
    }

    suspend fun verifierPin(pin: String): ResultatPin {
        val data = appeler("verifierPin", mapOf("pin" to pin))
        return ResultatPin(
            valide = data["valide"] as Boolean,
            essaisRestants = (data["essaisRestants"] as Number).toInt()
        )
    }

    private suspend fun appeler(nom: String, donnees: Map<String, Any>): Map<*, *> =
        functions.getHttpsCallable(nom).call(donnees).await().getData() as Map<*, *>
}
