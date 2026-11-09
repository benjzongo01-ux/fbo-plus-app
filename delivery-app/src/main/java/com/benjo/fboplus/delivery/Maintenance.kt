package com.benjo.fboplus.delivery

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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class EtatMaintenance(val actif: Boolean, val message: String, val finPrevue: Date?)

/** Écran d'attente à la place de l'application pendant une maintenance ; reprise automatique à la fin. */
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

private fun retourPrevu(fin: Date?, maintenant: Date): String {
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
    var maintenant by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            maintenant = Date()
        }
    }
    Column(
        Modifier.fillMaxSize().background(Fond).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("FBO+ Delivery", color = Or, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(20.dp))
        Text("Maintenance en cours", color = Texte, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(etat.message, color = TexteDoux, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            "Retour prévu ${retourPrevu(etat.finPrevue, maintenant)}.",
            color = Or,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Merci de votre patience. Vos missions sont conservées : terminez simplement la remise en cours, " +
                "elle pourra être enregistrée dès la fin de la maintenance.",
            color = TexteDoux,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}
