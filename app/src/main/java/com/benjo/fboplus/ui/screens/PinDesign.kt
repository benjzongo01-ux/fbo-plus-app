package com.benjo.fboplus.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/*
 * Écrans du code PIN, dans le style de la connexion : noir profond, or, blanc.
 * Petit logo FBO+, quatre points d'or qui s'allument, clavier rond et sobre,
 * secousse des points quand le code est faux.
 */

private val PNoir = Color(0xFF08090C)
private val PSurface = Color(0xFF15171D)
private val PSurfaceAppui = Color(0xFF22252D)
private val PTrait = Color(0xFF2A2D35)
private val POr = Color(0xFFE5B53A)
private val PGris = Color(0xFF9AA0AC)
private val PRouge = Color(0xFFFF6B6B)

/**
 * @param saisis nombre de chiffres déjà tapés (0 à 4)
 * @param numeroErreur augmente à chaque code refusé : déclenche la secousse
 * @param onBiometrie touche empreinte, si la biométrie est disponible
 */
@Composable
fun EcranPin(
    titre: String,
    sousTitre: String?,
    saisis: Int,
    erreur: String?,
    numeroErreur: Int,
    enCours: Boolean,
    onChiffre: (Char) -> Unit,
    onEffacer: () -> Unit,
    onBiometrie: (() -> Unit)? = null,
    lien: String? = null,
    onLien: () -> Unit = {}
) {
    val secousse = remember { Animatable(0f) }
    LaunchedEffect(numeroErreur) {
        if (numeroErreur > 0) {
            secousse.animateTo(0f, keyframes {
                durationMillis = 420
                -14f at 60; 12f at 120; -9f at 180; 7f at 240; -4f at 300; 2f at 360
            })
        }
    }
    val temps by rememberInfiniteTransition(label = "halo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )

    Box(Modifier.fillMaxSize().background(PNoir)) {
        Canvas(Modifier.fillMaxSize()) {
            val respiration = 0.85f + 0.15f * sin(temps * 2 * PI.toFloat())
            val centre = Offset(size.width / 2, size.height * 0.12f)
            drawCircle(
                Brush.radialGradient(
                    listOf(POr.copy(alpha = 0.16f * respiration), Color.Transparent),
                    center = centre, radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f, center = centre
            )
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FBO", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                Text("+", color = POr, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.weight(0.6f))
            Text(titre, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (sousTitre != null) {
                Spacer(Modifier.height(8.dp))
                Text(sousTitre, color = PGris, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.graphicsLayer { translationX = secousse.value.dp.toPx() }
            ) {
                repeat(4) { i -> Point(rempli = i < saisis, erreur = erreur != null && saisis == 0) }
            }
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                when {
                    enCours -> CircularProgressIndicator(Modifier.size(22.dp), color = POr, strokeWidth = 2.dp)
                    erreur != null -> Text(erreur, color = PRouge, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.weight(0.4f))
            Clavier(actif = !enCours, onChiffre = onChiffre, onEffacer = onEffacer, onBiometrie = onBiometrie)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                if (lien != null) {
                    Text(
                        lien,
                        color = POr,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable(remember { MutableInteractionSource() }, null, onClick = onLien)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Point(rempli: Boolean, erreur: Boolean) {
    val echelle by animateFloatAsState(if (rempli) 1.15f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "point")
    val couleur by animateColorAsState(
        when {
            rempli -> POr
            erreur -> PRouge.copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        tween(150), label = "couleur"
    )
    Box(
        Modifier
            .size(14.dp)
            .graphicsLayer { scaleX = echelle; scaleY = echelle }
            .clip(CircleShape)
            .background(couleur)
            .border(1.5.dp, if (rempli) POr else if (erreur) PRouge else PTrait, CircleShape)
    )
}

@Composable
private fun Clavier(actif: Boolean, onChiffre: (Char) -> Unit, onEffacer: () -> Unit, onBiometrie: (() -> Unit)?) {
    val rangees = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rangees.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                r.forEach { c -> Touche(actif, texte = c.toString()) { onChiffre(c) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            if (onBiometrie != null) Touche(actif, icone = Icons.Filled.Fingerprint, discrete = true, onClick = onBiometrie)
            else Spacer(Modifier.size(72.dp))
            Touche(actif, texte = "0") { onChiffre('0') }
            Touche(actif, icone = Icons.AutoMirrored.Outlined.Backspace, discrete = true, onClick = onEffacer)
        }
    }
}

@Composable
private fun Touche(
    actif: Boolean,
    texte: String? = null,
    icone: ImageVector? = null,
    discrete: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val appuye by interaction.collectIsPressedAsState()
    val echelle by animateFloatAsState(if (appuye) 0.92f else 1f, label = "touche")
    val fond by animateColorAsState(
        when {
            appuye -> PSurfaceAppui
            discrete -> Color.Transparent
            else -> PSurface
        },
        tween(120), label = "fond"
    )
    Box(
        Modifier
            .size(72.dp)
            .graphicsLayer { scaleX = echelle; scaleY = echelle }
            .clip(CircleShape)
            .background(fond)
            .clickable(interaction, null, enabled = actif, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (texte != null) Text(texte, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        if (icone != null) Icon(icone, null, tint = if (icone == Icons.Filled.Fingerprint) POr else PGris, modifier = Modifier.size(26.dp))
    }
}
