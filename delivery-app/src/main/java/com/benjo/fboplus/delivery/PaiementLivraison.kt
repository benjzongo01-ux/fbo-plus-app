package com.benjo.fboplus.delivery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Moyens de paiement mobile par pays (le serveur revérifie). */
private val MOYENS = mapOf(
    "Burkina Faso" to listOf("Orange Money", "Moov Money"),
    "Côte d'Ivoire" to listOf("Orange Money", "MTN MoMo", "Moov Money", "Wave"),
    "Sénégal" to listOf("Orange Money", "Wave", "Free Money"),
    "Guinée" to listOf("Orange Money", "MTN MoMo")
)

private fun qr(contenu: String, taille: Int = 600): Bitmap {
    val matrice = QRCodeWriter().encode(contenu, BarcodeFormat.QR_CODE, taille, taille)
    return Bitmap.createBitmap(taille, taille, Bitmap.Config.RGB_565).apply {
        for (x in 0 until taille) for (y in 0 until taille) setPixel(x, y, if (matrice[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}

/**
 * Encaissement chez le client. Par mobile money (recommandé) : le client scanne le QR code ou
 * confirme sur son téléphone ; l'argent arrive directement sur la plateforme et se répartit seul.
 * En espèces si c'est obligé : le livreur les remet ensuite au centre, qui confirme la remise.
 *
 * @param onEtat (payé par mobile money, espèces encaissées)
 */
@Composable
fun EncaissementClient(id: String, montant: Int, onEtat: (Boolean, Boolean) -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val fonctions = remember { FirebaseFunctions.getInstance(REGION_FUNCTIONS) }
    val portee = rememberCoroutineScope()
    var mode by remember { mutableStateOf("mobile") }
    var pays by remember { mutableStateOf("Burkina Faso") }
    var operateur by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf("") }
    var statut by remember { mutableStateOf<String?>(null) }
    var reference by remember { mutableStateOf("") }
    var lien by remember { mutableStateOf("") }
    var especes by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }

    DisposableEffect(id) {
        val db = FirebaseFirestore.getInstance()
        val ecoutes = listOfNotNull(
            uid?.let { db.document("users/$it").addSnapshotListener { d, _ -> d?.getString("pays")?.takeIf { p -> p in MOYENS }?.let { p -> pays = p } } },
            db.document("paiements/$id").addSnapshotListener { d, _ ->
                if (d != null && d.exists()) {
                    statut = d.getString("statut")
                    reference = d.getString("reference").orEmpty()
                }
            }
        )
        onDispose { ecoutes.forEach { it.remove() } }
    }
    LaunchedEffect(pays) { if (operateur !in (MOYENS[pays] ?: emptyList())) operateur = MOYENS[pays]?.first().orEmpty() }
    val payeMobile = statut == "confirme"
    LaunchedEffect(payeMobile, especes, mode) { onEtat(payeMobile, mode == "especes" && especes) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Carte).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Paiement du client", color = TexteDoux, fontSize = 13.sp)
        Text(montantFcfa(montant), color = Or, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        if (payeMobile) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Vert, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Payé par mobile money · $reference", color = Vert, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Puce("Mobile money", mode == "mobile") { mode = "mobile" }
            Puce("Espèces", mode == "especes") { mode = "especes" }
        }

        if (mode == "mobile") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (MOYENS[pays] ?: emptyList()).forEach { o -> Puce(o, o == operateur) { operateur = o } }
            }
            if (statut != "en_attente") {
                OutlinedTextField(
                    value = numero,
                    onValueChange = { numero = it.filter { c -> c.isDigit() || c == ' ' || c == '+' }.take(20) },
                    label = { Text("Numéro du client (facultatif)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = couleursChamp(),
                    modifier = Modifier.fillMaxWidth()
                )
                BoutonPrincipal("Demander le paiement", !enCours) {
                    erreur = null
                    enCours = true
                    portee.launch {
                        try {
                            val r = fonctions.getHttpsCallable("demanderPaiementLivraison")
                                .call(mapOf("id" to id, "operateur" to operateur, "numero" to numero)).await().getData() as Map<*, *>
                            lien = r["qr"] as? String ?: ""
                        } catch (e: Exception) {
                            erreur = e.message ?: "Paiement impossible pour le moment."
                        }
                        enCours = false
                    }
                }
            } else {
                Text("Le client scanne ce code, ou confirme la demande reçue sur son téléphone.", color = TexteDoux, fontSize = 13.sp)
                val image = remember(lien, reference) { qr(lien.ifBlank { "https://pay.fboplus.app/p/$reference" }).asImageBitmap() }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(image, "QR code de paiement", Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp))
                }
                Text("Référence $reference · en attente du client…", color = TexteDoux, fontSize = 13.sp)
                // Émulateurs : on joue la confirmation du client (l'agrégateur le fera au lancement).
                TextButton(onClick = {
                    portee.launch {
                        runCatching { fonctions.getHttpsCallable("confirmerPaiementSimule").call(mapOf("id" to id, "succes" to true)).await() }
                    }
                }) { Text("Mode test : simuler le paiement du client", color = Or) }
            }
        } else {
            Row(Modifier.fillMaxWidth().clickable { especes = !especes }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = especes, onCheckedChange = { especes = it }, colors = CheckboxDefaults.colors(checkedColor = Or, checkmarkColor = Fond))
                Text("J'ai encaissé ${montantFcfa(montant)} en espèces", color = Texte, fontSize = 15.sp)
            }
            Text("Remettez cet argent au centre : vos gains sont versés quand le centre confirme la remise.", color = TexteDoux, fontSize = 12.sp)
        }
        erreur?.let { Text(it, color = Rouge, fontSize = 13.sp) }
    }
}

@Composable
private fun Puce(texte: String, choisie: Boolean, onClick: () -> Unit) {
    Text(
        texte,
        color = if (choisie) Fond else Texte,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (choisie) Or else Color.Transparent)
            .border(1.dp, if (choisie) Or else Bordure, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    )
}

private fun montantFcfa(n: Int) = "%,d FCFA".format(java.util.Locale.FRANCE, n)
