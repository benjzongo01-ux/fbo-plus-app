package com.benjo.fboplus.siege

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/*
 * Scan d'un reçu du siège. La caméra lit le texte en direct sur le téléphone (ML Kit, sans réseau) :
 * aucune photo n'est enregistrée sur le téléphone. Au toucher, le texte part au serveur qui retrouve
 * le lot ; à la validation, l'image (gardée en mémoire seulement) est archivée sur le serveur.
 * Connexion instable : un message le dit, rien n'est perdu, on recommence.
 */

private val ID_FOREVER = Regex("""(?<!\d)\d{3}[\s-]?\d{3}[\s-]?\d{3}[\s-]?\d{3}(?!\d)""")
private val LIGNE_TOTAL = Regex("""(?i)\b(net\s*a\s*payer|total)\b""")

private data class LotTrouve(
    val id: String, val idForever: String, val pays: String, val nom: String, val montant: Long,
    val ecart: Long?, val cc: Double, val prixFactures: Map<String, Long>
)
private data class LigneLue(val ref: String, val designation: String, val quantite: Int, val montantHT: Long)
private data class Lecture(
    val total: Long?, val sousTotalHT: Long?, val tva: Long?, val airsi: Long?, val timbre: Long?,
    val points: Double?, val numero: String?, val lignes: List<LigneLue>, val lots: List<LotTrouve>
)

@Composable
fun ScannerRecu(attendus: List<Lot>, onFermer: () -> Unit) {
    val contexte = LocalContext.current
    var autorise by remember { mutableStateOf(ContextCompat.checkSelfPermission(contexte, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { autorise = it }
    LaunchedEffect(Unit) { if (!autorise) demande.launch(Manifest.permission.CAMERA) }

    var texte by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var figee by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
    val idLu = remember(texte) { ID_FOREVER.find(texte)?.value }
    val totalLu = remember(texte) { texte.lines().any { LIGNE_TOTAL.containsMatchIn(it) && !it.contains("SOUS", ignoreCase = true) } }

    Column(Modifier.fillMaxSize().background(SNoir).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFermer) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White) }
            Text("Scanner un reçu", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${attendus.size} attendu(s)", color = SGris, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
        }

        val gele = figee
        if (gele != null) {
            ConfirmationRecu(texte = gele.first, image = gele.second, onRecommencer = { figee = null }, onTermine = { figee = null })
            return@Column
        }
        if (!autorise) {
            Text("Autorisez la caméra pour lire les reçus.", color = SGris, modifier = Modifier.padding(24.dp))
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp))) {
            CameraTexte(onLecture = { t, b -> texte = t; image = b })
            Box(Modifier.fillMaxSize().padding(18.dp).border(2.dp, if (idLu != null) SVert else SOr.copy(alpha = 0.6f), RoundedCornerShape(14.dp)))
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Indicateur("ID Forever", idLu)
            Indicateur("Total", if (totalLu) "trouvé" else null)
            Text("Cadrez tout le reçu, bien à plat et éclairé.", color = SGris, fontSize = 12.sp)
            Button(
                onClick = { image?.let { figee = texte to it } },
                enabled = idLu != null && image != null,
                colors = ButtonDefaults.buttonColors(containerColor = SOr, contentColor = SNoir),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Lire ce reçu", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun Indicateur(libelle: String, valeur: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (valeur != null) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (valeur != null) SVert else SGris, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (valeur != null) "$libelle : $valeur" else "$libelle : en recherche…", color = if (valeur != null) Color.White else SGris, fontSize = 14.sp)
    }
}

/** Aperçu de la caméra et lecture du texte en continu (une image à la fois, la plus récente). */
@Composable
private fun CameraTexte(onLecture: (String, Bitmap) -> Unit) {
    val contexte = LocalContext.current
    val cycle = LocalLifecycleOwner.current
    val lecteur = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val executeur = remember { Executors.newSingleThreadExecutor() }
    val occupe = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) { onDispose { executeur.shutdown(); lecteur.close() } }

    AndroidView(
        factory = { ctx ->
            val vue = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val futur = ProcessCameraProvider.getInstance(ctx)
            futur.addListener({
                val fournisseur = futur.get()
                val apercu = Preview.Builder().build().also { it.setSurfaceProvider(vue.surfaceProvider) }
                val analyse = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analyse.setAnalyzer(executeur) { proxy ->
                    if (!occupe.compareAndSet(false, true)) { proxy.close(); return@setAnalyzer }
                    val rotation = proxy.imageInfo.rotationDegrees
                    val brute = proxy.toBitmap()
                    proxy.close()
                    val droite = if (rotation == 0) brute else Bitmap.createBitmap(brute, 0, 0, brute.width, brute.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                    lecteur.process(InputImage.fromBitmap(droite, 0))
                        .addOnSuccessListener { r -> if (r.text.isNotBlank()) onLecture(r.text, droite) }
                        .addOnCompleteListener { occupe.set(false) }
                }
                fournisseur.unbindAll()
                fournisseur.bindToLifecycle(cycle, CameraSelector.DEFAULT_BACK_CAMERA, apercu, analyse)
            }, ContextCompat.getMainExecutor(ctx))
            vue
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Résultat de la lecture : lot retrouvé, montant facturé (modifiable), numéro du reçu.
 * À la validation, l'image (en mémoire) est envoyée au serveur, puis le lot est validé.
 */
@Composable
private fun ConfirmationRecu(texte: String, image: Bitmap, onRecommencer: () -> Unit, onTermine: () -> Unit) {
    val fonctions = remember { FirebaseFunctions.getInstance(REGION_FUNCTIONS) }
    val portee = rememberCoroutineScope()
    var lecture by remember { mutableStateOf<Lecture?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var choisi by remember { mutableStateOf<LotTrouve?>(null) }
    var montant by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var valide by remember { mutableStateOf<String?>(null) }
    var essai by remember { mutableIntStateOf(0) }

    LaunchedEffect(essai) {
        erreur = null
        try {
            val r = fonctions.getHttpsCallable("lireRecu").call(mapOf("texte" to texte)).await().getData() as Map<*, *>
            val lots = (r["lots"] as? List<*>).orEmpty().mapNotNull { l ->
                val m = l as? Map<*, *> ?: return@mapNotNull null
                LotTrouve(
                    m["id"] as String, m["idForever"] as? String ?: "", m["pays"] as? String ?: "", m["nom"] as? String ?: "",
                    (m["montant"] as? Number)?.toLong() ?: 0L, (m["ecart"] as? Number)?.toLong(),
                    (m["ccTotal"] as? Number)?.toDouble() ?: 0.0,
                    (m["prixFactures"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) -> (v as? Number)?.let { k.toString() to it.toLong() } }.toMap()
                )
            }
            val lues = (r["lignes"] as? List<*>).orEmpty().mapNotNull { x ->
                val m = x as? Map<*, *> ?: return@mapNotNull null
                LigneLue(m["ref"] as? String ?: "", m["designation"] as? String ?: "", (m["quantite"] as? Number)?.toInt() ?: 0, (m["montantHT"] as? Number)?.toLong() ?: 0L)
            }
            fun n(cle: String) = (r[cle] as? Number)?.toLong()
            lecture = Lecture(n("total"), n("sousTotalHT"), n("tva"), n("airsi"), n("timbre"), (r["pointsCaisse"] as? Number)?.toDouble(), r["numero"] as? String, lues, lots)
            choisi = lots.firstOrNull()
            montant = ((r["total"] as? Number)?.toLong() ?: lots.firstOrNull()?.montant ?: 0L).toString()
            numero = r["numero"] as? String ?: ""
        } catch (e: Exception) {
            erreur = messageReseau(e)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val l = lecture
        when {
            valide != null -> {
                Text("Reçu validé", color = SVert, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(valide!!, color = Color.White, fontSize = 15.sp)
                Bouton("Scanner le reçu suivant", onTermine)
                return@Column
            }
            erreur != null && l == null -> {
                Text(erreur!!, color = SRouge, fontSize = 15.sp)
                Bouton("Réessayer l'envoi") { essai++ }
                TextButton(onClick = onRecommencer) { Text("Rescanner", color = SOr) }
                return@Column
            }
            l == null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = SOr, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Lecture du reçu…", color = Color.White)
                }
                return@Column
            }
        }
        if (l!!.lots.isEmpty()) {
            Text("Aucune validation en attente ne correspond à l'ID lu sur ce reçu.", color = SOrange, fontSize = 15.sp)
            Text("Vérifiez que le lot est bien dans « Reçus attendus », ou rescannez en cadrant mieux l'ID Forever.", color = SGris, fontSize = 13.sp)
            Bouton("Rescanner", onRecommencer)
            return@Column
        }

        Text("Validation retrouvée", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        l.lots.forEach { lot ->
            val actif = lot == choisi
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SSurface)
                    .border(1.dp, if (actif) SOr else STrait, RoundedCornerShape(14.dp))
                    .clickable { choisi = lot }.padding(12.dp)
            ) {
                Text("ID ${lot.idForever} · ${lot.nom}", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Attendu : ${fcfa(lot.montant)}", color = SGris, fontSize = 13.sp)
            }
        }
        // Détail lu sur le reçu : produits, taxes et points caisse, pour vérifier d'un coup d'œil.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SSurface).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("Lu sur le reçu", color = SOr, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            l.lignes.forEach { Text("${it.ref} · ${it.designation} × ${it.quantite} · ${fcfa(it.montantHT)} HT", color = Color.White, fontSize = 13.sp) }
            if (l.lignes.isEmpty()) Text("Lignes de produits non lues : seul le total sera utilisé.", color = SGris, fontSize = 12.sp)
            l.sousTotalHT?.let { Text("Sous-total HT : ${fcfa(it)}", color = SGris, fontSize = 13.sp) }
            l.tva?.let { Text("TVA : ${fcfa(it)}", color = SGris, fontSize = 13.sp) }
            l.airsi?.let { Text("AIRSI : ${fcfa(it)}", color = SGris, fontSize = 13.sp) }
            l.timbre?.let { Text("Timbre : ${fcfa(it)}", color = SGris, fontSize = 13.sp) }
            l.total?.let { Text("Grand total : ${fcfa(it)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            l.points?.let { p ->
                val attendu = choisi?.cc ?: 0.0
                val ok = kotlin.math.abs(p - attendu) < 0.0005
                Text(
                    "Points caisse : %.3f".format(java.util.Locale.FRANCE, p) + if (ok) " (conforme)" else " (attendu %.3f)".format(java.util.Locale.FRANCE, attendu),
                    color = if (ok) SVert else SOrange, fontSize = 13.sp
                )
            }
        }
        OutlinedTextField(
            value = montant, onValueChange = { montant = it.filter(Char::isDigit).take(9) },
            label = { Text("Montant payé au siège (FCFA)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
        )
        val attendu = choisi?.montant ?: 0L
        val ecart = (montant.toLongOrNull() ?: 0L) - attendu
        if (ecart != 0L && montant.isNotBlank()) {
            Text(
                if (ecart > 0) "Facture supérieure de ${fcfa(ecart)} : prélevé au distributeur." else "Facture inférieure de ${fcfa(-ecart)} : rendu au distributeur.",
                color = SOrange, fontSize = 13.sp
            )
        }
        OutlinedTextField(value = numero, onValueChange = { numero = it.take(40) }, label = { Text("Numéro du reçu") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        erreur?.let { Text(it, color = SRouge, fontSize = 13.sp) }
        Button(
            onClick = {
                val lot = choisi ?: return@Button
                enCours = true
                erreur = null
                portee.launch {
                    try {
                        // L'image n'existe qu'en mémoire : compressée, envoyée, jamais enregistrée sur le téléphone.
                        val octets = ByteArrayOutputStream().use { flux -> reduire(image).compress(Bitmap.CompressFormat.JPEG, 70, flux); flux.toByteArray() }
                        val chemin = "recus/${lot.pays}/${lot.id}/recu-${System.currentTimeMillis()}.jpg"
                        FirebaseStorage.getInstance().reference.child(chemin)
                            .putBytes(octets, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
                        fonctions.getHttpsCallable("validerLotSiege").call(
                            mapOf(
                                "id" to lot.id, "reference" to numero.trim(), "montantFacture" to (montant.toLongOrNull() ?: 0L),
                                "recu" to chemin, "prixFactures" to lot.prixFactures
                            )
                        ).await()
                        valide = "ID ${lot.idForever} · ${fcfa(montant.toLongOrNull() ?: 0L)} · reçu ${numero.ifBlank { "sans numéro" }}"
                    } catch (e: Exception) {
                        erreur = messageReseau(e)
                    }
                    enCours = false
                }
            },
            enabled = choisi != null && (montant.toLongOrNull() ?: 0L) > 0 && !enCours,
            colors = ButtonDefaults.buttonColors(containerColor = SOr, contentColor = SNoir),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (enCours) CircularProgressIndicator(Modifier.size(22.dp), color = SNoir, strokeWidth = 2.dp)
            else Text("Valider ce reçu", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        TextButton(onClick = onRecommencer, modifier = Modifier.fillMaxWidth()) { Text("Rescanner", color = SOr) }
    }
}

@Composable
private fun Bouton(texte: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = SOr, contentColor = SNoir),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) { Text(texte, fontWeight = FontWeight.SemiBold) }
}

/** Image du reçu limitée à 1 600 px : lisible, légère à envoyer et à stocker. */
private fun reduire(b: Bitmap, coteMax: Int = 1600): Bitmap {
    val plusGrand = maxOf(b.width, b.height)
    if (plusGrand <= coteMax) return b
    val f = coteMax.toFloat() / plusGrand
    return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
}
