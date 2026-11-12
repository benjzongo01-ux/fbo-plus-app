package com.benjo.fboplus.ui.screens

import com.benjo.fboplus.ui.theme.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.viewmodel.VentesViewModel
import java.util.Calendar

fun nomMois(moisAnnee: String): String {
    val parts = moisAnnee.split("/")
    val mois = parts.getOrNull(0)?.toIntOrNull() ?: return moisAnnee
    val annee = parts.getOrNull(1) ?: return moisAnnee
    val noms = listOf(
        "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    )
    return "${noms.getOrElse(mois - 1) { moisAnnee }} $annee"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(ventesViewModel: VentesViewModel, onEquipe: () -> Unit = {}) {
    val contexte = androidx.compose.ui.platform.LocalContext.current
    val commandesDuJourStats by com.benjo.fboplus.data.VentesRepository.commandes.collectAsState()
    val duMois = remember(commandesDuJourStats) {
        com.benjo.fboplus.data.VentesRepository.getCommandesDuMois(contexte, com.benjo.fboplus.data.VentesRepository.moisAnneeAujourdhui())
    }
    val beneficeMois = remember(commandesDuJourStats) {
        com.benjo.fboplus.data.VentesRepository.getStatMoisActuel(contexte).benefice
    }

    val toutesCommandes = remember { ventesViewModel.getToutesCommandes() }

    val commandesParMois = remember {
        toutesCommandes
            .groupBy { it.moisAnnee }
            .toSortedMap(compareByDescending { it })
    }

    val moisActuel = remember {
        val cal = Calendar.getInstance()
        val m = cal.get(Calendar.MONTH) + 1
        val a = cal.get(Calendar.YEAR)
        "%02d/%d".format(m, a)
    }

    var moisSelectionne by remember {
        mutableStateOf(
            if (commandesParMois.containsKey(moisActuel)) moisActuel
            else commandesParMois.keys.firstOrNull() ?: moisActuel
        )
    }

    var moisDeplie by remember { mutableStateOf<String?>(moisActuel) }
    var periodeSelectionnee by remember { mutableStateOf("30J") }
    var indexSelectionne by remember(periodeSelectionnee) { mutableStateOf<Int?>(null) }

    val periodes = listOf("7J", "30J", "12M")

    val nomsCourtsMois = listOf(
        "Jan", "Fév", "Mar", "Avr", "Mai", "Juin",
        "Juil", "Août", "Sep", "Oct", "Nov", "Déc"
    )

    val joursSemaine = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

    val dataPoints: List<Pair<String, Int>> = when (periodeSelectionnee) {
        "7J" -> {
            (6 downTo 0).map { i ->
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_MONTH, -i)
                val jour = "%02d".format(cal.get(Calendar.DAY_OF_MONTH))
                val mois = "%02d".format(cal.get(Calendar.MONTH) + 1)
                val annee = cal.get(Calendar.YEAR)
                val moisAnnee = "$mois/$annee"
                val jourSemaine = cal.get(Calendar.DAY_OF_WEEK)
                val label = joursSemaine[
                    when (jourSemaine) {
                        Calendar.MONDAY    -> 0
                        Calendar.TUESDAY   -> 1
                        Calendar.WEDNESDAY -> 2
                        Calendar.THURSDAY  -> 3
                        Calendar.FRIDAY    -> 4
                        Calendar.SATURDAY  -> 5
                        else               -> 6
                    }
                ]
                val ca = toutesCommandes
                    .filter { it.moisAnnee == moisAnnee }
                    .filter { it.dateStr.split("/").getOrNull(0) == jour }
                    .sumOf { it.caTotal }
                label to ca
            }
        }
        "30J" -> {
            val semaines = mutableMapOf<Int, Int>()
            (29 downTo 0).forEach { i ->
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_MONTH, -i)
                val jour = "%02d".format(cal.get(Calendar.DAY_OF_MONTH))
                val mois = "%02d".format(cal.get(Calendar.MONTH) + 1)
                val annee = cal.get(Calendar.YEAR)
                val moisAnnee = "$mois/$annee"
                val position = 29 - i
                val numSemaine = (position / 7) + 1
                val ca = toutesCommandes
                    .filter { it.moisAnnee == moisAnnee }
                    .filter { it.dateStr.split("/").getOrNull(0) == jour }
                    .sumOf { it.caTotal }
                semaines[numSemaine] = (semaines[numSemaine] ?: 0) + ca
            }
            (1..5).map { s -> "S$s" to (semaines[s] ?: 0) }
        }
        else -> {
            (11 downTo 0).map { i ->
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -i)
                val mois = "%02d".format(cal.get(Calendar.MONTH) + 1)
                val annee = cal.get(Calendar.YEAR)
                val moisAnnee = "$mois/$annee"
                val label = nomsCourtsMois[cal.get(Calendar.MONTH)]
                val ca = toutesCommandes
                    .filter { it.moisAnnee == moisAnnee }
                    .sumOf { it.caTotal }
                label to ca
            }
        }
    }

    val maxVal = dataPoints.maxOfOrNull { it.second } ?: 1
    val totalPeriode = dataPoints.sumOf { it.second }

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

            // ── HEADER ──
            item {
                Column {
                    Text(
                        "Stats & Historique",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldPrimary
                    )
                    Text(
                        "Analyse de vos performances",
                        fontSize = 11.sp,
                        color = WhiteSub
                    )
                }
            }

            // ── PERFORMANCE DU MOIS ET ÉQUIPE ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Performance · ${moisEnCours()}", color = WhitePrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    CartePerformance(
                        ca = duMois.sumOf { it.caTotal },
                        cc = duMois.sumOf { it.ccTotal },
                        benefice = beneficeMois,
                        commandes = duMois.size
                    )
                    LigneLien(Icons.Filled.Groups, "Mon équipe et rapports", "Filleuls, CC, export Excel ou PDF", onEquipe)
                }
            }

            // ── RÉSUMÉ GLOBAL ──
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
                        Text(
                            "📊 Résumé global",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("CA Total", color = WhiteFaint, fontSize = 11.sp)
                                Text(
                                    "%,d FCFA".format(toutesCommandes.sumOf { it.caTotal }),
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("CC Total", color = WhiteFaint, fontSize = 11.sp)
                                Text(
                                    "%.3f CC".format(toutesCommandes.sumOf { it.ccTotal }),
                                    color = GoldLight,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("PV Total", color = WhiteFaint, fontSize = 11.sp)
                                Text(
                                    "%,d FCFA".format(toutesCommandes.sumOf { it.prixValidation }),
                                    color = BlueAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Bénéfice Total", color = WhiteFaint, fontSize = 11.sp)
                                Text(
                                    "%,d FCFA".format(toutesCommandes.sumOf { it.beneficeTotal }),
                                    color = GreenPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = GoldPrimary.copy(0.15f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${toutesCommandes.size} commande(s) · ${commandesParMois.size} mois",
                            color = WhiteSub,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ── GRAPHIQUE COURBE ──
            item {
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(BlackCard)
                        .border(1.dp, GoldPrimary.copy(0.2f), RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "📈 Évolution des ventes",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = GoldPrimary
                                )
                                Text(
                                    if (indexSelectionne != null && indexSelectionne!! < dataPoints.size)
                                        "${dataPoints[indexSelectionne!!].first} — %,d FCFA".format(
                                            dataPoints[indexSelectionne!!].second
                                        )
                                    else
                                        "Total : %,d FCFA".format(totalPeriode),
                                    fontSize = 11.sp,
                                    color = if (indexSelectionne != null) GoldLight else WhiteFaint
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                periodes.forEach { periode ->
                                    val selected = periodeSelectionnee == periode
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (selected) Color(0xFFD4AF37) else BlackSurface
                                            )
                                            .border(
                                                1.dp,
                                                if (selected) Color.Transparent else BlackBorder,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                periodeSelectionnee = periode
                                                indexSelectionne = null
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            periode,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.ExtraBold
                                            else FontWeight.Normal,
                                            color = if (selected) BlackDeep else WhiteFaint
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (totalPeriode == 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📉", fontSize = 32.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Aucune donnée pour cette période",
                                        color = WhiteFaint,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) { }
                            ) {
                                val w = size.width
                                val h = size.height
                                val n = dataPoints.size
                                if (n < 2) return@Canvas

                                val pts = dataPoints.mapIndexed { i, (_, v) ->
                                    val x = i * w / (n - 1)
                                    val y = h - (v.toFloat() / maxVal) * (h * 0.82f) - h * 0.05f
                                    Offset(x, y)
                                }

                                // Zone remplie
                                val fillPath = Path()
                                fillPath.moveTo(pts.first().x, h)
                                pts.forEach { fillPath.lineTo(it.x, it.y) }
                                fillPath.lineTo(pts.last().x, h)
                                fillPath.close()
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFFD4AF37).copy(alpha = 0.25f),
                                            Color.Transparent
                                        )
                                    )
                                )

                                // Courbe dorée
                                for (i in 0 until pts.size - 1) {
                                    drawLine(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFFD4AF37),
                                                Color(0xFFFFD700)
                                            )
                                        ),
                                        start = pts[i],
                                        end = pts[i + 1],
                                        strokeWidth = 2.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }

                                // Point sélectionné
                                indexSelectionne?.let { idx ->
                                    if (idx < pts.size) {
                                        drawLine(
                                            color = Color(0xFFFFD700).copy(alpha = 0.4f),
                                            start = Offset(pts[idx].x, 0f),
                                            end = Offset(pts[idx].x, h),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                        drawCircle(
                                            color = Color(0xFFFFD700),
                                            radius = 6.dp.toPx(),
                                            center = pts[idx]
                                        )
                                        drawCircle(
                                            color = BlackDeep,
                                            radius = 3.dp.toPx(),
                                            center = pts[idx]
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                dataPoints.forEachIndexed { i, (label, _) ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (label.isNotEmpty()) {
                                            Text(
                                                label,
                                                fontSize = 8.sp,
                                                color = if (i == indexSelectionne) GoldPrimary
                                                else WhiteFaint,
                                                fontWeight = if (i == indexSelectionne)
                                                    FontWeight.Bold else FontWeight.Normal,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = GoldPrimary.copy(0.15f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("CA période", fontSize = 10.sp, color = WhiteFaint)
                                    Text(
                                        "%,d F".format(totalPeriode),
                                        fontWeight = FontWeight.Bold,
                                        color = GoldPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Meilleur", fontSize = 10.sp, color = WhiteFaint)
                                    Text(
                                        "%,d F".format(maxVal),
                                        fontWeight = FontWeight.Bold,
                                        color = GoldLight,
                                        fontSize = 13.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Actifs", fontSize = 10.sp, color = WhiteFaint)
                                    Text(
                                        "${dataPoints.count { it.second > 0 }}",
                                        fontWeight = FontWeight.Bold,
                                        color = GoldPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── LISTE DES MOIS ──
            if (commandesParMois.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Aucun historique disponible",
                            color = WhiteFaint,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                commandesParMois.forEach { (mois, commandes) ->
                    item {
                        val estSelectionne = moisSelectionne == mois
                        val estDeplie = moisDeplie == mois

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (estSelectionne) Brush.linearGradient(
                                        colors = listOf(Color(0xFF1A1500), BlackCard)
                                    ) else Brush.linearGradient(
                                        colors = listOf(BlackCard, BlackCard)
                                    )
                                )
                                .border(
                                    width = if (estSelectionne) 1.5.dp else 1.dp,
                                    color = if (estSelectionne) GoldPrimary
                                    else GoldPrimary.copy(0.15f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    moisSelectionne = mois
                                    moisDeplie = if (estDeplie) null else mois
                                }
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (estSelectionne) Text("📈 ", fontSize = 13.sp)
                                            Text(
                                                nomMois(mois),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                color = if (estSelectionne) GoldPrimary
                                                else WhitePrimary
                                            )
                                        }
                                        Text(
                                            "${commandes.size} commande(s)",
                                            fontSize = 11.sp,
                                            color = WhiteFaint
                                        )
                                    }
                                    Icon(
                                        imageVector = if (estDeplie) Icons.Filled.KeyboardArrowUp
                                        else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = GoldPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "%,d FCFA".format(commandes.sumOf { it.caTotal }),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = GoldPrimary,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        "%.3f CC".format(commandes.sumOf { it.ccTotal }),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = GoldLight,
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "PV : %,d FCFA".format(commandes.sumOf { it.prixValidation }),
                                        fontWeight = FontWeight.Medium,
                                        color = BlueAccent,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "Bnf : %,d FCFA".format(commandes.sumOf { it.beneficeTotal }),
                                        fontWeight = FontWeight.Medium,
                                        color = GreenPrimary,
                                        fontSize = 12.sp
                                    )
                                }

                                AnimatedVisibility(visible = estDeplie) {
                                    Column(modifier = Modifier.padding(top = 12.dp)) {
                                        HorizontalDivider(color = GoldPrimary.copy(0.15f))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        commandes.reversed().forEach { commande ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(BlackSurface)
                                                    .border(
                                                        1.dp,
                                                        GoldPrimary.copy(0.1f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .padding(12.dp)
                                            ) {
                                                Column {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            commande.nomAffiche,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = WhitePrimary,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Text(
                                                            commande.dateStr,
                                                            fontSize = 10.sp,
                                                            color = WhiteFaint
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
                                                            color = GoldPrimary,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            "%.3f CC".format(commande.ccTotal),
                                                            fontWeight = FontWeight.Bold,
                                                            color = GoldLight,
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            "PV : %,d F".format(commande.prixValidation),
                                                            fontSize = 10.sp,
                                                            color = BlueAccent
                                                        )
                                                        Text(
                                                            "Bnf : %,d F".format(commande.beneficeTotal),
                                                            fontSize = 10.sp,
                                                            color = GreenPrimary
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        commande.lignes.joinToString(", ") {
                                                            "${it.produitNom} ×${it.quantite}"
                                                        },
                                                        fontSize = 10.sp,
                                                        color = WhiteFaint,
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
                }
            }
        }
    }
}
