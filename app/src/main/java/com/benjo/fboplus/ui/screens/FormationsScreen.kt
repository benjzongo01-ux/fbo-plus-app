package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.REGION_FUNCTIONS
import com.benjo.fboplus.data.Commande
import com.benjo.fboplus.data.ProductRepository
import com.benjo.fboplus.data.Profil
import com.benjo.fboplus.data.VentesRepository
import com.benjo.fboplus.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val Forme = RoundedCornerShape(16.dp)
private fun Modifier.carteFormation() = fillMaxWidth().clip(Forme).background(BlackCard).border(1.dp, BlackBorder, Forme)

data class SectionFormation(val titre: String, val texte: String)
data class QuestionQuiz(val question: String, val choix: List<String>)
data class Formation(
    val id: String,
    val ordre: Int,
    val categorie: String,
    val titre: String,
    val resume: String,
    val sections: List<SectionFormation>,
    val quiz: List<QuestionQuiz>
)

@Suppress("UNCHECKED_CAST")
private fun formationDepuis(id: String, d: Map<String, Any?>) = Formation(
    id = id,
    ordre = (d["ordre"] as? Number)?.toInt() ?: 99,
    categorie = d["categorie"] as? String ?: "",
    titre = d["titre"] as? String ?: "",
    resume = d["resume"] as? String ?: "",
    sections = (d["sections"] as? List<Map<String, Any?>>).orEmpty()
        .map { SectionFormation(it["titre"] as? String ?: "", it["texte"] as? String ?: "") },
    quiz = (d["quiz"] as? List<Map<String, Any?>>).orEmpty()
        .map { QuestionQuiz(it["question"] as? String ?: "", (it["choix"] as? List<String>).orEmpty()) }
)

@Composable
private fun EnTete(titre: String, onRetour: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 40.dp, start = 4.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = GoldPrimary) }
        Text(titre, color = WhitePrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Accueil des formations : plan de progression, fiches produits, cours et certificats. */
@Composable
fun FormationsScreen(
    profil: Profil,
    onOuvrir: (String) -> Unit,
    onFichesProduits: () -> Unit,
    onRetour: () -> Unit
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var formations by remember { mutableStateOf<List<Formation>?>(null) }
    var certificats by remember { mutableStateOf<Set<String>>(emptySet()) }
    val commandes by VentesRepository.commandes.collectAsState()

    LaunchedEffect(uid) {
        val db = FirebaseFirestore.getInstance()
        formations = runCatching {
            db.collection("formations").get().await().documents
                .map { formationDepuis(it.id, it.data.orEmpty()) }.sortedBy { it.ordre }
        }.getOrDefault(emptyList())
        if (uid != null) certificats = runCatching {
            db.collection("certificats").whereEqualTo("userId", uid).get().await().documents
                .mapNotNull { it.getString("formationId") }.toSet()
        }.getOrDefault(emptySet())
    }

    Column(Modifier.fillMaxSize().background(BlackDeep)) {
        EnTete("Formations", onRetour)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PlanProgression(profil, commandes) }
            item {
                Column(Modifier.carteFormation().clickable(onClick = onFichesProduits).padding(16.dp)) {
                    Text("Fiches produits illustrées", color = WhitePrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Photo, description, prix et CC de chaque produit du catalogue.", color = WhiteSub, fontSize = 13.sp)
                }
            }
            val liste = formations
            if (liste == null) item { CircularProgressIndicator(color = GoldPrimary) }
            else liste.groupBy { it.categorie }.forEach { (categorie, cours) ->
                item { Text(categorie, color = GoldPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                items(cours, key = { it.id }) { f ->
                    Row(
                        Modifier.carteFormation().clickable { onOuvrir(f.id) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(f.titre, color = WhitePrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(f.resume, color = WhiteSub, fontSize = 13.sp)
                        }
                        if (f.id in certificats) Text("Certifié", color = GreenPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Un cours : ses sections, puis son quiz corrigé par le serveur. */
@Composable
fun FormationDetailScreen(formationId: String, onRetour: () -> Unit) {
    var formation by remember { mutableStateOf<Formation?>(null) }
    val reponses = remember { mutableStateMapOf<Int, Int>() }
    var resultat by remember { mutableStateOf<Map<*, *>?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var envoi by remember { mutableStateOf(false) }
    val portee = rememberCoroutineScope()

    LaunchedEffect(formationId) {
        formation = runCatching {
            FirebaseFirestore.getInstance().document("formations/$formationId").get().await()
                .let { formationDepuis(it.id, it.data.orEmpty()) }
        }.getOrNull()
    }

    Column(Modifier.fillMaxSize().background(BlackDeep)) {
        EnTete(formation?.titre ?: "Formation", onRetour)
        val f = formation ?: return@Column
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(f.sections) { s ->
                Column(Modifier.carteFormation().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.titre, color = GoldPrimary, fontWeight = FontWeight.Bold)
                    Text(s.texte, color = WhitePrimary, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
            if (f.quiz.isNotEmpty()) {
                item { Text("Quiz : 80 % de bonnes réponses pour le certificat", color = GoldPrimary, fontWeight = FontWeight.Bold) }
                val manquees = (resultat?.get("manquees") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
                items(f.quiz.size) { i ->
                    val q = f.quiz[i]
                    Column(Modifier.carteFormation().padding(14.dp)) {
                        Text("${i + 1}. ${q.question}", color = if (i in manquees) RedAlert else WhitePrimary, fontWeight = FontWeight.SemiBold)
                        q.choix.forEachIndexed { j, choix ->
                            Row(
                                Modifier.fillMaxWidth().clickable { reponses[i] = j; resultat = null },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = reponses[i] == j,
                                    onClick = { reponses[i] = j; resultat = null },
                                    colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                                )
                                Text(choix, color = WhiteSub, fontSize = 14.sp)
                            }
                        }
                    }
                }
                item {
                    resultat?.let { r ->
                        val reussi = r["reussi"] == true
                        Text(
                            if (reussi) "Bravo ! ${r["justes"]}/${r["total"]} : certificat obtenu."
                            else "${r["justes"]}/${r["total"]}. Relisez les questions en rouge, puis réessayez.",
                            color = if (reussi) GreenPrimary else GoldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    erreur?.let { Text(it, color = RedAlert) }
                    Button(
                        onClick = {
                            envoi = true
                            erreur = null
                            portee.launch {
                                try {
                                    resultat = FirebaseFunctions.getInstance(REGION_FUNCTIONS).getHttpsCallable("soumettreQuiz")
                                        .call(mapOf("formationId" to f.id, "reponses" to f.quiz.indices.map { reponses[it] ?: -1 }))
                                        .await().getData() as Map<*, *>
                                } catch (e: Exception) {
                                    erreur = e.message ?: "Envoi impossible. Réessayez."
                                } finally {
                                    envoi = false
                                }
                            }
                        },
                        enabled = !envoi && reponses.size == f.quiz.size,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = BlackDeep),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("Valider mes réponses", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Fiches produits illustrées, tirées du catalogue de l'application. */
@Composable
fun FichesProduitsScreen(onRetour: () -> Unit) {
    val produits = remember { ProductRepository.allProducts.sortedBy { it.categorie } }
    Column(Modifier.fillMaxSize().background(BlackDeep)) {
        EnTete("Fiches produits", onRetour)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(produits, key = { it.ref }) { p ->
                Row(Modifier.carteFormation().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (p.imageRes != 0) {
                        Image(
                            painterResource(p.imageRes),
                            contentDescription = p.nameFr,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(p.nameFr, color = WhitePrimary, fontWeight = FontWeight.Bold)
                        Text("${p.categorie} · réf. ${p.ref} · %,d F · %.3f CC".format(Locale.FRANCE, p.prixPublic, p.cc), color = GoldPrimary, fontSize = 12.sp)
                        if (p.description.isNotBlank()) Text(p.description, color = WhiteSub, fontSize = 13.sp, lineHeight = 18.sp)
                        if (p.pathologies.isNotEmpty()) Text("Conseillé pour : ${p.pathologies.joinToString(", ")}", color = WhiteSub, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Plan de progression : grade suivant, CC requis (plan marketing Forever, Company Policy 2026),
 * CC déjà réalisés dans FBO+, effort par semaine et conseils. Les CC de l'équipe s'ajoutent en plus.
 */
private data class Palier(val grade: String, val cc: Double, val mois: Int, val remise: Int)

private val PALIERS = listOf(
    Palier("Animateur Adjoint (AA)", 2.0, 2, 35),
    Palier("Animateur (A)", 10.0, 1, 38),
    Palier("Manager Adjoint (MA)", 60.0, 2, 43),
    Palier("Manager (M)", 120.0, 2, 48)
)
private val ORDRE = listOf("Client Privilégié (CP)") + PALIERS.map { it.grade }

@Composable
private fun PlanProgression(profil: Profil, commandes: List<Commande>) {
    val rang = ORDRE.indexOf(profil.gradeApplique).coerceAtLeast(0)
    val suivant = PALIERS.getOrNull(rang)
    val format = SimpleDateFormat("MM/yyyy", Locale.FRANCE)
    val cal = Calendar.getInstance()
    val ceMois = format.format(cal.time)
    cal.add(Calendar.MONTH, -1)
    val moisDernier = format.format(cal.time)
    val actives = commandes.filter { it.statut !in setOf("annulee", "retournee") }
    val ccMois = actives.filter { it.moisAnnee == ceMois }.sumOf { it.ccTotal }
    val ccDeuxMois = ccMois + actives.filter { it.moisAnnee == moisDernier }.sumOf { it.ccTotal }

    Column(Modifier.carteFormation().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Mon plan de progression", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Grade actuel : ${profil.gradeApplique}", color = WhitePrimary)
        Text(
            if (ccMois >= 4) "Actif ce mois-ci : %.2f CC (4 CC requis).".format(Locale.FRANCE, ccMois)
            else "Pour être actif ce mois-ci : encore %.2f CC (4 CC, dont 1 personnel).".format(Locale.FRANCE, 4 - ccMois),
            color = if (ccMois >= 4) GreenPrimary else GoldPrimary,
            fontSize = 13.sp
        )
        if (suivant == null) {
            Text("Vous êtes Manager : visez maintenant les reconnaissances Manager et plus (Senior, Soaring, Sapphire…).", color = WhiteSub, fontSize = 13.sp)
            return@Column
        }
        val realise = if (suivant.mois == 1) ccMois else ccDeuxMois
        val reste = (suivant.cc - realise).coerceAtLeast(0.0)
        val joursRestants = Calendar.getInstance().let { it.getActualMaximum(Calendar.DAY_OF_MONTH) - it.get(Calendar.DAY_OF_MONTH) + 1 }
        val parSemaine = reste / (joursRestants / 7.0).coerceAtLeast(1.0)
        Text(
            "Prochain grade : ${suivant.grade} (remise ${suivant.remise} %%) avec %.0f CC ${if (suivant.mois == 1) "sur un mois" else "sur deux mois consécutifs"}.".format(Locale.FRANCE, suivant.cc),
            color = WhitePrimary,
            fontSize = 14.sp
        )
        LinearProgressIndicator(
            progress = { (realise / suivant.cc).toFloat().coerceIn(0f, 1f) },
            color = GoldPrimary,
            trackColor = BlackBorder,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
        if (reste == 0.0) {
            Text("Objectif atteint dans FBO+ : votre qualification sera confirmée par Forever après validation au siège.", color = GreenPrimary, fontSize = 13.sp)
        } else {
            Text(
                "Il vous manque %.2f CC, soit environ %.2f CC par semaine jusqu'à la fin du mois (≈ %d produits à 0,1 CC comme la Pulpe d'Aloès).".format(
                    Locale.FRANCE, reste, parSemaine, kotlin.math.ceil(reste / 0.1).toInt()
                ),
                color = WhiteSub,
                fontSize = 13.sp
            )
            if (profil.objectifCC in 0.01..<reste) {
                Text("Votre objectif du mois (%.2f CC) est inférieur à ce qu'il faut : relevez-le à %.2f CC dans votre profil.".format(Locale.FRANCE, profil.objectifCC, reste), color = GoldPrimary, fontSize = 13.sp)
            }
            Text("Stratégie conseillée :", color = GoldPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            conseils(suivant.grade).forEach { Text("• $it", color = WhiteSub, fontSize = 13.sp) }
        }
        Text("Les CC de votre équipe (filleuls) comptent aussi : le total réel peut être plus élevé.", color = WhiteSub, fontSize = 11.sp)
    }
}

private fun conseils(grade: String): List<String> = when {
    grade.startsWith("Animateur Adjoint") -> listOf(
        "Vendez à vos proches les produits phares (Pulpe d'Aloès, Aloe Berry Nectar).",
        "Partagez le lien de votre boutique en ligne sur WhatsApp et vos réseaux.",
        "2 CC suffisent : environ 20 produits à 0,1 CC en deux mois."
    )
    grade.startsWith("Animateur (") -> listOf(
        "Visez 10 CC dans un seul mois : planifiez vos ventes semaine par semaine.",
        "Relancez chaque jour les clients de la liste « À relancer ».",
        "Parrainez un ou deux distributeurs motivés et aidez-les à faire leurs premières ventes."
    )
    grade.startsWith("Manager Adjoint") -> listOf(
        "60 CC en deux mois : misez sur votre équipe autant que sur vos ventes.",
        "Formez vos filleuls avec les cours FBO+ et fixez-leur un objectif mensuel.",
        "Proposez des cures complètes (packs) plutôt que des produits à l'unité."
    )
    else -> listOf(
        "120 CC sur un ou deux mois (ou 150 sur trois ou quatre) : construisez plusieurs lignées actives.",
        "Restez actif (4 CC) chaque mois de la période de qualification.",
        "Suivez chaque semaine les CC de votre équipe et accompagnez ceux qui ralentissent."
    )
}
