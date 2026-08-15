package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.viewmodel.AuthUiState
import com.benjo.fboplus.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

private val Or = Color(0xFFD4AF37)
private val OrClair = Color(0xFFFFE58A)
private val FondNoir = Color(0xFF000000)

@Composable
fun VerificationScreen(
    authViewModel: AuthViewModel,
    email: String,
    onNeedsPinCreation: (String) -> Unit,
    onNeedsPinEntry: (String) -> Unit
) {
    val uiState by authViewModel.uiState.collectAsState()

    var code by remember { mutableStateOf("") }
    var secondesRestantes by remember { mutableStateOf(60) }
    var peutRenvoyer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        secondesRestantes = 60
        peutRenvoyer = false
        while (secondesRestantes > 0) {
            delay(1000)
            secondesRestantes--
        }
        peutRenvoyer = true
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.NeedsPinCreation -> onNeedsPinCreation(state.token)
            is AuthUiState.NeedsPinEntry -> onNeedsPinEntry(state.token)
            else -> {}
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(FondNoir),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("FBO+", color = Or, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Vérification de l'email",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Un code à 4 chiffres a été envoyé à\n$email",
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = code,
                onValueChange = {
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        code = it
                        if (uiState is AuthUiState.Error) authViewModel.resetState()
                    }
                },
                label = { Text("Code de vérification") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 28.sp, letterSpacing = 12.sp, textAlign = TextAlign.Center
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Or,
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Or,
                    cursorColor = Or
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState is AuthUiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text((uiState as AuthUiState.Error).message, color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { authViewModel.verifyCode(email, code) },
                enabled = code.length == 4 && uiState !is AuthUiState.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Or, contentColor = Color.Black, disabledContainerColor = Color(0xFF444444)
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Vérifier", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (peutRenvoyer) {
                TextButton(onClick = {
                    authViewModel.resendCode(email)
                    secondesRestantes = 60
                    peutRenvoyer = false
                }) {
                    Text("Renvoyer le code", color = OrClair)
                }
            } else {
                Text("Renvoyer le code dans ${secondesRestantes}s", color = Color(0xFF888888), fontSize = 13.sp)
            }
        }
    }
}