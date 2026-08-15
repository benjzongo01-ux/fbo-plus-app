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
    token: String,
    onPinCreated: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsState()

    var step by remember { mutableStateOf(1) } // 1 = saisie, 2 = confirmation
    var firstPin by remember { mutableStateOf("") }
    var currentInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) onPinCreated()
    }

    fun onDigit(digit: String) {
        if (currentInput.length < 4) currentInput += digit
        if (currentInput.length == 4) {
            if (step == 1) {
                firstPin = currentInput
                currentInput = ""
                step = 2
                errorMessage = null
            } else {
                if (currentInput == firstPin) {
                    authViewModel.createPin(token, firstPin)
                } else {
                    errorMessage = "Les codes ne correspondent pas. Réessayez."
                    currentInput = ""
                    step = 1
                    firstPin = ""
                }
            }
        }
    }

    fun onDelete() {
        if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (step == 1) "Créez votre code PIN" else "Confirmez votre code PIN",
            fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Ce code vous servira pour vos connexions et opérations sensibles", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier.size(16.dp).clip(CircleShape)
                        .background(if (index < currentInput.length) Color(0xFF1E88E5) else Color.LightGray)
                )
            }
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = Color.Red, fontSize = 13.sp)
        }

        if (uiState is AuthUiState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text((uiState as AuthUiState.Error).message, color = Color.Red, fontSize = 13.sp)
        }

        if (uiState is AuthUiState.Loading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
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
                                    Modifier.border(1.dp, Color.LightGray, CircleShape).clickable {
                                        when (key) {
                                            "⌫" -> onDelete()
                                            else -> onDigit(key)
                                        }
                                    }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key.isNotEmpty()) Text(key, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}