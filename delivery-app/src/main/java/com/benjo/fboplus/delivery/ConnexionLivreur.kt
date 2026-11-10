package com.benjo.fboplus.delivery

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * Connexion à FBO+ Delivery : même écran que FBO+, avec la mention DELIVERY et « Espace livreur ».
 * Adresse email puis code reçu par email, ou Google ; seuls les livreurs invités entrent.
 */
@Composable
fun ConnexionEcran(vm: ConnexionViewModel) {
    val activity = LocalContext.current as Activity
    val etat by vm.etat.collectAsState()
    val erreur by vm.erreur.collectAsState()
    val info by vm.info.collectAsState()
    val emailCode by vm.emailCode.collectAsState()
    val envoi by vm.numeroEnvoi.collectAsState()
    var dialogueTest by remember { mutableStateOf(false) }

    BackHandler(enabled = emailCode != null) { vm.changerEmail() }

    EcranConnexion(
        etat = EtatEcranConnexion(
            livreur = true,
            emailCode = emailCode,
            numeroEnvoi = envoi,
            enCours = etat == EtatConnexion.Chargement,
            message = erreur ?: info,
            messageErreur = erreur != null,
            googleDisponible = vm.googleConfigure || vm.googleDeTest
        ),
        onGoogle = { if (vm.googleConfigure) vm.connecterGoogle(activity) else dialogueTest = true },
        onEnvoyerCode = { vm.demanderCode(it) },
        onVerifierCode = { vm.verifierCode(it) },
        onRenvoyerCode = { emailCode?.let { vm.demanderCode(it) } },
        onChangerEmail = { vm.changerEmail() }
    )

    if (dialogueTest) {
        var adresse by remember { mutableStateOf("livreur.test@gmail.com") }
        AlertDialog(
            onDismissRequest = { dialogueTest = false },
            containerColor = Carte,
            title = { Text("Compte Google de test", color = Texte) },
            text = {
                OutlinedTextField(
                    value = adresse,
                    onValueChange = { adresse = it },
                    label = { Text("Adresse Gmail fictive (émulateurs)") },
                    singleLine = true,
                    colors = couleursChamp()
                )
            },
            confirmButton = {
                TextButton(onClick = { dialogueTest = false; vm.connecterGoogleDeTest(adresse.trim()) }) {
                    Text("Continuer", color = Or)
                }
            },
            dismissButton = { TextButton(onClick = { dialogueTest = false }) { Text("Annuler", color = TexteDoux) } }
        )
    }
}
