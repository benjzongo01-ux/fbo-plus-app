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
 * est vérifié côté serveur par la Cloud Function verifierPin.
 */
@Composable
fun PinEntryScreen(
    authViewModel: AuthViewModel,
    onSuccess: () -> Unit,
    onOublie: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsState()
    var currentInput by remember { mutableStateOf("") }
    var numeroErreur by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) onSuccess()
        if (uiState is AuthUiState.Error) {
            currentInput = ""
            numeroErreur++
        }
    }

    EcranPin(
        titre = "Entrez votre code PIN",
        sousTitre = "Pour confirmer que c'est bien vous.",
        saisis = currentInput.length,
        erreur = (uiState as? AuthUiState.Error)?.message,
        numeroErreur = numeroErreur,
        enCours = uiState is AuthUiState.Loading,
        onChiffre = { c ->
            if (currentInput.length < 4) currentInput += c
            if (currentInput.length == 4) authViewModel.verifierPin(currentInput)
        },
        onEffacer = { if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1) },
        // Sans cette sortie, un PIN oublié bloquait l'application. Les ventes du téléphone sont conservées.
        lien = "Code oublié ? Choisir un nouveau code",
        onLien = onOublie
    )
}
