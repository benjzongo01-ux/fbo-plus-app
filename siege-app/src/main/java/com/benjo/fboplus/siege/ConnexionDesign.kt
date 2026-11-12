package com.benjo.fboplus.siege

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.siege.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/*
 * Écran de connexion FBO+. Le même fichier est copié dans FBO+ Delivery (seuls package et R changent) :
 * toute modification se fait dans les deux.
 * Sobre : noir profond, or, blanc. Peu de texte, une seule action principale.
 * Animation d'ouverture : un anneau d'or se trace autour du « + », les lettres FBO apparaissent,
 * le logo monte à sa place, puis le contenu arrive en cascade. Ensuite, un halo respire doucement
 * et quelques particules d'or flottent.
 */

private val Noir = Color(0xFF08090C)
private val Surface = Color(0xFF14161C)
private val Trait = Color(0xFF262932)
private val OrMarque = Color(0xFFE5B53A)
private val OrClair = Color(0xFFF7DA86)
private val OrProfond = Color(0xFFB8861B)
private val Gris = Color(0xFF9AA0AC)
private val GrisDiscret = Color(0xFF6B717D)
private val RougeErreur = Color(0xFFFF6B6B)

/** L'animation d'ouverture ne se joue qu'une fois par lancement de l'application. */
private var introJouee = false

/** 0 avant `debut`, 1 après `debut + duree`, adouci entre les deux. */
private fun phase(t: Float, debut: Float, duree: Float) =
    FastOutSlowInEasing.transform(((t - debut) / duree).coerceIn(0f, 1f))

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/** Ce que l'écran affiche ; les actions sont branchées par chaque application. */
data class EtatEcranConnexion(
    val livreur: Boolean,
    val emailCode: String?,
    val numeroEnvoi: Int,
    val enCours: Boolean,
    val message: String?,
    val messageErreur: Boolean,
    val googleDisponible: Boolean
)

@Composable
fun EcranConnexion(
    etat: EtatEcranConnexion,
    onGoogle: () -> Unit,
    onEnvoyerCode: (String) -> Unit,
    onVerifierCode: (String) -> Unit,
    onRenvoyerCode: () -> Unit,
    onChangerEmail: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }

    val intro = remember { Animatable(if (introJouee) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (intro.value < 1f) {
            // Démarrage à froid : on attend des images fluides, sinon l'animation serait sautée.
            var fluides = 0
            var avant = withFrameNanos { it }
            val limite = avant + 3_000_000_000L
            while (fluides < 6 && avant < limite) {
                val maintenant = withFrameNanos { it }
                fluides = if (maintenant - avant < 34_000_000L) fluides + 1 else 0
                avant = maintenant
            }
            intro.animateTo(1f, tween(2300, easing = LinearEasing))
        }
        introJouee = true
    }
    val temps by rememberInfiniteTransition(label = "ambiance").animateFloat(
        0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "temps"
    )
    val t = intro.value

    BoxWithConstraints(Modifier.fillMaxSize().background(Noir)) {
        val densite = LocalDensity.current
        val hauteur = with(densite) { maxHeight.toPx() }
        // Clavier ouvert : la zone du logo se resserre pour laisser voir le champ et le bouton.
        val clavierOuvert = WindowInsets.ime.getBottom(densite) > 0
        val zoneLogo by animateDpAsState(maxHeight * (if (clavierOuvert) 0.2f else 0.36f), tween(350), label = "zoneLogo")
        val zoneLogoPx = with(densite) { zoneLogo.toPx() }
        val contenu = phase(t, 0.7f, 0.3f)

        // Ambiance : halo d'or qui respire derrière le logo, particules qui montent lentement.
        Canvas(Modifier.fillMaxSize()) {
            val respiration = 0.85f + 0.15f * sin(temps * 2 * PI.toFloat() * 3)
            val montee = phase(t, 0.58f, 0.26f)
            val centre = Offset(size.width / 2, lerp(size.height / 2, zoneLogoPx / 2, montee))
            val intensite = phase(t, 0.05f, 0.35f) * respiration
            drawCircle(
                Brush.radialGradient(
                    listOf(OrMarque.copy(alpha = 0.22f * intensite), OrMarque.copy(alpha = 0.05f * intensite), Color.Transparent),
                    center = centre,
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = centre
            )
            repeat(12) { i ->
                val graine = (i * 0.6180339f) % 1f
                val x = size.width * ((graine * 7.3f + 0.13f * i) % 1f)
                val cycle = (temps * (0.6f + graine) + graine) % 1f
                val y = size.height * (1.05f - cycle * 1.1f)
                val a = (sin(cycle * PI.toFloat()) * 0.22f) * contenu
                drawCircle(OrClair.copy(alpha = a), radius = (0.7f + graine * 0.9f).dp.toPx(), center = Offset(x, y))
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(zoneLogo), contentAlignment = Alignment.Center) {
                Logo(etat.livreur, t, decalageY = (hauteur / 2 - zoneLogoPx / 2))
            }

            AnimatedContent(
                targetState = etat.emailCode,
                transitionSpec = {
                    val avance = targetState != null
                    (slideInHorizontally(tween(380)) { if (avance) it / 4 else -it / 4 } + fadeIn(tween(380))) togetherWith
                        (slideOutHorizontally(tween(300)) { if (avance) -it / 4 else it / 4 } + fadeOut(tween(200)))
                },
                label = "etape"
            ) { adresse ->
                Column(Modifier.fillMaxWidth()) {
                    if (adresse == null) {
                        EtapeEmail(
                            t = t, livreur = etat.livreur, email = email, onEmail = { email = it.trim() },
                            etat = etat, onGoogle = onGoogle, onContinuer = { onEnvoyerCode(email) }
                        )
                    } else {
                        EtapeCode(
                            adresse = adresse, etat = etat, onVerifier = onVerifierCode,
                            onRenvoyer = onRenvoyerCode, onChangerEmail = onChangerEmail
                        )
                    }
                }
            }
          }
            Text(
                buildAnnotatedString {
                    append("En continuant, vous acceptez les ")
                    withStyle(SpanStyle(color = Color.White)) { append("Conditions") }
                    append(" et la ")
                    withStyle(SpanStyle(color = Color.White)) { append("Politique de confidentialité") }
                    append(".")
                },
                color = GrisDiscret,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 32.dp, bottom = 24.dp)
                    .graphicsLayer { alpha = phase(t, 0.88f, 0.12f) }
            )
        }
    }
}

/** « FBO+ » : l'anneau d'or se trace autour du « + », puis les lettres arrivent et le tout monte. */
@Composable
private fun Logo(livreur: Boolean, t: Float, decalageY: Float) {
    var largeurFbo by remember { mutableIntStateOf(0) }
    val trace = phase(t, 0f, 0.32f)
    val plus = phase(t, 0.08f, 0.26f)
    val sortieAnneau = phase(t, 0.36f, 0.24f)
    val lettres = phase(t, 0.38f, 0.22f)
    val montee = phase(t, 0.58f, 0.26f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            translationY = decalageY * (1 - montee)
            val echelle = lerp(1.35f, 1f, montee)
            scaleX = echelle
            scaleY = echelle
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Au début, le « + » est seul au centre ; la rangée se recentre quand FBO apparaît.
            modifier = Modifier.graphicsLayer { translationX = -largeurFbo / 2f * (1 - lettres) }
        ) {
            Row(Modifier.onSizeChanged { largeurFbo = it.width }) {
                "FBO".forEachIndexed { i, lettre ->
                    val p = phase(t, 0.38f + i * 0.05f, 0.18f)
                    Text(
                        lettre.toString(),
                        color = Color.White,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp,
                        modifier = Modifier.graphicsLayer {
                            alpha = p
                            translationX = -(1 - p) * 14.dp.toPx()
                        }
                    )
                }
            }
            Text(
                "+",
                color = OrMarque,
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .drawBehind {
                        // Anneau d'or tracé à l'ouverture, qui s'élargit puis s'efface.
                        val a = 1f - sortieAnneau
                        if (a > 0f && trace > 0f) {
                            val rayon = 46.dp.toPx() * lerp(1f, 1.7f, sortieAnneau)
                            rotate(-90f) {
                                drawArc(
                                    Brush.sweepGradient(listOf(OrProfond, OrClair, OrMarque, OrProfond)),
                                    startAngle = 0f,
                                    sweepAngle = 360f * trace,
                                    useCenter = false,
                                    topLeft = Offset(center.x - rayon, center.y - rayon),
                                    size = Size(rayon * 2, rayon * 2),
                                    style = Stroke(width = 2.dp.toPx() * a, cap = StrokeCap.Round),
                                    alpha = a
                                )
                            }
                        }
                    }
                    .graphicsLayer {
                        alpha = plus
                        val e = lerp(0.3f, 1f, plus)
                        scaleX = e
                        scaleY = e
                        rotationZ = (1 - plus) * -90f
                    }
            )
        }
        if (livreur) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .graphicsLayer {
                        val p = phase(t, 0.55f, 0.2f)
                        alpha = p
                        translationY = (1 - p) * 8.dp.toPx()
                    }
            ) {
                Icon(Icons.Filled.AccountBalance, null, tint = OrMarque, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("SIÈGE", color = OrMarque, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            }
        }
    }
}

/** Apparition en cascade des éléments du formulaire, rang par rang. */
private fun Modifier.cascade(t: Float, rang: Int) = graphicsLayer {
    val p = phase(t, 0.68f + rang * 0.045f, 0.24f)
    alpha = p
    translationY = (1 - p) * 22.dp.toPx()
}

@Composable
private fun EtapeEmail(
    t: Float,
    livreur: Boolean,
    email: String,
    onEmail: (String) -> Unit,
    etat: EtatEcranConnexion,
    onGoogle: () -> Unit,
    onContinuer: () -> Unit
) {
    val emailValide = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$").matches(email.trim())
    Column {
        Text(
            if (livreur) "Espace correspondant" else "Bienvenue",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.cascade(t, 0)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (livreur) "Validations au siège : connectez-vous avec l'adresse invitée par l'administration."
            else "Connectez-vous ou créez votre compte en quelques secondes.",
            color = Gris,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier.cascade(t, 1)
        )
        Spacer(Modifier.height(28.dp))
        Box(Modifier.cascade(t, 2)) {
            ChampEmail(email, onEmail, enabled = !etat.enCours) { if (emailValide && !etat.enCours) onContinuer() }
        }
        Message(etat)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.cascade(t, 3)) {
            BoutonOr("Continuer", enabled = emailValide, enCours = etat.enCours, onClick = onContinuer)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 22.dp).cascade(t, 4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(Trait))
            Text("ou", color = GrisDiscret, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Trait))
        }
        Box(Modifier.cascade(t, 5)) {
            BoutonGoogle(enabled = etat.googleDisponible && !etat.enCours, onClick = onGoogle)
        }
    }
}

@Composable
private fun EtapeCode(
    adresse: String,
    etat: EtatEcranConnexion,
    onVerifier: (String) -> Unit,
    onRenvoyer: () -> Unit,
    onChangerEmail: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var renvoiDans by remember { mutableIntStateOf(60) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(etat.numeroEnvoi) {
        renvoiDans = 60
        while (renvoiDans > 0) {
            delay(1000)
            renvoiDans--
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Code refusé : on vide les cases pour une nouvelle saisie.
    LaunchedEffect(etat.message) { if (etat.messageErreur && etat.message != null) code = "" }

    Column {
        Text("Vérifiez votre email", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            buildAnnotatedString {
                append("Saisissez le code à 6 chiffres envoyé à ")
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) { append(adresse) }
            },
            color = Gris,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(28.dp))
        BasicTextField(
            value = code,
            onValueChange = { v ->
                val chiffres = v.filter(Char::isDigit).take(6)
                code = chiffres
                if (chiffres.length == 6 && !etat.enCours) onVerifier(chiffres)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.focusRequester(focus),
            decorationBox = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) { i -> CaseCode(code.getOrNull(i), actif = i == code.length && !etat.enCours, Modifier.weight(1f)) }
                }
            }
        )
        Message(etat)
        Spacer(Modifier.height(20.dp))
        BoutonOr("Vérifier", enabled = code.length == 6, enCours = etat.enCours) { onVerifier(code) }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (renvoiDans > 0) {
                Text("Renvoyer dans $renvoiDans s", color = GrisDiscret, fontSize = 14.sp)
            } else {
                Lien("Renvoyer le code", onRenvoyer)
            }
            Lien("Modifier l'adresse", onChangerEmail)
        }
    }
}

@Composable
private fun CaseCode(chiffre: Char?, actif: Boolean, modifier: Modifier) {
    // Petit rebond quand un chiffre arrive.
    val echelle by animateFloatAsState(if (chiffre != null) 1f else 0.96f, spring(dampingRatio = 0.45f), label = "case")
    Box(
        modifier
            .height(58.dp)
            .graphicsLayer { scaleX = echelle; scaleY = echelle }
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(
                if (actif) 1.5.dp else 1.dp,
                when {
                    actif -> OrMarque
                    chiffre != null -> OrMarque.copy(alpha = 0.45f)
                    else -> Trait
                },
                RoundedCornerShape(14.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(chiffre?.toString() ?: "", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChampEmail(valeur: String, onChange: (String) -> Unit, enabled: Boolean, onEnvoyer: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focus by interaction.collectIsFocusedAsState()
    val forme = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(forme)
            .background(Surface)
            .border(if (focus) 1.5.dp else 1.dp, if (focus) OrMarque else Trait, forme)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Email, null, tint = if (focus) OrMarque else GrisDiscret, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        BasicTextField(
            value = valeur,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            interactionSource = interaction,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(OrMarque),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onEnvoyer() }),
            decorationBox = { champ ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (valeur.isEmpty()) Text("Adresse email", color = GrisDiscret, fontSize = 16.sp)
                    champ()
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Message(etat: EtatEcranConnexion) {
    val texte = etat.message ?: return
    Text(
        texte,
        color = if (etat.messageErreur) RougeErreur else Color(0xFF4ADE80),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp)
    )
}

/** Bouton principal : or plein, texte noir, léger enfoncement au toucher, reflet qui passe. */
@Composable
private fun BoutonOr(texte: String, enabled: Boolean, enCours: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val appuye by interaction.collectIsPressedAsState()
    val echelle by animateFloatAsState(if (appuye) 0.97f else 1f, label = "appui")
    val reflet by rememberInfiniteTransition(label = "reflet").animateFloat(
        -0.5f, 1.5f, infiniteRepeatable(tween(1800, delayMillis = 2600, easing = LinearEasing)), label = "x"
    )
    val actif = enabled && !enCours
    val forme = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer { scaleX = echelle; scaleY = echelle }
            .clip(forme)
            .background(
                if (enabled || enCours) Brush.horizontalGradient(listOf(OrMarque, OrClair, OrMarque))
                else SolidColor(Color(0xFF2A2C33))
            )
            .drawBehind {
                if (actif) drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                        start = Offset(size.width * reflet - size.height, 0f),
                        end = Offset(size.width * reflet, size.height)
                    )
                )
            }
            .clickable(interactionSource = interaction, indication = null, enabled = actif, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (enCours) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Noir, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(texte, color = if (enabled) Noir else GrisDiscret, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, null,
                    tint = if (enabled) Noir else GrisDiscret,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Bouton Google : sobre, bordé, logo officiel en couleurs. */
@Composable
private fun BoutonGoogle(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val appuye by interaction.collectIsPressedAsState()
    val echelle by animateFloatAsState(if (appuye) 0.97f else 1f, label = "appui")
    val forme = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer { scaleX = echelle; scaleY = echelle; alpha = if (enabled) 1f else 0.5f }
            .clip(forme)
            .background(if (appuye) Surface else Color.Transparent)
            .border(BorderStroke(1.dp, Trait), forme)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.ic_google), null, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("Continuer avec Google", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Lien(texte: String, onClick: () -> Unit) {
    Text(
        texte,
        color = OrMarque,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
    )
}
