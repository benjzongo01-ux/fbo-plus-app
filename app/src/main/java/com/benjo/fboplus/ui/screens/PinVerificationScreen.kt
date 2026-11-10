package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.benjo.fboplus.auth.BiometricAuthHelper
import com.benjo.fboplus.auth.SecureTokenStorage
import com.benjo.fboplus.ui.components.*

@Composable
fun PinVerificationScreen(
    onVerifie: () -> Unit,
    onOublie: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val storage = remember { SecureTokenStorage(context) }
    val biometricHelper = remember { BiometricAuthHelper(activity) }
    val biometrieDisponible = remember {
        storage.isBiometricEnabled() && biometricHelper.isBiometricAvailable()
    }

    var currentInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun lancerBiometrie() {
        biometricHelper.authenticate(
            onSuccess = { onVerifie() },
            onError = { /* utilisateur bascule sur le PIN, rien à faire */ },
            onFailed = { }
        )
    }

    // Proposer la biométrie automatiquement à l'ouverture, si activée
    LaunchedEffect(Unit) {
        if (biometrieDisponible) lancerBiometrie()
    }

    fun onDigit(digit: String) {
        if (currentInput.length < 4) currentInput += digit
        if (currentInput.length == 4) {
            if (storage.verifyPin(currentInput)) {
                onVerifie()
            } else {
                errorMessage = "Code incorrect. Réessayez."
                currentInput = ""
            }
        }
    }

    fun onDelete() {
        if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SBackgroundBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Entrez votre code PIN", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = STextWhite)
        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (index < currentInput.length) SGoldPrimary else STextGray.copy(alpha = 0.3f))
                )
            }
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = SError, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(40.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(if (biometrieDisponible) "👆" else "", "0", "⌫")
        )

        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .then(
                                if (key.isNotEmpty())
                                    Modifier
                                        .border(1.dp, STextGray.copy(alpha = 0.3f), CircleShape)
                                        .clickable {
                                            when (key) {
                                                "⌫" -> onDelete()
                                                "👆" -> lancerBiometrie()
                                                else -> onDigit(key)
                                            }
                                        }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key.isNotEmpty()) {
                            Text(key, fontSize = if (key == "👆") 26.sp else 22.sp, color = STextWhite)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        // Sans cette sortie, un PIN oublié bloquait l'application. Les ventes du téléphone sont conservées.
        TextButton(onClick = onOublie) {
            Text("Code oublié ? Se reconnecter (Google ou email)", color = SGoldPrimary, fontSize = 14.sp)
        }
    }
}