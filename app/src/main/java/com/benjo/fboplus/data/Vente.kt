package com.benjo.fboplus.data

data class LigneCommande(
    val produitRef: String,
    val produitNom: String,
    val quantite: Int,
    val prixUnitaire: Int,
    val ccUnitaire: Double
) {
    val montantTotal: Int get() = prixUnitaire * quantite
    val ccTotal: Double get() = ccUnitaire * quantite
}

// ── Réductions par grade (en %) ──
// Utilisé pour calculer le prix de validation et le bénéfice au moment de la commande.
// IMPORTANT : les chaînes ci-dessous doivent correspondre EXACTEMENT à celles de listeGrades (Profil.kt),
// abréviation entre parenthèses incluse, sinon le when ne matche jamais et retombe sur else -> 0.0.
fun reductionPourGrade(grade: String): Double {
    return when (grade) {
        "Client Privilégié (CP)" -> 5.0
        "Animateur Adjoint (AA)" -> 35.0
        "Animateur (A)" -> 38.0
        "Manager Adjoint (MA)" -> 43.0
        "Manager (M)" -> 48.0
        else -> 0.0
    }
}

data class Commande(
    val id: Long = System.currentTimeMillis(),
    val titre: String = "",
    val lignes: List<LigneCommande> = emptyList(),
    val dateMillis: Long = System.currentTimeMillis(),
    val dateStr: String = "",
    val moisAnnee: String = "",
    // % de réduction du grade au moment de la commande (ex: 38.0 pour Animateur)
    val reductionPourcentage: Double = 0.0,
    // Identifiant Firestore : vide pour une vente locale pas encore importée.
    val docId: String = "",
    val statut: String = "livree",
    val clientId: String? = null,
    val clientNom: String = "",
    val adresse: String = "",
    // Réduction accordée au client, prise sur la marge du distributeur.
    val reduction: Int = 0,
    val fraisLivraison: Int = 0,
    val total: Int = 0,
    val historique: List<EtapeCommande> = emptyList(),
    val motif: String = ""
) {
    // CA = prix public total (somme des lignes au prix public)
    val caTotal: Int get() = lignes.sumOf { it.montantTotal }
    val ccTotal: Double get() = lignes.sumOf { it.ccTotal }

    // Prix de validation = ce que le distributeur paie réellement (CA - réduction du grade)
    val prixValidation: Int get() = (caTotal * (1.0 - reductionPourcentage / 100.0)).toInt()

    // Bénéfice / recette = CA (prix public) - prix de validation
    val beneficeTotal: Int get() = caTotal - prixValidation - reduction

    val modifiable: Boolean get() = docId.isNotEmpty() && statut == "nouvelle"
    val annulable: Boolean get() = docId.isNotEmpty() && statut in setOf("nouvelle", "confirmee")
}

data class EtapeCommande(val statut: String, val dateMillis: Long)

val etapesCommande = listOf(
    "nouvelle", "confirmee", "en_preparation", "prete", "assignee", "en_livraison", "livree"
)

fun libelleStatut(statut: String): String = when (statut) {
    "nouvelle" -> "Nouvelle"
    "confirmee" -> "Confirmée"
    "en_preparation" -> "En préparation"
    "prete" -> "Prête"
    "assignee" -> "Livreur assigné"
    "en_livraison" -> "En livraison"
    "livree" -> "Livrée"
    "annulee" -> "Annulée"
    "retournee" -> "Retournée"
    else -> statut
}

data class JourneeVentes(
    val date: String,
    val commandes: List<Commande> = emptyList()
) {
    val caTotalJour: Int get() = commandes.sumOf { it.caTotal }
    val ccTotalJour: Double get() = commandes.sumOf { it.ccTotal }
    val beneficeTotalJour: Int get() = commandes.sumOf { it.beneficeTotal }
}