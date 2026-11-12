package com.benjo.fboplus.delivery

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Encaissement chez le client.
 * Mobile money (recommandé) : le client envoie le montant sur un numéro de FBO+, le livreur
 * déclare la référence du SMS (et une capture s'il veut). L'administration vérifie la réception ;
 * la répartition se fait alors toute seule. Le livreur peut terminer la livraison dès la déclaration.
 * Espèces si c'est obligé : remises ensuite au centre, qui confirme.
 *
 * @param onEtat (payé ou déclaré par mobile money, espèces encaissées)
 */
@Composable
fun EncaissementClient(id: String, montant: Int, onEtat: (Boolean, Boolean) -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val contexte = LocalContext.current
    val fonctions = remember { FirebaseFunctions.getInstance(REGION_FUNCTIONS) }
    val portee = rememberCoroutineScope()
    var mode by remember { mutableStateOf("mobile") }
    var pays by remember { mutableStateOf("Burkina Faso") }
    var statut by remember { mutableStateOf<String?>(null) }
    var motifRefus by remember { mutableStateOf("") }
    var referenceDeclaree by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var capture by remember { mutableStateOf<Uri?>(null) }
    var choisi by remember { mutableStateOf<NumeroDepot?>(null) }
    var especes by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }
    val numeros = numerosDepot(pays)
    LaunchedEffect(numeros) { if (choisi == null || choisi !in numeros) choisi = numeros.firstOrNull() }

    DisposableEffect(id) {
        val db = FirebaseFirestore.getInstance()
        val ecoutes = listOfNotNull(
            uid?.let { db.document("users/$it").addSnapshotListener { d, _ -> d?.getString("pays")?.let { p -> if (p.isNotBlank()) pays = p } } },
            db.document("orders/$id").addSnapshotListener { d, _ ->
                val p = d?.get("paiement") as? Map<*, *>
                statut = p?.get("statut") as? String
                referenceDeclaree = p?.get("reference") as? String ?: ""
                motifRefus = p?.get("motif") as? String ?: ""
            }
        )
        onDispose { ecoutes.forEach { it.remove() } }
    }
    val payeMobile = statut == "confirme" || statut == "a_verifier"
    LaunchedEffect(payeMobile, especes, mode) { onEtat(payeMobile, mode == "especes" && especes) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Carte).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Paiement du client", color = TexteDoux, fontSize = 13.sp)
        Text(montantFcfa(montant), color = Or, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        if (payeMobile) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (statut == "confirme") Icons.Filled.CheckCircle else Icons.Filled.HourglassTop, null,
                    tint = if (statut == "confirme") Vert else Or, modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (statut == "confirme") "Paiement confirmé · réf. $referenceDeclaree"
                    else "Paiement déclaré · réf. $referenceDeclaree · en vérification",
                    color = if (statut == "confirme") Vert else Or, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
            }
            if (statut == "a_verifier") Text("Vous pouvez terminer la livraison : le centre vérifie la réception.", color = TexteDoux, fontSize = 12.sp)
            return@Column
        }
        if (statut == "refuse") {
            Text("Paiement non retrouvé${if (motifRefus.isNotBlank()) " : $motifRefus" else ""}. Déclarez à nouveau, ou encaissez en espèces.", color = Rouge, fontSize = 13.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Puce("Mobile money", mode == "mobile") { mode = "mobile" }
            Puce("Espèces", mode == "especes") { mode = "especes" }
        }

        if (mode == "mobile") {
            if (numeros.isEmpty()) {
                Text("Aucun numéro de dépôt FBO+ n'est configuré pour $pays : encaissez en espèces.", color = TexteDoux, fontSize = 13.sp)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    numeros.forEach { n -> Puce(n.operateur, n == choisi) { choisi = n } }
                }
                choisi?.let { CarteNumeroDepot(it, montantFcfa(montant)) }
                Text("Le client envoie le montant sur ce numéro, puis vous recopiez la référence de son SMS.", color = TexteDoux, fontSize = 13.sp)
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it.take(40) },
                    label = { Text("Référence de la transaction") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
                    colors = couleursChamp(),
                    modifier = Modifier.fillMaxWidth()
                )
                ChoixCapture(capture) { capture = it }
                BoutonPrincipal("Déclarer le paiement", !enCours && choisi != null && reference.trim().length >= 4) {
                    erreur = null
                    enCours = true
                    portee.launch {
                        try {
                            val chemin = capture?.let { envoyerCapture(contexte, it) }
                            fonctions.getHttpsCallable("declarerPaiement").call(
                                mapOf("type" to "commande", "id" to id, "operateur" to choisi!!.operateur, "reference" to reference.trim(), "capture" to (chemin ?: ""))
                            ).await()
                        } catch (e: Exception) {
                            erreur = e.message ?: "Déclaration impossible pour le moment."
                        }
                        enCours = false
                    }
                }
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
