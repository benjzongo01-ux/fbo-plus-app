package com.benjo.fboplus.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.benjo.fboplus.REGION_FUNCTIONS
import com.benjo.fboplus.data.Commande
import com.benjo.fboplus.data.Profil
import com.benjo.fboplus.data.VentesRepository
import com.benjo.fboplus.ui.theme.*
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MembreEquipe(val idForever: String, val nom: String, val grade: String, val ccMois: Double, val actif: Boolean, val surFboPlus: Boolean)

private val Forme = RoundedCornerShape(16.dp)
private fun fcfa(n: Int) = "%,d FCFA".format(Locale.FRANCE, n)

/** Équipe du distributeur (filleuls, CC du mois, actifs) et rapport mensuel à partager en Excel ou PDF. */
@Composable
fun EquipeScreen(profil: Profil, onRetour: () -> Unit) {
    val context = LocalContext.current
    val commandes by VentesRepository.commandes.collectAsState()
    var equipe by remember { mutableStateOf<List<MembreEquipe>?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val mois = remember { SimpleDateFormat("MM/yyyy", Locale.FRANCE).format(Date()) }
    val duMois = commandes.filter { it.moisAnnee == mois && it.statut !in setOf("annulee", "retournee") }

    LaunchedEffect(Unit) {
        try {
            @Suppress("UNCHECKED_CAST")
            val r = FirebaseFunctions.getInstance(REGION_FUNCTIONS).getHttpsCallable("monEquipe").call().await().getData() as Map<String, Any?>
            equipe = (r["equipe"] as? List<Map<String, Any?>>).orEmpty().map {
                MembreEquipe(
                    idForever = it["idForever"] as? String ?: "",
                    nom = it["nom"] as? String ?: "",
                    grade = it["grade"] as? String ?: "",
                    ccMois = (it["ccMois"] as? Number)?.toDouble() ?: 0.0,
                    actif = it["actif"] == true,
                    surFboPlus = it["surFboPlus"] == true
                )
            }
        } catch (e: Exception) {
            erreur = e.message
            equipe = emptyList()
        }
    }

    Column(Modifier.fillMaxSize().background(BlackDeep)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 40.dp, start = 4.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = GoldPrimary) }
            Text("Mon équipe et rapports", color = WhitePrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(Forme).background(BlackCard).border(1.dp, BlackBorder, Forme).padding(16.dp)) {
                    Text("Rapport du mois ($mois)", color = GoldPrimary, fontWeight = FontWeight.Bold)
                    Text("${duMois.size} commande(s) · ${fcfa(duMois.sumOf { it.caTotal })}", color = WhitePrimary)
                    Text("Bénéfice : ${fcfa(duMois.sumOf { it.beneficeTotal })} · %.3f CC".format(Locale.FRANCE, duMois.sumOf { it.ccTotal }), color = WhiteSub, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { partager(context, rapportExcel(context, profil, mois, duMois, equipe.orEmpty()), "text/csv") }) {
                            Text("Excel", color = GoldPrimary)
                        }
                        OutlinedButton(onClick = { partager(context, rapportPdf(context, profil, mois, duMois, equipe.orEmpty()), "application/pdf") }) {
                            Text("PDF", color = GoldPrimary)
                        }
                    }
                }
            }
            item {
                val liste = equipe.orEmpty()
                Text(
                    if (equipe == null) "Chargement de l'équipe…"
                    else "Équipe : ${liste.size} filleul(s), ${liste.count { it.actif }} actif(s), %.3f CC ce mois".format(Locale.FRANCE, liste.sumOf { it.ccMois }),
                    color = GoldPrimary,
                    fontWeight = FontWeight.Bold
                )
                erreur?.let { Text(it, color = RedAlert, fontSize = 13.sp) }
                if (equipe?.isEmpty() == true && erreur == null) {
                    Text(
                        "Vos filleuls apparaissent ici quand ils indiquent votre ID Forever comme parrain dans leur profil FBO+, ou quand vous commandez pour eux.",
                        color = WhiteSub,
                        fontSize = 13.sp
                    )
                }
            }
            items(equipe.orEmpty(), key = { it.idForever + it.nom }) { m ->
                Row(
                    Modifier.fillMaxWidth().clip(Forme).background(BlackCard).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.nom.ifBlank { "Filleul" }, color = WhitePrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOf("ID ${m.idForever}", m.grade, if (m.surFboPlus) "sur FBO+" else "commandes passées par vous")
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            color = WhiteSub,
                            fontSize = 12.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%.3f CC".format(Locale.FRANCE, m.ccMois), color = GoldPrimary, fontWeight = FontWeight.Bold)
                        Text(if (m.actif) "Actif" else "Pas encore actif", color = if (m.actif) GreenPrimary else WhiteSub, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun nomComplet(p: Profil) = listOf(p.prenom, p.nom).filter { it.isNotBlank() }.joinToString(" ")

/** Fichier CSV lisible par Excel (séparateur « ; », accents corrects). */
private fun rapportExcel(context: Context, profil: Profil, mois: String, commandes: List<Commande>, equipe: List<MembreEquipe>): File {
    fun champ(v: Any?) = "\"" + v.toString().replace("\"", "\"\"") + "\""
    val lignes = mutableListOf("Rapport FBO+ ${champ(mois)};${champ(nomComplet(profil))};ID ${profil.idDistributeur}", "")
    lignes += "Date;Commande;Statut;CA (FCFA);Bénéfice (FCFA);CC"
    commandes.sortedBy { it.dateMillis }.forEach {
        lignes += listOf(it.dateStr, it.nomAffiche, it.statut, it.caTotal, it.beneficeTotal, "%.3f".format(Locale.FRANCE, it.ccTotal)).joinToString(";") { v -> champ(v) }
    }
    lignes += listOf("Total", "", "", commandes.sumOf { it.caTotal }, commandes.sumOf { it.beneficeTotal }, "%.3f".format(Locale.FRANCE, commandes.sumOf { it.ccTotal })).joinToString(";") { champ(it) }
    lignes += ""
    lignes += "Équipe;ID Forever;Grade;CC du mois;Actif"
    equipe.forEach { lignes += listOf(it.nom, it.idForever, it.grade, "%.3f".format(Locale.FRANCE, it.ccMois), if (it.actif) "oui" else "non").joinToString(";") { v -> champ(v) } }
    return File(dossierRapports(context), "rapport-fbo-${mois.replace('/', '-')}.csv").apply {
        writeText("﻿" + lignes.joinToString("\r\n"))
    }
}

/** PDF A4 simple, généré sur le téléphone sans bibliothèque externe. */
private fun rapportPdf(context: Context, profil: Profil, mois: String, commandes: List<Commande>, equipe: List<MembreEquipe>): File {
    val doc = PdfDocument()
    val titre = Paint().apply { textSize = 18f; isFakeBoldText = true }
    val texte = Paint().apply { textSize = 11f }
    var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    var y = 50f
    fun ligne(t: String, p: Paint = texte, ecart: Float = 16f) {
        if (y > 800) {
            doc.finishPage(page)
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, doc.pages.size + 1).create())
            y = 50f
        }
        page.canvas.drawText(t, 40f, y, p)
        y += ecart
    }
    ligne("Rapport FBO+ · $mois", titre, 26f)
    ligne("${nomComplet(profil)} · ID ${profil.idDistributeur} · ${profil.gradeApplique}")
    ligne("${commandes.size} commande(s) · CA ${fcfa(commandes.sumOf { it.caTotal })} · bénéfice ${fcfa(commandes.sumOf { it.beneficeTotal })} · %.3f CC".format(Locale.FRANCE, commandes.sumOf { it.ccTotal }), ecart = 26f)
    ligne("Commandes", titre.apply { textSize = 14f }, 20f)
    commandes.sortedBy { it.dateMillis }.forEach {
        ligne("${it.dateStr}  ${it.nomAffiche.take(28)}  ${fcfa(it.caTotal)}  %.3f CC".format(Locale.FRANCE, it.ccTotal))
    }
    y += 10f
    ligne("Équipe", titre, 20f)
    if (equipe.isEmpty()) ligne("Aucun filleul enregistré.")
    equipe.forEach { ligne("${it.nom.take(28)}  ID ${it.idForever}  %.3f CC  ${if (it.actif) "actif" else ""}".format(Locale.FRANCE, it.ccMois)) }
    doc.finishPage(page)
    val fichier = File(dossierRapports(context), "rapport-fbo-${mois.replace('/', '-')}.pdf")
    fichier.outputStream().use { doc.writeTo(it) }
    doc.close()
    return fichier
}

private var dossierCache: File? = null
private fun dossierRapports(context: Context? = null): File {
    context?.let { dossierCache = File(it.cacheDir, "rapports").apply { mkdirs() } }
    return dossierCache ?: error("Dossier des rapports indisponible")
}

private fun partager(context: Context, fichier: File, type: String) {
    dossierRapports(context)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fichiers", fichier)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Partager le rapport"
        )
    )
}
