package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.benjo.fboplus.viewmodel.AuthUiState
import com.benjo.fboplus.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

private val Or = Color(0xFFD4AF37)
private val OrClair = Color(0xFFFFE58A)
private val FondNoir = Color(0xFF000000)
private const val LONGUEUR_CODE = 6

@Composable
fun VerificationScreen(
    authViewModel: AuthViewModel,
    telephone: String,
    onNeedsPinCreation: () -> Unit,
    onNeedsPinEntry: () -> Unit
) {
    val activity = LocalContext.current as FragmentActivity
    val uiState by authViewModel.uiState.collectAsState()

    var code by remember { mutableStateOf("") }
    var secondesRestantes by remember { mutableStateOf(60) }
    var peutRenvoyer by remember { mutableStateOf(false) }
    var envois by remember { mutableStateOf(0) }

    LaunchedEffect(envois) {
        secondesRestantes = 60
        peutRenvoyer = false
        while (secondesRestantes > 0) {
            delay(1000)
            secondesRestantes--
        }
        peutRenvoyer = true
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            AuthUiState.NeedsPinCreation -> onNeedsPinCreation()
            AuthUiState.NeedsPinEntry -> onNeedsPinEntry()
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
                "Vérification du numéro",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Un code à $LONGUEUR_CODE chiffres a été envoyé par SMS au\n$telephone",
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = code,
                onValueChange = {
                    if (it.length <= LONGUEUR_CODE && it.all { c -> c.isDigit() }) {
                        code = it
                        if (uiState is AuthUiState.Error) authViewModel.resetState()
                    }
                },
                label = { Text("Code de vérification") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 28.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center
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
                onClick = { authViewModel.verifierCode(code) },
                enabled = code.length == LONGUEUR_CODE && uiState !is AuthUiState.Loading,
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
                    authViewModel.renvoyerCode(activity, telephone)
                    envois++
                }) {
                    Text("Renvoyer le code", color = OrClair)
                }
            } else {
                Text("Renvoyer le code dans ${secondesRestantes}s", color = Color(0xFF888888), fontSize = 13.sp)
            }
        }
    }
}