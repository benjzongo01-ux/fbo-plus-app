package com.benjo.fboplus.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.viewmodel.VentesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoriqueScreen(ventesViewModel: VentesViewModel) {

    val toutesCommandes = remember { ventesViewModel.getToutesCommandes() }

    // Grouper par mois
    val commandesParMois = remember {
        toutesCommandes
            .groupBy { it.moisAnnee }
            .toSortedMap(compareByDescending { it })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📅 Historique Mensuel", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1B5E20),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (commandesParMois.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Aucun historique disponible\nCommencez à enregistrer vos ventes !",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Résumé global ──
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "📊 Résumé global",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("CA Total", color = Color.White.copy(0.7f), fontSize = 11.sp)
                                    Text(
                                        "%,d FCFA".format(toutesCommandes.sumOf { it.caTotal }),
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("CC Total", color = Color.White.copy(0.7f), fontSize = 11.sp)
                                    Text(
                                        "%.3f CC".format(toutesCommandes.sumOf { it.ccTotal }),
                                        color = Color(0xFFFFCC80),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "${toutesCommandes.size} commande(s) · ${commandesParMois.size} mois",
                                color = Color.White.copy(0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // ── Un bloc par mois ──
                commandesParMois.forEach { (mois, commandes) ->
                    item {
                        MoisCard(
                            mois = mois,
                            commandes = commandes
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MoisCard(
    mois: String,
    commandes: List<com.benjo.fboplus.data.Commande>
) {
    var expanded by remember { mutableStateOf(false) }

    val caMois = commandes.sumOf { it.caTotal }
    val ccMois = commandes.sumOf { it.ccTotal }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── En-tête mois cliquable ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "📅 $mois",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color(0xFF1B5E20)
                    )
                    Text(
                        "${commandes.size} commande(s)",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF1B5E20)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Totaux du mois ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Chiffre d'affaires", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        "%,d FCFA".format(caMois),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1B5E20),
                        fontSize = 17.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("CC acquis", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        "%.3f CC".format(ccMois),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF6F00),
                        fontSize = 17.sp
                    )
                }
            }

            // ── Détail des commandes dépliable ──
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = Color(0xFFE0E0E0))
                    Spacer(modifier = Modifier.height(8.dp))

                    commandes.reversed().forEach { commande ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        commande.titre.ifBlank { "Commande" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        commande.dateStr,
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "%,d FCFA".format(commande.caTotal),
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20),
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "%.3f CC".format(commande.ccTotal),
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF6F00),
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                val prixValidation = (commande.caTotal * (100 - commande.reductionPourcentage) / 100.0).toInt()
                                val benefice = commande.caTotal - prixValidation

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Validation (-${commande.reductionPourcentage}%) : %,d FCFA".format(prixValidation),
                                        fontSize = 11.sp,
                                        color = Color(0xFF6D4C41)
                                    )
                                    Text(
                                        "Bénéfice : %,d FCFA".format(benefice),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    commande.lignes.joinToString(", ") {
                                        "${it.produitNom} ×${it.quantite}"
                                    },
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}