package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.Client
import com.benjo.fboplus.data.ClientsRepository
import com.benjo.fboplus.ui.theme.*
import com.benjo.fboplus.viewmodel.EnvoiCommande
import com.benjo.fboplus.viewmodel.VentesViewModel

/** Client, adresse et réduction d'une commande. `choixClient` est masqué en modification. */
@Composable
fun ChampsCommande(
    ventesViewModel: VentesViewModel,
    reductionMax: Int,
    choixClient: Boolean = true
) {
    val client by ventesViewModel.client.collectAsState()
    val adresse by ventesViewModel.adresse.collectAsState()
    val reduction by ventesViewModel.reduction.collectAsState()
    var selection by remember { mutableStateOf(false) }

    if (choixClient) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BlackSurface)
                .border(1.dp, BlackBorder, RoundedCornerShape(12.dp))
                .clickable { selection = true }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                client?.nom ?: "Choisir un client (optionnel)",
                color = if (client == null) WhiteFaint else WhitePrimary,
                fontSize = 14.sp
            )
            Text("Changer", color = GoldPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
    }

    ChampCompact(
        valeur = adresse,
        indication = "Adresse de livraison",
        onChange = ventesViewModel::setAdresse
    )
    Spacer(Modifier.height(10.dp))
    ChampCompact(
        valeur = if (reduction == 0) "" else reduction.toString(),
        indication = "Réduction client en FCFA (max %,d)".format(reductionMax),
        clavier = KeyboardType.Number,
        onChange = { saisie ->
            ventesViewModel.setReduction(saisie.filter(Char::isDigit).take(9).toIntOrNull() ?: 0)
        }
    )
    if (reduction > reductionMax) {
        Text(
            "La réduction dépasse votre marge sur cette commande.",
            color = RedAlert,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    Spacer(Modifier.height(10.dp))

    if (selection) {
        DialogueClient(
            onChoix = {
                ventesViewModel.setClient(it)
                selection = false
            },
            onFermer = { selection = false }
        )
    }
}

@Composable
private fun ChampCompact(
    valeur: String,
    indication: String,
    clavier: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BlackSurface)
            .border(1.dp, BlackBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        BasicTextField(
            value = valeur,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = clavier),
            cursorBrush = SolidColor(GoldPrimary),
            textStyle = TextStyle(color = WhitePrimary, fontSize = 14.sp),
            decorationBox = { champ ->
                if (valeur.isEmpty()) Text(indication, color = WhiteFaint, fontSize = 14.sp)
                champ()
            }
        )
    }
}

@Composable
private fun DialogueClient(onChoix: (Client?) -> Unit, onFermer: () -> Unit) {
    val clients by ClientsRepository.clients.collectAsState()

    AlertDialog(
        onDismissRequest = onFermer,
        containerColor = BlackCard,
        title = { Text("Choisir un client", color = WhitePrimary, fontWeight = FontWeight.Bold) },
        text = {
            if (clients.isEmpty()) {
                Text("Ajoutez vos clients depuis Profil, Mes clients.", color = WhiteSub)
            } else {
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(clients) { client ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onChoix(client) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(client.nom, color = WhitePrimary, fontSize = 15.sp)
                            if (client.telephone.isNotBlank()) {
                                Text(client.telephone, color = WhiteSub, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoix(null) }) { Text("Sans client", color = GoldPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onFermer) { Text("Fermer", color = WhiteSub) }
        }
    )
}

/** Avertit quand le serveur a accepté, et affiche son refus (stock, réseau, réduction). */
@Composable
fun SuiviEnvoi(ventesViewModel: VentesViewModel, onReussi: () -> Unit) {
    val envoi by ventesViewModel.envoi.collectAsState()

    LaunchedEffect(envoi) {
        if (envoi == EnvoiCommande.Reussi) {
            ventesViewModel.reinitialiserEnvoi()
            onReussi()
        }
    }

    when (val etat = envoi) {
        EnvoiCommande.EnCours -> Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), color = GoldPrimary, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Envoi en cours…", color = WhiteSub, fontSize = 13.sp)
        }
        is EnvoiCommande.Erreur -> Text(
            etat.message,
            color = RedAlert,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        else -> {}
    }
}
