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

/** Frais d'une validation sur l'ID (le serveur applique ceux du pays, 2 000 FCFA par défaut). */
private const val FRAIS_VALIDATION = 2000

@Composable
private fun Ligne(libelle: String, valeur: String, fort: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(libelle, color = if (fort) WhitePrimary else WhiteSub, fontSize = 14.sp, fontWeight = if (fort) FontWeight.Bold else FontWeight.Normal)
        Text(valeur, color = if (fort) GoldPrimary else WhitePrimary, fontSize = 14.sp, fontWeight = if (fort) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Liste déroulante des pays, avec drapeau et recherche. */
@Composable
private fun DialoguePays(onChoix: (String) -> Unit, onFermer: () -> Unit) {
    var recherche by remember { mutableStateOf("") }
    val visibles = paysAutorises.filter { it.nom.contains(recherche.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onFermer,
        containerColor = BlackCard,
        title = { Text("Choisir le pays", color = WhitePrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    placeholder = { Text("Rechercher un pays") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                visibles.forEach { p ->
                    Text(
                        "${p.drapeau}  ${p.nom}  ${p.indicatif}",
                        color = WhitePrimary,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onChoix(p.nom) }.padding(vertical = 12.dp)
                    )
                }
                if (visibles.isEmpty()) Text("Aucun pays ne correspond.", color = WhiteSub)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onFermer) { Text("Fermer", color = WhiteSub) } }
    )
}
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

    val validation = details.mode == "validation"
    // Validation : prix public moins la remise du grade, plus les frais de validation (pas de réduction).
    val prixValidation = sousTotal - reductionMax
    // Le distributeur indique ce que paie le client ; la réduction en découle (prix public − prix client).
    var prixClientTexte by remember(sousTotal) { mutableStateOf((sousTotal - reduction).toString()) }
    val prixClient = prixClientTexte.toIntOrNull() ?: 0
    LaunchedEffect(prixClient, sousTotal) { if (!validation) ventesViewModel.setReduction(sousTotal - prixClient) }
    var confirmerPrix by remember { mutableStateOf(false) }
    // Seuil sous lequel le wallet complète : validation + livraison (si elle n'est pas facturée au client).
    var fraisMaxPays by remember { mutableIntStateOf(0) }
    LaunchedEffect(details.pays) {
        fraisMaxPays = runCatching { com.benjo.fboplus.data.VentesRepository.fraisLivraison(details.pays).max }.getOrDefault(0)
    }
    val seuilWallet = prixValidation + if (details.clientPaieLivraison) 0 else fraisMaxPays
    val totalEstime = if (validation) prixValidation + FRAIS_VALIDATION else prixClient
    var choixPays by remember { mutableStateOf(false) }

    // Le pays du profil est proposé par défaut, s'il fait partie des pays ouverts.
    LaunchedEffect(Unit) {
        if (details.pays.isBlank() && paysAutorises.any { it.nom == profil.pays }) {
            ventesViewModel.modifierDetails { it.copy(pays = profil.pays) }
        }
    }

    val erreur = details.erreur(profil.idDistributeur) ?: when {
        validation -> null
        else -> null
    }

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
                val choisi = paysAutorises.find { it.nom == details.pays }
                OutlinedButton(
                    onClick = { choixPays = true },
                    shape = Forme,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Text(
                        choisi?.let { "${it.drapeau}  ${it.nom}" } ?: "Choisir le pays",
                        color = WhitePrimary,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text("▾", color = GoldPrimary, fontSize = 18.sp)
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
                    Aide("Pas de livraison : les produits sont validés sur l'ID. Le montant est prélevé sur votre wallet à l'envoi.")
                    Ligne("Prix public", fcfa(sousTotal))
                    Ligne("Remise de votre grade", "− " + fcfa(reductionMax))
                    Ligne("Prix de validation", fcfa(prixValidation))
                    Ligne("Frais de validation", fcfa(FRAIS_VALIDATION))
                    Ligne("Prélevé sur votre wallet", fcfa(prixValidation + FRAIS_VALIDATION), fort = true)
                    SoldeWallet(prixValidation + FRAIS_VALIDATION)
                } else if (details.mode == "expedition") {
                    Aide("Le colis part de l'entrepôt du pays vers la ville indiquée. Les frais d'expédition sont fixés par l'administration.")
                    Champ("Ville, province ou village de destination", details.villeSaisie, majuscules = true) { v ->
                        ventesViewModel.modifierDetails { it.copy(villeSaisie = v) }
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
                    Champ("Gare d'expédition (facultatif)", details.gare, majuscules = true) { v ->
                        ventesViewModel.modifierDetails { it.copy(gare = v) }
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
                    Aide("Les frais de livraison sont fixés par l'administration selon le lieu, puis ajoutés au total.")
                }
            }

            Section(if (validation) "4. Note" else "4. Prix du client et note") {
                if (!validation) {
                    Ligne("Prix public", fcfa(sousTotal))
                    Ligne("Votre prix de validation (grade)", fcfa(prixValidation))
                    Champ("Le client paie (FCFA)", prixClientTexte, clavier = KeyboardType.Number) { v ->
                        prixClientTexte = v.filter(Char::isDigit).take(9)
                    }
                    Text("La livraison est payée par", color = WhitePrimary, fontSize = 14.sp)
                    Choix(
                        gauche = "Le client",
                        droite = "Moi",
                        droiteChoisie = !details.clientPaieLivraison,
                        onChoix = { moi -> ventesViewModel.modifierDetails { it.copy(clientPaieLivraison = !moi) } }
                    )
                    Aide(
                        if (details.clientPaieLivraison) "Les frais fixés par l'administration seront ajoutés à ce que paie le client."
                        else "Les frais de livraison seront pris sur votre bénéfice."
                    )
                    if (prixClient < seuilWallet) {
                        // Prix trop bas : la différence est prise sur le wallet (sinon la commande est refusée).
                        Text(
                            "Ce prix ne couvre pas la validation" + (if (details.clientPaieLivraison) "" else " et la livraison") +
                                " : environ ${fcfa(seuilWallet - prixClient)} seront pris sur votre wallet.",
                            color = RedAlert,
                            fontSize = 13.sp
                        )
                        SoldeWallet(seuilWallet - prixClient)
                    } else {
                        Ligne(
                            if (details.clientPaieLivraison) "Votre bénéfice estimé" else "Votre bénéfice estimé, avant livraison",
                            fcfa(prixClient - prixValidation),
                            fort = true
                        )
                    }
                }
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
                    when (details.mode) {
                        "validation" -> "Prélevé sur votre wallet (frais compris)"
                        else -> if (details.clientPaieLivraison) "Le client paie, plus la livraison" else "Le client paie (livraison comprise)"
                    },
                    color = WhiteSub,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(fcfa(totalEstime), color = GoldPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            SuiviEnvoi(ventesViewModel, onValidee)
            Button(
                onClick = {
                    tentative = true
                    // Livraison ou expédition : le distributeur confirme le montant annoncé au client.
                    if (erreur == null) {
                        if (validation) ventesViewModel.validerCommande() else confirmerPrix = true
                    }
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

    if (confirmerPrix) {
        AlertDialog(
            onDismissRequest = { confirmerPrix = false },
            containerColor = BlackCard,
            title = { Text("Vérifiez le montant", color = WhitePrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Le client paiera ${fcfa(prixClient)} pour les produits" +
                        (if (details.clientPaieLivraison) ", plus les frais de livraison fixés par l'administration."
                        else ". La livraison sera prise sur votre bénéfice.") +
                        (if (prixClient < seuilWallet) "\n\nEnviron ${fcfa(seuilWallet - prixClient)} seront pris sur votre wallet pour compléter." else "") +
                        "\n\nC'est ce montant que le livreur encaissera. Est-il exact ?",
                    color = WhiteSub,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmerPrix = false; ventesViewModel.validerCommande() }) {
                    Text("C'est exact, envoyer", color = GoldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmerPrix = false }) { Text("Corriger", color = WhiteSub) } }
        )
    }

    if (choixPays) {
        DialoguePays(
            onChoix = { p ->
                choixPays = false
                ventesViewModel.modifierDetails { if (it.pays == p) it else it.copy(pays = p) }
            },
            onFermer = { choixPays = false }
        )
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


/** Solde du wallet face au montant d'une validation : invite à recharger s'il ne suffit pas. */
@Composable
private fun SoldeWallet(aPrelever: Int) {
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    var solde by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose {}
        val ecoute = com.google.firebase.firestore.FirebaseFirestore.getInstance().document("wallets/$uid")
            .addSnapshotListener { d, _ -> solde = d?.getLong("solde") ?: 0L }
        onDispose { ecoute.remove() }
    }
    val s = solde ?: return
    Ligne("Solde de votre wallet", fcfa(s.toInt()))
    if (s < aPrelever) {
        Text(
            "Solde insuffisant : rechargez au moins ${fcfa(aPrelever - s.toInt())} dans Mon wallet avant d'envoyer.",
            color = RedAlert,
            fontSize = 13.sp
        )
    }
}
