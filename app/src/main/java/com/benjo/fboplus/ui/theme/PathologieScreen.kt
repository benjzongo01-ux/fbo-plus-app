package com.benjo.fboplus.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.PATHOLOGIES
import com.benjo.fboplus.data.PathologieInfo
import com.benjo.fboplus.data.ProductRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathologieScreen() {

    var recherche by remember { mutableStateOf("") }
    var pathologieSelectionnee by remember { mutableStateOf<PathologieInfo?>(null) }

    val pathologiesFiltrees = remember(recherche) {
        if (recherche.isEmpty()) PATHOLOGIES
        else PATHOLOGIES.filter {
            it.nom.contains(recherche, ignoreCase = true) ||
                    it.pathologiesSimilaires.any { s -> s.contains(recherche, ignoreCase = true) }
        }
    }

    // ── ÉCRAN DÉTAIL D'UNE PATHOLOGIE ──
    pathologieSelectionnee?.let { patho ->
        val produits = remember(patho) {
            ProductRepository.allProducts.filter { p ->
                patho.produitsRefs.contains(p.ref)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BlackDeep),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BlackCard)
                        .padding(top = 52.dp, start = 16.dp, end = 16.dp, bottom = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { pathologieSelectionnee = null }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = GoldPrimary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${patho.emoji} ${patho.nom}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = GoldPrimary
                        )
                    }
                }
            }

            // Description
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BlackCard)
                        .border(1.dp, GoldPrimary.copy(0.2f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("📋 Description", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(patho.description, fontSize = 13.sp, color = WhiteSub, lineHeight = 20.sp)
                    }
                }
            }

            // Symptômes
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BlackCard)
                        .border(1.dp, Color(0xFF2979FF).copy(0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("🔍 Symptômes", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2979FF))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(patho.symptomes, fontSize = 13.sp, color = WhiteSub, lineHeight = 20.sp)
                    }
                }
            }

            // Risques si négligé
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BlackCard)
                        .border(1.dp, Color(0xFFFF1744).copy(0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("⚠️ Risques si négligé", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFF1744))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(patho.risquesSiNeglige, fontSize = 13.sp, color = WhiteSub, lineHeight = 20.sp)
                    }
                }
            }

            // Pathologies similaires
            if (patho.pathologiesSimilaires.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BlackSurface)
                            .border(1.dp, BlackBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            patho.pathologiesSimilaires.joinToString(" · "),
                            fontSize = 12.sp,
                            color = WhiteFaint,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Titre produits
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "  💊 Produits entrant dans ce traitement (${produits.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = GoldPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Liste produits
            items(produits) { product ->
                val accent = CATEGORIES_UI[product.categorie]?.first ?: GoldPrimary
                val emoji = CATEGORIES_UI[product.categorie]?.second ?: "📦"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BlackCard)
                        .border(1.dp, GoldPrimary.copy(0.15f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.dp, GoldPrimary.copy(0.2f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (product.imageRes != 0) {
                                Image(
                                    painter = painterResource(id = product.imageRes),
                                    contentDescription = product.nameFr,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(emoji, fontSize = 24.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                product.nameFr,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = WhitePrimary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accent.copy(0.12f))
                                    .border(1.dp, accent.copy(0.25f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(product.categorie, fontSize = 9.sp, color = accent, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            if (product.description.isNotEmpty()) {
                                Text(
                                    product.description,
                                    fontSize = 11.sp,
                                    color = WhiteSub,
                                    lineHeight = 16.sp,
                                    maxLines = 3
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "%,d FCFA".format(product.prixPublic),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = GoldPrimary
                                )
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
        }
        return
    }

    // ── ÉCRAN LISTE DES PATHOLOGIES ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackDeep)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BlackCard)
                .padding(top = 52.dp, start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Column {
                Text(
                    "🏥 Santé",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    color = GoldPrimary
                )
                Text(
                    "Trouve les produits pour ta pathologie",
                    fontSize = 11.sp,
                    color = WhiteSub
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(BlackSurface)
                        .border(
                            1.dp,
                            if (recherche.isNotEmpty()) GoldPrimary else BlackBorder,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = if (recherche.isNotEmpty()) GoldPrimary else WhiteFaint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = recherche,
                            onValueChange = { recherche = it },
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = WhitePrimary,
                                fontSize = 14.sp
                            ),
                            decorationBox = { innerTextField ->
                                if (recherche.isEmpty()) {
                                    Text("Rechercher une pathologie...", color = WhiteFaint, fontSize = 14.sp)
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
                                Icon(Icons.Filled.Close, contentDescription = null, tint = WhiteFaint, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pathologiesFiltrees) { patho ->
                val nbProduits = ProductRepository.allProducts.count { p ->
                    patho.produitsRefs.contains(p.ref)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BlackCard)
                        .border(1.dp, GoldPrimary.copy(0.15f), RoundedCornerShape(16.dp))
                        .clickable { pathologieSelectionnee = patho }
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${patho.emoji} ${patho.nom}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = WhitePrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GoldFaint)
                                    .border(1.dp, GoldPrimary.copy(0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "$nbProduits produits",
                                    fontSize = 10.sp,
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (patho.pathologiesSimilaires.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                patho.pathologiesSimilaires.joinToString(" · "),
                                fontSize = 10.sp,
                                color = WhiteFaint,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}