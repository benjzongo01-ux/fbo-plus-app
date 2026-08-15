package com.benjo.fboplus.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.listeIndicatifs
import com.benjo.fboplus.ui.components.*
import com.benjo.fboplus.viewmodel.AuthUiState
import com.benjo.fboplus.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InscriptionScreen(
    authViewModel: AuthViewModel,
    onCodeEnvoye: (String) -> Unit,
    onNeedsPinEntry: (String) -> Unit,
    onAllerVersConnexion: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var indicatifSelectionne by remember { mutableStateOf(listeIndicatifs.first { it.dialCode == "+226" }) }
    var numeroTelephone by remember { mutableStateOf("") }
    var cguAcceptees by remember { mutableStateOf(false) }
    var menuIndicatifOuvert by remember { mutableStateOf(false) }

    val uiState by authViewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.CodeSent -> onCodeEnvoye(state.email)
            is AuthUiState.NeedsPinEntry -> onNeedsPinEntry(state.token)
            else -> {}
        }
    }

    val numeroValide = numeroTelephone.length in indicatifSelectionne.minDigits..indicatifSelectionne.maxDigits

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
            Text("Créer un compte", fontSize = 16.sp, color = STextGray)
            Spacer(modifier = Modifier.height(32.dp))

            AppTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (uiState is AuthUiState.Error) authViewModel.resetState()
                },
                placeholder = "Adresse email",
                keyboardType = KeyboardType.Email
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(
                    expanded = menuIndicatifOuvert,
                    onExpandedChange = { menuIndicatifOuvert = !menuIndicatifOuvert },
                    modifier = Modifier.width(110.dp)
                ) {
                    OutlinedTextField(
                        value = "${indicatifSelectionne.flag} ${indicatifSelectionne.dialCode}",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SFieldBackground,
                            unfocusedContainerColor = SFieldBackground,
                            focusedTextColor = STextWhite,
                            unfocusedTextColor = STextWhite,
                            focusedBorderColor = SGoldPrimary,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = menuIndicatifOuvert,
                        onDismissRequest = { menuIndicatifOuvert = false },
                        modifier = Modifier.heightIn(max = 350.dp)
                    ) {
                        listeIndicatifs.forEach { pays ->
                            DropdownMenuItem(
                                text = { Text("${pays.flag} ${pays.name} (${pays.dialCode})") },
                                onClick = {
                                    indicatifSelectionne = pays
                                    menuIndicatifOuvert = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                AppTextField(
                    value = numeroTelephone,
                    onValueChange = { input -> numeroTelephone = input.filter { it.isDigit() } },
                    placeholder = "Numéro de téléphone",
                    keyboardType = KeyboardType.Phone,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = cguAcceptees,
                    onCheckedChange = { cguAcceptees = it },
                    colors = CheckboxDefaults.colors(checkedColor = SGoldPrimary)
                )
                Text(
                    "J'accepte les Conditions d'utilisation et la Politique de confidentialité",
                    color = STextGray,
                    fontSize = 12.sp
                )
            }

            if (uiState is AuthUiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text((uiState as AuthUiState.Error).message, color = SError, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    authViewModel.register(email, indicatifSelectionne.dialCode + numeroTelephone)
                },
                enabled = email.contains("@") && numeroValide && cguAcceptees && uiState !is AuthUiState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = SGoldPrimary),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SBackgroundBlack)
                } else {
                    Text("S'inscrire", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = STextGray.copy(alpha = 0.3f))
                Text("  ou  ", color = STextGray, fontSize = 12.sp)
                HorizontalDivider(modifier = Modifier.weight(1f), color = STextGray.copy(alpha = 0.3f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            GoogleAuthButton(onClick = { /* branchement plus tard */ })

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onAllerVersConnexion) {
                Text("Déjà un compte ? Se connecter", color = STextGray, fontSize = 14.sp)
            }
        }
    }
}