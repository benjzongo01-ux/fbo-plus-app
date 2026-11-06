package com.benjo.fboplus.data

import android.app.Activity
import com.benjo.fboplus.REGION_FUNCTIONS
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface EnvoiCode {
    data class CodeEnvoye(
        val verificationId: String,
        val jetonRenvoi: PhoneAuthProvider.ForceResendingToken
    ) : EnvoiCode

    /** Android a lu le SMS lui-même : aucun code à saisir. */
    data class VerifieAutomatiquement(val identifiant: PhoneAuthCredential) : EnvoiCode
}

data class ProfilSession(val role: String, val aPin: Boolean)

data class ResultatPin(val valide: Boolean, val essaisRestants: Int)

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(REGION_FUNCTIONS)
) {

    fun estConnecte(): Boolean = auth.currentUser != null

    suspend fun envoyerCode(
        activity: Activity,
        telephone: String,
        jetonRenvoi: PhoneAuthProvider.ForceResendingToken? = null
    ): EnvoiCode = suspendCancellableCoroutine { suite ->
        val rappels = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(identifiant: PhoneAuthCredential) {
                if (suite.isActive) suite.resume(EnvoiCode.VerifieAutomatiquement(identifiant))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                if (suite.isActive) suite.resumeWithException(e)
            }

            override fun onCodeSent(
                verificationId: String,
                jeton: PhoneAuthProvider.ForceResendingToken
            ) {
                if (suite.isActive) suite.resume(EnvoiCode.CodeEnvoye(verificationId, jeton))
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(telephone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(rappels)
        jetonRenvoi?.let(options::setForceResendingToken)
        PhoneAuthProvider.verifyPhoneNumber(options.build())
    }

    fun identifiantDepuisCode(verificationId: String, code: String): PhoneAuthCredential =
        PhoneAuthProvider.getCredential(verificationId, code)

    /** Retourne true si ce numéro vient de créer un compte. */
    suspend fun connecter(identifiant: PhoneAuthCredential): Boolean =
        auth.signInWithCredential(identifiant).await().additionalUserInfo?.isNewUser == true

    suspend fun supprimerCompteCourant() {
        auth.currentUser?.delete()?.await()
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
