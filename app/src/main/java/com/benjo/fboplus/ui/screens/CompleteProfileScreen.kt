package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.Profil
import com.benjo.fboplus.data.listeGrades
import com.benjo.fboplus.data.listeIndicatifs
import com.benjo.fboplus.data.paysAutorises
import com.benjo.fboplus.ui.components.*
import com.benjo.fboplus.viewmodel.ProfilViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProfileScreen(
    profilViewModel: ProfilViewModel,
    onProfilComplete: () -> Unit
) {
    val profilActuel by profilViewModel.profil.collectAsState()

    var nom by remember { mutableStateOf("") }
    var prenom by remember { mutableStateOf("") }
    var paysSelectionne by remember { mutableStateOf(paysAutorises.first()) }
    var ville by remember { mutableStateOf("") }
    var idForever by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf(listeGrades.first()) }

    var indicatifTel by remember { mutableStateOf(listeIndicatifs.first { it.dialCode == "+226" }) }
    var telephone by remember { mutableStateOf("") }
    var indicatifWhatsapp by remember { mutableStateOf(listeIndicatifs.first { it.dialCode == "+226" }) }
    var whatsapp by remember { mutableStateOf("") }

    var menuPaysOuvert by remember { mutableStateOf(false) }
    var menuGradeOuvert by remember { mutableStateOf(false) }
    var menuIndicatifTelOuvert by remember { mutableStateOf(false) }
    var menuIndicatifWhatsappOuvert by remember { mutableStateOf(false) }

    val telValide = telephone.length in indicatifTel.minDigits..indicatifTel.maxDigits
    val whatsappValide = whatsapp.length in indicatifWhatsapp.minDigits..indicatifWhatsapp.maxDigits
    val formulaireValide = nom.isNotBlank() && prenom.isNotBlank() && ville.isNotBlank() &&
            idForever.isNotBlank() && telValide && whatsappValide

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SBackgroundBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                "Complétez votre profil",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = STextWhite
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Ces informations serviront à identifier votre compte sur tous vos appareils",
                fontSize = 13.sp,
                color = STextGray
            )
            Spacer(modifier = Modifier.height(28.dp))

            AppTextField(value = prenom, onValueChange = { prenom = it }, placeholder = "Prénom")
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(value = nom, onValueChange = { nom = it }, placeholder = "Nom")
            Spacer(modifier = Modifier.height(12.dp))

            // Pays (3 pays autorisés avec drapeau)
            ExposedDropdownMenuBox(
                expanded = menuPaysOuvert,
                onExpandedChange = { menuPaysOuvert = !menuPaysOuvert }
            ) {
                OutlinedTextField(
                    value = "${paysSelectionne.drapeau}  ${paysSelectionne.nom}",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Pays de résidence", color = STextGray) },
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
                    expanded = menuPaysOuvert,
                    onDismissRequest = { menuPaysOuvert = false }
                ) {
                    paysAutorises.forEach { pays ->
                        DropdownMenuItem(
                            text = { Text("${pays.drapeau}  ${pays.nom}") },
                            onClick = {
                                paysSelectionne = pays
                                menuPaysOuvert = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            AppTextField(value = ville, onValueChange = { ville = it }, placeholder = "Ville")
            Spacer(modifier = Modifier.height(12.dp))

            AppTextField(
                value = idForever,
                onValueChange = { input -> idForever = input.filter { it.isDigit() } },
                placeholder = "ID Distributeur Forever Living",
                keyboardType = KeyboardType.Number
            )
            Spacer(modifier = Modifier.height(20.dp))

            Text("Téléphone", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SGoldPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            ChampTelephoneAvecIndicatif(
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
            ChampTelephoneAvecIndicatif(
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
                ExposedDropdownMenu(
                    expanded = menuGradeOuvert,
                    onDismissRequest = { menuGradeOuvert = false }
                ) {
                    listeGrades.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g) },
                            onClick = { grade = g; menuGradeOuvert = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    profilViewModel.sauvegarderProfil(
                        profilActuel.copy(
                            nom = nom,
                            prenom = prenom,
                            pays = paysSelectionne.nom,
                            ville = ville,
                            idDistributeur = idForever,
                            indicatifTelephone = indicatifTel.dialCode,
                            telephone = telephone,
                            indicatifWhatsapp = indicatifWhatsapp.dialCode,
                            whatsapp = whatsapp,
                            grade = grade,
                            profilComplet = true
                        )
                    )
                    // TODO : envoyer ces infos au serveur dès que l'endpoint sera créé
                    onProfilComplete()
                },
                enabled = formulaireValide,
                colors = ButtonDefaults.buttonColors(containerColor = SGoldPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Continuer", fontWeight = FontWeight.Bold, color = SBackgroundBlack)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChampTelephoneAvecIndicatif(
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
                        onClick = {
                            onIndicatifChange(pays)
                            onMenuToggle(false)
                        }
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