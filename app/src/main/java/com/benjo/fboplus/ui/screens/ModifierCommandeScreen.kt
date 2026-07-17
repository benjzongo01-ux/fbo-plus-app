package com.benjo.fboplus.ui.screens

import com.benjo.fboplus.ui.theme.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.reductionPourGrade
import com.benjo.fboplus.viewmodel.ProfilViewModel
import com.benjo.fboplus.viewmodel.VentesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifierCommandeScreen(
    ventesViewModel: VentesViewModel,
    profilViewModel: ProfilViewModel,
    onCommandeMiseAJour: () -> Unit,
    onAnnuler: () -> Unit
) {
    val lignes by ventesViewModel.commandeEnCours.collectAsState()
    val titre by ventesViewModel.titreCommande.collectAsState()
    val profil by profilViewModel.profil.collectAsState()
    var recherche by remember { mutableStateOf("") }
    var recapExpanded by remember { mutableStateOf(true) }

    val produitsTries = remember { ventesViewModel.getProduitsTries() }
    val produitsFiltres = remember(recherche) {
        if (recherche.isEmpty()) produitsTries
        else produitsTries.filter {
            it.nameFr.contains(recherche, ignoreCase = true) ||
                    it.ref.contains(recherche, ignoreCase = true)
        }
    }

    // â”€â”€ Calcul du prix de validation selon le grade du profil â”€â”€
    val reduction = reductionPourGrade(profil.grade)
    val caCommande = lignes.sumOf { it.montantTotal }
    val prixValidationCommande = (caCommande * (1.0 - reduction / 100.0)).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // â”€â”€ TOP BAR â”€â”€
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlackCard)
                    .padding(top = 48.dp, start = 8.dp, end = 20.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onAnnuler) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = GoldPrimary
                        )
                    }
                    Text(
                        "Modifier la Commande",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = WhitePrimary
                    )
                }
            }

            // â”€â”€ LISTE PRODUITS â”€â”€
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Barre recherche
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BlackSurface)
                        .border(
                            1.dp,
                            if (recherche.isNotEmpty()) GoldPrimary else BlackBorder,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = if (recherche.isNotEmpty()) GoldPrimary else WhiteFaint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        BasicTextField(
                            value = recherche,
                            onValueChange = { recherche = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(color = WhitePrimary, fontSize = 14.sp),
                            decorationBox = { innerTextField ->
                                if (recherche.isEmpty()) {
                                    Text("Rechercher un produit...", color = WhiteFaint, fontSize = 14.sp)
                                }
                                innerTextField()
                            },
                            singleLine = true
                        )
                        if (recherche.isNotEmpty()) {
                            IconButton(
                                onClick = { recherche = "" },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = WhiteFaint,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "ðŸ‘† Cliquez pour ajouter Â· âœ• dans le rÃ©cap pour retirer",
                    fontSize = 11.sp,
                    color = WhiteFaint
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(produitsFiltres) { product ->
                        CommandeProduitItem(
                            product = product,
                            quantiteEnCours = lignes.find { it.produitRef == product.ref }?.quantite ?: 0,
                            onClick = {
                                ventesViewModel.ajouterProduit(
                                    produitRef = product.ref,
                                    produitNom = product.nameFr,
                                    prixUnitaire = product.prixPublic,
                                    ccUnitaire = product.cc
                                )
                            }
                        )
                    }
                }
            }

            // â”€â”€ PARTIE BASSE â”€â”€
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlackCard)
                    .padding(14.dp)
            ) {
                Column {
                    if (lignes.isNotEmpty()) {

                        // Barre rÃ©sumÃ© cliquable
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BlackSurface)
                                .border(1.dp, GoldPrimary.copy(0.3f), RoundedCornerShape(12.dp))
                                .clickable { recapExpanded = !recapExpanded }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        "%,d F".format(caCommande),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = GoldPrimary,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "%.3f CC".format(lignes.sumOf { it.ccTotal }),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = GoldLight,
                                        fontSize = 14.sp
                                    )
                                    Text("${lignes.size} art.", fontSize = 12.sp, color = WhiteFaint)
                                }
                                Text(
                                    if (recapExpanded) "â–² RÃ©duire" else "â–¼ Voir",
                                    fontSize = 12.sp,
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Prix de validation selon le grade
                            Text(
                                "Prix de validation (${profil.grade}, -${reduction.toInt()}%%) : %,d F".format(prixValidationCommande),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WhiteSub
                            )
                        }

                        // DÃ©tail dÃ©pliable
                        AnimatedVisibility(visible = recapExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BlackSurface)
                                    .border(1.dp, GoldPrimary.copy(0.15f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    lignes.forEach { ligne ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
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
                                            IconButton(
                                                onClick = { ventesViewModel.retirerLigne(ligne.produitRef) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFF4444),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Champ titre
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BlackSurface)
                            .border(1.dp, BlackBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 13.dp)
                    ) {
                        BasicTextField(
                            value = titre,
                            onValueChange = { ventesViewModel.setTitreCommande(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = WhitePrimary, fontSize = 14.sp),
                            decorationBox = { innerTextField ->
                                if (titre.isEmpty()) {
                                    Text("Titre de la commande (optionnel)", color = WhiteFaint, fontSize = 14.sp)
                                }
                                innerTextField()
                            },
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bouton enregistrer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (lignes.isNotEmpty()) GoldGradient
                                else Brush.linearGradient(listOf(BlackBorder, BlackBorder))
                            )
                            .clickable(enabled = lignes.isNotEmpty()) {
                                ventesViewModel.mettreAJourCommande(profil.grade)
                                onCommandeMiseAJour()
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "ðŸ’¾ Enregistrer les modifications",
                            color = if (lignes.isNotEmpty()) BlackDeep else WhiteFaint,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
