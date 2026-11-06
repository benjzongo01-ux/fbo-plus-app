package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.benjo.fboplus.ui.components.*
import com.benjo.fboplus.viewmodel.AuthUiState
import com.benjo.fboplus.viewmodel.AuthViewModel

@Composable
fun ConnexionScreen(
    authViewModel: AuthViewModel,
    onCodeEnvoye: (String) -> Unit,
    onNeedsPinCreation: () -> Unit,
    onNeedsPinEntry: () -> Unit,
    onAllerVersInscription: () -> Unit
) {
    val activity = LocalContext.current as FragmentActivity
    var indicatif by remember { mutableStateOf(indicatifParDefaut) }
    var numero by remember { mutableStateOf("") }

    val uiState by authViewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.CodeSent -> onCodeEnvoye(state.telephone)
            AuthUiState.NeedsPinCreation -> onNeedsPinCreation()
            AuthUiState.NeedsPinEntry -> onNeedsPinEntry()
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SBackgroundBlack)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("FBO+", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = SGoldPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Connexion", fontSize = 16.sp, color = STextGray)
            Spacer(modifier = Modifier.height(32.dp))

            ChampTelephone(
                indicatif = indicatif,
                onIndicatifChange = { indicatif = it },
                numero = numero,
                onNumeroChange = {
                    numero = it
                    if (uiState is AuthUiState.Error) authViewModel.resetState()
                }
            )

            Text(
                "Un code de vérification vous sera envoyé par SMS",
                color = STextGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (uiState is AuthUiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text((uiState as AuthUiState.Error).message, color = SError, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    authViewModel.envoyerCode(activity, indicatif.dialCode + numero, inscription = false)
                },
                enabled = indicatif.accepte(numero) && uiState !is AuthUiState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = SGoldPrimary),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SBackgroundBlack)
                } else {
                    Text("Recevoir le code", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onAllerVersInscription) {
                Text("Pas de compte ? Créer un compte", color = STextGray, fontSize = 14.sp)
            }
        }
    }
}
