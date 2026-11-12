package com.benjo.fboplus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.benjo.fboplus.data.ProfilRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Alertes d'objectif (remplace l'ancien rappel quotidien de saisie des ventes : les commandes
 * sont maintenant enregistrées toutes seules par le serveur).
 *
 * Chaque soir, le téléphone regarde où en est le mois, mais ne prévient qu'à trois moments :
 * - le 1er du mois, si aucun objectif n'est fixé ;
 * - le 15 et 5 jours avant la fin du mois, si les CC du mois sont en dessous de l'objectif.
 * Pas de notification les autres jours, ni quand l'objectif est atteint.
 */
class RappelWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val profil = ProfilRepository.getProfil(applicationContext)
        val cal = Calendar.getInstance()
        val jour = cal.get(Calendar.DAY_OF_MONTH)
        val dernierJour = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val joursRestants = dernierJour - jour

        if (profil.objectifCC <= 0.0) {
            if (jour == 1) notifier("Nouveau mois, nouvel objectif", "Fixez votre objectif de CC du mois dans votre profil pour suivre votre progression.")
            return Result.success()
        }
        if (jour != 15 && joursRestants != 5) return Result.success()

        val cc = try { ccDuMois(uid) } catch (e: Exception) { return Result.retry() }
        if (cc >= profil.objectifCC) return Result.success()
        val manque = profil.objectifCC - cc
        notifier(
            "Objectif du mois : il reste $joursRestants jours",
            "Vous êtes à ${"%.2f".format(Locale.FRANCE, cc)} CC sur ${"%.2f".format(Locale.FRANCE, profil.objectifCC)}. " +
                "Encore ${"%.2f".format(Locale.FRANCE, manque)} CC pour l'atteindre."
        )
        return Result.success()
    }

    /** CC des commandes du mois en cours (sans les commandes annulées ou retournées). */
    private suspend fun ccDuMois(uid: String): Double {
        val debut = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val docs = FirebaseFirestore.getInstance().collection("orders")
            .whereEqualTo("fboId", uid)
            .whereGreaterThanOrEqualTo("createdAt", Timestamp(debut.time))
            .get().await()
        return docs.documents
            .filter { it.getString("statut") !in setOf("annulee", "retournee") }
            .sumOf { d ->
                (d.get("lignes") as? List<*>).orEmpty().sumOf { l ->
                    val ligne = l as? Map<*, *> ?: return@sumOf 0.0
                    ((ligne["cc"] as? Number)?.toDouble() ?: 0.0) * ((ligne["quantite"] as? Number)?.toInt() ?: 0)
                }
            }
    }

    private fun notifier(titre: String, texte: String) {
        val canal = "fboplus_objectif"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(canal, "Objectif du mois", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Vous prévient à mi-mois et avant la fin du mois si vous êtes en retard sur votre objectif"
                }
            )
        }
        manager.notify(
            1001,
            NotificationCompat.Builder(applicationContext, canal)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(titre)
                .setContentText(texte)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texte))
                .setAutoCancel(true)
                .build()
        )
    }
}

object RappelScheduler {

    private const val NOM = "alertes_objectif"

    /** Active ou coupe les alertes d'objectif (vérification chaque soir vers 19 h). */
    fun appliquer(context: Context, actif: Boolean) {
        val wm = WorkManager.getInstance(context)
        // L'ancien rappel quotidien de saisie n'a plus lieu d'être.
        wm.cancelAllWorkByTag("rappel_ventes")
        if (!actif) {
            wm.cancelUniqueWork(NOM)
            return
        }
        val maintenant = Calendar.getInstance()
        val cible = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 19); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(maintenant)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val requete = PeriodicWorkRequestBuilder<RappelWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(cible.timeInMillis - maintenant.timeInMillis, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(NOM, ExistingPeriodicWorkPolicy.KEEP, requete)
    }
}
