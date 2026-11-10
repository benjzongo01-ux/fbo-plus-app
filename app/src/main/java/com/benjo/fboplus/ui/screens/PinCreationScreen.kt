package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import com.benjo.fboplus.viewmodel.AuthUiState
import com.benjo.fboplus.viewmodel.AuthViewModel

@Composable
fun PinCreationScreen(
    authViewModel: AuthViewModel,
    onPinCreated: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsState()

    var step by remember { mutableStateOf(1) } // 1 = saisie, 2 = confirmation
    var firstPin by remember { mutableStateOf("") }
    var currentInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var numeroErreur by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) onPinCreated()
        if (uiState is AuthUiState.Error) {
            currentInput = ""
            numeroErreur++
        }
    }

    fun onDigit(digit: Char) {
        if (currentInput.length < 4) currentInput += digit
        if (currentInput.length == 4) {
            if (step == 1) {
                firstPin = currentInput
                currentInput = ""
                step = 2
                errorMessage = null
            } else {
                if (currentInput == firstPin) {
                    authViewModel.creerPin(firstPin)
                } else {
                    errorMessage = "Les codes ne correspondent pas. Recommencez."
                    numeroErreur++
                    currentInput = ""
                    step = 1
                    firstPin = ""
                }
            }
        }
    }

    EcranPin(
        titre = if (step == 1) "Créez votre code PIN" else "Confirmez votre code PIN",
        sousTitre = if (step == 1) "4 chiffres pour protéger votre compte." else "Saisissez-le une seconde fois.",
        saisis = currentInput.length,
        erreur = errorMessage ?: (uiState as? AuthUiState.Error)?.message,
        numeroErreur = numeroErreur,
        enCours = uiState is AuthUiState.Loading,
        onChiffre = { onDigit(it) },
        onEffacer = { if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1) }
    )
}
