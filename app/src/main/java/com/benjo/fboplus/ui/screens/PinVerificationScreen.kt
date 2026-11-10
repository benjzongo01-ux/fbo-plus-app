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
    var numeroErreur by remember { mutableIntStateOf(0) }

    fun lancerBiometrie() {
        biometricHelper.authenticate(
            onSuccess = { onVerifie() },
            onError = { /* l'utilisateur bascule sur le PIN, rien à faire */ },
            onFailed = { }
        )
    }

    // Proposer la biométrie automatiquement à l'ouverture, si activée
    LaunchedEffect(Unit) {
        if (biometrieDisponible) lancerBiometrie()
    }

    EcranPin(
        titre = "Bon retour",
        sousTitre = "Entrez votre code PIN pour continuer.",
        saisis = currentInput.length,
        erreur = errorMessage,
        numeroErreur = numeroErreur,
        enCours = false,
        onChiffre = { c ->
            if (currentInput.length < 4) currentInput += c
            if (currentInput.length == 4) {
                if (storage.verifyPin(currentInput)) {
                    onVerifie()
                } else {
                    errorMessage = "Code incorrect. Réessayez."
                    numeroErreur++
                    currentInput = ""
                }
            }
        },
        onEffacer = {
            if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
            errorMessage = null
        },
        onBiometrie = if (biometrieDisponible) ({ lancerBiometrie() }) else null,
        // Sans cette sortie, un PIN oublié bloquait l'application. Les ventes du téléphone sont conservées.
        lien = "Code oublié ? Se reconnecter (Google ou email)",
        onLien = onOublie
    )
}
