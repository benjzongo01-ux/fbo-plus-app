package com.benjo.fboplus.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    private val gson = Gson()

    private fun dateAujourdhui(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
    }

    fun moisAnneeAujourdhui(): String {
        return SimpleDateFormat("MM/yyyy", Locale.FRANCE).format(Date())
    }

    // ── Sauvegarder une commande ──
    fun sauvegarderCommande(context: Context, commande: Commande) {
        val toutes = getToutesCommandes(context).toMutableList()
        toutes.add(commande)
        val json = gson.toJson(toutes)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_COMMANDES, json).apply()
    }

    // ── Récupérer toutes les commandes ──
    fun getToutesCommandes(context: Context): List<Commande> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COMMANDES, null) ?: return emptyList()
        val type = object : TypeToken<List<Commande>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    // ── Commandes du jour ──
    fun getCommandesDuJour(context: Context): List<Commande> {
        val aujourd = dateAujourdhui()
        return getToutesCommandes(context).filter { it.dateStr == aujourd }
    }

    // ── Commandes d'un mois ──
    fun getCommandesDuMois(context: Context, moisAnnee: String): List<Commande> {
        return getToutesCommandes(context).filter { it.moisAnnee == moisAnnee }
    }

    // ── Supprimer une commande ──
    fun supprimerCommande(context: Context, id: Long) {
        val toutes = getToutesCommandes(context).filter { it.id != id }
        val json = gson.toJson(toutes)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_COMMANDES, json).apply()
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