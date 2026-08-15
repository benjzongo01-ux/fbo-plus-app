package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.RappelScheduler
import com.benjo.fboplus.data.ProductRepository
import com.benjo.fboplus.data.VentesRepository
import com.benjo.fboplus.viewmodel.ProfilViewModel
import com.benjo.fboplus.viewmodel.VentesViewModel

// ── Palette noir/or (cohérente avec CommandeScreen / PathologieScreen) ──
private val PGoldPrimary = Color(0xFFD4AF37)
private val PGoldLight = Color(0xFFFFE58A)
private val PBackgroundBlack = Color(0xFF000000)
private val PBlackCard = Color(0xFF1A1A1A)
private val PBlackCardLight = Color(0xFF242424)
private val PTextWhite = Color(0xFFFFFFFF)
private val PTextGray = Color(0xFFAAAAAA)
private val PTrackDark = Color(0xFF3A3A3A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilScreen(
    profilViewModel: ProfilViewModel,
    ventesViewModel: VentesViewModel,
    onModifierProfil: () -> Unit,
    onSecurite: () -> Unit,
    onDeconnexion: () -> Unit
) {
    val profil by profilViewModel.profil.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var modeEditionObjectifs by remember { mutableStateOf(false) }
    var objectifCC by remember(profil) { mutableStateOf(profil.objectifCC.toString()) }
    var objectifCA by remember(profil) { mutableStateOf(profil.objectifCA.toString()) }
    var rappelActive by remember { mutableStateOf(false) }
    var heureRappel by remember { mutableStateOf("19:00") }
    val prixParCC = remember { ProductRepository.getPrixParCC() }

    val ctx = LocalContext.current
    val moisActuel = remember { VentesRepository.moisAnneeAujourdhui() }
    val commandesDuJour by ventesViewModel.commandesDuJour.collectAsState()
    val commandesDuMois = remember(commandesDuJour) {
        VentesRepository.getCommandesDuMois(ctx, moisActuel)
    }
    val caDuMois = commandesDuMois.sumOf { it.caTotal }
    val ccDuMois = commandesDuMois.sumOf { it.ccTotal }
    val beneficeDuMois = remember(commandesDuJour) {
        VentesRepository.getStatMoisActuel(ctx).benefice
    }

    val objCC = profil.objectifCC
    val objCA = profil.objectifCA

    Scaffold(
        containerColor = PBackgroundBlack,
        topBar = {
            TopAppBar(
                title = { Text("👤 Mon Profil", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PTextWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PBlackCard,
                    titleContentColor = PTextWhite
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PBackgroundBlack)
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Carte fixe : identité (non éditable directement ici) ──
            Card(
                colors = CardDefaults.cardColors(containerColor = PBlackCard),
                border = BorderStroke(1.dp, PGoldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (profil.prenom.isNotEmpty() || profil.nom.isNotEmpty())
                                    "${profil.prenom} ${profil.nom}".trim()
                                else "Mon Profil FBO+",
                                color = PTextWhite,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                            Text(profil.grade, color = PGoldLight, fontSize = 13.sp)
                            if (profil.ville.isNotEmpty() || profil.pays.isNotEmpty()) {
                                Text(
                                    listOf(profil.ville, profil.pays).filter { it.isNotEmpty() }.joinToString(", "),
                                    color = PTextGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        IconButton(onClick = onModifierProfil) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Modifier",
                                tint = PGoldPrimary
                            )
                        }
                    }

                    if (profil.idDistributeur.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PBlackCardLight, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("ID Forever Living", color = PTextGray, fontSize = 11.sp)
                                Text(
                                    profil.idDistributeur,
                                    color = PGoldPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(profil.idDistributeur))
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copier l'ID",
                                    tint = PGoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = PGoldPrimary.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("CA du mois", color = PTextGray, fontSize = 11.sp)
                            Text(
                                "%,d FCFA".format(caDuMois),
                                color = PTextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("CC du mois", color = PTextGray, fontSize = 11.sp)
                            Text(
                                "%.3f CC".format(ccDuMois),
                                color = PGoldPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PBlackCardLight, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💰 Bénéfice du mois", color = PGoldLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "%,d FCFA".format(beneficeDuMois),
                            color = PGoldLight,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // ── Objectifs + barres ──
            Card(
                colors = CardDefaults.cardColors(containerColor = PBlackCard),
                border = BorderStroke(1.dp, PGoldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎯 Objectifs du mois", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PGoldPrimary)
                        IconButton(onClick = { modeEditionObjectifs = !modeEditionObjectifs }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modifier les objectifs", tint = PGoldPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (objCC > 0) {
                        val progressCC = (ccDuMois / objCC).coerceIn(0.0, 1.0)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Objectif CC", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PTextWhite)
                            Text("%.3f CC".format(objCC), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PTextWhite)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progressCC.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            color = PGoldPrimary,
                            trackColor = PTrackDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Réalisé : %.3f CC".format(ccDuMois), fontSize = 11.sp, color = PTextGray)
                            Text("${(progressCC * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PGoldPrimary)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    if (objCA > 0) {
                        val progressCA = (caDuMois.toDouble() / objCA).coerceIn(0.0, 1.0)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Objectif CA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PGoldLight)
                            Text("%,d FCFA".format(objCA), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PGoldLight)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progressCA.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            color = PGoldLight,
                            trackColor = PTrackDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Réalisé : %,d FCFA".format(caDuMois), fontSize = 11.sp, color = PTextGray)
                            Text("${(progressCA * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PGoldLight)
                        }
                    }

                    if (modeEditionObjectifs) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = PGoldPrimary.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = objectifCC,
                            onValueChange = {
                                objectifCC = it
                                val cc = it.toDoubleOrNull()
                                if (cc != null && cc > 0) objectifCA = (cc * prixParCC).toInt().toString()
                            },
                            label = { Text("Objectif CC du mois") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = champColors()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = objectifCA,
                            onValueChange = {
                                objectifCA = it
                                val ca = it.toIntOrNull()
                                if (ca != null && ca > 0 && prixParCC > 0) objectifCC = "%.3f".format(ca / prixParCC)
                            },
                            label = { Text("Objectif CA du mois (FCFA)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = champColors()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                profilViewModel.sauvegarderProfil(
                                    profil.copy(
                                        objectifCC = objectifCC.toDoubleOrNull() ?: 0.0,
                                        objectifCA = objectifCA.toIntOrNull() ?: 0
                                    )
                                )
                                modeEditionObjectifs = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PGoldPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💾 Sauvegarder", color = PBackgroundBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Rappel quotidien ──
            Card(
                colors = CardDefaults.cardColors(containerColor = PBlackCard),
                border = BorderStroke(1.dp, PGoldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("⏰ Rappel quotidien", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PGoldPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Activer le rappel", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PTextWhite)
                            Text("Notification chaque jour à l'heure choisie", fontSize = 11.sp, color = PTextGray)
                        }
                        Switch(
                            checked = rappelActive,
                            onCheckedChange = {
                                rappelActive = it
                                if (it) {
                                    val parts = heureRappel.split(":")
                                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 19
                                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                    RappelScheduler.planifierRappelQuotidien(context, h, m)
                                } else {
                                    RappelScheduler.annulerRappel(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PBackgroundBlack,
                                checkedTrackColor = PGoldPrimary,
                                uncheckedThumbColor = PTextGray,
                                uncheckedTrackColor = PTrackDark
                            )
                        )
                    }
                    if (rappelActive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = heureRappel,
                            onValueChange = { nouvelle ->
                                heureRappel = nouvelle
                                val parts = nouvelle.split(":")
                                val h = parts.getOrNull(0)?.toIntOrNull()
                                val m = parts.getOrNull(1)?.toIntOrNull()
                                if (h != null && m != null && h in 0..23 && m in 0..59) {
                                    RappelScheduler.planifierRappelQuotidien(context, h, m)
                                }
                            },
                            label = { Text("Heure du rappel", color = PTextGray) },
                            placeholder = { Text("19:00", color = PTextGray) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = champColors()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "⏰ Rappel planifié chaque jour à $heureRappel",
                            fontSize = 11.sp,
                            color = PGoldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Sécurité (fixée en bas, futurs ajouts en dessous) ──
            Card(
                colors = CardDefaults.cardColors(containerColor = PBlackCard),
                border = BorderStroke(1.dp, PGoldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSecurite)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("🔒 Sécurité", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PTextWhite)
                        Text("PIN, biométrie", fontSize = 11.sp, color = PTextGray)
                    }
                    Text("›", color = PTextGray, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = onDeconnexion,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE84142).copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE84142))
            ) {
                Text("Déconnexion", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun champColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PTextWhite,
    unfocusedTextColor = PTextWhite,
    focusedBorderColor = PGoldPrimary,
    unfocusedBorderColor = PTextGray,
    cursorColor = PGoldPrimary,
    focusedLabelColor = PGoldPrimary,
    unfocusedLabelColor = PTextGray
)