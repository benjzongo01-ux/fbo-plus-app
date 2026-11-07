package com.benjo.fboplus.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.CountryDialCode
import com.benjo.fboplus.data.Profil
import com.benjo.fboplus.data.listeGrades
import com.benjo.fboplus.data.listeIndicatifs
import com.benjo.fboplus.data.paysAutorises
import com.benjo.fboplus.data.reductionPourGrade
import com.benjo.fboplus.ui.theme.*
import com.benjo.fboplus.viewmodel.ProfilViewModel
import java.text.Normalizer

private val FormeChamp = RoundedCornerShape(12.dp)
private val FormeSection = RoundedCornerShape(18.dp)

private fun indicatifDe(code: String): CountryDialCode =
    listeIndicatifs.find { it.dialCode == code } ?: listeIndicatifs.first { it.dialCode == "+226" }

private fun CountryDialCode.accepte(numero: String) = numero.length in minDigits..maxDigits

private val MarquesAccent = Regex("\\p{Mn}+")

/** « sen » doit trouver « Sénégal » : beaucoup d'utilisateurs tapent sans accent. */
private fun sansAccents(texte: String): String =
    Normalizer.normalize(texte, Normalizer.Form.NFD).replace(MarquesAccent, "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifierProfilScreen(
    profilViewModel: ProfilViewModel,
    onRetour: () -> Unit
) {
    val context = LocalContext.current
    val profil by profilViewModel.profil.collectAsState()

    var prenom by remember(profil) { mutableStateOf(profil.prenom) }
    var nom by remember(profil) { mutableStateOf(profil.nom) }
    var pays by remember(profil) {
        mutableStateOf(paysAutorises.find { it.nom == profil.pays } ?: paysAutorises.first())
    }
    var ville by remember(profil) { mutableStateOf(profil.ville) }
    var indicatifTel by remember(profil) { mutableStateOf(indicatifDe(profil.indicatifTelephone)) }
    var telephone by remember(profil) { mutableStateOf(profil.telephone) }
    var indicatifWhatsapp by remember(profil) { mutableStateOf(indicatifDe(profil.indicatifWhatsapp)) }
    var whatsapp by remember(profil) { mutableStateOf(profil.whatsapp) }
    var idForever by remember(profil) { mutableStateOf(profil.idDistributeur) }
    var grade by remember(profil) { mutableStateOf(profil.grade) }
    var memeNumero by remember(profil) {
        mutableStateOf(profil.whatsapp.isNotEmpty() && profil.whatsapp == profil.telephone &&
            profil.indicatifWhatsapp == profil.indicatifTelephone)
    }

    var erreursVisibles by remember { mutableStateOf(false) }
    var confirmationAbandon by remember { mutableStateOf(false) }
    var feuille by remember { mutableStateOf(Feuille.AUCUNE) }

    // Une fois enregistré, l'ID Forever ne se change plus depuis l'application.
    val idVerrouille = profil.idDistributeur.isNotBlank()

    val indicatifWhatsappFinal = if (memeNumero) indicatifTel else indicatifWhatsapp
    val whatsappFinal = if (memeNumero) telephone else whatsapp

    val prenomValide = prenom.isNotBlank()
    val nomValide = nom.isNotBlank()
    val villeValide = ville.isNotBlank()
    val telValide = indicatifTel.accepte(telephone)
    val whatsappValide = indicatifWhatsappFinal.accepte(whatsappFinal)
    val formulaireValide = prenomValide && nomValide && villeValide && telValide && whatsappValide

    val profilModifie: Profil = profil.copy(
        prenom = prenom.trim(),
        nom = nom.trim(),
        pays = pays.nom,
        ville = ville.trim(),
        indicatifTelephone = indicatifTel.dialCode,
        telephone = telephone,
        indicatifWhatsapp = indicatifWhatsappFinal.dialCode,
        whatsapp = whatsappFinal,
        idDistributeur = idForever,
        grade = grade
    )
    // Pays et indicatifs ont une valeur par défaut : un profil vide n'est pas « modifié » pour autant.
    val modifie = listOf(
        prenom.trim() to profil.prenom,
        nom.trim() to profil.nom,
        pays.nom to profil.pays.ifBlank { paysAutorises.first().nom },
        ville.trim() to profil.ville,
        indicatifTel.dialCode to profil.indicatifTelephone.ifBlank { "+226" },
        telephone to profil.telephone,
        indicatifWhatsappFinal.dialCode to profil.indicatifWhatsapp.ifBlank { "+226" },
        whatsappFinal to profil.whatsapp,
        idForever to profil.idDistributeur,
        grade to profil.grade
    ).any { (actuel, initial) -> actuel != initial }

    fun quitter() {
        if (modifie) confirmationAbandon = true else onRetour()
    }

    fun enregistrer() {
        if (!formulaireValide) {
            erreursVisibles = true
            Toast.makeText(context, "Vérifiez les champs signalés", Toast.LENGTH_SHORT).show()
            return
        }
        profilViewModel.sauvegarderProfil(profilModifie)
        Toast.makeText(context, "Profil enregistré", Toast.LENGTH_SHORT).show()
        onRetour()
    }

    BackHandler(enabled = modifie) { confirmationAbandon = true }

    Scaffold(
        containerColor = BlackDeep,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Modifier le profil",
                        color = WhitePrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::quitter) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = WhitePrimary
                        )
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackDeep)
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlackCard)
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(FormeChamp)
                        .background(GoldGradientH)
                        .clickable(onClick = ::enregistrer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Enregistrer",
                        color = BlackDeep,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ApercuIdentite(prenom = prenom, nom = nom, grade = grade)

            SectionFormulaire("Identité") {
                ChampTexte(
                    libelle = "Prénom",
                    valeur = prenom,
                    onChange = { prenom = it },
                    erreur = "Indiquez votre prénom".takeIf { erreursVisibles && !prenomValide },
                    majuscules = KeyboardCapitalization.Words
                )
                ChampTexte(
                    libelle = "Nom",
                    valeur = nom,
                    onChange = { nom = it },
                    erreur = "Indiquez votre nom".takeIf { erreursVisibles && !nomValide },
                    majuscules = KeyboardCapitalization.Words
                )
            }

            SectionFormulaire("Localisation") {
                ChampChoix(
                    libelle = "Pays",
                    valeur = "${pays.drapeau}  ${pays.nom}",
                    onClick = { feuille = Feuille.PAYS }
                )
                ChampTexte(
                    libelle = "Ville",
                    valeur = ville,
                    onChange = { ville = it },
                    erreur = "Indiquez votre ville".takeIf { erreursVisibles && !villeValide },
                    majuscules = KeyboardCapitalization.Words
                )
            }

            SectionFormulaire("Coordonnées") {
                ChampNumero(
                    libelle = "Téléphone",
                    indicatif = indicatifTel,
                    numero = telephone,
                    onNumero = { telephone = it },
                    onIndicatif = { feuille = Feuille.INDICATIF_TELEPHONE },
                    erreur = "Numéro invalide pour ce pays".takeIf { erreursVisibles && !telValide }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FormeChamp)
                        .clickable { memeNumero = !memeNumero },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = memeNumero,
                        onCheckedChange = { memeNumero = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = GoldPrimary,
                            checkmarkColor = BlackDeep,
                            uncheckedColor = WhiteFaint
                        )
                    )
                    Text("WhatsApp sur ce même numéro", color = WhiteSub, fontSize = 14.sp)
                }

                if (!memeNumero) {
                    ChampNumero(
                        libelle = "WhatsApp",
                        indicatif = indicatifWhatsapp,
                        numero = whatsapp,
                        onNumero = { whatsapp = it },
                        onIndicatif = { feuille = Feuille.INDICATIF_WHATSAPP },
                        erreur = "Numéro invalide pour ce pays".takeIf { erreursVisibles && !whatsappValide }
                    )
                }
            }

            SectionFormulaire("Activité Forever") {
                if (idVerrouille) {
                    ChampVerrouille(libelle = "ID Forever Living", valeur = idForever)
                } else {
                    ChampTexte(
                        libelle = "ID Forever Living",
                        valeur = idForever,
                        onChange = { saisie -> idForever = saisie.filter { it.isDigit() } },
                        clavier = KeyboardType.Number,
                        aide = "Vérifiez-le bien : ensuite, seule l'administration pourra le corriger",
                        optionnel = true
                    )
                }
                ChampChoix(
                    libelle = "Grade",
                    valeur = grade,
                    aide = if (grade == profil.gradeApplique) {
                        "Remise appliquée : ${reductionPourGrade(grade).toInt()} %"
                    } else {
                        "Remise de ${reductionPourGrade(grade).toInt()} % après validation par l'administration"
                    },
                    onClick = { feuille = Feuille.GRADE }
                )
            }

            Spacer(Modifier.height(4.dp))
        }
    }

    when (feuille) {
        Feuille.PAYS -> FeuilleChoix(
            titre = "Pays",
            options = paysAutorises,
            libelle = { "${it.drapeau}  ${it.nom}" },
            selection = pays,
            onChoix = { pays = it },
            onFermer = { feuille = Feuille.AUCUNE }
        )
        Feuille.GRADE -> FeuilleChoix(
            titre = "Grade",
            options = listeGrades,
            libelle = { it },
            detail = { "Remise ${reductionPourGrade(it).toInt()} %" },
            selection = grade,
            onChoix = { grade = it },
            onFermer = { feuille = Feuille.AUCUNE }
        )
        Feuille.INDICATIF_TELEPHONE -> FeuilleChoix(
            titre = "Indicatif",
            options = listeIndicatifs,
            libelle = { "${it.flag}  ${it.name}" },
            detail = { it.dialCode },
            selection = indicatifTel,
            recherche = true,
            onChoix = { indicatifTel = it },
            onFermer = { feuille = Feuille.AUCUNE }
        )
        Feuille.INDICATIF_WHATSAPP -> FeuilleChoix(
            titre = "Indicatif",
            options = listeIndicatifs,
            libelle = { "${it.flag}  ${it.name}" },
            detail = { it.dialCode },
            selection = indicatifWhatsapp,
            recherche = true,
            onChoix = { indicatifWhatsapp = it },
            onFermer = { feuille = Feuille.AUCUNE }
        )
        Feuille.AUCUNE -> {}
    }

    if (confirmationAbandon) {
        AlertDialog(
            onDismissRequest = { confirmationAbandon = false },
            containerColor = BlackCard,
            title = { Text("Abandonner les modifications ?", color = WhitePrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Les changements non enregistrés seront perdus.", color = WhiteSub) },
            confirmButton = {
                TextButton(onClick = {
                    confirmationAbandon = false
                    onRetour()
                }) {
                    Text("Abandonner", color = RedAlert, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmationAbandon = false }) {
                    Text("Continuer", color = WhiteSub)
                }
            }
        )
    }
}

private enum class Feuille { AUCUNE, PAYS, GRADE, INDICATIF_TELEPHONE, INDICATIF_WHATSAPP }

@Composable
private fun ApercuIdentite(prenom: String, nom: String, grade: String) {
    val initiales = listOf(prenom, nom)
        .mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }
        .joinToString("")
    val nomComplet = listOf(prenom, nom).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FormeSection)
            .background(Brush.horizontalGradient(listOf(GoldGlow, GoldFaint)))
            .border(1.dp, GoldPrimary.copy(alpha = 0.25f), FormeSection)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(GoldGradient, CircleShape)
                .padding(2.dp)
                .background(BlackCard, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (initiales.isEmpty()) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = GoldPrimary)
            } else {
                Text(initiales, color = GoldPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                nomComplet.ifEmpty { "Votre nom" },
                color = if (nomComplet.isEmpty()) WhiteSub else WhitePrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(grade, color = GoldPrimary, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SectionFormulaire(titre: String, contenu: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            titre,
            color = WhiteSub,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FormeSection)
                .background(BlackCard)
                .border(1.dp, BlackBorder, FormeSection)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = contenu
        )
    }
}

@Composable
private fun LibelleChamp(texte: String, optionnel: Boolean = false) {
    Row(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(texte, color = WhitePrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (optionnel) Text("  (facultatif)", color = WhiteFaint, fontSize = 12.sp)
    }
}

@Composable
private fun TexteSousChamp(erreur: String?, aide: String?) {
    val texte = erreur ?: aide ?: return
    Text(
        texte,
        color = if (erreur != null) RedAlert else WhiteSub,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 2.dp, top = 5.dp)
    )
}

@Composable
private fun couleursChamp() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = BlackSurface,
    unfocusedContainerColor = BlackSurface,
    errorContainerColor = BlackSurface,
    focusedTextColor = WhitePrimary,
    unfocusedTextColor = WhitePrimary,
    errorTextColor = WhitePrimary,
    focusedBorderColor = GoldPrimary,
    unfocusedBorderColor = Color.Transparent,
    errorBorderColor = RedAlert,
    cursorColor = GoldPrimary,
    errorCursorColor = GoldPrimary
)

@Composable
private fun ChampTexte(
    libelle: String,
    valeur: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    erreur: String? = null,
    aide: String? = null,
    optionnel: Boolean = false,
    clavier: KeyboardType = KeyboardType.Text,
    majuscules: KeyboardCapitalization = KeyboardCapitalization.None
) {
    Column(modifier) {
        LibelleChamp(libelle, optionnel)
        OutlinedTextField(
            value = valeur,
            onValueChange = onChange,
            singleLine = true,
            isError = erreur != null,
            keyboardOptions = KeyboardOptions(
                capitalization = majuscules,
                keyboardType = clavier,
                imeAction = ImeAction.Next
            ),
            shape = FormeChamp,
            modifier = Modifier.fillMaxWidth(),
            colors = couleursChamp()
        )
        TexteSousChamp(erreur, aide)
    }
}

/** Zone cliquable qui ouvre une liste, avec la même apparence qu'un champ de saisie. */
@Composable
private fun BoiteChoix(
    valeur: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(FormeChamp)
            .background(BlackSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            valeur,
            color = WhitePrimary,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = WhiteSub,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ChampChoix(libelle: String, valeur: String, onClick: () -> Unit, aide: String? = null) {
    Column {
        LibelleChamp(libelle)
        BoiteChoix(valeur = valeur, onClick = onClick, modifier = Modifier.fillMaxWidth())
        TexteSousChamp(null, aide)
    }
}

@Composable
private fun ChampNumero(
    libelle: String,
    indicatif: CountryDialCode,
    numero: String,
    onNumero: (String) -> Unit,
    onIndicatif: () -> Unit,
    erreur: String?
) {
    Column {
        LibelleChamp(libelle)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoiteChoix(
                valeur = "${indicatif.flag} ${indicatif.dialCode}",
                onClick = onIndicatif,
                modifier = Modifier.width(116.dp)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = numero,
                onValueChange = { saisie -> onNumero(saisie.filter { it.isDigit() }) },
                singleLine = true,
                isError = erreur != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                shape = FormeChamp,
                modifier = Modifier.weight(1f),
                colors = couleursChamp()
            )
        }
        TexteSousChamp(erreur, null)
    }
}

@Composable
private fun ChampVerrouille(libelle: String, valeur: String) {
    Column {
        LibelleChamp(libelle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(FormeChamp)
                .border(1.dp, BlackBorder, FormeChamp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(valeur, color = WhiteSub, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Non modifiable",
                tint = WhiteFaint,
                modifier = Modifier.size(18.dp)
            )
        }
        TexteSousChamp(null, "Non modifiable depuis l'application")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> FeuilleChoix(
    titre: String,
    options: List<T>,
    libelle: (T) -> String,
    selection: T,
    onChoix: (T) -> Unit,
    onFermer: () -> Unit,
    detail: ((T) -> String)? = null,
    recherche: Boolean = false
) {
    var filtre by remember { mutableStateOf("") }
    val visibles = remember(filtre) {
        val texte = sansAccents(filtre.trim())
        if (texte.isEmpty()) options else options.filter {
            sansAccents(libelle(it)).contains(texte, ignoreCase = true) ||
                detail?.invoke(it)?.contains(texte) == true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onFermer,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = recherche),
        containerColor = BlackCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BlackBorder) }
    ) {
        // Hauteur fixe avec recherche : sinon le clavier recouvre les résultats.
        Column(if (recherche) Modifier.fillMaxHeight(0.9f).imePadding() else Modifier) {
        Text(
            titre,
            color = WhitePrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )
        if (recherche) {
            OutlinedTextField(
                value = filtre,
                onValueChange = { filtre = it },
                singleLine = true,
                placeholder = { Text("Rechercher un pays", color = WhiteFaint) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = WhiteSub) },
                shape = FormeChamp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                colors = couleursChamp()
            )
        }
        if (visibles.isEmpty()) {
            Text(
                "Aucun résultat",
                color = WhiteSub,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(visibles) { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onChoix(option)
                            onFermer()
                        }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        libelle(option),
                        color = if (option == selection) GoldPrimary else WhitePrimary,
                        fontSize = 15.sp,
                        fontWeight = if (option == selection) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    detail?.let { Text(it(option), color = WhiteSub, fontSize = 13.sp) }
                    if (option == selection) {
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Sélectionné",
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        }
    }
}
