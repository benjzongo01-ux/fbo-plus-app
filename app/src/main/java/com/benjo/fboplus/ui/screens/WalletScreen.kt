package com.benjo.fboplus.ui.screens

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.REGION_FUNCTIONS
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/*
 * FBO+ Wallet : solde, dépôts, retraits et historique.
 * Le même fichier est copié dans FBO+ Delivery (seuls le package et l'import de REGION_FUNCTIONS changent) :
 * toute modification se fait dans les deux.
 * Le serveur est la seule source du solde ; l'application ne fait que lire et demander.
 */

private val WNoir = Color(0xFF08090C)
private val WSurface = Color(0xFF14161C)
private val WTrait = Color(0xFF262932)
private val WOr = Color(0xFFE5B53A)
private val WOrClair = Color(0xFFF7DA86)
private val WGris = Color(0xFF9AA0AC)
private val WGrisDiscret = Color(0xFF6B717D)
private val WVert = Color(0xFF4ADE80)
private val WRouge = Color(0xFFFF6B6B)
private val WOrange = Color(0xFFFFB547)

/** Moyens de paiement par pays : la même liste que le serveur, qui revérifie tout. */
private val OPERATEURS_PAYS = mapOf(
    "Burkina Faso" to listOf("Orange Money", "Moov Money"),
    "Côte d'Ivoire" to listOf("Orange Money", "MTN MoMo", "Moov Money", "Wave"),
    "Sénégal" to listOf("Orange Money", "Wave", "Free Money"),
    "Guinée" to listOf("Orange Money", "MTN MoMo")
)
private val INDICATIFS = mapOf("Burkina Faso" to "+226", "Côte d'Ivoire" to "+225", "Sénégal" to "+221", "Guinée" to "+224")

private data class Mouvement(
    val id: String,
    val type: String,
    val montant: Long,
    val libelle: String,
    val statut: String,
    val date: Timestamp?
)

private fun fcfa(n: Long) = "%,d FCFA".format(Locale.FRANCE, n).replace(' ', ' ').replace(' ', ' ')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(onRetour: (() -> Unit)? = null) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = remember { FirebaseFirestore.getInstance() }
    val fonctions = remember { FirebaseFunctions.getInstance(REGION_FUNCTIONS) }
    val portee = rememberCoroutineScope()

    var solde by remember { mutableLongStateOf(0L) }
    var bloque by remember { mutableLongStateOf(0L) }
    var pays by remember { mutableStateOf("Burkina Faso") }
    var mouvements by remember { mutableStateOf<List<Mouvement>?>(null) }
    var masque by remember { mutableStateOf(false) }
    var feuille by remember { mutableStateOf<String?>(null) } // "depot" ou "retrait"
    var depotEnAttente by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(uid) {
        val ecoutes = mutableListOf<ListenerRegistration>()
        ecoutes += db.document("wallets/$uid").addSnapshotListener { d, _ ->
            solde = d?.getLong("solde") ?: 0L
            bloque = d?.getLong("bloque") ?: 0L
        }
        ecoutes += db.collection("wallets/$uid/mouvements")
            .orderBy("creeLe", Query.Direction.DESCENDING).limit(60)
            .addSnapshotListener { docs, _ ->
                mouvements = docs?.documents?.map {
                    Mouvement(
                        it.id, it.getString("type") ?: "", it.getLong("montant") ?: 0L,
                        it.getString("libelle") ?: "", it.getString("statut") ?: "", it.getTimestamp("creeLe")
                    )
                } ?: mouvements
            }
        ecoutes += db.document("users/$uid").addSnapshotListener { d, _ ->
            d?.getString("pays")?.takeIf { it in OPERATEURS_PAYS }?.let { pays = it }
        }
        onDispose { ecoutes.forEach { it.remove() } }
    }

    val soldeAffiche by animateIntAsState(solde.toInt(), tween(700), label = "solde")

    Scaffold(
        containerColor = WNoir,
        topBar = {
            TopAppBar(
                title = { Text("Mon wallet", color = Color.White, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (onRetour != null) {
                        IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WNoir)
            )
        }
    ) { marges ->
        LazyColumn(
            Modifier.fillMaxSize().padding(marges),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CarteSolde(
                    solde = if (masque) null else soldeAffiche.toLong(),
                    bloque = bloque,
                    masque = masque,
                    onMasque = { masque = !masque }
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Bouton("Déposer", Icons.Filled.ArrowDownward, plein = true, Modifier.weight(1f)) { message = null; feuille = "depot" }
                    Bouton("Retirer", Icons.Filled.ArrowUpward, plein = false, Modifier.weight(1f)) { message = null; feuille = "retrait" }
                }
            }
            message?.let { m -> item { Text(m, color = WVert, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)) } }
            item {
                Text("Historique", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            }
            val liste = mouvements
            when {
                liste == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = WOr, strokeWidth = 2.dp) } }
                liste.isEmpty() -> item {
                    Text(
                        "Aucun mouvement pour l'instant.\nVos dépôts, gains et retraits s'afficheront ici.",
                        color = WGrisDiscret, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                    )
                }
                else -> items(liste, key = { it.id }) { LigneMouvement(it) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    feuille?.let { type ->
        FeuilleOperation(
            depot = type == "depot",
            disponible = solde,
            operateurs = OPERATEURS_PAYS[pays] ?: emptyList(),
            indicatif = INDICATIFS[pays] ?: "",
            onFermer = { feuille = null },
            onValider = { montant, operateur, numero, surErreur ->
                portee.launch {
                    try {
                        val donnees = mapOf(
                            "cleIdempotence" to UUID.randomUUID().toString(),
                            "montant" to montant,
                            "operateur" to operateur,
                            "numero" to numero
                        )
                        if (type == "depot") {
                            val r = fonctions.getHttpsCallable("demarrerDepot").call(donnees).await().getData() as Map<*, *>
                            feuille = null
                            depotEnAttente = (r["id"] as String) to montant
                        } else {
                            fonctions.getHttpsCallable("demanderRetrait").call(donnees).await()
                            feuille = null
                            message = "Demande de retrait envoyée : l'administration la traite rapidement."
                        }
                    } catch (e: Exception) {
                        surErreur(e.message ?: "Opération impossible pour le moment.")
                    }
                }
            }
        )
    }

    // Paiement simulé (émulateurs) : on joue la confirmation que le client ferait sur son téléphone.
    depotEnAttente?.let { (id, montant) ->
        var enCours by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {},
            containerColor = WSurface,
            title = { Text("Confirmez le paiement", color = Color.White, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Une demande de ${fcfa(montant)} a été envoyée sur votre mobile money.\n\nMode test : aucun argent réel n'est utilisé.",
                    color = WGris, fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(enabled = !enCours, onClick = {
                    enCours = true
                    portee.launch {
                        runCatching { fonctions.getHttpsCallable("confirmerDepotSimule").call(mapOf("id" to id, "succes" to true)).await() }
                        depotEnAttente = null
                        message = "Dépôt de ${fcfa(montant)} crédité."
                    }
                }) { Text("Simuler le paiement", color = WOr, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(enabled = !enCours, onClick = {
                    portee.launch {
                        runCatching { fonctions.getHttpsCallable("confirmerDepotSimule").call(mapOf("id" to id, "succes" to false)).await() }
                        depotEnAttente = null
                    }
                }) { Text("Annuler", color = WGris) }
            }
        )
    }
}

@Composable
private fun CarteSolde(solde: Long?, bloque: Long, masque: Boolean, onMasque: () -> Unit) {
    val forme = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forme)
            .background(Brush.linearGradient(listOf(Color(0xFF1C1810), Color(0xFF111217), Color(0xFF0C0D11))))
            .border(BorderStroke(1.dp, Brush.linearGradient(listOf(WOr.copy(alpha = 0.7f), WTrait, WOr.copy(alpha = 0.3f)))), forme)
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Solde disponible", color = WGris, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(
                if (masque) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                if (masque) "Afficher le solde" else "Masquer le solde",
                tint = WGris,
                modifier = Modifier.size(20.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onMasque)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            solde?.let { fcfa(it) } ?: "••••••",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )
        if (bloque > 0) {
            Spacer(Modifier.height(10.dp))
            Text("Retrait en cours : ${if (masque) "•••" else fcfa(bloque)}", color = WOrange, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FBO", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text("+", color = WOr, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("  Wallet", color = WOrClair, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Bouton(texte: String, icone: ImageVector, plein: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val forme = RoundedCornerShape(16.dp)
    Row(
        modifier
            .height(54.dp)
            .clip(forme)
            .background(if (plein) WOr else Color.Transparent)
            .border(1.dp, if (plein) WOr else WTrait, forme)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icone, null, tint = if (plein) WNoir else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(texte, color = if (plein) WNoir else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LigneMouvement(m: Mouvement) {
    val entree = m.montant > 0
    val icone = when (m.type) {
        "depot" -> Icons.Filled.ArrowDownward
        "retrait" -> Icons.Filled.ArrowUpward
        "remboursement" -> Icons.Filled.Undo
        else -> Icons.Filled.Tune
    }
    val (etiquette, couleurEtiquette) = when (m.statut) {
        "en_attente" -> "En attente" to WOrange
        "refuse" -> "Refusé" to WRouge
        "paye" -> "Payé" to WVert
        else -> null to WGris
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WSurface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (entree) WVert.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) { Icon(icone, null, tint = if (entree) WVert else WGris, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.libelle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                m.date?.toDate()?.let { SimpleDateFormat("d MMM yyyy · HH:mm", Locale.FRANCE).format(it) } ?: "À l'instant",
                color = WGrisDiscret, fontSize = 12.sp
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (entree) "+ " else "− ") + fcfa(kotlin.math.abs(m.montant)),
                color = when {
                    m.statut == "refuse" -> WGrisDiscret
                    entree -> WVert
                    else -> Color.White
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (etiquette != null) Text(etiquette, color = couleurEtiquette, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleOperation(
    depot: Boolean,
    disponible: Long,
    operateurs: List<String>,
    indicatif: String,
    onFermer: () -> Unit,
    onValider: (montant: Long, operateur: String, numero: String, surErreur: (String) -> Unit) -> Unit
) {
    var montant by remember { mutableStateOf("") }
    var operateur by remember { mutableStateOf(operateurs.firstOrNull() ?: "") }
    var numero by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }
    val valeur = montant.toLongOrNull() ?: 0L
    val valide = valeur > 0 && numero.count(Char::isDigit) >= 8 && operateur.isNotBlank() && (depot || valeur <= disponible)

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = WSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp).imePadding()) {
            Text(if (depot) "Déposer" else "Retirer", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (depot) "Alimentez votre wallet par mobile money." else "Disponible : ${fcfa(disponible)}",
                color = WGris, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )
            Champ(montant, { montant = it.filter(Char::isDigit).take(9) }, "Montant (FCFA)", KeyboardType.Number)
            if (!depot && disponible > 0) {
                Text(
                    "Tout retirer",
                    color = WOr, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp).clickable { montant = disponible.toString() }
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Moyen de paiement", color = WGris, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                operateurs.forEach { o ->
                    val choisi = o == operateur
                    Text(
                        o,
                        color = if (choisi) WNoir else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (choisi) WOr else Color.Transparent)
                            .border(1.dp, if (choisi) WOr else WTrait, RoundedCornerShape(12.dp))
                            .clickable { operateur = o }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Champ(numero, { numero = it.filter { c -> c.isDigit() || c == ' ' || c == '+' }.take(20) }, "Numéro $operateur ($indicatif)", KeyboardType.Phone)
            erreur?.let { Text(it, color = WRouge, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    erreur = null
                    enCours = true
                    onValider(valeur, operateur, numero) { e -> erreur = e; enCours = false }
                },
                enabled = valide && !enCours,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WOr, contentColor = WNoir, disabledContainerColor = Color(0xFF2A2C33)),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (enCours) CircularProgressIndicator(Modifier.size(22.dp), color = WNoir, strokeWidth = 2.dp)
                else Text(
                    if (depot) "Déposer ${if (valeur > 0) fcfa(valeur) else ""}" else "Demander le retrait",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }
            if (!depot) {
                Text(
                    "Le retrait est vérifié par l'administration, puis envoyé sur ce numéro.",
                    color = WGrisDiscret, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun Champ(valeur: String, onChange: (String) -> Unit, libelle: String, clavier: KeyboardType) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(libelle) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = clavier),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = WOr, unfocusedBorderColor = WTrait,
            focusedLabelColor = WOr, unfocusedLabelColor = WGris,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = WOr
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
