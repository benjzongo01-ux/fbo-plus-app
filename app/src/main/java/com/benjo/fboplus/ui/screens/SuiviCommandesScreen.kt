package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.Commande
import com.benjo.fboplus.data.VentesRepository
import com.benjo.fboplus.data.etapesPour
import com.benjo.fboplus.data.libelleMode
import com.benjo.fboplus.data.libelleStatut
import com.benjo.fboplus.ui.theme.*
import com.benjo.fboplus.viewmodel.VentesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FormeCarte = RoundedCornerShape(16.dp)

fun couleurStatut(statut: String): Color = when (statut) {
    "livree", "validee", "expediee" -> GreenPrimary
    "annulee", "retournee" -> RedAlert
    "en_livraison", "assignee" -> BlueAccent
    else -> GoldPrimary
}

@Composable
fun BadgeStatut(statut: String) {
    val couleur = couleurStatut(statut)
    Text(
        libelleStatut(statut),
        color = couleur,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(couleur.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnTeteSimple(titre: String, onRetour: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(titre, color = WhitePrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onRetour) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = WhitePrimary)
            }
        },
        actions = actions,
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackDeep)
    )
}

@Composable
fun MesCommandesScreen(onOuvrir: (String) -> Unit, onRetour: () -> Unit) {
    val commandes by VentesRepository.commandes.collectAsState()
    val suivies = commandes.filter { it.docId.isNotEmpty() }.sortedByDescending { it.dateMillis }

    Column(Modifier.fillMaxSize().background(BlackDeep)) {
        EnTeteSimple("Mes commandes", onRetour)
        if (suivies.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucune commande pour le moment", color = WhiteSub, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(suivies, key = { it.docId }) { commande ->
                    CarteCommande(commande) { onOuvrir(commande.docId) }
                }
            }
        }
    }
}

@Composable
fun CarteCommande(commande: Commande, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(FormeCarte)
            .background(BlackCard)
            .border(1.dp, BlackBorder, FormeCarte)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                commande.nomAffiche,
                color = WhitePrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            BadgeStatut(commande.statut)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${commande.dateStr} · ${commande.lignes.size} produit(s) · %,d FCFA".format(Locale.FRANCE, commande.total),
            color = WhiteSub,
            fontSize = 13.sp
        )
    }
}

@Composable
fun CommandeDetailScreen(
    docId: String,
    ventesViewModel: VentesViewModel,
    onModifier: (Commande) -> Unit,
    onRetour: () -> Unit
) {
    val commandes by VentesRepository.commandes.collectAsState()
    val commande = commandes.find { it.docId == docId }
    var confirmation by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(BlackDeep)) {
        EnTeteSimple("Suivi de la commande", onRetour)

        if (commande == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Commande introuvable", color = WhiteSub)
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.fillMaxWidth().clip(FormeCarte).background(BlackCard).padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        commande.nomAffiche,
                        color = WhitePrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    BadgeStatut(commande.statut)
                }
                if (commande.mode.isNotBlank()) {
                    Ligne("Type", libelleMode(commande.mode))
                }
                if (commande.pays.isNotBlank()) Ligne("Pays", commande.pays)
                if (commande.beneficiaireId.isNotBlank()) {
                    val qui = if (commande.pourQui == "filleul") "Filleul : ${commande.beneficiaireNom}" else "Moi"
                    Ligne("Bénéficiaire", qui)
                    Ligne("ID Forever", commande.beneficiaireId)
                }
                if (commande.villeExpedition.isNotBlank()) Ligne("Expédiée vers", commande.villeExpedition)
                if (commande.compagnie.isNotBlank()) Ligne("Compagnie", commande.compagnie)
                if (commande.numeroColis.isNotBlank()) Ligne("N° du colis", commande.numeroColis)
                if (commande.clientNom.isNotBlank()) {
                    Ligne(if (commande.mode == "expedition") "Destinataire" else "Client", commande.clientNom)
                }
                if (commande.clientTelephone.isNotBlank()) Ligne("Téléphone", commande.clientTelephone)
                if (commande.clientTelephone2.isNotBlank()) Ligne("Téléphone 2", commande.clientTelephone2)
                if (commande.adresse.isNotBlank()) Ligne("Adresse", commande.adresse)
                if (commande.motif.isNotBlank()) Ligne("Motif", commande.motif)
            }

            TimelineStatuts(commande)

            Column(Modifier.fillMaxWidth().clip(FormeCarte).background(BlackCard).padding(16.dp)) {
                commande.lignes.forEach { l ->
                    Ligne("${l.produitNom} × ${l.quantite}", "%,d F".format(Locale.FRANCE, l.montantTotal))
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = BlackBorder)
                if (commande.reduction > 0) {
                    Ligne("Réduction", "-%,d F".format(Locale.FRANCE, commande.reduction))
                }
                if (commande.fraisLivraison > 0) {
                    Ligne(
                        if (commande.mode == "expedition") "Expédition" else "Livraison",
                        "%,d F".format(Locale.FRANCE, commande.fraisLivraison)
                    )
                }
                Ligne("Total", "%,d FCFA".format(Locale.FRANCE, commande.total), gras = true)
            }

            SuiviEnvoi(ventesViewModel) { }

            if (commande.modifiable) {
                OutlinedButton(
                    onClick = { onModifier(commande) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary)
                ) { Text("Modifier la commande") }
            }
            if (commande.annulable) {
                TextButton(onClick = { confirmation = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Annuler la commande", color = RedAlert)
                }
            }
        }

        if (confirmation) {
            AlertDialog(
                onDismissRequest = { confirmation = false },
                containerColor = BlackCard,
                title = { Text("Annuler la commande ?", color = WhitePrimary, fontWeight = FontWeight.Bold) },
                text = { Text("Le stock réservé sera libéré.", color = WhiteSub) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmation = false
                        ventesViewModel.annulerCommandeServeur(commande.docId)
                    }) { Text("Annuler la commande", color = RedAlert, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmation = false }) { Text("Retour", color = WhiteSub) }
                }
            )
        }
    }
}

@Composable
private fun Ligne(libelle: String, valeur: String, gras: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(libelle, color = WhiteSub, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            valeur,
            color = WhitePrimary,
            fontSize = 14.sp,
            fontWeight = if (gras) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TimelineStatuts(commande: Commande) {
    val format = remember { SimpleDateFormat("dd/MM à HH:mm", Locale.FRANCE) }
    val terminee = commande.statut in setOf("annulee", "retournee")
    val etapes = if (terminee) commande.historique.map { it.statut } else etapesPour(commande.mode)
    val atteintes = commande.historique.associateBy { it.statut }

    Column(Modifier.fillMaxWidth().clip(FormeCarte).background(BlackCard).padding(16.dp)) {
        etapes.forEachIndexed { i, statut ->
            val etape = atteintes[statut]
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .background(if (etape != null) couleurStatut(statut) else BlackSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (etape != null) {
                            Icon(Icons.Filled.Check, null, tint = BlackDeep, modifier = Modifier.size(14.dp))
                        }
                    }
                    if (i < etapes.lastIndex) {
                        Box(Modifier.width(2.dp).height(26.dp).background(BlackBorder))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        libelleStatut(statut),
                        color = if (etape != null) WhitePrimary else WhiteFaint,
                        fontSize = 14.sp,
                        fontWeight = if (etape != null) FontWeight.Medium else FontWeight.Normal
                    )
                    if (etape != null && etape.dateMillis > 0) {
                        Text(format.format(Date(etape.dateMillis)), color = WhiteSub, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
