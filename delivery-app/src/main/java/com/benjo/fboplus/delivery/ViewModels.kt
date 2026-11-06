package com.benjo.fboplus.delivery

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private fun messagePour(e: Exception): String = when {
    e is FirebaseNetworkException -> "Connexion impossible. Réessayez."
    e is FirebaseAuthInvalidCredentialsException -> "Code ou numéro incorrect."
    e is FirebaseTooManyRequestsException -> "Trop de tentatives. Réessayez plus tard."
    e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.UNAVAILABLE ->
        "Connexion impossible. Réessayez."
    e is FirebaseFunctionsException && e.message != null -> e.message!!
    else -> "Une erreur est survenue. Réessayez."
}

sealed interface EtatConnexion {
    data object Saisie : EtatConnexion
    data object Chargement : EtatConnexion
    data class Code(val id: String) : EtatConnexion
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

    private fun executer(retour: EtatConnexion, action: suspend () -> Unit) {
        _erreur.value = null
        _etat.value = EtatConnexion.Chargement
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Connexion", "Échec", e)
                _erreur.value = messagePour(e)
                _etat.value = retour
            }
        }
    }

    private suspend fun terminer(identifiant: PhoneAuthCredential) {
        if (depot.connecterLivreur(identifiant)) {
            _etat.value = EtatConnexion.Connecte
        } else {
            _erreur.value = "Ce numéro n'a pas de compte livreur. Contactez l'administration."
            _etat.value = EtatConnexion.Saisie
        }
    }

    fun envoyerCode(activity: Activity, telephone: String) = executer(EtatConnexion.Saisie) {
        when (val envoi = depot.envoyerCode(activity, telephone)) {
            is EnvoiCode.Envoye -> _etat.value = EtatConnexion.Code(envoi.id)
            is EnvoiCode.Automatique -> terminer(envoi.identifiant)
        }
    }

    fun verifier(id: String, code: String) = executer(EtatConnexion.Code(id)) {
        terminer(depot.identifiant(id, code))
    }

    fun changerNumero() {
        _erreur.value = null
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
