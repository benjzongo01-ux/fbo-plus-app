package com.benjo.fboplus.viewmodel

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benjo.fboplus.auth.SecureTokenStorage
import com.benjo.fboplus.data.AuthRepository
import com.benjo.fboplus.data.EnvoiCode
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
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
    data class CodeSent(val telephone: String) : AuthUiState()
    object NeedsPinCreation : AuthUiState()
    object NeedsPinEntry : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository()
    private val tokenStorage = SecureTokenStorage(application)

    private var verificationId: String? = null
    private var jetonRenvoi: PhoneAuthProvider.ForceResendingToken? = null
    private var inscription = false
    private var numeroVerifie = false

    // Le serveur accepte un nouveau PIN sans l'ancien pendant 5 minutes après un code SMS.
    private var smsVerifieA = 0L

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun isLoggedIn(): Boolean = repository.estConnecte() && tokenStorage.sessionOuverte()

    /** Inscription et connexion partagent le même envoi de code SMS. */
    fun envoyerCode(activity: Activity, telephone: String, inscription: Boolean) {
        this.inscription = inscription
        numeroVerifie = false
        executer {
            traiterEnvoi(repository.envoyerCode(activity, telephone), telephone)
        }
    }

    fun renvoyerCode(activity: Activity, telephone: String) {
        viewModelScope.launch {
            try {
                when (val envoi = repository.envoyerCode(activity, telephone, jetonRenvoi)) {
                    is EnvoiCode.CodeEnvoye -> memoriser(envoi)
                    is EnvoiCode.VerifieAutomatiquement -> terminerConnexion(envoi.identifiant)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(messagePour(e))
            }
        }
    }

    fun verifierCode(code: String) {
        val id = verificationId
        if (id == null) {
            _uiState.value = AuthUiState.Error("Code expiré. Demandez un nouveau code.")
            return
        }
        executer {
            // Un code SMS ne sert qu'une fois : après un échec du profil, on ne reprend que le profil.
            if (numeroVerifie) chargerProfil()
            else terminerConnexion(repository.identifiantDepuisCode(id, code))
        }
    }

    /** Vrai si le code SMS vient d'être vérifié : on peut alors choisir un nouveau PIN. */
    fun smsRecent(): Boolean = System.currentTimeMillis() - smsVerifieA < 4 * 60 * 1000L

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

    private fun traiterEnvoi(envoi: EnvoiCode, telephone: String) {
        when (envoi) {
            is EnvoiCode.CodeEnvoye -> {
                memoriser(envoi)
                _uiState.value = AuthUiState.CodeSent(telephone)
            }
            is EnvoiCode.VerifieAutomatiquement -> executer { terminerConnexion(envoi.identifiant) }
        }
    }

    private fun memoriser(envoi: EnvoiCode.CodeEnvoye) {
        verificationId = envoi.verificationId
        jetonRenvoi = envoi.jetonRenvoi
    }

    private suspend fun terminerConnexion(identifiant: PhoneAuthCredential) {
        val nouveauCompte = repository.connecter(identifiant)
        // L'écran Connexion ne recueille pas l'accord aux conditions d'utilisation.
        if (nouveauCompte && !inscription) {
            repository.supprimerCompteCourant()
            _uiState.value = AuthUiState.Error("Aucun compte trouvé avec ce numéro.")
            return
        }
        numeroVerifie = true
        smsVerifieA = System.currentTimeMillis()
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
        is FirebaseAuthInvalidCredentialsException -> "Code ou numéro incorrect."
        is FirebaseTooManyRequestsException -> "Trop de tentatives. Réessayez plus tard."
        is FirebaseFunctionsException -> when (e.code) {
            // Limite de requêtes : le serveur dit combien de secondes attendre. Sinon, PIN bloqué.
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                e.message?.takeIf { it.contains("Réessayez dans") } ?: MESSAGE_PIN_BLOQUE
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> "Code PIN incorrect."
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
