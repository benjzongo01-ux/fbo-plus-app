package com.benjo.fboplus.ui.screens

import com.benjo.fboplus.ui.theme.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benjo.fboplus.data.Product
import com.benjo.fboplus.data.VentesRepository
import com.benjo.fboplus.viewmodel.ProductViewModel
import com.benjo.fboplus.viewmodel.VentesViewModel

val CATEGORIES_UI = mapOf(
    "Tous"                to Pair(GoldPrimary,      "🌿"),
    "Buvable"             to Pair(Color(0xFF00C853), "🥤"),
    "Produit de la ruche" to Pair(Color(0xFFFFB300), "🍯"),
    "Soins de la peau"    to Pair(Color(0xFFAA00FF), "✨"),
    "Soins personnels"    to Pair(Color(0xFF2979FF), "🧴"),
    "Nutrition"           to Pair(Color(0xFFFF6D00), "💊"),
    "Gestion de poids"    to Pair(Color(0xFF00BCD4), "⚖️"),
    "Combo Packs"         to Pair(Color(0xFFD4AF37), "📦"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: ProductViewModel = viewModel(),
    ventesViewModel: VentesViewModel,
    onNouvelleCommande: () -> Unit = {},
    listState: LazyListState = rememberLazyListState()
) {
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategorie by viewModel.selectedCategorie.collectAsState()
    val categories = viewModel.getCategories()
    var selectedProduct by remember { mutableStateOf<Product?>(null) }

    val commandesDuJour by ventesViewModel.commandesDuJour.collectAsState()
    val caDuJour = commandesDuJour.sumOf { it.caTotal }
    val ccDuJour = commandesDuJour.sumOf { it.ccTotal }

    val context = LocalContext.current
    val moisActuel = remember { VentesRepository.moisAnneeAujourdhui() }
    val commandesDuMois = remember(commandesDuJour) {
        VentesRepository.getCommandesDuMois(context, moisActuel)
    }
    val ccDuMois = commandesDuMois.sumOf { it.ccTotal }
    val estActif = ccDuMois >= 4.0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackDeep)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // ── HEADER ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(colors = listOf(BlackCard, BlackDeep))
                        )
                        .padding(top = 52.dp, start = 20.dp, end = 20.dp, bottom = 24.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    "FBO+",
                                    style = TextStyle(
                                        brush = GoldGradientH,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                )
                                Text(
                                    "Forever Business Office",
                                    fontSize = 11.sp,
                                    color = WhiteSub
                                )
                            }

                            // ── Colonne de droite : badge + carte Aujourd'hui, MÊME LARGEUR ──
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.width(IntrinsicSize.Max)
                            ) {
                                // Badge ACTIF / NON ACTIF — largeur fixée par la carte du dessous
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (estActif) Color(0xFF00C853).copy(alpha = 0.15f)
                                            else Color(0xFFFF1744).copy(alpha = 0.15f)
                                        )
                                        .border(
                                            1.dp,
                                            if (estActif) Color(0xFF00C853) else Color(0xFFFF1744),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (estActif) "ACTIF" else "NON ACTIF: %.3f CC".format(ccDuMois),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (estActif) Color(0xFF00C853) else Color(0xFFFF1744),
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Carte Aujourd'hui — détermine la largeur du groupe
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(BlackSurface)
                                        .border(1.dp, GoldGlow, RoundedCornerShape(14.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Aujourd'hui", fontSize = 9.sp, color = WhiteFaint)
                                        Text(
                                            "%,d F".format(caDuJour),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = GoldPrimary
                                        )
                                        Text(
                                            "%.3f CC".format(ccDuJour),
                                            fontSize = 11.sp,
                                            color = GoldLight,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(BlackSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (searchQuery.isNotEmpty()) GoldPrimary else BlackBorder,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = if (searchQuery.isNotEmpty()) GoldPrimary else WhiteFaint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(color = WhitePrimary, fontSize = 14.sp),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text("Rechercher un produit ou réf...", color = WhiteFaint, fontSize = 14.sp)
                                        }
                                        innerTextField()
                                    },
                                    singleLine = true
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.setSearchQuery("") },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = null,
                                            tint = WhiteFaint,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── FILTRES CATÉGORIES ──
            item {
                ScrollableTabRow(
                    selectedTabIndex = categories.indexOf(selectedCategorie).coerceAtLeast(0),
                    containerColor = BlackDeep,
                    contentColor = GoldPrimary,
                    edgePadding = 16.dp,
                    indicator = {},
                    divider = {}
                ) {
                    categories.forEach { cat ->
                        val selected = selectedCategorie == cat
                        val emoji = CATEGORIES_UI[cat]?.second ?: "📦"
                        Tab(
                            selected = selected,
                            onClick = { viewModel.setCategorie(cat) },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) GoldFaint else BlackSurface)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) GoldPrimary else BlackBorder,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    if (cat == "Tous") "🌿 Tous" else "$emoji $cat",
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) GoldPrimary else WhiteSub
                                )
                            }
                        }
                    }
                }
            }

            // ── COMPTEUR ──
            item {
                Text(
                    "  ${products.size} produit(s)",
                    fontSize = 11.sp,
                    color = WhiteFaint,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            // ── LISTE PRODUITS AVEC IMAGES ──
            items(products) { product ->
                PremiumProductCard(
                    product = product,
                    onClick = { selectedProduct = product }
                )
            }
        }
    }

    // ── MODAL PRODUIT AVEC GRANDE PHOTO ──
    selectedProduct?.let { product ->
        PremiumProductModal(
            product = product,
            onClose = { selectedProduct = null }
        )
    }
}

@Composable
fun PremiumProductCard(
    product: Product,
    onClick: () -> Unit
) {
    val accent = CATEGORIES_UI[product.categorie]?.first ?: GoldPrimary
    val emoji = CATEGORIES_UI[product.categorie]?.second ?: "📦"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BlackCard)
            .border(
                width = 1.dp,
                color = GoldPrimary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(70.dp)
                .align(Alignment.CenterStart)
                .background(
                    Brush.verticalGradient(colors = listOf(GoldLight, GoldPrimary.copy(0.2f)))
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── PHOTO DU PRODUIT ──
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, GoldPrimary.copy(0.2f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageRes != 0) {
                    Image(
                        painter = painterResource(id = product.imageRes),
                        contentDescription = product.nameFr,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(emoji, fontSize = 22.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.nameFr,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = WhitePrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(0.12f))
                            .border(1.dp, accent.copy(0.25f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            product.categorie,
                            fontSize = 9.sp,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Réf: ${product.ref}", fontSize = 9.sp, color = WhiteFaint)
                }
                Spacer(modifier = Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "%,d F".format(product.prixPublic),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = GoldPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GoldFaint)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "CC ${product.cc}",
                            fontSize = 10.sp,
                            color = GoldLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PremiumProductModal(
    product: Product,
    onClose: () -> Unit
) {
    val accent = CATEGORIES_UI[product.categorie]?.first ?: GoldPrimary
    val emoji = CATEGORIES_UI[product.categorie]?.second ?: "📦"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(BlackCard)
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(1.dp, GoldPrimary.copy(0.3f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (product.imageRes != 0) {
                        Image(
                            painter = painterResource(id = product.imageRes),
                            contentDescription = product.nameFr,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(emoji, fontSize = 70.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    product.nameFr,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = WhitePrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(product.categorie, fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Réf: ${product.ref}", fontSize = 11.sp, color = WhiteFaint)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GoldPrimary.copy(0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Prix public", fontSize = 10.sp, color = WhiteFaint)
                        Text(
                            "%,d FCFA".format(product.prixPublic),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = GoldPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("CC", fontSize = 10.sp, color = WhiteFaint)
                        Text(
                            "${product.cc}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = GoldLight
                        )
                    }
                }

                if (product.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("📋 Description", fontSize = 11.sp, color = WhiteFaint, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        product.description,
                        fontSize = 13.sp,
                        color = WhiteSub,
                        lineHeight = 19.sp
                    )
                }

                if (product.pathologies.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("🏥 Pathologies", fontSize = 11.sp, color = WhiteFaint, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        product.pathologies.forEach { patho ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(GoldFaint)
                                    .border(1.dp, GoldPrimary.copy(0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(patho, fontSize = 11.sp, color = GoldPrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BlackSurface)
                        .border(1.dp, BlackBorder, RoundedCornerShape(16.dp))
                        .clickable { onClose() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Fermer", color = WhiteFaint, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
