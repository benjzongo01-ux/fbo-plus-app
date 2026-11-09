package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.ui.theme.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EtatMaintenance(val actif: Boolean, val message: String, val finPrevue: Date?)

/**
 * Affiche l'écran d'attente à la place de toute l'application pendant une maintenance décidée
 * par le super administrateur. L'application reprend d'elle-même dès la fin de la maintenance.
 */
@Composable
fun AvecMaintenance(contenu: @Composable () -> Unit) {
    var etat by remember { mutableStateOf<EtatMaintenance?>(null) }
    DisposableEffect(Unit) {
        val ecoute = FirebaseFirestore.getInstance().document("parametres/maintenance")
            .addSnapshotListener { doc, _ ->
                etat = EtatMaintenance(
                    actif = doc?.getBoolean("actif") == true,
                    message = doc?.getString("message").orEmpty(),
                    finPrevue = (doc?.get("finPrevue") as? Timestamp)?.toDate()
                )
            }
        onDispose { ecoute.remove() }
    }

    val e = etat
    if (e?.actif == true) EcranMaintenance(e) else contenu()
}

/** « vers 15 h 30 (dans environ 45 min) », ou « très bientôt » si l'heure est inconnue. */
fun retourPrevu(fin: Date?, maintenant: Date = Date()): String {
    if (fin == null) return "très bientôt"
    val minutes = ((fin.time - maintenant.time) / 60_000).toInt()
    if (minutes <= 0) return "dans quelques instants"
    val heure = SimpleDateFormat("HH 'h' mm", Locale.FRANCE).format(fin)
    val memeJour = SimpleDateFormat("yyyyMMdd", Locale.FRANCE).let { it.format(fin) == it.format(maintenant) }
    val jour = if (memeJour) "" else " le " + SimpleDateFormat("dd/MM", Locale.FRANCE).format(fin)
    val duree = if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"
    return "vers $heure$jour (dans environ $duree)"
}

@Composable
private fun EcranMaintenance(etat: EtatMaintenance) {
    // Recalcule « dans environ … » toutes les 30 secondes.
    var maintenant by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            maintenant = Date()
        }
    }

    Column(
        Modifier.fillMaxSize().background(BlackDeep).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("FBO+", color = GoldPrimary, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(20.dp))
        Text("Maintenance en cours", color = WhitePrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(etat.message, color = WhiteSub, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            "Retour prévu ${retourPrevu(etat.finPrevue, maintenant)}.",
            color = GoldPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Nous vous prions de nous excuser pour la gêne occasionnée. Vos ventes et vos données sont en sécurité. " +
                "L'application se rouvrira d'elle-même dès la fin de la maintenance.",
            color = WhiteSub,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}
