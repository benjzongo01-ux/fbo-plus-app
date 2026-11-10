package com.benjo.fboplus.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.BuildConfig
import com.benjo.fboplus.RappelScheduler
import com.benjo.fboplus.data.NotificationsRepository
import com.benjo.fboplus.data.ProductRepository
import com.benjo.fboplus.data.Profil
import com.benjo.fboplus.data.VentesRepository
import com.benjo.fboplus.data.reductionPourGrade
import com.benjo.fboplus.ui.theme.*
import com.benjo.fboplus.viewmodel.ProfilViewModel
import com.benjo.fboplus.viewmodel.VentesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FormeCarte = RoundedCornerShape(18.dp)

private fun montant(valeur: Int): String = "%,d".format(Locale.FRANCE, valeur)
private fun points(valeur: Double): String = "%.3f".format(Locale.FRANCE, valeur)

/** Accepte la virgule, car le clavier et l'affichage français l'utilisent. */
private fun nombre(saisie: String): Double? = saisie.trim().replace(',', '.').toDoubleOrNull()

private fun Modifier.carte(): Modifier = this
    .fillMaxWidth()
    .clip(FormeCarte)
    .background(BlackCard)
    .border(1.dp, BlackBorder, FormeCarte)

private fun Profil.nomComplet(): String = listOf(prenom, nom).filter { it.isNotBlank() }.joinToString(" ")

private fun Profil.initiales(): String =
    listOf(prenom, nom).mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }.joinToString("")

private fun Profil.lieu(): String = listOf(ville, pays).filter { it.isNotBlank() }.joinToString(", ")

private fun Profil.completion(): Float {
    val champs = listOf(prenom, nom, ville, idDistributeur, telephone, whatsapp)
    return champs.count { it.isNotBlank() }.toFloat() / champs.size
}

@Composable
fun ProfilScreen(
    profilViewModel: ProfilViewModel,
    ventesViewModel: VentesViewModel,
    onModifierProfil: () -> Unit,
    onSecurite: () -> Unit,
    onDeconnexion: () -> Unit,
    onCommandes: () -> Unit,
    onClients: () -> Unit,
    onBoutique: () -> Unit,
    onNotifications: () -> Unit,
    onValidations: () -> Unit,
    onCommandesBoutique: () -> Unit,
    onMessagerie: () -> Unit,
    onFormations: () -> Unit
) {
    val profil by profilViewModel.profil.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val moisActuel = remember { VentesRepository.moisAnneeAujourdhui() }
    val commandesDuJour by ventesViewModel.commandesDuJour.collectAsState()
    val commandesDuMois = remember(commandesDuJour) {
        VentesRepository.getCommandesDuMois(context, moisActuel)
    }
    val beneficeDuMois = remember(commandesDuJour) {
        VentesRepository.getStatMoisActuel(context).benefice
    }
    val caDuMois = commandesDuMois.sumOf { it.caTotal }
    val ccDuMois = commandesDuMois.sumOf { it.ccTotal }

    var editionObjectifs by remember { mutableStateOf(false) }
    var choixHeure by remember { mutableStateOf(false) }
    var confirmationDeconnexion by remember { mutableStateOf(false) }

    fun enregistrerRappel(actif: Boolean, heure: String) {
        profilViewModel.sauvegarderProfil(profil.copy(rappelActif = actif, heureRappel = heure))
        RappelScheduler.appliquer(context, actif, heure)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackDeep)
            .verticalScroll(rememberScrollState())
    ) {
        EnTeteProfil(profil = profil, onModifier = onModifierProfil)

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (profil.completion() < 1f) {
                CarteCompletion(progression = profil.completion(), onCompleter = onModifierProfil)
            }

            Section("Performance · ${moisEnCours()}") {
                CartePerformance(
                    ca = caDuMois,
                    cc = ccDuMois,
                    benefice = beneficeDuMois,
                    commandes = commandesDuMois.size
                )
            }

            Section(
                titre = "Objectifs du mois",
                action = {
                    TextButton(onClick = { editionObjectifs = true }) {
                        Text(
                            if (profil.objectifCC > 0 || profil.objectifCA > 0) "Modifier" else "Définir",
                            color = GoldPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            ) {
                CarteObjectifs(
                    profil = profil,
                    ca = caDuMois,
                    cc = ccDuMois,
                    onDefinir = { editionObjectifs = true }
                )
            }

            Section("Mon activité") {
                Column(Modifier.carte()) {
                    val nonLues = NotificationsRepository.notifications.collectAsState().value.count { !it.lu }
                    LigneOption(
                        Icons.Filled.Notifications,
                        "Notifications",
                        sousTitre = if (nonLues > 0) "$nonLues non lue(s)" else "À jour",
                        onClick = onNotifications
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = WhiteFaint)
                    }
                    Separateur()
                    LigneOption(Icons.AutoMirrored.Filled.ReceiptLong, "Mes commandes", sousTitre = "Suivi en temps réel", onClick = onCommandes) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = WhiteFaint)
                    }
                    Separateur()
                    LigneOption(Icons.Filled.Verified, "Formations", sousTitre = "Plan de progression, cours et certificats", onClick = onFormations) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = WhiteFaint)
                    }
                    Separateur()
                    LigneOption(Icons.Filled.Notifications, "Centre d'appel", sousTitre = "Messagerie", onClick = onMessagerie) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = WhiteFaint)
                    }
                    Separateur()
                    LigneOption(Icons.Filled.Verified, "Validations au siège", sousTitre = "Achat minimum et CC", onClick = onValidations) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = WhiteFaint)
                    }
                    Separateur()
                    LigneOption(Icons.Filled.Groups, "Mes clients", sousTitre = "Fiches et historique", onClick = onClients) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = WhiteFaint)
                    }
                    Separateur()
                    LigneOption(Icons.Filled.Storefront, "Commandes de ma boutique", sousTitre = "Reçues en ligne", onClick = onCommandesBoutique) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = WhiteFaint)
                    }
                    Separateur()
                    LigneOption(Icons.Filled.Storefront, "Ma boutique", sousTitre = "Lien et QR code", onClick = onBoutique) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = WhiteFaint)
                    }
                }
            }

            Section("Informations professionnelles") {
                Column(Modifier.carte()) {
                    LigneInfo(
                        icone = Icons.Filled.Badge,
                        libelle = "ID Forever Living",
                        valeur = profil.idDistributeur,
                        action = if (profil.idDistributeur.isBlank()) null else {
                            {
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(profil.idDistributeur))
                                    Toast.makeText(context, "ID copié", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = "Copier l'ID",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    )
                    Separateur()
                    LigneInfo(
                        icone = Icons.Filled.Phone,
                        libelle = "Téléphone",
                        valeur = "${profil.indicatifTelephone} ${profil.telephone}".takeIf { profil.telephone.isNotBlank() }.orEmpty()
                    )
                    Separateur()
                    LigneInfo(
                        icone = Icons.Filled.Sms,
                        libelle = "WhatsApp",
                        valeur = "${profil.indicatifWhatsapp} ${profil.whatsapp}".takeIf { profil.whatsapp.isNotBlank() }.orEmpty()
                    )
                }
            }

            Section("Paramètres") {
                Column(Modifier.carte()) {
                    LigneOption(
                        icone = Icons.Filled.Lock,
                        titre = "Sécurité",
                        sousTitre = "Code PIN et biométrie",
                        onClick = onSecurite
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = WhiteFaint
                        )
                    }
                    Separateur()
                    LigneOption(
                        icone = Icons.Filled.Notifications,
                        titre = "Rappel quotidien",
                        sousTitre = if (profil.rappelActif) "Chaque jour à ${profil.heureRappel}" else "Désactivé"
                    ) {
                        Switch(
                            checked = profil.rappelActif,
                            onCheckedChange = { enregistrerRappel(it, profil.heureRappel) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BlackDeep,
                                checkedTrackColor = GoldPrimary,
                                uncheckedThumbColor = WhiteFaint,
                                uncheckedTrackColor = BlackSurface,
                                uncheckedBorderColor = BlackBorder
                            )
                        )
                    }
                    AnimatedVisibility(visible = profil.rappelActif) {
                        Column {
                            Separateur()
                            LigneOption(
                                icone = Icons.Filled.Schedule,
                                titre = "Heure du rappel",
                                onClick = { choixHeure = true }
                            ) {
                                Text(
                                    profil.heureRappel,
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            LigneOption(
                icone = Icons.AutoMirrored.Filled.Logout,
                titre = "Se déconnecter",
                couleur = RedAlert,
                modifier = Modifier.carte(),
                onClick = { confirmationDeconnexion = true }
            )

            Text(
                "FBO+ · Version ${BuildConfig.VERSION_NAME}",
                color = WhiteFaint,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp)
            )
        }
    }

    if (editionObjectifs) {
        DialogueObjectifs(
            profil = profil,
            onAnnuler = { editionObjectifs = false },
            onValider = { cc, ca ->
                profilViewModel.sauvegarderProfil(profil.copy(objectifCC = cc, objectifCA = ca))
                editionObjectifs = false
            }
        )
    }

    if (choixHeure) {
        DialogueHeure(
            heure = profil.heureRappel,
            onAnnuler = { choixHeure = false },
            onValider = {
                enregistrerRappel(true, it)
                choixHeure = false
            }
        )
    }

    if (confirmationDeconnexion) {
        AlertDialog(
            onDismissRequest = { confirmationDeconnexion = false },
            containerColor = BlackCard,
            title = { Text("Se déconnecter ?", color = WhitePrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Un code SMS vous sera demandé pour vous reconnecter.",
                    color = WhiteSub
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmationDeconnexion = false
                    onDeconnexion()
                }) {
                    Text("Se déconnecter", color = RedAlert, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmationDeconnexion = false }) {
                    Text("Annuler", color = WhiteSub)
                }
            }
        )
    }
}

private fun moisEnCours(): String =
    SimpleDateFormat("MMMM yyyy", Locale.FRENCH).format(Date())
        .replaceFirstChar { it.uppercaseChar() }

@Composable
private fun EnTeteProfil(profil: Profil, onModifier: () -> Unit) {
    val nom = profil.nomComplet()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(GoldGlow, Color.Transparent)))
    ) {
        IconButton(
            onClick = onModifier,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(Icons.Filled.Edit, contentDescription = "Modifier le profil", tint = GoldPrimary)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(initiales = profil.initiales())
            Spacer(Modifier.height(14.dp))
            Text(
                nom.ifEmpty { "Votre profil FBO+" },
                color = WhitePrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            BadgeGrade(grade = profil.gradeApplique)
            if (profil.gradeEnAttente) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Demande « ${profil.grade} » en attente de validation par l'administration",
                    color = WhiteSub,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (profil.lieu().isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = WhiteFaint,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(profil.lieu(), color = WhiteSub, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun Avatar(initiales: String) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(GoldGradient, CircleShape)
            .padding(3.dp)
            .background(BlackCard, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (initiales.isEmpty()) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = GoldPrimary,
                modifier = Modifier.size(46.dp)
            )
        } else {
            Text(initiales, color = GoldPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BadgeGrade(grade: String) {
    val remise = reductionPourGrade(grade).toInt()

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(GoldFaint)
            .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = GoldPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(grade, color = GoldPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (remise > 0) {
            Text(" · Remise $remise %", color = WhiteSub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CarteCompletion(progression: Float, onCompleter: () -> Unit) {
    Column(
        Modifier
            .carte()
            .clickable(onClick = onCompleter)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Complétez votre profil",
                color = WhitePrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${(progression * 100).toInt()} %",
                color = GoldPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progression },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = GoldPrimary,
            trackColor = BlackSurface,
            drawStopIndicator = {}
        )
    }
}

@Composable
private fun Section(
    titre: String,
    action: (@Composable () -> Unit)? = null,
    contenu: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(titre, color = WhiteSub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            action?.invoke()
        }
        contenu()
    }
}

@Composable
private fun CartePerformance(ca: Int, cc: Double, benefice: Int, commandes: Int) {
    Column(Modifier.carte().padding(16.dp)) {
        Text("Chiffre d'affaires", color = WhiteSub, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(montant(ca), color = WhitePrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(
                "FCFA",
                color = WhiteSub,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = BlackBorder)
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Indicateur("Bénéfice", montant(benefice), GreenPrimary, Modifier.weight(1.3f))
            Indicateur("Points CC", points(cc), GoldPrimary, Modifier.weight(1f))
            Indicateur("Commandes", commandes.toString(), WhitePrimary, Modifier.weight(0.9f))
        }
    }
}

@Composable
private fun Indicateur(libelle: String, valeur: String, couleur: Color, modifier: Modifier) {
    Column(modifier) {
        Text(libelle, color = WhiteSub, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            valeur,
            color = couleur,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CarteObjectifs(profil: Profil, ca: Int, cc: Double, onDefinir: () -> Unit) {
    if (profil.objectifCC <= 0 && profil.objectifCA <= 0) {
        Column(
            Modifier.carte().clickable(onClick = onDefinir).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Flag,
                contentDescription = null,
                tint = GoldPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Aucun objectif pour ce mois",
                color = WhitePrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Fixez un cap pour suivre votre progression",
                color = WhiteSub,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Row(
        Modifier.carte().padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        if (profil.objectifCC > 0) {
            AnneauObjectif(
                libelle = "Points CC",
                detail = "${points(cc)} / ${points(profil.objectifCC)}",
                progression = (cc / profil.objectifCC).toFloat(),
                couleur = GoldPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        if (profil.objectifCA > 0) {
            AnneauObjectif(
                libelle = "Chiffre d'affaires",
                detail = "${montant(ca)} / ${montant(profil.objectifCA)}",
                progression = ca.toFloat() / profil.objectifCA,
                couleur = GreenPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AnneauObjectif(
    libelle: String,
    detail: String,
    progression: Float,
    couleur: Color,
    modifier: Modifier,
    taille: Dp = 88.dp
) {
    val anime by animateFloatAsState(
        targetValue = progression.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "progression"
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { anime },
                modifier = Modifier.size(taille),
                color = couleur,
                trackColor = BlackSurface,
                strokeWidth = 8.dp,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp
            )
            Text(
                "${(progression * 100).toInt()} %",
                color = WhitePrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(libelle, color = WhitePrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            detail,
            color = WhiteSub,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun PastilleIcone(icone: ImageVector, couleur: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(couleur.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icone, contentDescription = null, tint = couleur, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun LigneInfo(
    icone: ImageVector,
    libelle: String,
    valeur: String,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PastilleIcone(icone, GoldPrimary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(libelle, color = WhiteSub, fontSize = 12.sp)
            Text(
                valeur.ifBlank { "Non renseigné" },
                color = if (valeur.isBlank()) WhiteFaint else WhitePrimary,
                fontSize = 15.sp,
                fontWeight = if (valeur.isBlank()) FontWeight.Normal else FontWeight.Medium
            )
        }
        action?.invoke()
    }
}

@Composable
private fun LigneOption(
    icone: ImageVector,
    titre: String,
    modifier: Modifier = Modifier,
    sousTitre: String? = null,
    couleur: Color = GoldPrimary,
    onClick: (() -> Unit)? = null,
    fin: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PastilleIcone(icone, couleur)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titre,
                color = if (couleur == GoldPrimary) WhitePrimary else couleur,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (sousTitre != null) {
                Text(sousTitre, color = WhiteSub, fontSize = 12.sp)
            }
        }
        fin?.invoke()
    }
}

@Composable
private fun Separateur() {
    HorizontalDivider(modifier = Modifier.padding(start = 62.dp), color = BlackBorder)
}

@Composable
private fun DialogueObjectifs(
    profil: Profil,
    onAnnuler: () -> Unit,
    onValider: (cc: Double, ca: Int) -> Unit
) {
    val prixParCC = remember { ProductRepository.getPrixParCC() }
    var objectifCC by remember {
        mutableStateOf(if (profil.objectifCC > 0) "%.3f".format(Locale.FRANCE, profil.objectifCC) else "")
    }
    var objectifCA by remember {
        mutableStateOf(if (profil.objectifCA > 0) profil.objectifCA.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onAnnuler,
        containerColor = BlackCard,
        title = { Text("Objectifs du mois", color = WhitePrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChampObjectif(
                    valeur = objectifCC,
                    libelle = "Points CC",
                    clavier = KeyboardType.Decimal,
                    onChange = {
                        objectifCC = it
                        val cc = nombre(it)
                        if (cc != null && cc > 0) objectifCA = (cc * prixParCC).toInt().toString()
                    }
                )
                ChampObjectif(
                    valeur = objectifCA,
                    libelle = "Chiffre d'affaires (FCFA)",
                    clavier = KeyboardType.Number,
                    onChange = {
                        objectifCA = it
                        val ca = it.toIntOrNull()
                        if (ca != null && ca > 0 && prixParCC > 0) {
                            objectifCC = "%.3f".format(Locale.FRANCE, ca / prixParCC)
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onValider(nombre(objectifCC) ?: 0.0, objectifCA.toIntOrNull() ?: 0)
            }) {
                Text("Enregistrer", color = GoldPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onAnnuler) { Text("Annuler", color = WhiteSub) }
        }
    )
}

@Composable
private fun ChampObjectif(
    valeur: String,
    libelle: String,
    clavier: KeyboardType,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(libelle) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = clavier),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = WhitePrimary,
            unfocusedTextColor = WhitePrimary,
            focusedBorderColor = GoldPrimary,
            unfocusedBorderColor = BlackBorder,
            cursorColor = GoldPrimary,
            focusedLabelColor = GoldPrimary,
            unfocusedLabelColor = WhiteSub
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogueHeure(heure: String, onAnnuler: () -> Unit, onValider: (String) -> Unit) {
    val parts = heure.split(":")
    val etat = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 19,
        initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onAnnuler,
        containerColor = BlackCard,
        title = { Text("Heure du rappel", color = WhitePrimary, fontWeight = FontWeight.Bold) },
        text = {
            TimePicker(
                state = etat,
                colors = TimePickerDefaults.colors(
                    clockDialColor = BlackSurface,
                    clockDialSelectedContentColor = BlackDeep,
                    clockDialUnselectedContentColor = WhitePrimary,
                    selectorColor = GoldPrimary,
                    timeSelectorSelectedContainerColor = GoldGlow,
                    timeSelectorUnselectedContainerColor = BlackSurface,
                    timeSelectorSelectedContentColor = GoldPrimary,
                    timeSelectorUnselectedContentColor = WhitePrimary
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onValider("%02d:%02d".format(etat.hour, etat.minute)) }) {
                Text("Enregistrer", color = GoldPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onAnnuler) { Text("Annuler", color = WhiteSub) }
        }
    )
}
