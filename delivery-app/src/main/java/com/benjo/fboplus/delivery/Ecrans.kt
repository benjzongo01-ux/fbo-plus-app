package com.benjo.fboplus.delivery

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas as CanvasCompose
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Fond = Color(0xFF0B0D14)
val Carte = Color(0xFF141821)
val Surface2 = Color(0xFF232834)
val Bordure = Color(0xFF2C3040)
val Or = Color(0xFFD4AF37)
val Texte = Color(0xFFF2F2F2)
val TexteDoux = Color(0xFFB0B8C8)
val TexteDiscret = Color(0xFF8791A5)
val Rouge = Color(0xFFFF5470)
val Vert = Color(0xFF00C853)
val Bleu = Color(0xFF6EA8FF)

val Couleurs = darkColorScheme(
    primary = Or,
    onPrimary = Fond,
    background = Fond,
    surface = Carte,
    onSurface = Texte,
    surfaceVariant = Surface2,
    error = Rouge
)

private val Forme = RoundedCornerShape(16.dp)
private fun fcfa(n: Int) = "%,d FCFA".format(Locale.FRANCE, n)

private fun libelle(statut: String) = when (statut) {
    "assignee" -> "À livrer"
    "en_livraison" -> "En livraison"
    "livree" -> "Livrée"
    "retournee" -> "Retournée"
    "annulee" -> "Annulée"
    else -> statut
}

private fun couleur(statut: String) = when (statut) {
    "livree" -> Vert
    "retournee", "annulee" -> Rouge
    "en_livraison" -> Bleu
    else -> Or
}

@Composable
private fun Badge(statut: String) {
    Text(
        libelle(statut),
        color = couleur(statut),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(couleur(statut).copy(alpha = 0.12f), RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun couleursChamp() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Surface2,
    unfocusedContainerColor = Surface2,
    focusedTextColor = Texte,
    unfocusedTextColor = Texte,
    focusedBorderColor = Or,
    unfocusedBorderColor = Bordure,
    cursorColor = Or,
    focusedLabelColor = Or,
    unfocusedLabelColor = TexteDoux
)

@Composable
private fun BoutonPrincipal(texte: String, actif: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = actif,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Or, contentColor = Fond),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text(texte, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun ConnexionEcran(vm: ConnexionViewModel) {
    val activity = LocalContext.current as Activity
    val etat by vm.etat.collectAsState()
    val erreur by vm.erreur.collectAsState()
    var telephone by remember { mutableStateOf("+226") }
    var code by remember { mutableStateOf("") }
    val chargement = etat == EtatConnexion.Chargement
    val etape = etat as? EtatConnexion.Code

    Column(
        Modifier
            .fillMaxSize()
            .background(Fond)
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("FBO+", color = Or, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Delivery", color = TexteDoux, fontSize = 16.sp)
        Spacer(Modifier.height(32.dp))

        if (etape == null) {
            OutlinedTextField(
                value = telephone,
                onValueChange = { telephone = it.filter { c -> c.isDigit() || c == '+' } },
                label = { Text("Numéro de téléphone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp),
                colors = couleursChamp(),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
                label = { Text("Code reçu par SMS") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(12.dp),
                colors = couleursChamp(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        erreur?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Rouge, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))

        if (chargement) {
            CircularProgressIndicator(color = Or)
        } else if (etape == null) {
            BoutonPrincipal("Recevoir le code", telephone.length >= 8) {
                vm.envoyerCode(activity, telephone)
            }
        } else {
            BoutonPrincipal("Se connecter", code.length == 6) { vm.verifier(etape.id, code) }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Les comptes livreurs sont créés par l'administration.",
            color = TexteDiscret,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CarteMission(mission: Mission, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Forme)
            .background(Carte)
            .border(1.dp, Bordure, Forme)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                mission.clientNom.ifBlank { "Client" },
                color = Texte,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Badge(mission.statut)
        }
        if (mission.adresse.isNotBlank()) {
            Text(mission.adresse, color = TexteDoux, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            "À encaisser : ${fcfa(mission.montant)}",
            color = Or,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun Titre(texte: String, fin: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(texte, color = Texte, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        fin()
    }
}

@Composable
private fun EtatVide(texte: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(texte, color = TexteDoux, fontSize = 14.sp)
    }
}

@Composable
fun MissionsEcran(vm: MissionsViewModel, onOuvrir: (String) -> Unit, onDeconnexion: () -> Unit) {
    val missions by vm.missions.collectAsState()
    val enCours = missions?.filter { it.enCours }

    Column(Modifier.fillMaxSize().background(Fond)) {
        Titre("Mes missions") {
            IconButton(onClick = onDeconnexion) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Se déconnecter", tint = TexteDoux)
            }
        }
        when {
            enCours == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Or)
            }
            enCours.isEmpty() -> EtatVide("Aucune mission pour le moment")
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(enCours, key = { it.id }) { m -> CarteMission(m) { onOuvrir(m.id) } }
            }
        }
    }
}

@Composable
fun HistoriqueEcran(vm: MissionsViewModel) {
    val missions by vm.missions.collectAsState()
    val terminees = missions?.filter { !it.enCours }.orEmpty()

    Column(Modifier.fillMaxSize().background(Fond)) {
        Titre("Historique")
        if (terminees.isEmpty()) {
            EtatVide("Aucune livraison terminée")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(terminees, key = { it.id }) { m -> CarteMission(m) }
            }
        }
    }
}

@Composable
fun RevenusEcran(vm: MissionsViewModel) {
    val missions by vm.missions.collectAsState()
    val livrees = missions?.filter { it.statut == "livree" }.orEmpty()
    val format = remember { SimpleDateFormat("MM/yyyy", Locale.FRANCE) }
    val ceMois = format.format(Date())
    val duMois = livrees.filter { format.format(Date(it.dateMillis)) == ceMois }

    Column(Modifier.fillMaxSize().background(Fond)) {
        Titre("Revenus")
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CarteChiffre("Commissions ce mois", fcfa(duMois.sumOf { it.commission }), "${duMois.size} livraison(s)")
            CarteChiffre("Commissions au total", fcfa(livrees.sumOf { it.commission }), "${livrees.size} livraison(s)")
            CarteChiffre("Encaissé au total", fcfa(livrees.sumOf { it.montant }), "À reverser à FBO+")
        }
    }
}

@Composable
private fun CarteChiffre(libelle: String, valeur: String, detail: String) {
    Column(Modifier.fillMaxWidth().clip(Forme).background(Carte).padding(16.dp)) {
        Text(libelle, color = TexteDoux, fontSize = 13.sp)
        Text(valeur, color = Texte, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = TexteDiscret, fontSize = 13.sp)
    }
}

/** Partage la position tant que la mission est en cours et l'écran ouvert. */
@SuppressLint("MissingPermission")
@Composable
private fun SuiviPosition(mission: Mission, vm: MissionsViewModel) {
    val context = LocalContext.current
    var autorise by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        autorise = it
    }

    LaunchedEffect(mission.id, mission.statut, autorise) {
        if (mission.statut != "en_livraison") return@LaunchedEffect
        if (!autorise) {
            demande.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return@LaunchedEffect
        }
        val localisation = LocationServices.getFusedLocationProviderClient(context)
        while (true) {
            try {
                localisation.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                    ?.let { vm.enregistrerPosition(mission.id, it.latitude, it.longitude) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Position", "Position indisponible", e)
            }
            delay(30_000)
        }
    }
}

private fun signatureEnPng(traits: List<List<Offset>>, largeur: Int, hauteur: Int): ByteArray {
    val image = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888)
    val toile = Canvas(image).apply { drawColor(android.graphics.Color.WHITE) }
    val crayon = Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    traits.forEach { trait ->
        trait.zipWithNext { a, b -> toile.drawLine(a.x, a.y, b.x, b.y, crayon) }
    }
    return ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

@Composable
fun MissionEcran(id: String, vm: MissionsViewModel, onRetour: () -> Unit) {
    val context = LocalContext.current
    val missions by vm.missions.collectAsState()
    val occupe by vm.occupe.collectAsState()
    val erreur by vm.erreur.collectAsState()
    val mission = missions?.find { it.id == id }

    var encaisse by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var photoEnAttente by remember { mutableStateOf<Uri?>(null) }
    val traits = remember { mutableStateListOf<List<Offset>>() }
    var trait by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var taille by remember { mutableStateOf(0 to 0) }
    var saisieRetour by remember { mutableStateOf(false) }
    var motif by remember { mutableStateOf("") }
    val defilement = rememberScrollState()

    val appareil = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photo = photoEnAttente
    }

    fun prendrePhoto() {
        val dossier = File(context.cacheDir, "preuves").apply { mkdirs() }
        val fichier = File.createTempFile("preuve", ".jpg", dossier)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fichiers", fichier)
        photoEnAttente = uri
        appareil.launch(uri)
    }

    fun lancer(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Aucune application disponible", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Fond)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRetour) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Texte)
            }
            Text("Mission", color = Texte, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        if (mission == null) {
            EtatVide("Mission introuvable")
            return@Column
        }

        SuiviPosition(mission, vm)
        LaunchedEffect(mission.statut) {
            if (!mission.enCours) onRetour()
        }

        // Le geste de signature ne doit pas faire défiler la page.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(defilement, enabled = trait.isEmpty())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CarteMission(mission)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { lancer(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${mission.clientTelephone}"))) },
                    enabled = mission.clientTelephone.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Phone, null, tint = Or, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Appeler", color = Or)
                }
                OutlinedButton(
                    onClick = {
                        lancer(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(mission.adresse)}")))
                    },
                    enabled = mission.adresse.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Navigation, null, tint = Or, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Itinéraire", color = Or)
                }
            }

            Column(Modifier.fillMaxWidth().clip(Forme).background(Carte).padding(14.dp)) {
                mission.lignes.forEach { l ->
                    Text("${l.quantite} × ${l.nom}", color = Texte, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            if (mission.statut == "en_livraison") {
                Row(
                    Modifier.fillMaxWidth().clickable { encaisse = !encaisse },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = encaisse,
                        onCheckedChange = { encaisse = it },
                        colors = CheckboxDefaults.colors(checkedColor = Or, checkmarkColor = Fond)
                    )
                    Text("J'ai encaissé ${fcfa(mission.montant)}", color = Texte, fontSize = 15.sp)
                }

                OutlinedButton(
                    onClick = ::prendrePhoto,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(
                        if (photo == null) Icons.Filled.CameraAlt else Icons.Filled.Check,
                        null,
                        tint = if (photo == null) Or else Vert,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (photo == null) "Prendre la photo de preuve" else "Photo prise, reprendre", color = Or)
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Signature du client", color = TexteDoux, fontSize = 13.sp)
                    TextButton(onClick = { traits.clear() }) { Text("Effacer", color = Or) }
                }
                CanvasCompose(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .pointerInput(Unit) {
                            taille = size.width to size.height
                            detectDragGestures(
                                onDragStart = { trait = listOf(it) },
                                onDragEnd = {
                                    traits.add(trait)
                                    trait = emptyList()
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    trait = trait + change.position
                                }
                            )
                        }
                ) {
                    (traits + listOf(trait)).filter { it.size > 1 }.forEach { t ->
                        val chemin = Path().apply {
                            moveTo(t.first().x, t.first().y)
                            t.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(chemin, Color.Black, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    }
                }

                if (saisieRetour) {
                    OutlinedTextField(
                        value = motif,
                        onValueChange = { motif = it },
                        label = { Text("Motif du retour") },
                        shape = RoundedCornerShape(12.dp),
                        colors = couleursChamp(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            erreur?.let { Text(it, color = Rouge, fontSize = 14.sp) }

            when {
                occupe -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Or)
                }
                mission.statut == "assignee" -> BoutonPrincipal("Démarrer la livraison") { vm.demarrer(id) }
                saisieRetour -> BoutonPrincipal("Confirmer le retour", motif.isNotBlank()) {
                    vm.signalerRetour(id, motif, onRetour)
                }
                else -> {
                    BoutonPrincipal(
                        "Valider la livraison",
                        encaisse && photo != null && traits.isNotEmpty()
                    ) {
                        val uri = photo!!
                        vm.terminer(
                            id = id,
                            montant = mission.montant,
                            photo = { photoCompressee(context, uri) },
                            signature = signatureEnPng(traits.toList(), taille.first, taille.second),
                            apres = onRetour
                        )
                    }
                    TextButton(onClick = { saisieRetour = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Signaler un retour", color = Rouge)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
