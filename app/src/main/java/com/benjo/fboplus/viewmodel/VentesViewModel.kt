package com.benjo.fboplus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.functions.FirebaseFunctionsException
import com.benjo.fboplus.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.*

sealed interface EnvoiCommande {
    data object Repos : EnvoiCommande
    data object EnCours : EnvoiCommande
    data object Reussi : EnvoiCommande
    data class Erreur(val message: String) : EnvoiCommande
}

class VentesViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext

    // ── Commande en cours de construction ──
    private val _commandeEnCours = MutableStateFlow<List<LigneCommande>>(emptyList())
    val commandeEnCours: StateFlow<List<LigneCommande>> = _commandeEnCours

    private val _titreCommande = MutableStateFlow("")
    val titreCommande: StateFlow<String> = _titreCommande

    // ── Commande en édition ──
    private val _commandeEnEdition = MutableStateFlow<Long?>(null)
    val commandeEnEdition: StateFlow<Long?> = _commandeEnEdition

    // ── Commandes du jour ──
    private val _commandesDuJour = MutableStateFlow<List<Commande>>(emptyList())
    val commandesDuJour: StateFlow<List<Commande>> = _commandesDuJour

    // ── Suggestion clôture mois ──
    private val _suggererClotureMois = MutableStateFlow(false)
    val suggererClotureMois: StateFlow<Boolean> = _suggererClotureMois

    private val _client = MutableStateFlow<Client?>(null)
    val client: StateFlow<Client?> = _client

    private val _adresse = MutableStateFlow("")
    val adresse: StateFlow<String> = _adresse

    private val _reduction = MutableStateFlow(0)
    val reduction: StateFlow<Int> = _reduction

    private val _envoi = MutableStateFlow<EnvoiCommande>(EnvoiCommande.Repos)
    val envoi: StateFlow<EnvoiCommande> = _envoi

    // Reste la même tant que la commande n'est pas passée : réessayer ne crée pas de doublon.
    private var cleCommande = UUID.randomUUID().toString()

    init {
        viewModelScope.launch {
            VentesRepository.commandes.collect { chargerCommandesDuJour() }
        }
        verifierFinDeMois()
    }

    fun setClient(client: Client?) {
        _client.value = client
        if (client != null && _adresse.value.isBlank()) _adresse.value = client.adresse
    }

    fun setAdresse(adresse: String) {
        _adresse.value = adresse
    }

    fun setReduction(montant: Int) {
        _reduction.value = montant
    }

    fun reinitialiserEnvoi() {
        _envoi.value = EnvoiCommande.Repos
    }

    private fun viderCommande() {
        _commandeEnCours.value = emptyList()
        _titreCommande.value = ""
        _commandeEnEdition.value = null
        _client.value = null
        _adresse.value = ""
        _reduction.value = 0
    }

    private fun envoyer(action: suspend () -> Unit) {
        if (_envoi.value == EnvoiCommande.EnCours) return
        _envoi.value = EnvoiCommande.EnCours
        viewModelScope.launch {
            _envoi.value = try {
                action()
                EnvoiCommande.Reussi
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("VentesViewModel", "Commande refusée", e)
                EnvoiCommande.Erreur(messagePour(e))
            }
        }
    }

    private fun messagePour(e: Exception): String = when {
        e is FirebaseNetworkException -> MESSAGE_RESEAU
        e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.UNAVAILABLE -> MESSAGE_RESEAU
        e is FirebaseFunctionsException && e.message != null -> e.message!!
        else -> "Une erreur est survenue. Réessayez."
    }

    fun chargerCommandesDuJour() {
        _commandesDuJour.value = VentesRepository.getCommandesDuJour(ctx)
    }

    fun setTitreCommande(titre: String) {
        _titreCommande.value = titre
    }

    fun ajouterProduit(
        produitRef: String,
        produitNom: String,
        prixUnitaire: Int,
        ccUnitaire: Double
    ) {
        val lignes = _commandeEnCours.value.toMutableList()
        val existant = lignes.indexOfFirst { it.produitRef == produitRef }
        if (existant >= 0) {
            val old = lignes[existant]
            lignes[existant] = old.copy(quantite = old.quantite + 1)
        } else {
            lignes.add(
                LigneCommande(
                    produitRef = produitRef,
                    produitNom = produitNom,
                    quantite = 1,
                    prixUnitaire = prixUnitaire,
                    ccUnitaire = ccUnitaire
                )
            )
        }
        _commandeEnCours.value = lignes
    }

    fun ajouterProduitDirect(product: com.benjo.fboplus.data.Product) {
        ajouterProduit(
            produitRef = product.ref,
            produitNom = product.nameFr,
            prixUnitaire = product.prixPublic,
            ccUnitaire = product.cc
        )
    }

    fun retirerLigne(produitRef: String) {
        _commandeEnCours.value = _commandeEnCours.value
            .filter { it.produitRef != produitRef }
    }

    // ── Valider la commande en cours : le serveur calcule les prix et réserve le stock ──
    fun validerCommande() {
        val lignes = _commandeEnCours.value
        if (lignes.isEmpty()) return
        envoyer {
            VentesRepository.creerCommande(
                cle = cleCommande,
                lignes = lignes,
                titre = _titreCommande.value.ifBlank { "Commande" },
                client = _client.value,
                adresse = _adresse.value,
                reduction = _reduction.value
            )
            cleCommande = UUID.randomUUID().toString()
            viderCommande()
        }
    }

    fun annulerCommande() {
        viderCommande()
    }

    fun annulerCommandeServeur(docId: String, motif: String = "") {
        envoyer { VentesRepository.annulerCommande(docId, motif) }
    }

    // ── Charger une commande pour modification ──
    fun chargerCommandePourModification(commande: Commande) {
        _commandeEnCours.value = commande.lignes.toMutableList()
        _titreCommande.value = commande.titre
        _commandeEnEdition.value = commande.id
        _adresse.value = commande.adresse
        _reduction.value = commande.reduction
    }

    // ── Mettre à jour une commande existante ──
    fun mettreAJourCommande() {
        val id = _commandeEnEdition.value ?: return
        val lignes = _commandeEnCours.value
        if (lignes.isEmpty()) return
        val ancienne = VentesRepository.commandes.value.find { it.id == id } ?: return
        envoyer {
            VentesRepository.modifierCommande(
                docId = ancienne.docId,
                lignes = lignes,
                titre = _titreCommande.value.ifBlank { "Commande" },
                adresse = _adresse.value,
                reduction = _reduction.value
            )
            viderCommande()
        }
    }

    fun annulerModification() {
        viderCommande()
    }

    // ── Totaux du jour ──
    val caDuJour: Int get() = _commandesDuJour.value.sumOf { it.caTotal }
    val ccDuJour: Double get() = _commandesDuJour.value.sumOf { it.ccTotal }

    // ── Clôture de mois ──
    fun cloreMois() {
        val moisActuel = VentesRepository.moisAnneeAujourdhui()
        VentesRepository.cloreMois(ctx, moisActuel)
        _suggererClotureMois.value = false
    }

    fun ignorerSuggestionCloture() {
        _suggererClotureMois.value = false
    }

    private fun verifierFinDeMois() {
        val cal = Calendar.getInstance()
        val dernierJour = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val aujourdhui = cal.get(Calendar.DAY_OF_MONTH)
        val moisActuel = VentesRepository.moisAnneeAujourdhui()
        val moisClos = VentesRepository.getMoisClos(ctx)
        if (aujourdhui >= dernierJour - 1 && !moisClos.contains(moisActuel)) {
            _suggererClotureMois.value = true
        }
    }

    // ── Produits les plus commandés ──
    fun getProduitsTries(): List<com.benjo.fboplus.data.Product> {
        val toutes = VentesRepository.getToutesCommandes(ctx)
        val frequences = mutableMapOf<String, Int>()
        toutes.forEach { commande ->
            commande.lignes.forEach { ligne ->
                frequences[ligne.produitRef] = (frequences[ligne.produitRef] ?: 0) + ligne.quantite
            }
        }
        return ProductRepository.allProducts.sortedByDescending { frequences[it.ref] ?: 0 }
    }

    // ── Stats ──
    fun getStatsSemaine() = VentesRepository.getStatsSemaine(ctx)
    fun getStatsMois(nb: Int) = VentesRepository.getStatsMois(ctx, nb)
    fun getStatsDepuisDebut() = VentesRepository.getStatsDepuisDebut(ctx)
    fun getToutesCommandes() = VentesRepository.getToutesCommandes(ctx)

    private companion object {
        const val MESSAGE_RESEAU = "Connexion requise pour envoyer la commande. Réessayez."
    }
}
