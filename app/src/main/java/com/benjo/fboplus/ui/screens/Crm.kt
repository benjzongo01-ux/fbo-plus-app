package com.benjo.fboplus.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.Client
import com.benjo.fboplus.data.ClientsRepository
import com.benjo.fboplus.data.Commande
import com.benjo.fboplus.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Catégories du carnet de clients, calculées à partir des commandes. */
enum class CategorieClient(val libelle: String, val couleur: Color) {
    PROSPECT("Prospect", Color(0xFF6EA8FF)),
    NOUVEAU("Nouveau", Color(0xFFB0B8C8)),
    FIDELE("Fidèle", Color(0xFF00C853)),
    VIP("VIP", Color(0xFFD4AF37))
}

data class SuiviClient(
    val commandes: List<Commande>,
    val chiffreAffaires: Int,
    val dernierAchat: Long?,
    val categorie: CategorieClient,
    val aRelancer: Boolean,
    val raisonRelance: String
)

const val SEUIL_VIP = 150_000
private const val JOURS_SANS_ACHAT = 30
private val INACTIVES = setOf("annulee", "retournee")

private fun chiffres(t: String) = t.filter(Char::isDigit).takeLast(8)

/** Commandes du client : liées à sa fiche, ou passées avec le même numéro de téléphone. */
fun suiviClient(client: Client, toutes: List<Commande>, maintenant: Long = System.currentTimeMillis()): SuiviClient {
    val tel = chiffres(client.telephone)
    val siennes = toutes.filter {
        it.statut !in INACTIVES && (it.clientId == client.id || (tel.length >= 8 && chiffres(it.clientTelephone) == tel))
    }
    val ca = siennes.sumOf { it.total }
    val dernier = siennes.maxOfOrNull { it.dateMillis }
    val categorie = when {
        siennes.isEmpty() -> CategorieClient.PROSPECT
        ca >= SEUIL_VIP -> CategorieClient.VIP
        siennes.size >= 3 -> CategorieClient.FIDELE
        else -> CategorieClient.NOUVEAU
    }
    val relanceDue = client.relanceLe != null && client.relanceLe <= maintenant
    val joursSansAchat = dernier?.let { TimeUnit.MILLISECONDS.toDays(maintenant - it) }
    val inactif = joursSansAchat != null && joursSansAchat >= JOURS_SANS_ACHAT
    return SuiviClient(
        commandes = siennes,
        chiffreAffaires = ca,
        dernierAchat = dernier,
        categorie = categorie,
        aRelancer = relanceDue || inactif,
        raisonRelance = when {
            relanceDue -> client.relanceMotif.ifBlank { "Relance prévue" }
            inactif -> "Aucun achat depuis $joursSansAchat jours"
            else -> ""
        }
    )
}

@Composable
fun BadgeCategorie(categorie: CategorieClient) {
    Text(
        categorie.libelle,
        color = categorie.couleur,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, categorie.couleur, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** Filtres du carnet : tous, à relancer, puis chaque catégorie. */
@Composable
fun FiltresClients(choisi: String, compteARelancer: Int, onChoix: (String) -> Unit) {
    val filtres = listOf("Tous", "À relancer") + CategorieClient.entries.map { it.libelle }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filtres.forEach { f ->
            val actif = f == choisi
            Text(
                if (f == "À relancer" && compteARelancer > 0) "$f ($compteARelancer)" else f,
                color = if (actif) BlackDeep else WhitePrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (actif) GoldPrimary else BlackCard)
                    .clickable { onChoix(f) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

fun garderSelonFiltre(filtre: String, suivi: SuiviClient) = when (filtre) {
    "Tous" -> true
    "À relancer" -> suivi.aRelancer
    else -> suivi.categorie.libelle == filtre
}

fun depuisLe(millis: Long?): String {
    millis ?: return "Aucun achat"
    val jours = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - millis)
    return when (jours) {
        0L -> "Dernier achat aujourd'hui"
        1L -> "Dernier achat hier"
        else -> "Dernier achat il y a $jours jours"
    }
}

/** Relance et fidélisation, dans la fiche du client. */
@Composable
fun SectionRelance(client: Client, suivi: SuiviClient, nomDistributeur: String) {
    val context = LocalContext.current
    var motif by remember(client.id) { mutableStateOf(client.relanceMotif) }
    val dernierProduit = suivi.commandes.maxByOrNull { it.dateMillis }?.lignes?.firstOrNull()?.produitNom
    val message = buildString {
        append("Bonjour ${client.nom.substringBefore(' ').lowercase().replaceFirstChar { it.uppercase() }}, ")
        append("c'est $nomDistributeur, votre distributeur Forever. ")
        if (dernierProduit != null) {
            append("Comment se passe votre ${dernierProduit.lowercase()} ? ")
            append("Pour garder les bienfaits, pensez à renouveler avant la fin de votre cure. ")
        } else {
            append("Je vous propose de découvrir les produits Forever adaptés à vos besoins. ")
        }
        append("Je reste à votre disposition.")
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BlackCard).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Relance et fidélisation", color = GoldPrimary, fontWeight = FontWeight.Bold)
        if (suivi.aRelancer) Text(suivi.raisonRelance, color = GoldPrimary, fontSize = 13.sp)
        client.relanceLe?.let {
            Text("Relance prévue le ${SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(it))}", color = WhiteSub, fontSize = 13.sp)
        }
        OutlinedTextField(
            value = motif,
            onValueChange = { motif = it.take(120) },
            label = { Text("Motif (ex. : fin de cure, promesse d'achat)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 15, 30).forEach { jours ->
                OutlinedButton(onClick = {
                    ClientsRepository.programmerRelance(client.id, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(jours.toLong()), motif)
                }) { Text("$jours j", color = GoldPrimary) }
            }
            if (client.relanceLe != null) {
                TextButton(onClick = { ClientsRepository.programmerRelance(client.id, null, "") }) { Text("Aucune", color = WhiteSub) }
            }
        }
        val numero = client.telephone.filter { it.isDigit() }
        if (numero.isNotBlank()) {
            Button(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$numero?text=${Uri.encode(message)}")))
                    // Relance faite : la prochaine se programme à nouveau si besoin.
                    if (client.relanceLe != null && client.relanceLe <= System.currentTimeMillis()) {
                        ClientsRepository.programmerRelance(client.id, null, "")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = BlackDeep),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Relancer sur WhatsApp", fontWeight = FontWeight.Bold) }
        }
    }
}
