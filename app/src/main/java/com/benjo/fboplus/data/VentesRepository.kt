package com.benjo.fboplus.data

import android.content.Context
import android.util.Log
import com.benjo.fboplus.REGION_FUNCTIONS
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// ── Résumé CA / CC / Bénéfice pour un mois donné ──
data class StatMoisDetail(
    val moisAnnee: String,
    val ca: Int,
    val cc: Double,
    val benefice: Int
)

object VentesRepository {

    private const val PREFS_NAME = "fboplus_ventes"
    private const val KEY_COMMANDES = "toutes_commandes"
    private const val KEY_MOIS_CLOS = "mois_clos"
    private const val KEY_IMPORT = "import_termine_"
    private const val KEY_DECISION = "import_decision_"
    private val gson = Gson()
    private val db get() = FirebaseFirestore.getInstance()
    private val functions get() = FirebaseFunctions.getInstance(REGION_FUNCTIONS)
    private val portee = CoroutineScope(Dispatchers.IO)

    private val STATUTS_ABANDONNES = setOf("annulee", "retournee")

    // Toutes les commandes du compte, y compris annulées, à jour en temps réel.
    private val _commandes = MutableStateFlow<List<Commande>>(emptyList())
    val commandes: StateFlow<List<Commande>> = _commandes

    // Ventes du téléphone pas encore copiées sur le serveur : elles restent visibles entre-temps.
    private var enAttenteImport: List<Commande> = emptyList()
    private var distantes: List<Commande> = emptyList()
    private var ecoute: ListenerRegistration? = null
    private var uidActuel: String? = null

    // Nombre de ventes locales proposées à l'import ; null quand il n'y a rien à décider.
    private val _propositionImport = MutableStateFlow<Int?>(null)
    val propositionImport: StateFlow<Int?> = _propositionImport

    fun demarrer(context: Context, uid: String) {
        ecoute?.remove()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val termine = prefs.getBoolean(KEY_IMPORT + uid, false)
        val decision = prefs.getString(KEY_DECISION + uid, null)
        val locales = if (termine) emptyList() else lireVentesLocales(context)
        val profilLocal = ProfilRepository.getProfil(context).prenom.isNotBlank()

        uidActuel = uid
        enAttenteImport = if (decision == "oui") locales else emptyList()
        // Le téléphone a pu servir à quelqu'un d'autre : on ne rattache rien sans accord explicite.
        _propositionImport.value =
            if (!termine && decision == null && (locales.isNotEmpty() || profilLocal)) locales.size else null
        publier()

        ecoute = db.collection("orders")
            .whereEqualTo("fboId", uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { docs, erreur ->
                if (erreur != null) Log.w("VentesRepository", "Écoute des commandes refusée", erreur)
                if (docs == null) return@addSnapshotListener
                distantes = docs.documents.map(::versCommande)
                publier()
            }
        importerVentesLocales(context, uid)
    }

    fun repondreImport(context: Context, accepte: Boolean) {
        val uid = uidActuel ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DECISION + uid, if (accepte) "oui" else "non").apply()
        _propositionImport.value = null
        if (accepte) {
            ProfilRepository.envoyerProfilLocal(context)
            enAttenteImport = lireVentesLocales(context)
            publier()
            importerVentesLocales(context, uid)
        } else {
            ProfilRepository.oublierProfilAffiche()
        }
    }

    fun arreter() {
        ecoute?.remove()
        ecoute = null
        uidActuel = null
        _propositionImport.value = null
        distantes = emptyList()
        enAttenteImport = emptyList()
        _commandes.value = emptyList()
    }

    private fun publier() {
        _commandes.value = enAttenteImport + distantes
    }

    /** Copie sans jamais effacer : les ventes locales restent sur le téléphone. */
    private fun importerVentesLocales(context: Context, uid: String) {
        if (enAttenteImport.isEmpty()) return
        val ventes = enAttenteImport.map { c ->
            mapOf(
                "id" to c.id.toString(),
                "titre" to c.titre,
                "dateMillis" to c.dateMillis,
                "reductionPourcentage" to c.reductionPourcentage,
                "lignes" to c.lignes.map { l ->
                    mapOf(
                        "produitRef" to l.produitRef,
                        "produitNom" to l.produitNom,
                        "quantite" to l.quantite,
                        "prixUnitaire" to l.prixUnitaire,
                        "ccUnitaire" to l.ccUnitaire
                    )
                }
            )
        }
        portee.launch {
            try {
                functions.getHttpsCallable("importerVentes").call(mapOf("ventes" to ventes)).await()
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_IMPORT + uid, true).apply()
                enAttenteImport = emptyList()
                publier()
            } catch (e: Exception) {
                Log.w("VentesRepository", "Import reporté à la prochaine ouverture", e)
            }
        }
    }

    suspend fun creerCommande(
        cle: String,
        lignes: List<LigneCommande>,
        titre: String,
        details: com.benjo.fboplus.viewmodel.DetailsCommande,
        reduction: Int
    ) {
        functions.getHttpsCallable("creerCommande").call(
            mapOf(
                "cleIdempotence" to cle,
                "lignes" to enLignes(lignes),
                "titre" to titre,
                "pays" to details.pays,
                "pourQui" to details.pourQui,
                "filleulNom" to details.filleulNom.trim(),
                "filleulPrenom" to details.filleulPrenom.trim(),
                "filleulId" to details.filleulId.trim(),
                "mode" to details.mode,
                "clientId" to (details.clientId ?: ""),
                "clientNom" to details.clientNom.trim(),
                "clientTelephone" to details.clientTelephone.trim(),
                "clientTelephone2" to details.clientTelephone2.trim(),
                "clientQuartier" to details.quartier.trim(),
                "adresse" to details.adresse.trim(),
                "note" to details.note.trim(),
                "villeExpedition" to details.villeSaisie.trim(),
                "gare" to details.gare.trim(),

                "destinataireNom" to details.clientNom.trim(),
                "destinataireTelephone" to details.clientTelephone.trim(),
                "destinataireTelephone2" to details.clientTelephone2.trim(),
                "compagnie" to details.compagnie.trim(),
                "reduction" to reduction,
                "clientPaieLivraison" to details.clientPaieLivraison
            )
        ).await()
    }

    /** Frais de livraison du pays (mêmes règles que le serveur : tarifs du pays, sinon tarifs communs). */
    suspend fun fraisLivraison(pays: String): TarifsLivraison {
        val db = FirebaseFirestore.getInstance()
        val propres = db.document("tarifsPays/$pays").get().await()
        val tarifs = if (propres.exists()) propres else db.document("parametres/tarifs").get().await()
        val base = tarifs.getLong("fraisLivraison")?.toInt() ?: 0
        return TarifsLivraison(
            base = base,
            min = tarifs.getLong("fraisMin")?.toInt() ?: base,
            max = tarifs.getLong("fraisMax")?.toInt() ?: base
        )
    }

    /** Zones de livraison du pays (communes, quartiers) avec leur tarif. */
    suspend fun zonesLivraison(pays: String): List<VilleExpedition> =
        FirebaseFirestore.getInstance().collection("zonesLivraison")
            .whereEqualTo("pays", pays)
            .whereEqualTo("actif", true)
            .get().await()
            .documents
            .map { VilleExpedition(it.id, it.getString("nom").orEmpty(), it.getLong("frais")?.toInt() ?: 0) }
            .sortedBy { it.frais * 1000 + it.nom.length }

    /** Villes où l'administration du pays accepte d'expédier, avec leurs frais. */
    suspend fun villesExpedition(pays: String): List<VilleExpedition> =
        FirebaseFirestore.getInstance().collection("villesExpedition")
            .whereEqualTo("pays", pays)
            .whereEqualTo("actif", true)
            .get().await()
            .documents
            .map { VilleExpedition(it.id, it.getString("ville").orEmpty(), it.getLong("frais")?.toInt() ?: 0) }
            .sortedBy { it.nom }

    suspend fun modifierCommande(
        docId: String,
        lignes: List<LigneCommande>,
        titre: String,
        adresse: String,
        reduction: Int
    ) {
        functions.getHttpsCallable("modifierCommande").call(
            mapOf(
                "id" to docId,
                "lignes" to enLignes(lignes),
                "titre" to titre,
                "adresse" to adresse,
                "reduction" to reduction
            )
        ).await()
    }

    suspend fun annulerCommande(docId: String, motif: String) {
        functions.getHttpsCallable("annulerCommande")
            .call(mapOf("id" to docId, "motif" to motif)).await()
    }

    private fun enLignes(lignes: List<LigneCommande>) =
        lignes.map { mapOf("produitId" to it.produitRef, "quantite" to it.quantite) }

    @Suppress("UNCHECKED_CAST")
    private fun versCommande(doc: DocumentSnapshot): Commande {
        val date = doc.getTimestamp("createdAt")?.toDate() ?: Date()
        val lignes = (doc.get("lignes") as? List<Map<String, Any?>>).orEmpty().map { l ->
            LigneCommande(
                produitRef = l["produitId"] as? String ?: "",
                produitNom = l["nom"] as? String ?: "",
                quantite = (l["quantite"] as? Number)?.toInt() ?: 0,
                prixUnitaire = (l["prixUnitaire"] as? Number)?.toInt() ?: 0,
                ccUnitaire = (l["cc"] as? Number)?.toDouble() ?: 0.0
            )
        }
        val historique = (doc.get("historiqueStatuts") as? List<Map<String, Any?>>).orEmpty().map { e ->
            EtapeCommande(
                statut = e["statut"] as? String ?: "",
                dateMillis = (e["date"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
            )
        }
        val client = doc.get("client") as? Map<String, Any?>
        val beneficiaire = doc.get("beneficiaire") as? Map<String, Any?>
        val expedition = doc.get("expedition") as? Map<String, Any?>
        return Commande(
            id = doc.id.hashCode().toLong(),
            titre = doc.getString("titre").orEmpty(),
            lignes = lignes,
            dateMillis = date.time,
            dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(date),
            moisAnnee = SimpleDateFormat("MM/yyyy", Locale.FRANCE).format(date),
            reductionPourcentage = doc.getDouble("remiseGrade") ?: 0.0,
            docId = doc.id,
            statut = doc.getString("statut").orEmpty(),
            clientId = client?.get("id") as? String,
            clientNom = client?.get("nom") as? String ?: "",
            adresse = doc.getString("adresse").orEmpty(),
            reduction = doc.getLong("reduction")?.toInt() ?: 0,
            fraisLivraison = doc.getLong("fraisLivraison")?.toInt() ?: 0,
            total = doc.getLong("total")?.toInt() ?: 0,
            historique = historique,
            motif = doc.getString("motifAnnulation") ?: doc.getString("motifRetour").orEmpty(),
            pays = doc.getString("pays").orEmpty(),
            mode = doc.getString("mode").orEmpty(),
            pourQui = beneficiaire?.get("type") as? String ?: "",
            beneficiaireNom = listOf(beneficiaire?.get("prenom"), beneficiaire?.get("nom"))
                .mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }.joinToString(" "),
            beneficiaireId = beneficiaire?.get("idForever") as? String ?: "",
            clientTelephone = client?.get("telephone") as? String ?: "",
            clientTelephone2 = client?.get("telephone2") as? String ?: "",
            villeExpedition = expedition?.get("ville") as? String ?: "",
            compagnie = expedition?.get("compagnie") as? String ?: "",
            numeroColis = expedition?.get("numeroColis") as? String ?: ""
        )
    }

    private fun lireVentesLocales(context: Context): List<Commande> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COMMANDES, null) ?: return emptyList()
        val type = object : TypeToken<List<Commande>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    private fun dateAujourdhui(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
    }

    fun moisAnneeAujourdhui(): String {
        return SimpleDateFormat("MM/yyyy", Locale.FRANCE).format(Date())
    }

    // ── Sauvegarder une commande ──
    // ── Ventes comptées dans les statistiques : ni annulées, ni retournées ──
    fun getToutesCommandes(context: Context): List<Commande> =
        _commandes.value.filter { it.statut !in STATUTS_ABANDONNES }

    // ── Commandes du jour ──
    fun getCommandesDuJour(context: Context): List<Commande> {
        val aujourd = dateAujourdhui()
        return getToutesCommandes(context).filter { it.dateStr == aujourd }
    }

    // ── Commandes d'un mois ──
    fun getCommandesDuMois(context: Context, moisAnnee: String): List<Commande> {
        return getToutesCommandes(context).filter { it.moisAnnee == moisAnnee }
    }

    // ── Mois clos ──
    fun getMoisClos(context: Context): List<String> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MOIS_CLOS, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    fun cloreMois(context: Context, moisAnnee: String) {
        val moisClos = getMoisClos(context).toMutableList()
        if (!moisClos.contains(moisAnnee)) {
            moisClos.add(moisAnnee)
            val json = gson.toJson(moisClos)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_MOIS_CLOS, json).apply()
        }
    }

    // ── Stats par période (existant, inchangé) ──
    fun getStatsSemaine(context: Context): List<Pair<String, Int>> {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        val toutes = getToutesCommandes(context)
        return (6 downTo 0).map { i ->
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = fmt.format(cal.time)
            val ca = toutes.filter { it.dateStr == dateStr }.sumOf { it.caTotal }
            dateStr.substring(0, 5) to ca
        }
    }

    fun getStatsMois(context: Context, nbMois: Int): List<Pair<String, Int>> {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("MM/yyyy", Locale.FRANCE)
        val toutes = getToutesCommandes(context)
        return (nbMois - 1 downTo 0).map { i ->
            cal.time = Date()
            cal.add(Calendar.MONTH, -i)
            val moisStr = fmt.format(cal.time)
            val ca = toutes.filter { it.moisAnnee == moisStr }.sumOf { it.caTotal }
            moisStr to ca
        }
    }

    fun getStatsDepuisDebut(context: Context): List<Pair<String, Int>> {
        val toutes = getToutesCommandes(context)
        return toutes
            .groupBy { it.moisAnnee }
            .map { (mois, cmds) -> mois to cmds.sumOf { it.caTotal } }
            .sortedBy { it.first }
    }

    // ── NOUVEAU : CA / CC / Bénéfice du mois en cours (pour écran Profil) ──
    fun getStatMoisActuel(context: Context): StatMoisDetail {
        val mois = moisAnneeAujourdhui()
        val commandesDuMois = getCommandesDuMois(context, mois)
        return StatMoisDetail(
            moisAnnee = mois,
            ca = commandesDuMois.sumOf { it.caTotal },
            cc = commandesDuMois.sumOf { it.ccTotal },
            benefice = commandesDuMois.sumOf { it.beneficeTotal }
        )
    }

    // ── NOUVEAU : CA / CC / Bénéfice pour un mois précis ──
    fun getStatMoisDetail(context: Context, moisAnnee: String): StatMoisDetail {
        val commandesDuMois = getCommandesDuMois(context, moisAnnee)
        return StatMoisDetail(
            moisAnnee = moisAnnee,
            ca = commandesDuMois.sumOf { it.caTotal },
            cc = commandesDuMois.sumOf { it.ccTotal },
            benefice = commandesDuMois.sumOf { it.beneficeTotal }
        )
    }

    // ── NOUVEAU : CA / CC / Bénéfice mois par mois, depuis la toute première commande ──
    // Utilisé pour l'écran Historique : liste complète, du plus récent au plus ancien.
    fun getHistoriqueMensuel(context: Context): List<StatMoisDetail> {
        val toutes = getToutesCommandes(context)
        return toutes
            .groupBy { it.moisAnnee }
            .map { (mois, cmds) ->
                StatMoisDetail(
                    moisAnnee = mois,
                    ca = cmds.sumOf { it.caTotal },
                    cc = cmds.sumOf { it.ccTotal },
                    benefice = cmds.sumOf { it.beneficeTotal }
                )
            }
            .sortedByDescending { it.moisAnnee.takeLast(4) + it.moisAnnee.take(2) } // tri année puis mois, plus récent d'abord
    }
}