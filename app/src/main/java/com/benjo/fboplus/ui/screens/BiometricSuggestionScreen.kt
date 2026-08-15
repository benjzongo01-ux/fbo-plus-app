package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SBackgroundBlack)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔒", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "Activer la connexion biométrique ?",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = STextWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Utilisez votre empreinte ou Face ID pour vous connecter et valider vos opérations plus rapidement",
                    fontSize = 13.sp,
                    color = STextGray
                )
                Spacer(modifier = Modifier.height(32.dp))

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
                    colors = ButtonDefaults.buttonColors(containerColor = SGoldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Activer", fontWeight = FontWeight.Bold, color = SBackgroundBlack)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = {
                    storage.setBiometricEnabled(false)
                    onTermine()
                }) {
                    Text("Plus tard", color = STextGray)
                }
            }
        }
    }
}