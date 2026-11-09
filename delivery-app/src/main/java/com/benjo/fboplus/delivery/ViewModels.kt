package com.benjo.fboplus.delivery

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private fun messagePour(e: Exception): String = when {
    e is FirebaseNetworkException -> "Connexion impossible. Réessayez."
    e is FirebaseAuthUserCollisionException -> "Cette adresse a déjà un mot de passe : connectez-vous, ou utilisez « Mot de passe oublié »."
    e is FirebaseAuthInvalidCredentialsException && e.errorCode == "ERROR_WEAK_PASSWORD" ->
        "Mot de passe trop faible : 8 caractères au moins."
    e is FirebaseAuthInvalidCredentialsException || e is FirebaseAuthInvalidUserException -> "Email ou mot de passe incorrect."
    e is FirebaseTooManyRequestsException -> "Trop de tentatives. Réessayez plus tard."
    e is GetCredentialCancellationException -> "Connexion Google annulée."
    e is NoCredentialException -> "Aucun compte Google sur ce téléphone. Utilisez votre email."
    e is GetCredentialException -> "Connexion Google impossible pour le moment. Utilisez votre email."
    e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.UNAVAILABLE ->
        "Connexion impossible. Réessayez."
    e is FirebaseFunctionsException && e.message != null -> e.message!!
    else -> "Une erreur est survenue. Réessayez."
}

sealed interface EtatConnexion {
    data object Saisie : EtatConnexion
    data object Chargement : EtatConnexion
    data object Connecte : EtatConnexion
}

class ConnexionViewModel : ViewModel() {
    private val depot = DepotAuth()

    private val _etat = MutableStateFlow<EtatConnexion>(
        if (depot.estConnecte()) EtatConnexion.Connecte else EtatConnexion.Saisie
    )
    val etat: StateFlow<EtatConnexion> = _etat

    private val _erreur = MutableStateFlow<String?>(null)
    val erreur: StateFlow<String?> = _erreur

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

    val googleConfigure get() = depot.googleConfigure
    val googleDeTest get() = depot.googleDeTest

    private fun executer(action: suspend () -> Unit) {
        _erreur.value = null
        _info.value = null
        _etat.value = EtatConnexion.Chargement
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Connexion", "Échec", e)
                depot.deconnecter()
                _erreur.value = messagePour(e)
                _etat.value = EtatConnexion.Saisie
            }
        }
    }

    private suspend fun terminer() {
        val refus = depot.verifierLivreur()
        if (refus == null) {
            _etat.value = EtatConnexion.Connecte
        } else {
            _erreur.value = refus
            _etat.value = EtatConnexion.Saisie
        }
    }

    fun connecterGoogle(activity: Activity) = executer { depot.connecterGoogle(activity); terminer() }
    fun connecterGoogleDeTest(email: String) = executer { depot.connecterGoogleDeTest(email); terminer() }
    fun connecterEmail(email: String, motDePasse: String) = executer { depot.connecterEmail(email, motDePasse); terminer() }

    fun creerMotDePasse(email: String, motDePasse: String) = executer {
        depot.creerMotDePasse(email, motDePasse)
        _info.value = "Un lien de confirmation a été envoyé à votre adresse. Cliquez dessus, puis connectez-vous."
        _etat.value = EtatConnexion.Saisie
    }

    fun motDePasseOublie(email: String) = executer {
        depot.reinitialiserMotDePasse(email)
        _info.value = "Si cette adresse a un compte, un lien pour choisir un nouveau mot de passe vient d'être envoyé."
        _etat.value = EtatConnexion.Saisie
    }

    fun deconnecter() {
        depot.deconnecter()
        _etat.value = EtatConnexion.Saisie
    }
}

class MissionsViewModel : ViewModel() {
    private val depot = Depot()

    private val _missions = MutableStateFlow<List<Mission>?>(null)
    val missions: StateFlow<List<Mission>?> = _missions

    private val _occupe = MutableStateFlow(false)
    val occupe: StateFlow<Boolean> = _occupe

    private val _erreur = MutableStateFlow<String?>(null)
    val erreur: StateFlow<String?> = _erreur

    init {
        depot.uid?.let { uid ->
            viewModelScope.launch {
                depot.missions(uid).collect { liste ->
                    _missions.value = liste.sortedByDescending { it.dateMillis }
                }
            }
        }
    }

    private fun executer(apres: () -> Unit = {}, action: suspend () -> Unit) {
        if (_occupe.value) return
        _occupe.value = true
        _erreur.value = null
        viewModelScope.launch {
            try {
                action()
                apres()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Missions", "Échec", e)
                _erreur.value = messagePour(e)
            } finally {
                _occupe.value = false
            }
        }
    }

    fun demarrer(id: String) = executer { depot.demarrer(id) }

    fun signalerRetour(id: String, motif: String, apres: () -> Unit) =
        executer(apres) { depot.signalerRetour(id, motif) }

    fun terminer(id: String, montant: Int, photo: () -> ByteArray, signature: ByteArray, apres: () -> Unit) =
        executer(apres) { depot.terminer(id, montant, photo(), signature) }

    fun enregistrerPosition(id: String, lat: Double, lng: Double) =
        depot.enregistrerPosition(id, lat, lng)
}
