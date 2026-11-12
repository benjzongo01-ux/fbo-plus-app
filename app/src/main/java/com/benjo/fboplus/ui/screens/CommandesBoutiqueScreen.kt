package com.benjo.fboplus.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.LigneCommande
import com.benjo.fboplus.ui.theme.*
import com.benjo.fboplus.viewmodel.VentesViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

/** Commande envoyée par un client depuis la boutique en ligne du distributeur. */
data class CommandeBoutique(
    val id: String,
    val clientNom: String,
    val telephone: String,
    val telephone2: String,
    val quartier: String,
    val adresse: String,
    val note: String,
    val lignes: List<LigneCommande>,
    val total: Int,
    val date: String
)

private val Forme = RoundedCornerShape(16.dp)
private fun fcfa(n: Int) = "%,d FCFA".format(Locale.FRANCE, n)

@Suppress("UNCHECKED_CAST")
private fun depuis(id: String, d: Map<String, Any?>): CommandeBoutique {
    val client = d["client"] as? Map<String, Any?> ?: emptyMap()
    val lignes = (d["lignes"] as? List<Map<String, Any?>>).orEmpty().map {
        LigneCommande(
            produitRef = it["ref"] as? String ?: "",
            produitNom = it["nom"] as? String ?: "",
            quantite = (it["quantite"] as? Number)?.toInt() ?: 1,
            prixUnitaire = (it["prixUnitaire"] as? Number)?.toInt() ?: 0,
            ccUnitaire = 0.0
        )
    }
    return CommandeBoutique(
        id = id,
        clientNom = client["nom"] as? String ?: "",
        telephone = client["telephone"] as? String ?: "",
        telephone2 = client["telephone2"] as? String ?: "",
        quartier = client["quartier"] as? String ?: "",
        adresse = client["adresse"] as? String ?: "",
        note = d["note"] as? String ?: "",
        lignes = lignes,
        total = (d["total"] as? Number)?.toInt() ?: 0,
        date = (d["createdAt"] as? Timestamp)?.toDate()?.let { SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(it) }.orEmpty()
    )
}

/**
 * Commandes reçues par la boutique en ligne : le distributeur appelle le client, puis « Préparer »
 * ouvre la commande déjà remplie (produits et client). Il ne reste qu'à choisir pays et livraison.
 */
@Composable
fun CommandesBoutiqueScreen(
    ventesViewModel: VentesViewModel,
    onPreparer: () -> Unit,
    onRetour: () -> Unit,
    avecEnTete: Boolean = true
) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var commandes by remember { mutableStateOf<List<CommandeBoutique>?>(null) }
    var aRefuser by remember { mutableStateOf<CommandeBoutique?>(null) }

    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose {}
        val ecoute = FirebaseFirestore.getInstance().collection("commandesBoutique")
            .whereEqualTo("fboId", uid)
            .whereEqualTo("statut", "nouvelle")
            .addSnapshotListener { r, _ ->
                commandes = r?.documents.orEmpty()
                    .sortedByDescending { (it.get("createdAt") as? Timestamp)?.seconds ?: 0 }
                    .map { depuis(it.id, it.data.orEmpty()) }
            }
        onDispose { ecoute.remove() }
    }

    fun classer(c: CommandeBoutique, statut: String) {
        FirebaseFirestore.getInstance().document("commandesBoutique/${c.id}")
            .update(mapOf("statut" to statut, "traiteeLe" to FieldValue.serverTimestamp()))
    }

    Column(Modifier.fillMaxSize().background(BlackDeep)) {
        if (avecEnTete) Row(
            Modifier.fillMaxWidth().padding(top = 40.dp, start = 4.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = GoldPrimary) }
            Text("Commandes de ma boutique", color = WhitePrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        val liste = commandes
        when {
            liste == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldPrimary)
            }
            liste.isEmpty() -> Text(
                "Aucune nouvelle commande. Partagez le lien ou le QR code de votre boutique pour en recevoir.",
                color = WhiteSub,
                modifier = Modifier.padding(16.dp)
            )
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(liste, key = { it.id }) { c ->
                    Column(
                        Modifier.fillMaxWidth().clip(Forme).background(BlackCard).border(1.dp, BlackBorder, Forme).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.clientNom, color = WhitePrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(c.date, color = WhiteSub, fontSize = 12.sp)
                        }
                        Text("${c.quartier}${if (c.adresse.isNotBlank()) ", ${c.adresse}" else ""}", color = WhiteSub, fontSize = 13.sp)
                        c.lignes.forEach { Text("• ${it.produitNom} × ${it.quantite}", color = WhiteSub, fontSize = 13.sp) }
                        if (c.note.isNotBlank()) Text("« ${c.note} »", color = GoldPrimary, fontSize = 13.sp)
                        Text(fcfa(c.total), color = GoldPrimary, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.telephone}")))
                            }) { Text("Appeler", color = GoldPrimary) }
                            Button(
                                onClick = {
                                    ventesViewModel.preremplirDepuisBoutique(c)
                                    classer(c, "traitee")
                                    onPreparer()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = BlackDeep)
                            ) { Text("Préparer", fontWeight = FontWeight.Bold) }
                            TextButton(onClick = { aRefuser = c }) { Text("Refuser", color = WhiteSub) }
                        }
                    }
                }
            }
        }
    }

    aRefuser?.let { c ->
        AlertDialog(
            onDismissRequest = { aRefuser = null },
            containerColor = BlackCard,
            title = { Text("Refuser cette commande ?", color = WhitePrimary) },
            text = { Text("La commande de ${c.clientNom} sera retirée de la liste.", color = WhiteSub) },
            confirmButton = { TextButton(onClick = { classer(c, "refusee"); aRefuser = null }) { Text("Refuser", color = RedAlert) } },
            dismissButton = { TextButton(onClick = { aRefuser = null }) { Text("Annuler", color = WhiteSub) } }
        )
    }
}
