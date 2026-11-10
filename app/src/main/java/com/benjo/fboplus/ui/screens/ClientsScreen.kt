package com.benjo.fboplus.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.Client
import com.benjo.fboplus.data.ClientsRepository
import com.benjo.fboplus.data.ProfilRepository
import com.benjo.fboplus.data.VentesRepository
import com.benjo.fboplus.ui.theme.*
import java.util.Locale

private val Forme = RoundedCornerShape(16.dp)

@Composable
fun ClientsScreen(onOuvrir: (String) -> Unit, onNouveau: () -> Unit, onRetour: () -> Unit) {
    val clients by ClientsRepository.clients.collectAsState()
    val commandes by VentesRepository.commandes.collectAsState()
    var recherche by remember { mutableStateOf("") }
    var filtre by remember { mutableStateOf("Tous") }
    val suivis = remember(clients, commandes) { clients.associate { it.id to suiviClient(it, commandes) } }
    val visibles = clients.filter {
        (it.nom.contains(recherche, ignoreCase = true) || it.telephone.contains(recherche)) &&
            garderSelonFiltre(filtre, suivis.getValue(it.id))
    }.sortedByDescending { suivis.getValue(it.id).aRelancer }

    Column(Modifier.fillMaxSize().background(BlackDeep)) {
        EnTeteSimple("Mes clients", onRetour) {
            IconButton(onClick = onNouveau) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter un client", tint = GoldPrimary)
            }
        }
        OutlinedTextField(
            value = recherche,
            onValueChange = { recherche = it },
            singleLine = true,
            placeholder = { Text("Nom ou téléphone", color = WhiteFaint) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = WhiteSub) },
            shape = RoundedCornerShape(12.dp),
            colors = couleursChampClient(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        FiltresClients(filtre, suivis.values.count { it.aRelancer }) { filtre = it }
        if (visibles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (clients.isEmpty()) "Ajoutez votre premier client avec le bouton +" else "Aucun résultat",
                    color = WhiteSub,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibles, key = { it.id }) { client ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Forme)
                            .background(BlackCard)
                            .border(1.dp, BlackBorder, Forme)
                            .clickable { onOuvrir(client.id) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(42.dp).background(GoldFaint, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                client.nom.take(1).uppercase(),
                                color = GoldPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        val suivi = suivis.getValue(client.id)
                        Column(Modifier.weight(1f)) {
                            Text(client.nom, color = WhitePrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (suivi.aRelancer) suivi.raisonRelance else depuisLe(suivi.dernierAchat),
                                color = if (suivi.aRelancer) GoldPrimary else WhiteSub,
                                fontSize = 13.sp
                            )
                        }
                        BadgeCategorie(suivi.categorie)
                    }
                }
            }
        }
    }
}

@Composable
fun ClientDetailScreen(
    clientId: String,
    onModifier: () -> Unit,
    onCommande: (String) -> Unit,
    onRetour: () -> Unit
) {
    val context = LocalContext.current
    val clients by ClientsRepository.clients.collectAsState()
    val commandes by VentesRepository.commandes.collectAsState()
    val client = clients.find { it.id == clientId }
    val suivi = client?.let { suiviClient(it, commandes) }
    val historique = suivi?.commandes.orEmpty().sortedByDescending { it.dateMillis }
    val valides = historique
    val profil = remember { ProfilRepository.getProfil(context) }

    Column(Modifier.fillMaxSize().background(BlackDeep)) {
        EnTeteSimple(client?.nom ?: "Client", onRetour) {
            if (client != null) {
                TextButton(onClick = onModifier) { Text("Modifier", color = GoldPrimary) }
            }
        }
        if (client == null) return@Column

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().clip(Forme).background(BlackCard).padding(16.dp)) {
                    if (client.telephone.isNotBlank()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client.telephone}"))
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Phone, null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(client.telephone, color = WhitePrimary, fontSize = 15.sp)
                        }
                    }
                    if (client.adresse.isNotBlank()) {
                        Text(client.adresse, color = WhiteSub, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (client.notes.isNotBlank()) {
                        Text(client.notes, color = WhiteSub, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${valides.size} commande(s) · %,d FCFA".format(Locale.FRANCE, valides.sumOf { it.total }),
                        color = GoldPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (suivi != null) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgeCategorie(suivi.categorie)
                    Text(depuisLe(suivi.dernierAchat), color = WhiteSub, fontSize = 13.sp)
                }
            }
            if (suivi != null) item {
                SectionRelance(client, suivi, listOf(profil.prenom, profil.nom).filter { it.isNotBlank() }.joinToString(" "))
            }
            items(historique, key = { it.docId }) { commande ->
                CarteCommande(commande) { onCommande(commande.docId) }
            }
        }
    }
}

@Composable
fun ClientFormulaireScreen(clientId: String?, onTermine: () -> Unit, onRetour: () -> Unit) {
    val clients by ClientsRepository.clients.collectAsState()
    val existant = clients.find { it.id == clientId }

    var nom by remember(existant) { mutableStateOf(existant?.nom.orEmpty()) }
    var telephone by remember(existant) { mutableStateOf(existant?.telephone.orEmpty()) }
    var adresse by remember(existant) { mutableStateOf(existant?.adresse.orEmpty()) }
    var notes by remember(existant) { mutableStateOf(existant?.notes.orEmpty()) }
    var erreur by remember { mutableStateOf(false) }
    var suppression by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(BlackDeep)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        EnTeteSimple(if (clientId == null) "Nouveau client" else "Modifier le client", onRetour)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChampClient("Nom", nom, majuscules = true, erreur = erreur && nom.isBlank()) { nom = it }
            ChampClient("Téléphone", telephone, clavier = KeyboardType.Phone) { telephone = it }
            ChampClient("Adresse", adresse) { adresse = it }
            ChampClient("Notes", notes, uneLigne = false) { notes = it }

            Button(
                onClick = {
                    if (nom.isBlank()) {
                        erreur = true
                    } else {
                        ClientsRepository.enregistrer(
                            Client(clientId.orEmpty(), nom, telephone, adresse, notes)
                        )
                        onTermine()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = BlackDeep),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Enregistrer", fontWeight = FontWeight.Bold) }

            if (clientId != null) {
                TextButton(onClick = { suppression = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Supprimer ce client", color = RedAlert)
                }
            }
        }
    }

    if (suppression && clientId != null) {
        AlertDialog(
            onDismissRequest = { suppression = false },
            containerColor = BlackCard,
            title = { Text("Supprimer ce client ?", color = WhitePrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Ses commandes passées restent dans votre historique.", color = WhiteSub) },
            confirmButton = {
                TextButton(onClick = {
                    ClientsRepository.supprimer(clientId)
                    suppression = false
                    onTermine()
                }) { Text("Supprimer", color = RedAlert, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { suppression = false }) { Text("Annuler", color = WhiteSub) }
            }
        )
    }
}

@Composable
private fun couleursChampClient() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = BlackSurface,
    unfocusedContainerColor = BlackSurface,
    errorContainerColor = BlackSurface,
    focusedTextColor = WhitePrimary,
    unfocusedTextColor = WhitePrimary,
    focusedBorderColor = GoldPrimary,
    unfocusedBorderColor = BlackBorder,
    cursorColor = GoldPrimary,
    focusedLabelColor = GoldPrimary,
    unfocusedLabelColor = WhiteSub
)

@Composable
private fun ChampClient(
    libelle: String,
    valeur: String,
    clavier: KeyboardType = KeyboardType.Text,
    majuscules: Boolean = false,
    uneLigne: Boolean = true,
    erreur: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(libelle) },
        singleLine = uneLigne,
        minLines = if (uneLigne) 1 else 3,
        isError = erreur,
        supportingText = if (erreur) ({ Text("Champ obligatoire") }) else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = clavier,
            capitalization = if (majuscules) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences
        ),
        shape = RoundedCornerShape(12.dp),
        colors = couleursChampClient(),
        modifier = Modifier.fillMaxWidth()
    )
}
