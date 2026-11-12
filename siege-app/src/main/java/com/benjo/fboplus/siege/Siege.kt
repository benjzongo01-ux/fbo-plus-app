package com.benjo.fboplus.siege

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

/*
 * FBO+ Siège : les lots de validation arrivent en direct. Le correspondant choisit ceux qu'il dépose
 * au siège (total d'argent à apporter), puis scanne chaque reçu. Les lots déposés sans reçu restent
 * listés dans « Reçus attendus » jusqu'au scan.
 */

internal val SNoir = Color(0xFF08090C)
internal val SSurface = Color(0xFF14161C)
internal val STrait = Color(0xFF262932)
internal val SOr = Color(0xFFE5B53A)
internal val SGris = Color(0xFF9AA0AC)
internal val SVert = Color(0xFF4ADE80)
internal val SRouge = Color(0xFFFF6B6B)
internal val SOrange = Color(0xFFFFB547)

data class LigneLot(val ref: String, val nom: String, val quantite: Int)

data class Lot(
    val id: String,
    val idForever: String,
    val nom: String,
    val telephone: String,
    val montant: Long,
    val cc: Double,
    val statut: String,
    val pays: String,
    val lignes: List<LigneLot>,
    val cree: Timestamp?
)

internal fun fcfa(n: Long) = "%,d FCFA".format(Locale.FRANCE, n).replace(' ', ' ').replace(' ', ' ')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EspaceSiege(onDeconnexion: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val fonctions = remember { FirebaseFunctions.getInstance(REGION_FUNCTIONS) }
    val portee = rememberCoroutineScope()
    var pays by remember { mutableStateOf<String?>(null) }
    var lots by remember { mutableStateOf<List<Lot>?>(null) }
    var onglet by remember { mutableIntStateOf(0) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var scanner by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }

    // Pays du correspondant (dans son jeton) : il ne voit que les validations de son pays.
    LaunchedEffect(Unit) {
        val claims = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.claims
        pays = claims?.get("pays") as? String ?: ""
    }
    DisposableEffect(pays) {
        val p = pays ?: return@DisposableEffect onDispose {}
        var requete = db.collection("lotsValidation").whereIn("statut", listOf("a_valider", "au_siege"))
        if (p.isNotBlank()) requete = requete.whereEqualTo("pays", p)
        val ecoute: ListenerRegistration = requete.addSnapshotListener { docs, _ ->
            lots = docs?.documents?.map { d ->
                Lot(
                    id = d.id,
                    idForever = d.getString("idForever").orEmpty(),
                    nom = d.getString("nom").orEmpty(),
                    telephone = d.getString("telephone").orEmpty(),
                    montant = d.getLong("montant") ?: 0L,
                    cc = d.getDouble("ccTotal") ?: 0.0,
                    statut = d.getString("statut").orEmpty(),
                    pays = d.getString("pays").orEmpty(),
                    lignes = (d.get("lignes") as? List<*>).orEmpty().mapNotNull { l ->
                        val m = l as? Map<*, *> ?: return@mapNotNull null
                        LigneLot(m["ref"] as? String ?: "", m["nom"] as? String ?: "", (m["quantite"] as? Number)?.toInt() ?: 0)
                    },
                    cree = d.getTimestamp("createdAt")
                )
            }?.sortedBy { it.cree?.seconds ?: 0 } ?: lots
        }
        onDispose { ecoute.remove() }
    }

    if (scanner) {
        BackHandler { scanner = false }
        ScannerRecu(attendus = lots.orEmpty().filter { it.statut == "au_siege" }, onFermer = { scanner = false })
        return
    }

    val aDeposer = lots.orEmpty().filter { it.statut == "a_valider" }
    val attendus = lots.orEmpty().filter { it.statut == "au_siege" }

    Scaffold(
        containerColor = SNoir,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FBO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("+", color = SOr, fontWeight = FontWeight.Black, fontSize = 23.sp)
                        Text("  Siège", color = SGris, fontSize = 16.sp)
                    }
                },
                actions = { IconButton(onClick = onDeconnexion) { Icon(Icons.AutoMirrored.Filled.Logout, "Se déconnecter", tint = SGris) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SNoir)
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(SSurface).navigationBarsPadding().padding(16.dp)) {
                message?.let { Text(it, color = SVert, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                if (onglet == 0) {
                    val choisis = aDeposer.filter { it.id in selection }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${choisis.size} lot(s) choisi(s)", color = SGris, fontSize = 13.sp)
                            Text("À apporter : ${fcfa(choisis.sumOf { it.montant })}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                enCours = true
                                portee.launch {
                                    message = try {
                                        val r = fonctions.getHttpsCallable("deposerLotsAuSiege").call(mapOf("ids" to choisis.map { it.id })).await().getData() as Map<*, *>
                                        selection = emptySet()
                                        onglet = 1
                                        "${r["deposes"]} lot(s) déposé(s) : scannez les reçus quand ils sortent."
                                    } catch (e: Exception) {
                                        messageReseau(e)
                                    }
                                    enCours = false
                                }
                            },
                            enabled = choisis.isNotEmpty() && !enCours,
                            colors = ButtonDefaults.buttonColors(containerColor = SOr, contentColor = SNoir),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Déposer au siège", fontWeight = FontWeight.SemiBold) }
                    }
                } else {
                    Button(
                        onClick = { message = null; scanner = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SOr, contentColor = SNoir),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Icon(Icons.Filled.DocumentScanner, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scanner un reçu", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { marges ->
        Column(Modifier.fillMaxSize().padding(marges)) {
            TabRow(selectedTabIndex = onglet, containerColor = SNoir, contentColor = SOr) {
                Tab(selected = onglet == 0, onClick = { onglet = 0 }, text = { Text("À déposer (${aDeposer.size})") })
                Tab(selected = onglet == 1, onClick = { onglet = 1 }, text = { Text("Reçus attendus (${attendus.size})") })
            }
            val liste = if (onglet == 0) aDeposer else attendus
            when {
                lots == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = SOr) }
                liste.isEmpty() -> Text(
                    if (onglet == 0) "Aucune validation à déposer pour l'instant." else "Tous les reçus sont arrivés.",
                    color = SGris, modifier = Modifier.padding(24.dp)
                )
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onglet == 0 && liste.size > 1) {
                        item {
                            Text(
                                if (liste.all { it.id in selection }) "Tout désélectionner" else "Tout sélectionner",
                                color = SOr, fontSize = 14.sp,
                                modifier = Modifier.clickable {
                                    selection = if (liste.all { it.id in selection }) emptySet() else liste.map { it.id }.toSet()
                                }
                            )
                        }
                    }
                    items(liste, key = { it.id }) { lot ->
                        CarteLot(
                            lot,
                            coche = if (onglet == 0) lot.id in selection else null,
                            onCoche = { selection = if (lot.id in selection) selection - lot.id else selection + lot.id }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteLot(lot: Lot, coche: Boolean?, onCoche: () -> Unit) {
    val forme = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forme)
            .background(SSurface)
            .border(1.dp, if (coche == true) SOr else STrait, forme)
            .clickable(enabled = coche != null, onClick = onCoche)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (coche != null) {
                Checkbox(checked = coche, onCheckedChange = { onCoche() }, colors = CheckboxDefaults.colors(checkedColor = SOr, checkmarkColor = SNoir))
            }
            Column(Modifier.weight(1f)) {
                Text("ID ${lot.idForever}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(listOf(lot.nom, lot.telephone).filter { it.isNotBlank() }.joinToString(" · "), color = SGris, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fcfa(lot.montant), color = SOr, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("%.3f CC".format(Locale.FRANCE, lot.cc), color = SGris, fontSize = 12.sp)
            }
        }
        if (lot.lignes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            lot.lignes.groupBy { it.ref to it.nom }.forEach { (cle, l) ->
                Text("Réf. ${cle.first} · ${cle.second} × ${l.sumOf { it.quantite }}", color = Color.White, fontSize = 13.sp)
            }
        }
        lot.cree?.toDate()?.let {
            Text("Créé le " + SimpleDateFormat("d MMM · HH:mm", Locale.FRANCE).format(it), color = SGris, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Message clair quand la connexion lâche : rien n'est perdu, il suffit de recommencer. */
internal fun messageReseau(e: Exception): String {
    val reseau = e is com.google.firebase.FirebaseNetworkException ||
        (e as? com.google.firebase.functions.FirebaseFunctionsException)?.code == com.google.firebase.functions.FirebaseFunctionsException.Code.UNAVAILABLE ||
        (e as? com.google.firebase.storage.StorageException)?.errorCode == com.google.firebase.storage.StorageException.ERROR_RETRY_LIMIT_EXCEEDED ||
        e is java.io.IOException
    return if (reseau) "Connexion instable : l'envoi n'est pas parti. Vérifiez la connexion et réessayez."
    else e.message ?: "Opération impossible pour le moment."
}
