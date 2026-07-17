package com.benjo.fboplus.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.Product
import com.benjo.fboplus.data.reductionPourGrade
import com.benjo.fboplus.viewmodel.ProfilViewModel
import com.benjo.fboplus.viewmodel.VentesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NouvelleCommandeScreen(
    ventesViewModel: VentesViewModel,
    profilViewModel: ProfilViewModel,
    onCommandeValidee: () -> Unit,
    onAnnuler: () -> Unit
) {
    val lignes by ventesViewModel.commandeEnCours.collectAsState()
    val titre by ventesViewModel.titreCommande.collectAsState()
    val profil by profilViewModel.profil.collectAsState()
    var recherche by remember { mutableStateOf("") }
    var recapExpanded by remember { mutableStateOf(false) }

    val produitsTries = remember { ventesViewModel.getProduitsTries() }
    val produitsFiltres = remember(recherche) {
        if (recherche.isEmpty()) produitsTries
        else produitsTries.filter {
            it.nameFr.contains(recherche, ignoreCase = true) ||
                    it.ref.contains(recherche, ignoreCase = true)
        }
    }

    // ── Calcul du prix de validation selon le grade du profil ──
    val reduction = reductionPourGrade(profil.grade)
    val caCommande = lignes.sumOf { it.montantTotal }
    val prixValidationCommande = (caCommande * (1.0 - reduction / 100.0)).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── TOP BAR ──
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
                        "Nouvelle Commande",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = WhitePrimary
                    )
                }
            }

            // ── LISTE PRODUITS ──
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
                Text("👆 Cliquez sur un produit pour l'ajouter", fontSize = 11.sp, color = WhiteFaint)
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

            // ── PARTIE BASSE ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlackCard)
                    .padding(14.dp)
            ) {
                Column {
                    if (lignes.isNotEmpty()) {

                        // Barre résumé cliquable
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
                                    if (recapExpanded) "▲" else "▼ Voir",
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

                        // Détail dépliable
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
                                                "${ligne.produitNom} ×${ligne.quantite}",
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

                    // Bouton valider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (lignes.isNotEmpty()) GoldGradient
                                else Brush.linearGradient(listOf(BlackBorder, BlackBorder))
                            )
                            .clickable(enabled = lignes.isNotEmpty()) {
                                ventesViewModel.validerCommande(profil.grade)
                                onCommandeValidee()
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✅ Valider la commande",
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

@Composable
fun CommandeProduitItem(
    product: Product,
    quantiteEnCours: Int,
    onClick: () -> Unit
) {
    val accent = CATEGORIES_UI[product.categorie]?.first ?: GoldPrimary
    val emoji = CATEGORIES_UI[product.categorie]?.second ?: "📦"
    val isAdded = quantiteEnCours > 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isAdded) BlackSurface else BlackCard)
            .border(
                1.dp,
                if (isAdded) GoldPrimary.copy(0.5f) else BlackBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // ── PHOTO DU PRODUIT (avec repli emoji) ──
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(
                            1.dp,
                            if (isAdded) GoldPrimary.copy(0.5f) else accent.copy(0.3f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val imageValide = remember(product.ref, product.imageRes) {
                        product.imageRes != 0 && runCatching {
                            context.resources.getResourceName(product.imageRes)
                        }.isSuccess
                    }

                    if (imageValide) {
                        Image(
                            painter = painterResource(id = product.imageRes),
                            contentDescription = product.nameFr,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(emoji, fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        product.nameFr,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isAdded) GoldPrimary else WhitePrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Réf: ${product.ref} · ${product.categorie}",
                        fontSize = 10.sp,
                        color = WhiteFaint
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "%,d F".format(product.prixPublic),
                    fontWeight = FontWeight.ExtraBold,
                    color = GoldPrimary,
                    fontSize = 13.sp
                )
                Text("CC: ${product.cc}", fontSize = 10.sp, color = GoldLight)
                if (isAdded) {
                    Text(
                        "× $quantiteEnCours",
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}