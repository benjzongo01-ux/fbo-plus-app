package com.benjo.fboplus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.benjo.fboplus.ui.theme.*
import com.benjo.fboplus.viewmodel.AuthUiState
import com.benjo.fboplus.viewmodel.AuthViewModel

/**
 * Connexion à FBO+ : l'adresse email puis un code à 6 chiffres reçu par email (pas de mot de passe),
 * ou Google en un toucher ; ensuite le code PIN. Le dessin est dans ConnexionDesign.kt.
 */
@Composable
fun ConnexionScreen(
    authViewModel: AuthViewModel,
    onNeedsPinCreation: () -> Unit,
    onNeedsPinEntry: () -> Unit
) {
    val activity = LocalContext.current as FragmentActivity
    val uiState by authViewModel.uiState.collectAsState()
    val emailCode by authViewModel.emailCode.collectAsState()
    val envoi by authViewModel.numeroEnvoi.collectAsState()
    var dialogueGoogleTest by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (uiState) {
            AuthUiState.NeedsPinCreation -> onNeedsPinCreation()
            AuthUiState.NeedsPinEntry -> onNeedsPinEntry()
            else -> {}
        }
    }
    BackHandler(enabled = emailCode != null) { authViewModel.changerEmail() }

    val etat = uiState
    EcranConnexion(
        etat = EtatEcranConnexion(
            livreur = false,
            emailCode = emailCode,
            numeroEnvoi = envoi,
            enCours = etat is AuthUiState.Loading,
            message = when (etat) {
                is AuthUiState.Error -> etat.message
                is AuthUiState.Info -> etat.message
                else -> null
            },
            messageErreur = etat is AuthUiState.Error,
            googleDisponible = authViewModel.googleConfigure || authViewModel.googleDeTest
        ),
        onGoogle = {
            when {
                authViewModel.googleConfigure -> authViewModel.connecterGoogle(activity)
                authViewModel.googleDeTest -> dialogueGoogleTest = true
            }
        },
        onEnvoyerCode = { authViewModel.demanderCode(it) },
        onVerifierCode = { authViewModel.verifierCode(it) },
        onRenvoyerCode = { emailCode?.let { authViewModel.demanderCode(it) } },
        onChangerEmail = { authViewModel.changerEmail() }
    )

    if (dialogueGoogleTest) DialogueGoogleDeTest(
        onChoix = { adresse ->
            dialogueGoogleTest = false
            authViewModel.connecterGoogleDeTest(adresse)
        },
        onFermer = { dialogueGoogleTest = false }
    )
}

/** Émulateurs uniquement : remplace le sélecteur de comptes Google, qui exige le vrai projet. */
@Composable
private fun DialogueGoogleDeTest(onChoix: (String) -> Unit, onFermer: () -> Unit) {
    var adresse by remember { mutableStateOf("distributeur.test@gmail.com") }
    AlertDialog(
        onDismissRequest = onFermer,
        containerColor = BlackCard,
        title = { Text("Compte Google de test", color = WhitePrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Émulateurs : aucun vrai compte Google n'est utilisé.", color = WhiteSub, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = adresse,
                    onValueChange = { adresse = it },
                    label = { Text("Adresse Gmail fictive") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoix(adresse.trim()) }, enabled = adresse.contains("@")) {
                Text("Continuer", color = GoldPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler", color = WhiteSub) } }
    )
}
