package com.benjo.fboplus.ui.screens

import com.benjo.fboplus.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.Commande
import com.benjo.fboplus.viewmodel.ProfilViewModel
import com.benjo.fboplus.viewmodel.VentesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentesScreen(
    ventesViewModel: VentesViewModel,
    onNouvelleCommande: () -> Unit,
    onModifierCommande: () -> Unit,
    profilViewModel: ProfilViewModel
) {
    val commandesDuJour by ventesViewModel.commandesDuJour.collectAsState()
    val suggererCloture by ventesViewModel.suggererClotureMois.collectAsState()
    val profil by profilViewModel.profil.collectAsState()
    val context = LocalContext.current

    val caDuJour = commandesDuJour.sumOf { it.caTotal }
    val ccDuJour = commandesDuJour.sumOf { it.ccTotal }
    val pvDuJour = commandesDuJour.sumOf { it.prixValidation }
    val bnfDuJour = commandesDuJour.sumOf { it.beneficeTotal }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackDeep)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 52.dp, start = 16.dp,
                end = 16.dp, bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // â”€â”€ HEADER â”€â”€
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Mes Ventes",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldPrimary
                        )
                        Text(
                            "Suivi de vos commandes",
                            fontSize = 11.sp,
                            color = WhiteSub
                        )
                    }
                    Text(
                        "${commandesDuJour.size} cmd",
                        fontSize = 12.sp,
                        color = WhiteFaint
                    )
                }
            }

            // â”€â”€ Suggestion clÃ´ture mois â”€â”€
            if (suggererCloture) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(BlackCard)
                            .border(1.dp, GoldPrimary.copy(0.4f), RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                "ðŸ“… Fin de mois approche !",
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                "Voulez-vous clÃ´turer le mois en cours ?",
                                fontSize = 12.sp,
                                color = WhiteSub
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(GoldGradientH)
                                        .clickable { ventesViewModel.cloreMois() }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        "âœ… ClÃ´turer",
                                        color = BlackDeep,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BlackSurface)
                                        .border(1.dp, BlackBorder, RoundedCornerShape(12.dp))
                                        .clickable { ventesViewModel.ignorerSuggestionCloture() }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text("Plus tard", color = WhiteSub, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // â”€â”€ TABLEAU DE BORD DU JOUR â”€â”€
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1A1500), BlackCard)
                            )
                        )
                        .border(1.dp, GoldPrimary.copy(0.3f), RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Text("ðŸ“Š Aujourd'hui", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Chiffre d'affaires", color = WhiteFaint, fontSize = 11.sp)
                                Text(
                                    "%,d FCFA".format(caDuJour),
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("CC acquis", color = WhiteFaint, fontSize = 11.sp)
                                Text(
                                    "%.3f CC".format(ccDuJour),
                                    color = GoldLight,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = GoldPrimary.copy(0.15f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Prix de validation", color = WhiteFaint, fontSize = 11.sp)
                                Text(
                                    "%,d FCFA".format(pvDuJour),
                                    color = BlueAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("BÃ©nÃ©fice", color = WhiteFaint, fontSize = 11.sp)
                                Text(
                                    "%,d FCFA".format(bnfDuJour),
                                    color = GreenPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = GoldPrimary.copy(0.15f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "${commandesDuJour.size} commande(s) enregistrÃ©e(s)",
                            color = WhiteSub,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // â”€â”€ BOUTON NOUVELLE COMMANDE â”€â”€
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(GoldGradientH)
                        .clickable { onNouvelleCommande() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "âž• Nouvelle Commande",
                        color = BlackDeep,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            }

            // â”€â”€ LISTE COMMANDES â”€â”€
            if (commandesDuJour.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ðŸ›’", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Aucune commande aujourd'hui",
                                color = WhiteSub,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Appuyez sur + pour commencer",
                                color = WhiteFaint,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(commandesDuJour.reversed()) { commande ->
                    GoldCommandeCard(
                        commande = commande,
                        onSupprimer = { ventesViewModel.supprimerCommande(commande.id) },
                        onModifier = {
                            ventesViewModel.chargerCommandePourModification(commande)
                            onModifierCommande()
                        },
                        nomDistributeur = "${profil.prenom} ${profil.nom}".trim(),
                        idDistributeur = profil.idDistributeur,
                        context = context
                    )
                }
            }
        }
    }
}

@Composable
fun GoldCommandeCard(
    commande: Commande,
    onSupprimer: () -> Unit,
    onModifier: () -> Unit,
    nomDistributeur: String = "",
    idDistributeur: String = "",
    context: android.content.Context
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BlackCard)
            .border(1.dp, GoldPrimary.copy(0.2f), RoundedCornerShape(18.dp))
    ) {
        // Barre gauche dorÃ©e
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GoldLight, GoldPrimary.copy(0.2f))
                    )
                )
        )

        Column(
            modifier = Modifier.padding(
                start = 16.dp, end = 14.dp,
                top = 14.dp, bottom = 14.dp
            )
        ) {
            // En-tÃªte
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        commande.titre.ifBlank { "Commande" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = WhitePrimary
                    )
                    Text(
                        "${commande.dateStr} Â· ${commande.lignes.size} produit(s)",
                        fontSize = 11.sp,
                        color = WhiteFaint
                    )
                }
                Row {
                    // Partager avec nom + ID distributeur
                    IconButton(onClick = {
                        val texte = buildString {
                            appendLine("ðŸŒ¿ *FBO+ â€” Commande Forever Living*")
                            appendLine()
                            if (nomDistributeur.isNotEmpty()) {
                                appendLine("ðŸ‘¤ *$nomDistributeur*")
                            }
                            if (idDistributeur.isNotEmpty()) {
                                appendLine("ðŸ†” ID: $idDistributeur")
                            }
                            appendLine("ðŸ“… ${commande.dateStr}")
                            appendLine("ðŸ“‹ *${commande.titre.ifBlank { "Commande" }}*")
                            appendLine("â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€")
                            commande.lignes.forEach { ligne ->
                                appendLine("â€¢ ${ligne.produitNom} Ã—${ligne.quantite} â†’ %,d FCFA".format(ligne.montantTotal))
                            }
                            appendLine("â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€")
                            appendLine("ðŸ’° *CA Total : %,d FCFA*".format(commande.caTotal))
                            appendLine("â­ *CC Total : %.3f CC*".format(commande.ccTotal))
                            appendLine("ðŸ’³ *Prix de validation : %,d FCFA*".format(commande.prixValidation))
                            appendLine("ðŸ“ˆ *BÃ©nÃ©fice : %,d FCFA*".format(commande.beneficeTotal))
                        }
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_SEND
                        ).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, texte)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(intent, "Partager via...")
                        )
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = Color(0xFF2979FF))
                    }
                    IconButton(onClick = onModifier) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = GoldPrimary)
                    }
                    IconButton(onClick = onSupprimer) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFFF4444))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Totaux CA / CC
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "%,d FCFA".format(commande.caTotal),
                    fontWeight = FontWeight.ExtraBold,
                    color = GoldPrimary,
                    fontSize = 17.sp
                )
                Text(
                    "%.3f CC".format(commande.ccTotal),
                    fontWeight = FontWeight.ExtraBold,
                    color = GoldLight,
                    fontSize = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Totaux PV / Bnf
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "PV : %,d FCFA".format(commande.prixValidation),
                    fontWeight = FontWeight.Medium,
                    color = BlueAccent,
                    fontSize = 12.sp
                )
                Text(
                    "Bnf : %,d FCFA".format(commande.beneficeTotal),
                    fontWeight = FontWeight.Medium,
                    color = GreenPrimary,
                    fontSize = 12.sp
                )
            }

            // Voir dÃ©tail
            Row(
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (expanded) "â–² Masquer le dÃ©tail" else "â–¼ Voir le dÃ©tail",
                    fontSize = 12.sp,
                    color = GoldPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = GoldPrimary.copy(0.15f))
                Spacer(modifier = Modifier.height(8.dp))
                commande.lignes.forEach { ligne ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${ligne.produitNom} Ã—${ligne.quantite}",
                            fontSize = 12.sp,
                            color = WhiteSub,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "%,d F".format(ligne.montantTotal),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    }
                }
            }
        }
    }
}
