package com.benjo.fboplus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.benjo.fboplus.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

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

    init {
        chargerCommandesDuJour()
        verifierFinDeMois()
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

    // ── Valider la commande en cours ──
    // grade : le grade actuel du profil, utilisé pour figer le % de réduction
    // au moment de la commande (l'historique reste juste même si le grade change plus tard).
    fun validerCommande(grade: String) {
        if (_commandeEnCours.value.isEmpty()) return
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        val fmtMois = SimpleDateFormat("MM/yyyy", Locale.FRANCE)
        val now = Date()
        val commande = Commande(
            titre = _titreCommande.value.ifBlank { "Commande" },
            lignes = _commandeEnCours.value,
            dateMillis = now.time,
            dateStr = fmt.format(now),
            moisAnnee = fmtMois.format(now),
            reductionPourcentage = reductionPourGrade(grade)
        )
        VentesRepository.sauvegarderCommande(ctx, commande)
        _commandeEnCours.value = emptyList()
        _titreCommande.value = ""
        chargerCommandesDuJour()
    }

    fun annulerCommande() {
        _commandeEnCours.value = emptyList()
        _titreCommande.value = ""
    }

    fun supprimerCommande(id: Long) {
        VentesRepository.supprimerCommande(ctx, id)
        chargerCommandesDuJour()
    }

    // ── Charger une commande pour modification ──
    fun chargerCommandePourModification(commande: Commande) {
        _commandeEnCours.value = commande.lignes.toMutableList()
        _titreCommande.value = commande.titre
        _commandeEnEdition.value = commande.id
    }

    // ── Mettre à jour une commande existante ──
    // grade : optionnel — si fourni, la réduction est recalculée sur le grade actuel ;
    // sinon la réduction d'origine de la commande est conservée.
    fun mettreAJourCommande(grade: String? = null) {
        val id = _commandeEnEdition.value ?: return
        if (_commandeEnCours.value.isEmpty()) return
        val ancienne = VentesRepository.getToutesCommandes(ctx).find { it.id == id } ?: return
        val commandeMaj = ancienne.copy(
            titre = _titreCommande.value.ifBlank { "Commande" },
            lignes = _commandeEnCours.value,
            reductionPourcentage = grade?.let { reductionPourGrade(it) } ?: ancienne.reductionPourcentage
        )
        VentesRepository.supprimerCommande(ctx, id)
        VentesRepository.sauvegarderCommande(ctx, commandeMaj)
        _commandeEnCours.value = emptyList()
        _titreCommande.value = ""
        _commandeEnEdition.value = null
        chargerCommandesDuJour()
    }

    fun annulerModification() {
        _commandeEnCours.value = emptyList()
        _titreCommande.value = ""
        _commandeEnEdition.value = null
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
}