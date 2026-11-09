package com.benjo.fboplus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.Client
import com.benjo.fboplus.data.ClientsRepository
import com.benjo.fboplus.data.Profil
import com.benjo.fboplus.data.TarifsLivraison
import com.benjo.fboplus.data.VilleExpedition
import com.benjo.fboplus.data.VentesRepository
import com.benjo.fboplus.data.paysAutorises
import com.benjo.fboplus.ui.theme.*
import com.benjo.fboplus.viewmodel.DetailsCommande
import com.benjo.fboplus.viewmodel.EnvoiCommande
import com.benjo.fboplus.viewmodel.VentesViewModel
import java.util.Locale

private val Forme = RoundedCornerShape(12.dp)
private val FormeSection = RoundedCornerShape(16.dp)
private fun fcfa(n: Int) = "%,d FCFA".format(Locale.FRANCE, n)

/**
 * Deuxième étape de la commande : pays, bénéficiaire, livraison ou validation.
 * Les coordonnées du client ne sont demandées que pour une livraison.
 */
@Composable
fun DetailsCommandeEcran(
    ventesViewModel: VentesViewModel,
    profil: Profil,
    sousTotal: Int,
    reductionMax: Int,
    onRetour: () -> Unit,
    onValidee: () -> Unit
) {
    val details by ventesViewModel.details.collectAsState()
    val reduction by ventesViewModel.reduction.collectAsState()
    val titre by ventesViewModel.titreCommande.collectAsState()
    val envoi by ventesViewModel.envoi.collectAsState()
    var tentative by remember { mutableStateOf(false) }
    var choixClient by remember { mutableStateOf(false) }

    // Villes d'expédition du pays choisi : rechargées à chaque changement de pays.
    var villes by remember { mutableStateOf<List<VilleExpedition>?>(null) }
    var tarifs by remember { mutableStateOf<TarifsLivraison?>(null) }
    val fraisLivraison = tarifs?.base
    var zones by remember { mutableStateOf<List<VilleExpedition>>(emptyList()) }
    LaunchedEffect(details.pays) {
        villes = null
        tarifs = null
        if (details.pays.isNotBlank()) {
            villes = runCatching { VentesRepository.villesExpedition(details.pays) }.getOrDefault(emptyList())
            tarifs = runCatching { VentesRepository.fraisLivraison(details.pays) }.getOrNull()
            zones = runCatching { VentesRepository.zonesLivraison(details.pays) }.getOrDefault(emptyList())
        }
    }

    // Total que le serveur calculera, affiché avant l'envoi.
    val frais = when (details.mode) {
        "livraison" -> when {
            details.zoneId.isNotBlank() -> details.fraisZone
            details.fraisChoisis > 0 -> details.fraisChoisis
            else -> fraisLivraison ?: 0
        }
        "expedition" -> details.fraisVille
        else -> 0
    }
    val totalEstime = sousTotal - reduction + frais

    // Le pays du profil est proposé par défaut, s'il fait partie des pays ouverts.
    LaunchedEffect(Unit) {
        if (details.pays.isBlank() && paysAutorises.any { it.nom == profil.pays }) {
            ventesViewModel.modifierDetails { it.copy(pays = profil.pays) }
        }
    }

    val erreur = details.erreur(profil.idDistributeur) ?: if (reduction > reductionMax) {
        "La réduction dépasse votre marge sur cette commande."
    } else null

    BackHandler(onBack = onRetour)

    Column(
        Modifier
            .fillMaxSize()
            .background(BlackDeep)
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(BlackCard)
                .padding(top = 40.dp, start = 4.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRetour) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour aux produits", tint = GoldPrimary)
            }
            Column {
                Text("Détails de la commande", color = WhitePrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("Sous-total : ${fcfa(sousTotal)}", color = WhiteSub, fontSize = 13.sp)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Section("1. Pays") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    paysAutorises.forEach { p ->
                        Pastille(
                            texte = "${p.drapeau} ${p.nom}",
                            choisie = details.pays == p.nom,
                            modifier = Modifier.weight(1f)
                        ) {
                            ventesViewModel.modifierDetails {
                                if (it.pays == p.nom) it
                                else it.copy(pays = p.nom, villeId = "", villeNom = "", fraisVille = 0, zoneId = "", zoneNom = "", fraisZone = 0, fraisChoisis = 0)
                            }
                        }
                    }
                }
                Aide("La commande sera traitée par l'administration de ce pays.")
            }

            Section("2. Pour qui ?") {
                Choix(
                    gauche = "Ma commande",
                    droite = "Mon filleul",
                    droiteChoisie = details.pourQui == "filleul",
                    onChoix = { filleul ->
                        ventesViewModel.modifierDetails { it.copy(pourQui = if (filleul) "filleul" else "moi") }
                    }
                )
                if (details.pourQui == "moi") {
                    if (profil.idDistributeur.isBlank()) {
                        Text(
                            "Renseignez votre ID Forever dans votre profil pour commander à votre nom.",
                            color = RedAlert,
                            fontSize = 13.sp
                        )
                    } else {
                        Aide("Validée sur votre ID Forever : ${profil.idDistributeur}")
                    }
                } else {
                    Champ("Nom du filleul", details.filleulNom, majuscules = true) { v ->
                        ventesViewModel.modifierDetails { it.copy(filleulNom = v) }
                    }
                    Champ("Prénom du filleul", details.filleulPrenom, majuscules = true) { v ->
                        ventesViewModel.modifierDetails { it.copy(filleulPrenom = v) }
                    }
                    Champ("ID Forever du filleul", details.filleulId, clavier = KeyboardType.Number) { v ->
                        ventesViewModel.modifierDetails { it.copy(filleulId = v.filter { c -> c.isDigit() || c == '-' }) }
                    }
                    Aide("La commande sera validée sur cet ID.")
                }
            }

            Section("3. Livraison, validation ou expédition ?") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("livraison" to "Livraison", "validation" to "Validation", "expedition" to "Expédition")
                        .forEach { (mode, libelle) ->
                            Pastille(libelle, details.mode == mode, Modifier.weight(1f)) {
                                ventesViewModel.modifierDetails { it.copy(mode = mode) }
                            }
                        }
                }
                if (details.mode == "validation") {
                    Aide("Pas de livraison : les produits sont seulement validés sur l'ID. Aucune information client n'est demandée.")
                } else if (details.mode == "expedition") {
                    Aide("Le colis part de l'entrepôt du pays vers une autre ville, par une compagnie de transport.")
                    val liste = villes
                    when {
                        liste == null -> Aide("Chargement des villes…")
                        liste.isEmpty() -> Text(
                            "Aucune ville d'expédition n'est encore ouverte pour ce pays.",
                            color = RedAlert,
                            fontSize = 13.sp
                        )
                        else -> liste.forEach { v ->
                            Pastille(
                                "${v.nom} · ${fcfa(v.frais)}",
                                details.villeId == v.id,
                                Modifier.fillMaxWidth()
                            ) {
                                ventesViewModel.modifierDetails {
                                    it.copy(villeId = v.id, villeNom = v.nom, fraisVille = v.frais)
                                }
                            }
                        }
                    }
                    Champ("Nom et prénom du destinataire", details.clientNom, majuscules = true) { v ->
                        ventesViewModel.modifierDetails { it.copy(clientNom = v) }
                    }
                    Champ("Téléphone du destinataire", details.clientTelephone, clavier = KeyboardType.Phone) { v ->
                        ventesViewModel.modifierDetails { it.copy(clientTelephone = v) }
                    }
                    Champ("Deuxième téléphone (facultatif)", details.clientTelephone2, clavier = KeyboardType.Phone) { v ->
                        ventesViewModel.modifierDetails { it.copy(clientTelephone2 = v) }
                    }
                    Champ("Compagnie de transport (facultatif)", details.compagnie, majuscules = true) { v ->
                        ventesViewModel.modifierDetails { it.copy(compagnie = v) }
                    }
                    Aide("Si vous n'indiquez pas de compagnie, celui qui expédie la choisira.")
                    Champ("Précisions : gare, point de retrait (facultatif)", details.adresse) { v ->
                        ventesViewModel.modifierDetails { it.copy(adresse = v) }
                    }
                    if (details.villeId.isNotBlank()) {
                        Aide("Frais d'expédition vers ${details.villeNom} : ${fcfa(details.fraisVille)}, ajoutés au total. L'administration peut les ajuster selon le colis.")
                    }
                } else {
                    OutlinedButton(
                        onClick = { choixClient = true },
                        shape = Forme,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Remplir depuis mes clients", color = GoldPrimary) }
                    Champ("Nom et prénom du client", details.clientNom, majuscules = true) { v ->
                        ventesViewModel.modifierDetails { it.copy(clientNom = v) }
                    }
                    Champ("Téléphone du client", details.clientTelephone, clavier = KeyboardType.Phone) { v ->
                        ventesViewModel.modifierDetails { it.copy(clientTelephone = v) }
                    }
                    Champ("Deuxième téléphone (facultatif)", details.clientTelephone2, clavier = KeyboardType.Phone) { v ->
                        ventesViewModel.modifierDetails { it.copy(clientTelephone2 = v) }
                    }
                    Champ("Quartier", details.quartier, majuscules = true) { v ->
                        ventesViewModel.modifierDetails { it.copy(quartier = v) }
                    }
                    Champ("Adresse, repère (facultatif)", details.adresse) { v ->
                        ventesViewModel.modifierDetails { it.copy(adresse = v) }
                    }
                    if (zones.isNotEmpty()) {
                        Aide("Zone de livraison : le tarif dépend de la commune.")
                        Pastille(
                            "Autre zone · ${fcfa(fraisLivraison ?: 0)}",
                            details.zoneId.isBlank(),
                            Modifier.fillMaxWidth()
                        ) { ventesViewModel.modifierDetails { it.copy(zoneId = "", zoneNom = "", fraisZone = 0) } }
                        zones.forEach { z ->
                            Pastille("${z.nom} · ${fcfa(z.frais)}", details.zoneId == z.id, Modifier.fillMaxWidth()) {
                                ventesViewModel.modifierDetails { it.copy(zoneId = z.id, zoneNom = z.nom, fraisZone = z.frais) }
                            }
                        }
                    } else {
                        val paliers = tarifs?.paliers.orEmpty()
                        if (paliers.size > 1) {
                            Aide("Frais de livraison : moins cher pour un coin proche, plus pour un coin reculé.")
                            val choisi = details.fraisChoisis.takeIf { it in paliers } ?: tarifs!!.base
                            paliers.chunked(4).forEach { rangee ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rangee.forEach { montant ->
                                        Pastille("%,d".format(Locale.FRANCE, montant), choisi == montant, Modifier.weight(1f)) {
                                            ventesViewModel.modifierDetails { it.copy(fraisChoisis = montant) }
                                        }
                                    }
                                    repeat(4 - rangee.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        } else {
                            Aide(
                                fraisLivraison?.let { "Frais de livraison (${details.pays}) : ${fcfa(it)}, ajoutés au total." }
                                    ?: "Les frais de livraison du pays s'ajoutent au total."
                            )
                        }
                    }
                }
            }

            Section("4. Réduction, titre et note") {
                Champ(
                    "Réduction client en FCFA (max ${"%,d".format(Locale.FRANCE, reductionMax)})",
                    if (reduction == 0) "" else reduction.toString(),
                    clavier = KeyboardType.Number
                ) { v -> ventesViewModel.setReduction(v.filter(Char::isDigit).take(9).toIntOrNull() ?: 0) }
                Champ("Titre de la commande (facultatif)", titre) { ventesViewModel.setTitreCommande(it) }
                OutlinedTextField(
                    value = details.note,
                    onValueChange = { v -> ventesViewModel.modifierDetails { it.copy(note = v.take(500)) } },
                    label = { Text("Note pour le centre d'appel (facultatif)") },
                    placeholder = { Text("Ex. : livrer après 17 h, appeler avant de passer") },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        Column(Modifier.fillMaxWidth().background(BlackCard).padding(16.dp)) {
            if (tentative && erreur != null) {
                Text(erreur, color = RedAlert, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (frais > 0) "Total (dont ${fcfa(frais)} de frais)" else "Total",
                    color = WhiteSub,
                    fontSize = 14.sp
                )
                Text(fcfa(totalEstime), color = GoldPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            SuiviEnvoi(ventesViewModel, onValidee)
            Button(
                onClick = {
                    tentative = true
                    if (erreur == null) ventesViewModel.validerCommande()
                },
                enabled = envoi != EnvoiCommande.EnCours,
                shape = Forme,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = BlackDeep),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    when (details.mode) {
                        "validation" -> "Envoyer pour validation"
                        "expedition" -> "Envoyer pour expédition"
                        else -> "Envoyer pour livraison"
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }

    if (choixClient) {
        DialogueClient(
            onChoix = { client ->
                if (client != null) ventesViewModel.setClient(client)
                choixClient = false
            },
            onFermer = { choixClient = false }
        )
    }
}

@Composable
private fun Section(titre: String, contenu: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(FormeSection)
            .background(BlackCard)
            .border(1.dp, BlackBorder, FormeSection)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(titre, color = GoldPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        contenu()
    }
}

@Composable
private fun Aide(texte: String) {
    Text(texte, color = WhiteSub, fontSize = 13.sp)
}

@Composable
private fun Pastille(texte: String, choisie: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(Forme)
            .background(if (choisie) GoldFaint else BlackSurface)
            .border(1.dp, if (choisie) GoldPrimary else BlackBorder, Forme)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texte,
            color = if (choisie) GoldPrimary else WhitePrimary,
            fontSize = 13.sp,
            fontWeight = if (choisie) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun Choix(gauche: String, droite: String, droiteChoisie: Boolean, onChoix: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pastille(gauche, !droiteChoisie, Modifier.weight(1f)) { onChoix(false) }
        Pastille(droite, droiteChoisie, Modifier.weight(1f)) { onChoix(true) }
    }
}

@Composable
private fun Champ(
    libelle: String,
    valeur: String,
    clavier: KeyboardType = KeyboardType.Text,
    majuscules: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(libelle) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = clavier,
            capitalization = if (majuscules) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences
        ),
        shape = Forme,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = BlackSurface,
            unfocusedContainerColor = BlackSurface,
            focusedTextColor = WhitePrimary,
            unfocusedTextColor = WhitePrimary,
            focusedBorderColor = GoldPrimary,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = GoldPrimary,
            focusedLabelColor = GoldPrimary,
            unfocusedLabelColor = WhiteSub
        )
    )
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
        confirmButton = {},
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
