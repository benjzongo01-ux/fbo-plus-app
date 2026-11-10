package com.benjo.fboplus.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.benjo.fboplus.auth.BiometricAuthHelper
import com.benjo.fboplus.auth.SecureTokenStorage
import com.benjo.fboplus.ui.components.*

@Composable
fun BiometricSuggestionScreen(
    onTermine: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val storage = remember { SecureTokenStorage(context) }
    val biometricHelper = remember { BiometricAuthHelper(activity) }
    val disponible = remember { biometricHelper.isBiometricAvailable() }

    // Si l'appareil ne supporte pas la biométrie, on passe directement
    LaunchedEffect(Unit) {
        if (!disponible) onTermine()
    }

    if (disponible) {
        // Empreinte dans un cercle d'or, entourée d'ondes qui s'élargissent doucement.
        val onde by rememberInfiniteTransition(label = "onde").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "o"
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08090C))
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(132.dp)
                    .drawBehind {
                        for (k in 0..1) {
                            val p = (onde + k * 0.5f) % 1f
                            drawCircle(
                                Color(0xFFE5B53A).copy(alpha = 0.35f * (1 - p)),
                                radius = size.minDimension / 2 * (0.62f + 0.38f * p),
                                style = Stroke(1.5.dp.toPx())
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF15171D))
                        .border(1.5.dp, Color(0xFFE5B53A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Fingerprint, null, tint = Color(0xFFE5B53A), modifier = Modifier.size(42.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Connexion par empreinte",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Ouvrez FBO+ et validez vos opérations d'un simple toucher, sans retaper votre code PIN.",
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = Color(0xFF9AA0AC),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    biometricHelper.authenticate(
                        onSuccess = {
                            storage.setBiometricEnabled(true)
                            onTermine()
                        },
                        onError = { onTermine() },
                        onFailed = { }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5B53A), contentColor = Color(0xFF08090C)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(58.dp)
            ) {
                Text("Activer l'empreinte", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    storage.setBiometricEnabled(false)
                    onTermine()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Plus tard", color = Color(0xFF9AA0AC), fontSize = 15.sp)
            }
        }
    }
}
