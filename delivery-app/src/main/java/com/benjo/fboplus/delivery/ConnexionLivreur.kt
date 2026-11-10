package com.benjo.fboplus.delivery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.compose.material.icons.filled.DeliveryDining
import kotlinx.coroutines.delay
import kotlin.math.sin

/** L'animation d'ouverture ne se joue qu'une fois par lancement de l'application. */
private var introJouee = false

private val FondEcran = Color(0xFF05060A)
private val BlackCard = Color(0xFF141821)
private val WhitePrimary = Color(0xFFF2F2F2)
private val WhiteSub = Color(0xFFB0B8C8)
private val WhiteFaint = Color(0xFF5A6275)
private val GoldPrimary = Color(0xFFD4AF37)
private val GreenPrimary = Color(0xFF00C853)
private val OrClair = Color(0xFFFFE27A)
private val OrVif = Color(0xFFF5BE1E)
private val OrFonce = Color(0xFFD08F0A)

/** Avancement d'une étape de l'animation : 0 avant `debut`, 1 après `debut + duree`, adouci entre les deux. */
private fun phase(t: Float, debut: Float, duree: Float) =
    FastOutSlowInEasing.transform(((t - debut) / duree).coerceIn(0f, 1f))

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/**
 * Connexion à FBO+ Delivery : même design que FBO+, avec le badge « DELIVERY » et le scooter.
 * Google en un toucher, ou l'adresse email puis un code à 6 chiffres reçu par email.
 * Seules les adresses invitées par l'administration entrent (vérifié par le serveur).
 */
@Composable
fun ConnexionEcran(vm: ConnexionViewModel) {
    val activity = LocalContext.current as Activity
    val etat by vm.etat.collectAsState()
    val erreur by vm.erreur.collectAsState()
    val info by vm.info.collectAsState()
    val emailCode by vm.emailCode.collectAsState()
    val envoi by vm.numeroEnvoi.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    val creation = false
    var dialogueGoogleTest by remember { mutableStateOf(false) }
    val message: Pair<String, Boolean>? = erreur?.let { it to true } ?: info?.let { it to false }

    BackHandler(enabled = emailCode != null) { vm.changerEmail() }

    val intro = remember { Animatable(if (introJouee) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (intro.value < 1f) intro.animateTo(1f, tween(2800, easing = LinearEasing))
        introJouee = true
    }
    val flottement by rememberInfiniteTransition(label = "globe").animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "flottement"
    )

    val enCours = etat == EtatConnexion.Chargement
    val emailValide = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$").matches(email.trim())

    BoxWithConstraints(Modifier.fillMaxSize().background(FondEcran)) {
        // Toutes les mesures suivent la maquette : 571 points de large.
        val s = maxWidth / 571f
        val densite = LocalDensity.current
        val ts: (Float) -> TextUnit = { with(densite) { (s * it).toSp() } }
        val l = with(densite) { maxWidth.toPx() }
        val h = with(densite) { maxHeight.toPx() }
        val px = with(densite) { s.toPx() }
        val t = intro.value
        val apparitionUi = phase(t, 0.72f, 0.28f)

        // Vagues dorées du bas, fixes derrière le contenu.
        Image(
            painterResource(R.drawable.connexion_vagues), null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    val v = phase(t, 0.6f, 0.35f)
                    alpha = v
                    translationY = (1 - v) * 152 * px
                }
        )
        // Halo de l'apparition, au centre de l'écran.
        val eclat = (phase(t, 0.05f, 0.3f) - phase(t, 0.42f, 0.25f)).coerceIn(0f, 1f)
        if (eclat > 0f) Box(
            Modifier.fillMaxSize().drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(OrVif.copy(alpha = 0.45f * eclat), Color.Transparent),
                        center = Offset(l / 2, h * 0.4f),
                        radius = l * 0.75f
                    ),
                    radius = l * 0.75f,
                    center = Offset(l / 2, h * 0.4f)
                )
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            // ---------- En-tête : carte, globe, logo, titre ----------
            Box(Modifier.fillMaxWidth().height(s * 505)) {
                Image(
                    painterResource(R.drawable.connexion_carte), null,
                    modifier = Modifier.offset(0.dp, 0.dp).size(s * 330, s * 115).graphicsLayer { alpha = apparitionUi }
                )
                // Globe : apparaît au centre en tournant, puis rejoint le coin.
                Image(
                    painterResource(R.drawable.connexion_globe), null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset(s * 335, 0.dp)
                        .size(s * 236, s * 460)
                        .graphicsLayer {
                            val a = phase(t, 0f, 0.4f)
                            val m = phase(t, 0.48f, 0.34f)
                            alpha = a
                            rotationZ = -24f * (1 - a)
                            val echelle = lerp(0.3f, 1.15f, a) - 0.15f * m
                            scaleX = echelle
                            scaleY = echelle
                            translationX = (l / 2 - 453 * px) * (1 - m)
                            translationY = (h * 0.4f - 230 * px) * (1 - m) + sin(flottement) * 4 * px * m
                        }
                )
                // Logo FBO+ : apparaît par-dessus le globe, puis monte à sa place.
                Image(
                    painterResource(R.drawable.connexion_logo), "FBO+",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset(s * 12, s * 103)
                        .size(s * 348, s * 148)
                        .graphicsLayer {
                            val a = phase(t, 0.18f, 0.32f)
                            val m = phase(t, 0.5f, 0.32f)
                            alpha = a
                            val echelle = lerp(0.5f, 1.2f, a) - 0.2f * m
                            scaleX = echelle
                            scaleY = echelle
                            translationX = (l / 2 - 186 * px) * (1 - m)
                            translationY = (h * 0.4f - 177 * px) * (1 - m)
                        }
                )
                BadgeDelivery(s, ts, t, l, px)
                Column(
                    Modifier
                        .offset(s * 40, s * 292)
                        .width(s * 360)
                        .graphicsLayer {
                            alpha = apparitionUi
                            translationY = (1 - apparitionUi) * 24 * px
                        }
                ) {
                    Text(
                        when {
                            emailCode != null -> "Vérification"
                            else -> "Espace livreur"
                        },
                        color = Color.White,
                        fontSize = ts(if (creation && emailCode == null) 38f else 46f),
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(s * 340)
                    )
                    Box(
                        Modifier
                            .padding(start = s * 30, top = s * 4)
                            .size(s * 280, s * 3)
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, OrVif, OrClair, Color.Transparent)))
                    )
                    Spacer(Modifier.height(s * 26))
                    if (emailCode == null) {
                        Text(
                            buildAnnotatedString {
                                append("En continuant, vous acceptez les\n")
                                withStyle(SpanStyle(color = OrVif, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                                    append("Conditions d'utilisation")
                                }
                                append(" et la\n")
                                withStyle(SpanStyle(color = OrVif, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                                    append("Politique de confidentialité.")
                                }
                            },
                            color = Color.White,
                            fontSize = ts(18f),
                            lineHeight = ts(25f),
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(start = s * 25)
                        )
                    } else {
                        Text(
                            buildAnnotatedString {
                                append("Entrez le code à 6 chiffres\nenvoyé à ")
                                withStyle(SpanStyle(color = OrVif, fontWeight = FontWeight.Bold)) { append(emailCode) }
                            },
                            color = Color.White,
                            fontSize = ts(18f),
                            lineHeight = ts(25f),
                            modifier = Modifier.padding(start = s * 25)
                        )
                    }
                }
            }

            // ---------- Formulaire ----------
            AnimatedContent(
                targetState = emailCode,
                transitionSpec = {
                    val avance = targetState != null
                    (slideInHorizontally { if (avance) it / 3 else -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (avance) -it / 3 else it / 3 } + fadeOut())
                },
                label = "etape",
                modifier = Modifier.graphicsLayer {
                    alpha = apparitionUi
                    translationY = (1 - apparitionUi) * 40 * px
                }
            ) { adresse ->
                Column(Modifier.fillMaxWidth().padding(horizontal = s * 36)) {
                    if (adresse == null) {
                        EtapeEmail(
                            s = s, ts = ts, email = email, onEmail = { email = it.trim() },
                            emailValide = emailValide, enCours = enCours, creation = creation,
                            googleDisponible = vm.googleConfigure || vm.googleDeTest,
                            onGoogle = {
                                when {
                                    vm.googleConfigure -> vm.connecterGoogle(activity)
                                    vm.googleDeTest -> dialogueGoogleTest = true
                                }
                            },
                            onEnvoyer = { vm.demanderCode(email) },
                                                                                    message = message
                        )
                    } else {
                        EtapeCode(
                            s = s, ts = ts, envoi = envoi, enCours = enCours, message = message,
                            onVerifier = { vm.verifierCode(it) },
                            onRenvoyer = { vm.demanderCode(adresse) },
                            onChangerEmail = { vm.changerEmail() }
                        )
                    }
                    Spacer(Modifier.height(s * 170))
                }
            }
        }
    }

    if (dialogueGoogleTest) DialogueGoogleDeTest(
        onChoix = { adresse ->
            dialogueGoogleTest = false
            vm.connecterGoogleDeTest(adresse)
        },
        onFermer = { dialogueGoogleTest = false }
    )
}

@Composable
private fun EtapeEmail(
    s: Dp,
    ts: (Float) -> TextUnit,
    email: String,
    onEmail: (String) -> Unit,
    emailValide: Boolean,
    enCours: Boolean,
    creation: Boolean,
    googleDisponible: Boolean,
    onGoogle: () -> Unit,
    onEnvoyer: () -> Unit,
    message: Pair<String, Boolean>?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoutonGoogle(s, ts, enabled = !enCours && googleDisponible, onClick = onGoogle)
        if (!googleDisponible) {
            Text("Connexion Google bientôt disponible.", color = WhiteSub, fontSize = ts(15f), modifier = Modifier.padding(top = s * 8))
        }
        Spacer(Modifier.height(s * 34))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(s * 2).background(Brush.horizontalGradient(listOf(Color.Transparent, OrVif))))
            Text("  ou avec votre email  ", color = Color(0xFFE6E6E6), fontSize = ts(19f))
            Box(Modifier.weight(1f).height(s * 2).background(Brush.horizontalGradient(listOf(OrVif, Color.Transparent))))
        }
        Spacer(Modifier.height(s * 30))
        ChampOr(s, ts, Icons.Outlined.Email) {
            BasicTextField(
                value = email,
                onValueChange = onEmail,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = ts(21f)),
                cursorBrush = SolidColor(OrVif),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (emailValide && !enCours) onEnvoyer() }),
                decorationBox = { champ ->
                    if (email.isEmpty()) Text("Adresse email", color = Color(0xFFC9CBD3), fontSize = ts(21f))
                    champ()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        MessageEtat(message, ts, s)
        Spacer(Modifier.height(s * 34))
        BoutonOr(
            s, ts,
            texte = if (creation) "Créer mon compte" else "Se connecter",
            enabled = emailValide && !enCours,
            enCours = enCours,
            onClick = onEnvoyer
        )
        Text(
            "Un code de connexion vous sera envoyé par email.",
            color = WhiteSub,
            fontSize = ts(15f),
            modifier = Modifier.padding(top = s * 12)
        )
        Spacer(Modifier.height(s * 30))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, null, tint = OrVif, modifier = Modifier.size(s * 30))
            Spacer(Modifier.width(s * 14))
            Text(
                "Accès réservé aux livreurs invités par l'administration.",
                color = Color.White,
                fontSize = ts(18f)
            )
        }
    }
}

@Composable
private fun EtapeCode(
    s: Dp,
    ts: (Float) -> TextUnit,
    envoi: Int,
    enCours: Boolean,
    message: Pair<String, Boolean>?,
    onVerifier: (String) -> Unit,
    onRenvoyer: () -> Unit,
    onChangerEmail: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var renvoiDans by remember { mutableIntStateOf(60) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(envoi) {
        renvoiDans = 60
        while (renvoiDans > 0) {
            delay(1000)
            renvoiDans--
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Après un code refusé, on vide les cases pour une nouvelle saisie.
    LaunchedEffect(message) { if (message?.second == true) code = "" }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            value = code,
            onValueChange = { v ->
                val chiffres = v.filter(Char::isDigit).take(6)
                code = chiffres
                if (chiffres.length == 6 && !enCours) onVerifier(chiffres)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.focusRequester(focus),
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(s * 12)) {
                    repeat(6) { i ->
                        val actif = i == code.length && !enCours
                        Box(
                            Modifier
                                .size(s * 68, s * 82)
                                .clip(RoundedCornerShape(s * 18))
                                .background(Brush.verticalGradient(listOf(Color(0xFF0E1222), Color(0xFF080A12))))
                                .border(
                                    if (actif) 2.dp else 1.dp,
                                    if (actif) OrClair else OrVif.copy(alpha = 0.55f),
                                    RoundedCornerShape(s * 18)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                code.getOrNull(i)?.toString() ?: "",
                                color = Color.White,
                                fontSize = ts(34f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        )
        MessageEtat(message, ts, s)
        Spacer(Modifier.height(s * 34))
        BoutonOr(s, ts, "Vérifier", enabled = code.length == 6 && !enCours, enCours = enCours) { onVerifier(code) }
        Spacer(Modifier.height(s * 28))
        if (renvoiDans > 0) {
            Text("Renvoyer le code dans $renvoiDans s", color = WhiteSub, fontSize = ts(18f))
        } else {
            LienIcone(s, ts, Icons.Outlined.Email, debut = "Rien reçu ?", lien = "Renvoyer le code", onClick = onRenvoyer)
        }
        Spacer(Modifier.height(s * 22))
        LienIcone(s, ts, null, debut = null, lien = "Modifier l'adresse email", onClick = onChangerEmail)
        Text(
            "Pensez à regarder dans les courriers indésirables (spam).",
            color = WhiteFaint,
            fontSize = ts(15f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = s * 18)
        )
    }
}

@Composable
private fun MessageEtat(message: Pair<String, Boolean>?, ts: (Float) -> TextUnit, s: Dp) {
    val (texte, estErreur) = message ?: return
    Text(
        texte,
        color = if (estErreur) Color(0xFFFF6B6B) else GreenPrimary,
        fontSize = ts(17f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = s * 14)
    )
}

/** Bouton blanc « Continuer avec Google », en pilule, avec un halo doré. */
@Composable
private fun BoutonGoogle(s: Dp, ts: (Float) -> TextUnit, enabled: Boolean, onClick: () -> Unit) {
    val forme = RoundedCornerShape(50)
    Row(
        Modifier
            .fillMaxWidth()
            .height(s * 80)
            .shadow(s * 18, forme, ambientColor = OrVif, spotColor = OrVif)
            .clip(forme)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE9EAF0))))
            .border(BorderStroke(s * 2, Brush.horizontalGradient(listOf(OrClair, OrVif, OrClair))), forme)
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
            .padding(horizontal = s * 60),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.ic_google), "Google", Modifier.size(s * 42))
        Spacer(Modifier.width(s * 22))
        Box(Modifier.size(s * 2, s * 44).background(Color(0xFF222222)))
        Spacer(Modifier.width(s * 22))
        Text("Continuer avec Google", color = Color(0xFF111111), fontSize = ts(23f), fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Champ sombre bordé d'or, avec une icône dorée à gauche. */
@Composable
private fun ChampOr(s: Dp, ts: (Float) -> TextUnit, icone: ImageVector, contenu: @Composable () -> Unit) {
    val forme = RoundedCornerShape(s * 26)
    Row(
        Modifier
            .fillMaxWidth()
            .height(s * 80)
            .clip(forme)
            .background(Brush.verticalGradient(listOf(Color(0xFF0D1122), Color(0xFF07090F))))
            .border(BorderStroke(s * 2, Brush.horizontalGradient(listOf(OrVif, OrClair.copy(alpha = 0.7f), OrVif))), forme)
            .padding(horizontal = s * 30),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icone, null, tint = OrVif, modifier = Modifier.size(s * 36))
        Spacer(Modifier.width(s * 30))
        Box(Modifier.weight(1f)) { contenu() }
    }
}

/** Grand bouton doré en pilule, avec la pastille noire et sa flèche. */
@Composable
private fun BoutonOr(s: Dp, ts: (Float) -> TextUnit, texte: String, enabled: Boolean, enCours: Boolean, onClick: () -> Unit) {
    val forme = RoundedCornerShape(50)
    // Reflet qui glisse sur le bouton.
    val reflet by rememberInfiniteTransition(label = "reflet").animateFloat(
        -0.4f, 1.4f, infiniteRepeatable(tween(2600, delayMillis = 1200, easing = LinearEasing)), label = "x"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(s * 80)
            .shadow(s * 16, forme, ambientColor = OrVif, spotColor = OrVif)
            .clip(forme)
            .background(Brush.verticalGradient(listOf(OrClair, OrVif, OrFonce)))
            .drawBehind {
                if (enabled) drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                        start = Offset(size.width * reflet - size.height, 0f),
                        end = Offset(size.width * reflet + size.height, size.height)
                    )
                )
            }
            .clickable(enabled = enabled && !enCours, onClick = onClick)
            .graphicsLayer { alpha = if (enabled || enCours) 1f else 0.55f },
        contentAlignment = Alignment.Center
    ) {
        if (enCours) {
            CircularProgressIndicator(Modifier.size(s * 36), color = Color(0xFF111111), strokeWidth = 2.5.dp)
        } else {
            Text(texte, color = Color(0xFF111111), fontSize = ts(27f), fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = s * 22)
                .size(s * 44)
                .clip(CircleShape)
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = OrVif, modifier = Modifier.size(s * 36))
        }
    }
}

/** Ligne « icône · texte · lien doré souligné d'un trait ». */
@Composable
private fun LienIcone(s: Dp, ts: (Float) -> TextUnit, icone: ImageVector?, debut: String?, lien: String, onClick: () -> Unit) {
    Row(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = s * 6),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icone != null) {
            Icon(icone, null, tint = OrVif, modifier = Modifier.size(s * 30))
            Spacer(Modifier.width(s * 14))
        }
        if (debut != null) {
            Text(debut, color = Color.White, fontSize = ts(20f), fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(s * 10))
        }
        Column {
            Text(lien, color = OrVif, fontSize = ts(21f), fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .padding(top = s * 3)
                    .fillMaxWidth()
                    .height(s * 2)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, OrVif, Color.Transparent)))
            )
        }
    }
}

/** Émulateurs uniquement : remplace le sélecteur de comptes Google, qui exige le vrai projet. */
@Composable
private fun DialogueGoogleDeTest(onChoix: (String) -> Unit, onFermer: () -> Unit) {
    var adresse by remember { mutableStateOf("distributeur.test@gmail.com") }
    AlertDialog(
        onDismissRequest = onFermer,
        containerColor = BlackCard,
        title = { Text("Compte Google de test", color = WhitePrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Émulateurs : aucun vrai compte Google n'est utilisé.", color = WhiteSub, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = adresse,
                    onValueChange = { adresse = it },
                    label = { Text("Adresse Gmail fictive") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoix(adresse.trim()) }, enabled = adresse.contains("@")) {
                Text("Continuer", color = GoldPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler", color = WhiteSub) } }
    )
}

/** « 🛵 DELIVERY » sous le logo : distingue l'application des livreurs de celle des distributeurs. */
@Composable
private fun BadgeDelivery(s: Dp, ts: (Float) -> TextUnit, t: Float, largeur: Float, px: Float) {
    val badge = phase(t, 0.62f, 0.25f)
    val scooter = phase(t, 0.58f, 0.4f)
    Row(
        Modifier
            .offset(s * 70, s * 238)
            .graphicsLayer {
                alpha = badge
                scaleX = lerp(0.6f, 1f, badge)
                scaleY = lerp(0.6f, 1f, badge)
            }
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color(0xFF1A1405), Color(0xFF0B0D14))))
            .border(BorderStroke(s * 2, Brush.horizontalGradient(listOf(OrClair, OrVif, OrFonce))), RoundedCornerShape(50))
            .padding(horizontal = s * 22, vertical = s * 8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.DeliveryDining, null, tint = OrVif,
            modifier = Modifier
                .size(s * 38)
                .graphicsLayer { translationX = -(1 - scooter) * largeur * 0.9f }
        )
        Spacer(Modifier.width(s * 12))
        Text(
            "DELIVERY",
            color = Color.White,
            fontSize = ts(26f),
            fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic,
            letterSpacing = ts(5f)
        )
    }
}
