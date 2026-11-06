package com.benjo.fboplus.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.benjo.fboplus.data.CountryDialCode
import com.benjo.fboplus.data.listeIndicatifs

val indicatifParDefaut: CountryDialCode = listeIndicatifs.first { it.dialCode == "+226" }

fun CountryDialCode.accepte(numero: String): Boolean = numero.length in minDigits..maxDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChampTelephone(
    indicatif: CountryDialCode,
    onIndicatifChange: (CountryDialCode) -> Unit,
    numero: String,
    onNumeroChange: (String) -> Unit
) {
    var menuOuvert by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(
            expanded = menuOuvert,
            onExpandedChange = { menuOuvert = !menuOuvert },
            modifier = Modifier.width(110.dp)
        ) {
            OutlinedTextField(
                value = "${indicatif.flag} ${indicatif.dialCode}",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
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
                onDismissRequest = { menuOuvert = false },
                modifier = Modifier.heightIn(max = 350.dp)
            ) {
                listeIndicatifs.forEach { pays ->
                    DropdownMenuItem(
                        text = { Text("${pays.flag} ${pays.name} (${pays.dialCode})") },
                        onClick = {
                            onIndicatifChange(pays)
                            menuOuvert = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        AppTextField(
            value = numero,
            onValueChange = { saisie -> onNumeroChange(saisie.filter { it.isDigit() }) },
            placeholder = "Numéro de téléphone",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.weight(1f)
        )
    }
}
