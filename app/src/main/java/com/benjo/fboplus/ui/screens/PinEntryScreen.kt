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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.ui.components.*
import com.benjo.fboplus.viewmodel.AuthUiState
import com.benjo.fboplus.viewmodel.AuthViewModel

/**
 * Saisie du PIN existant lors d'une connexion sur un nouvel appareil.
 * Contrairement à PinVerificationScreen (vérification locale), ce PIN
 * est vérifié côté serveur via le token temporaire reçu après verify-email.
 */
@Composable
fun PinEntryScreen(
    authViewModel: AuthViewModel,
    token: String,
    onSuccess: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsState()
    var currentInput by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) onSuccess()
    }

    fun onDigit(digit: String) {
        if (currentInput.length < 4) currentInput += digit
        if (currentInput.length == 4) {
            authViewModel.loginWithPin(token, currentInput)
        }
    }

    fun onDelete() {
        if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
    }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Error) currentInput = ""
    }

    Column(
        modifier = Modifier.fillMaxSize().background(SBackgroundBlack).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Entrez votre code PIN", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = STextWhite)
        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier.size(16.dp).clip(CircleShape)
                        .background(if (index < currentInput.length) SGoldPrimary else STextGray.copy(alpha = 0.3f))
                )
            }
        }

        if (uiState is AuthUiState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text((uiState as AuthUiState.Error).message, color = SError, fontSize = 13.sp)
        }

        if (uiState is AuthUiState.Loading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(color = SGoldPrimary)
        }

        Spacer(modifier = Modifier.height(40.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )

        val clavierActif = uiState !is AuthUiState.Loading

        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                            .then(
                                if (key.isNotEmpty() && clavierActif)
                                    Modifier.border(1.dp, STextGray.copy(alpha = 0.3f), CircleShape).clickable {
                                        when (key) {
                                            "⌫" -> onDelete()
                                            else -> onDigit(key)
                                        }
                                    }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key.isNotEmpty()) Text(key, fontSize = 22.sp, color = STextWhite)
                    }
                }
            }
        }
    }
}