package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.listeGrades
import com.benjo.fboplus.data.listeIndicatifs
import com.benjo.fboplus.data.paysAutorises
import com.benjo.fboplus.ui.components.*
import com.benjo.fboplus.viewmodel.ProfilViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifierProfilScreen(
    profilViewModel: ProfilViewModel,
    onRetour: () -> Unit
) {
    val profil by profilViewModel.profil.collectAsState()

    var nom by remember(profil) { mutableStateOf(profil.nom) }
    var prenom by remember(profil) { mutableStateOf(profil.prenom) }
    var paysSelectionne by remember(profil) {
        mutableStateOf(paysAutorises.find { it.nom == profil.pays } ?: paysAutorises.first())
    }
    var ville by remember(profil) { mutableStateOf(profil.ville) }
    var grade by remember(profil) { mutableStateOf(profil.grade) }
    var indicatifTel by remember(profil) {
        mutableStateOf(listeIndicatifs.find { it.dialCode == profil.indicatifTelephone } ?: listeIndicatifs.first { it.dialCode == "+226" })
    }
    var telephone by remember(profil) { mutableStateOf(profil.telephone) }
    var indicatifWhatsapp by remember(profil) {
        mutableStateOf(listeIndicatifs.find { it.dialCode == profil.indicatifWhatsapp } ?: listeIndicatifs.first { it.dialCode == "+226" })
    }
    var whatsapp by remember(profil) { mutableStateOf(profil.whatsapp) }

    var menuPaysOuvert by remember { mutableStateOf(false) }
    var menuGradeOuvert by remember { mutableStateOf(false) }
    var menuIndicatifTelOuvert by remember { mutableStateOf(false) }
    var menuIndicatifWhatsappOuvert by remember { mutableStateOf(false) }

    val telValide = telephone.length in indicatifTel.minDigits..indicatifTel.maxDigits
    val whatsappValide = whatsapp.length in indicatifWhatsapp.minDigits..indicatifWhatsapp.maxDigits
    val formulaireValide = nom.isNotBlank() && prenom.isNotBlank() && ville.isNotBlank() && telValide && whatsappValide

    Scaffold(
        containerColor = SBackgroundBlack,
        topBar = {
            TopAppBar(
                title = { Text("Modifier le profil", color = STextWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Note : l'ID Forever n'est pas modifiable ici — géré séparément (règle de sécurité)

            AppTextField(value = prenom, onValueChange = { prenom = it }, placeholder = "Prénom")
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(value = nom, onValueChange = { nom = it }, placeholder = "Nom")
            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = menuPaysOuvert,
                onExpandedChange = { menuPaysOuvert = !menuPaysOuvert }
            ) {
                OutlinedTextField(
                    value = "${paysSelectionne.drapeau}  ${paysSelectionne.nom}",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SFieldBackground,
                        unfocusedContainerColor = SFieldBackground,
                        focusedTextColor = STextWhite,
                        unfocusedTextColor = STextWhite,
                        focusedBorderColor = SGoldPrimary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
                ExposedDropdownMenu(expanded = menuPaysOuvert, onDismissRequest = { menuPaysOuvert = false }) {
                    paysAutorises.forEach { pays ->
                        DropdownMenuItem(
                            text = { Text("${pays.drapeau}  ${pays.nom}") },
                            onClick = { paysSelectionne = pays; menuPaysOuvert = false }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            AppTextField(value = ville, onValueChange = { ville = it }, placeholder = "Ville")
            Spacer(modifier = Modifier.height(20.dp))

            Text("Téléphone", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SGoldPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            ChampTelephoneModifiable(
                indicatifSelectionne = indicatifTel,
                onIndicatifChange = { indicatifTel = it },
                numero = telephone,
                onNumeroChange = { telephone = it.filter { c -> c.isDigit() } },
                menuOuvert = menuIndicatifTelOuvert,
                onMenuToggle = { menuIndicatifTelOuvert = it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SGoldPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            ChampTelephoneModifiable(
                indicatifSelectionne = indicatifWhatsapp,
                onIndicatifChange = { indicatifWhatsapp = it },
                numero = whatsapp,
                onNumeroChange = { whatsapp = it.filter { c -> c.isDigit() } },
                menuOuvert = menuIndicatifWhatsappOuvert,
                onMenuToggle = { menuIndicatifWhatsappOuvert = it }
            )
            Spacer(modifier = Modifier.height(20.dp))

            Text("Grade", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SGoldPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            ExposedDropdownMenuBox(
                expanded = menuGradeOuvert,
                onExpandedChange = { menuGradeOuvert = !menuGradeOuvert }
            ) {
                OutlinedTextField(
                    value = grade,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SFieldBackground,
                        unfocusedContainerColor = SFieldBackground,
                        focusedTextColor = STextWhite,
                        unfocusedTextColor = STextWhite,
                        focusedBorderColor = SGoldPrimary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
                ExposedDropdownMenu(expanded = menuGradeOuvert, onDismissRequest = { menuGradeOuvert = false }) {
                    listeGrades.forEach { g ->
                        DropdownMenuItem(text = { Text(g) }, onClick = { grade = g; menuGradeOuvert = false })
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    profilViewModel.sauvegarderProfil(
                        profil.copy(
                            nom = nom,
                            prenom = prenom,
                            pays = paysSelectionne.nom,
                            ville = ville,
                            indicatifTelephone = indicatifTel.dialCode,
                            telephone = telephone,
                            indicatifWhatsapp = indicatifWhatsapp.dialCode,
                            whatsapp = whatsapp,
                            grade = grade
                        )
                    )
                    // TODO : synchroniser avec le serveur dès que l'endpoint sera prêt
                    onRetour()
                },
                enabled = formulaireValide,
                colors = ButtonDefaults.buttonColors(containerColor = SGoldPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Enregistrer", fontWeight = FontWeight.Bold, color = SBackgroundBlack)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChampTelephoneModifiable(
    indicatifSelectionne: com.benjo.fboplus.data.CountryDialCode,
    onIndicatifChange: (com.benjo.fboplus.data.CountryDialCode) -> Unit,
    numero: String,
    onNumeroChange: (String) -> Unit,
    menuOuvert: Boolean,
    onMenuToggle: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(
            expanded = menuOuvert,
            onExpandedChange = { onMenuToggle(!menuOuvert) },
            modifier = Modifier.width(110.dp)
        ) {
            OutlinedTextField(
                value = "${indicatifSelectionne.flag} ${indicatifSelectionne.dialCode}",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
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
                expanded = menuOuvert,
                onDismissRequest = { onMenuToggle(false) },
                modifier = Modifier.heightIn(max = 350.dp)
            ) {
                com.benjo.fboplus.data.listeIndicatifs.forEach { pays ->
                    DropdownMenuItem(
                        text = { Text("${pays.flag} ${pays.name} (${pays.dialCode})") },
                        onClick = { onIndicatifChange(pays); onMenuToggle(false) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        AppTextField(
            value = numero,
            onValueChange = onNumeroChange,
            placeholder = "Numéro",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.weight(1f)
        )
    }
}