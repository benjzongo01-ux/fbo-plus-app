package com.benjo.fboplus.viewmodel

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benjo.fboplus.auth.SecureTokenStorage
import com.benjo.fboplus.data.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** FBO+ n'accepte que les distributeurs : les autres rôles ont leur propre application. */
private const val ROLE_APPLICATION = "fbo"

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Info(val message: String) : AuthUiState()
    object NeedsPinCreation : AuthUiState()
    object NeedsPinEntry : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository()
    private val tokenStorage = SecureTokenStorage(application)

    // Le serveur accepte un nouveau PIN sans l'ancien pendant 5 minutes après une connexion.
    private var connexionA = 0L

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun isLoggedIn(): Boolean = repository.estConnecte() && tokenStorage.sessionOuverte()

    val googleConfigure: Boolean get() = repository.googleConfigure
    val googleDeTest: Boolean get() = repository.googleDeTest

    fun connecterGoogle(activity: Activity) = executer {
        repository.connecterGoogle(activity)
        apresConnexion()
    }

    /** Émulateurs : compte Google fictif, pour les essais sans configuration Google. */
    fun connecterGoogleDeTest(email: String) = executer {
        repository.connecterGoogleDeTest(email)
        apresConnexion()
    }

    // Connexion par code email : l'adresse en attente du code, et le numéro de l'envoi (relance le décompte).
    private val _emailCode = MutableStateFlow<String?>(null)
    val emailCode: StateFlow<String?> = _emailCode.asStateFlow()
    private val _numeroEnvoi = MutableStateFlow(0)
    val numeroEnvoi: StateFlow<Int> = _numeroEnvoi.asStateFlow()

    fun demanderCode(email: String) = executer {
        val adresse = email.trim().lowercase()
        repository.demanderCodeEmail(adresse)
        _emailCode.value = adresse
        _numeroEnvoi.value++
        _uiState.value = AuthUiState.Idle
    }

    fun verifierCode(code: String) {
        val adresse = _emailCode.value ?: return
        executer {
            repository.verifierCodeEmail(adresse, code)
            _emailCode.value = null
            apresConnexion()
        }
    }

    fun changerEmail() {
        _emailCode.value = null
        resetState()
    }

    /** Vrai juste après une connexion : on peut alors choisir un nouveau PIN sans l'ancien. */
    fun connexionRecente(): Boolean = System.currentTimeMillis() - connexionA < 4 * 60 * 1000L

    fun creerPin(pin: String) {
        executer {
            repository.definirPin(pin)
            ouvrirSession(pin)
        }
    }

    /** Saisie du PIN existant (compte déjà créé, nouvelle connexion) */
    fun verifierPin(pin: String) {
        executer {
            val resultat = repository.verifierPin(pin)
            when {
                resultat.valide -> ouvrirSession(pin)
                resultat.essaisRestants == 0 ->
                    _uiState.value = AuthUiState.Error(MESSAGE_PIN_BLOQUE)
                else -> _uiState.value =
                    AuthUiState.Error("PIN incorrect. Essais restants : ${resultat.essaisRestants}.")
            }
        }
    }

    /** Depuis Sécurité : le serveur reste la référence du PIN. `surFin` reçoit l'erreur éventuelle. */
    fun changerPin(pinActuel: String, nouveauPin: String, surFin: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                repository.definirPin(nouveauPin, pinActuel)
                tokenStorage.savePin(nouveauPin)
                surFin(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                surFin(messagePour(e))
            }
        }
    }

    fun deconnecter() {
        repository.deconnecter()
        tokenStorage.clearAll()
        resetState()
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    private suspend fun apresConnexion() {
        connexionA = System.currentTimeMillis()
        chargerProfil()
    }

    private suspend fun chargerProfil() {
        val profil = repository.initialiserProfil()
        if (profil.role != ROLE_APPLICATION) {
            repository.deconnecter()
            _uiState.value = AuthUiState.Error("Ce compte n'a pas accès à l'application FBO+.")
            return
        }
        _uiState.value = if (profil.aPin) AuthUiState.NeedsPinEntry else AuthUiState.NeedsPinCreation
    }

    private fun ouvrirSession(pin: String) {
        tokenStorage.savePin(pin)
        tokenStorage.ouvrirSession()
        _uiState.value = AuthUiState.Success
    }

    private fun executer(action: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(messagePour(e))
            }
        }
    }

    private fun messagePour(e: Exception): String {
        Log.w("AuthViewModel", "Échec de l'authentification", e)
        return when (e) {
        is FirebaseNetworkException -> "Erreur réseau : impossible de contacter le serveur."
        is FirebaseAuthInvalidCredentialsException -> when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> "Adresse email invalide."
            "ERROR_WEAK_PASSWORD" -> "Mot de passe trop faible : 8 caractères au moins."
            else -> "Email ou mot de passe incorrect."
        }
        is FirebaseAuthInvalidUserException -> "Email ou mot de passe incorrect."
        is FirebaseAuthUserCollisionException -> "Cette adresse a déjà un compte : connectez-vous, ou utilisez « Mot de passe oublié »."
        is GetCredentialCancellationException -> "Connexion Google annulée."
        is NoCredentialException -> "Aucun compte Google sur ce téléphone. Ajoutez-en un dans les réglages, ou utilisez votre email."
        is GetCredentialException -> "Connexion Google impossible pour le moment. Réessayez ou utilisez votre email."
        is FirebaseTooManyRequestsException -> "Trop de tentatives. Réessayez plus tard."
        is FirebaseFunctionsException -> when (e.code) {
            // Limite de requêtes : le serveur dit combien de secondes attendre. Sinon, PIN bloqué.
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                e.message?.takeIf { it.contains("Réessayez dans") } ?: MESSAGE_PIN_BLOQUE
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                e.message?.takeIf { it.startsWith("Code incorrect") || it.contains("suspendu") } ?: "Code PIN incorrect."
            // Code email expiré ou adresse invalide : le serveur explique.
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> e.message ?: "Code invalide."
            // Maintenance, compte à confirmer… : le serveur explique lui-même.
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> e.message ?: "Action impossible pour le moment."
            FirebaseFunctionsException.Code.UNAVAILABLE ->
                "Erreur réseau : impossible de contacter le serveur."
            else -> "Une erreur est survenue. Réessayez."
        }
        else -> "Une erreur est survenue. Réessayez."
        }
    }

    private companion object {
        const val MESSAGE_PIN_BLOQUE = "Trop d'essais. Réessayez dans 15 minutes."
    }
}
