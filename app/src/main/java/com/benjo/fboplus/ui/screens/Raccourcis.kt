package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.ui.theme.*
import com.benjo.fboplus.viewmodel.VentesViewModel
import com.benjo.fboplus.viewmodel.ProfilViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

/*
 * Raccourcis répartis dans les onglets (le Profil ne garde que le compte) :
 * icônes de l'en-tête de l'Accueil, cartes Wallet et Formation, tuiles des Ventes,
 * lien d'équipe des Stats, et l'écran « Ma boutique » qui réunit le lien et les commandes reçues.
 */

/** Icône ronde de l'en-tête (cloche, discussion), avec une pastille quand il y a du nouveau. */
@Composable
fun IconeEntete(icone: ImageVector, description: String, pastille: Int, onClick: () -> Unit) {
    Box {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BlackSurface)
                .border(1.dp, BlackBorder, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icone, description, tint = WhitePrimary, modifier = Modifier.size(20.dp)) }
        if (pastille > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape)
                    .background(GoldPrimary)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (pastille > 9) "9+" else "$pastille", color = BlackDeep, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Accueil : solde du wallet et formation, côte à côte. */
@Composable
fun CartesAccueil(onWallet: () -> Unit, onFormations: () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var solde by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose {}
        val ecoute = FirebaseFirestore.getInstance().document("wallets/$uid")
            .addSnapshotListener { d, _ -> solde = d?.getLong("solde") ?: 0L }
        onDispose { ecoute.remove() }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CarteRaccourci(
            icone = Icons.Filled.AccountBalanceWallet,
            titre = "Wallet",
            valeur = solde?.let { "%,d F".format(Locale.FRANCE, it).replace(' ', ' ').replace(' ', ' ') } ?: "…",
            modifier = Modifier.weight(1f),
            onClick = onWallet
        )
        CarteRaccourci(
            icone = Icons.Filled.School,
            titre = "Formation",
            valeur = "Continuer",
            modifier = Modifier.weight(1f),
            onClick = onFormations
        )
    }
}

@Composable
private fun CarteRaccourci(icone: ImageVector, titre: String, valeur: String, modifier: Modifier, onClick: () -> Unit) {
    val forme = RoundedCornerShape(16.dp)
    Row(
        modifier
            .clip(forme)
            .background(Brush.linearGradient(listOf(Color(0xFF1A1710), BlackCard)))
            .border(1.dp, GoldGlow, forme)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(GoldFaint), contentAlignment = Alignment.Center) {
            Icon(icone, null, tint = GoldPrimary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(titre, color = WhiteSub, fontSize = 12.sp)
            Text(valeur, color = WhitePrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Ventes : tuile carrée vers une partie du suivi des ventes. */
@Composable
fun TuileVentes(icone: ImageVector, titre: String, modifier: Modifier, onClick: () -> Unit) {
    val forme = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(forme)
            .background(BlackCard)
            .border(1.dp, BlackBorder, forme)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(GoldFaint), contentAlignment = Alignment.Center) {
            Icon(icone, null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(titre, color = WhitePrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Stats : ligne cliquable vers une page (équipe et rapports). */
@Composable
fun LigneLien(icone: ImageVector, titre: String, sousTitre: String, onClick: () -> Unit) {
    val forme = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forme)
            .background(BlackCard)
            .border(1.dp, BlackBorder, forme)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(GoldFaint), contentAlignment = Alignment.Center) {
            Icon(icone, null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titre, color = WhitePrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sousTitre, color = WhiteSub, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = WhiteFaint)
    }
}

/** « Ma boutique » : le lien et le QR code d'un côté, les commandes reçues de l'autre. */
@Composable
fun MaBoutiqueScreen(
    profilViewModel: ProfilViewModel,
    ventesViewModel: VentesViewModel,
    commandesDabord: Boolean,
    onPreparer: () -> Unit,
    onRetour: () -> Unit
) {
    var onglet by remember { mutableIntStateOf(if (commandesDabord) 1 else 0) }
    Column(Modifier.fillMaxSize().background(BlackDeep)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 40.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = GoldPrimary) }
            Text("Ma boutique", color = WhitePrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        TabRow(
            selectedTabIndex = onglet,
            containerColor = BlackDeep,
            contentColor = GoldPrimary,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[onglet]), color = GoldPrimary)
            },
            divider = { HorizontalDivider(color = BlackBorder) }
        ) {
            listOf("Lien et QR code", "Commandes reçues").forEachIndexed { i, titre ->
                Tab(
                    selected = onglet == i,
                    onClick = { onglet = i },
                    text = { Text(titre, color = if (onglet == i) GoldPrimary else WhiteSub, fontWeight = FontWeight.SemiBold) }
                )
            }
        }
        Box(Modifier.weight(1f)) {
            if (onglet == 0) BoutiqueScreen(profilViewModel, onRetour, avecEnTete = false)
            else CommandesBoutiqueScreen(ventesViewModel, onPreparer, onRetour, avecEnTete = false)
        }
    }
}
