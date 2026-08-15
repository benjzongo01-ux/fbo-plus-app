package com.benjo.fboplus.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Palette (cohérente avec le reste de l'app) ──
val SGoldPrimary = Color(0xFFD4AF37)
val SBackgroundBlack = Color(0xFF000000)
val SFieldBackground = Color(0xFF1A1A1A)
val STextWhite = Color(0xFFFFFFFF)
val STextGray = Color(0xFF7A7A7A)
val SError = Color(0xFFE84142)
val SSuccess = Color(0xFF2ECC71)

/**
 * Champ de saisie avec filigrane (placeholder) qui disparaît dès qu'on tape.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = STextGray, fontSize = 15.sp) },
        singleLine = singleLine,
        leadingIcon = leadingContent,
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SFieldBackground,
            unfocusedContainerColor = SFieldBackground,
            focusedTextColor = STextWhite,
            unfocusedTextColor = STextWhite,
            focusedBorderColor = SGoldPrimary,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = SGoldPrimary
        )
    )
}

/**
 * Champ mot de passe avec checklist en temps réel
 * (8-128 caractères, au moins un chiffre, au moins une majuscule)
 * et bouton afficher/masquer.
 */
@Composable
fun AppPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Mot de passe",
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    val hasLength = value.length in 8..128
    val hasDigit = value.any { it.isDigit() }
    val hasUpper = value.any { it.isUpperCase() }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = STextGray, fontSize = 15.sp) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = STextGray
                    )
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SFieldBackground,
                unfocusedContainerColor = SFieldBackground,
                focusedTextColor = STextWhite,
                unfocusedTextColor = STextWhite,
                focusedBorderColor = SGoldPrimary,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = SGoldPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        CritereItem("8 à 128 caractères", hasLength)
        CritereItem("Au moins un chiffre", hasDigit)
        CritereItem("Au moins une lettre majuscule", hasUpper)
    }
}

@Composable
private fun CritereItem(label: String, valide: Boolean) {
    val couleur by animateColorAsState(if (valide) SSuccess else STextGray, label = "critere_color")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(couleur, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = couleur, fontSize = 12.sp)
    }
}

fun motDePasseValide(pwd: String): Boolean =
    pwd.length in 8..128 && pwd.any { it.isDigit() } && pwd.any { it.isUpperCase() }

/**
 * Bouton "Continuer avec Google" — UI seule pour l'instant,
 * le branchement réel se fait plus tard côté serveur.
 */
@Composable
fun GoogleAuthButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, STextGray.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = STextWhite)
    ) {
        Text("Continuer avec Google", fontSize = 15.sp)
    }
}