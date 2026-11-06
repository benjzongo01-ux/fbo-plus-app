package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.benjo.fboplus.auth.BiometricAuthHelper
import com.benjo.fboplus.auth.SecureTokenStorage
import com.benjo.fboplus.viewmodel.AuthViewModel
import com.benjo.fboplus.ui.components.*

private enum class EtapeChangementPin { AUCUNE, ANCIEN, NOUVEAU, CONFIRMATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuriteScreen(
    authViewModel: AuthViewModel,
    onRetour: () -> Unit,
) {
val context = LocalContext.current
    val activity = context as FragmentActivity
    val storage = remember { SecureTokenStorage(context) }
    val biometricHelper = remember { BiometricAuthHelper(activity) }
    val biometrieDisponibleSurAppareil = remember { biometricHelper.isBiometricAvailable() }

    var biometrieActivee by remember { mutableStateOf(storage.isBiometricEnabled()) }
    var etapePin by remember { mutableStateOf(EtapeChangementPin.AUCUNE) }
    var saisieActuelle by remember { mutableStateOf("") }
    var ancienPinTemp by remember { mutableStateOf("") }
    var nouveauPinTemp by remember { mutableStateOf("") }
    var messageErreur by remember { mutableStateOf<String?>(null) }
    var messageSucces by remember { mutableStateOf<String?>(null) }

    fun reinitialiserFlowPin() {
        etapePin = EtapeChangementPin.AUCUNE
        saisieActuelle = ""
        ancienPinTemp = ""
        nouveauPinTemp = ""
    }

    fun onDigit(digit: String) {
        if (saisieActuelle.length >= 4) return
        saisieActuelle += digit
        if (saisieActuelle.length == 4) {
            when (etapePin) {
                EtapeChangementPin.ANCIEN -> {
                    if (storage.verifyPin(saisieActuelle)) {
                        ancienPinTemp = saisieActuelle
                        saisieActuelle = ""
                        etapePin = EtapeChangementPin.NOUVEAU
                        messageErreur = null
                    } else {
                        messageErreur = "Code incorrect. Réessayez."
                        saisieActuelle = ""
                    }
                }
                EtapeChangementPin.NOUVEAU -> {
                    nouveauPinTemp = saisieActuelle
                    saisieActuelle = ""
                    etapePin = EtapeChangementPin.CONFIRMATION
                }
                EtapeChangementPin.CONFIRMATION -> {
                    if (saisieActuelle == nouveauPinTemp) {
                        authViewModel.changerPin(ancienPinTemp, nouveauPinTemp) { erreur ->
                            if (erreur == null) {
                                messageSucces = "PIN mis à jour avec succès."
                                reinitialiserFlowPin()
                            } else {
                                reinitialiserFlowPin()
                                messageErreur = erreur
                                etapePin = EtapeChangementPin.ANCIEN
                            }
                        }
                    } else {
                        messageErreur = "Les codes ne correspondent pas."
                        saisieActuelle = ""
                        nouveauPinTemp = ""
                        etapePin = EtapeChangementPin.NOUVEAU
                    }
                }
                else -> {}
            }
        }
    }

    fun onDelete() {
        if (saisieActuelle.isNotEmpty()) saisieActuelle = saisieActuelle.dropLast(1)
    }

    Scaffold(
        containerColor = SBackgroundBlack,
        topBar = {
            TopAppBar(
                title = { Text("Sécurité", color = STextWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (etapePin != EtapeChangementPin.AUCUNE) reinitialiserFlowPin() else onRetour()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = STextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SBackgroundBlack)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SBackgroundBlack)
                .padding(padding)
                .padding(20.dp)
        ) {
            if (etapePin == EtapeChangementPin.AUCUNE) {
                // ── Liste des options de sécurité ──

                messageSucces?.let {
                    Text(it, color = SSuccess, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OptionSecurite(
                    titre = "Modifier le code PIN",
                    sousTitre = "Changer votre code à 4 chiffres",
                    onClick = { etapePin = EtapeChangementPin.ANCIEN; messageErreur = null; messageSucces = null }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Connexion biométrique", color = STextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            if (biometrieDisponibleSurAppareil) "Empreinte ou reconnaissance faciale" else "Non disponible sur cet appareil",
                            color = STextGray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = biometrieActivee,
                        enabled = biometrieDisponibleSurAppareil,
                        onCheckedChange = { activer ->
                            if (activer) {
                                biometricHelper.authenticate(
                                    onSuccess = {
                                        storage.setBiometricEnabled(true)
                                        biometrieActivee = true
                                    },
                                    onError = { },
                                    onFailed = { }
                                )
                            } else {
                                storage.setBiometricEnabled(false)
                                biometrieActivee = false
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SBackgroundBlack,
                            checkedTrackColor = SGoldPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "D'autres options (portefeuilles, objectifs) seront ajoutées ici prochainement.",
                    color = STextGray,
                    fontSize = 12.sp
                )

            } else {
                // ── Flow de changement de PIN ──
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val titre = when (etapePin) {
                        EtapeChangementPin.ANCIEN -> "Entrez votre PIN actuel"
                        EtapeChangementPin.NOUVEAU -> "Entrez votre nouveau PIN"
                        EtapeChangementPin.CONFIRMATION -> "Confirmez votre nouveau PIN"
                        else -> ""
                    }
                    Text(titre, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = STextWhite)
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(4) { index ->
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (index < saisieActuelle.length) SGoldPrimary else STextGray.copy(alpha = 0.3f))
                            )
                        }
                    }

                    messageErreur?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(it, color = SError, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    val rangees = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("", "0", "⌫")
                    )
                    rangees.forEach { rangee ->
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                            rangee.forEach { touche ->
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (touche.isNotEmpty())
                                                Modifier
                                                    .border(1.dp, STextGray.copy(alpha = 0.3f), CircleShape)
                                                    .clickable {
                                                        if (touche == "⌫") onDelete() else onDigit(touche)
                                                    }
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (touche.isNotEmpty()) Text(touche, fontSize = 20.sp, color = STextWhite)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionSecurite(titre: String, sousTitre: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(titre, color = STextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(sousTitre, color = STextGray, fontSize = 12.sp)
        }
        Text("›", color = STextGray, fontSize = 20.sp)
    }
}