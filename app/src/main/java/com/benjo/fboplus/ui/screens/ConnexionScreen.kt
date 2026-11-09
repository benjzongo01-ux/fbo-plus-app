package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.benjo.fboplus.ui.theme.*
import com.benjo.fboplus.viewmodel.AuthUiState
import com.benjo.fboplus.viewmodel.AuthViewModel

private enum class Mode { CONNEXION, CREATION, OUBLI }

private val Forme = RoundedCornerShape(12.dp)

/**
 * Connexion à FBO+ : Google en premier (gratuit, en un toucher), email et mot de passe ensuite.
 * Le même écran sert à créer un compte : un nouveau compte devient distributeur.
 */
@Composable
fun ConnexionScreen(
    authViewModel: AuthViewModel,
    onNeedsPinCreation: () -> Unit,
    onNeedsPinEntry: () -> Unit
) {
    val activity = LocalContext.current as FragmentActivity
    val uiState by authViewModel.uiState.collectAsState()
    var mode by remember { mutableStateOf(Mode.CONNEXION) }
    var email by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var voirMotDePasse by remember { mutableStateOf(false) }
    var conditionsAcceptees by remember { mutableStateOf(false) }
    var dialogueGoogleTest by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (uiState) {
            AuthUiState.NeedsPinCreation -> onNeedsPinCreation()
            AuthUiState.NeedsPinEntry -> onNeedsPinEntry()
            else -> {}
        }
    }

    val enCours = uiState is AuthUiState.Loading
    val emailValide = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$").matches(email.trim())
    val peutValider = !enCours && emailValide && when (mode) {
        Mode.CONNEXION -> motDePasse.isNotEmpty()
        Mode.CREATION -> motDePasse.length >= 8 && conditionsAcceptees
        Mode.OUBLI -> true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BlackDeep)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("FBO+", color = GoldPrimary, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            when (mode) {
                Mode.CONNEXION -> "Connexion"
                Mode.CREATION -> "Créer mon compte distributeur"
                Mode.OUBLI -> "Mot de passe oublié"
            },
            color = WhiteSub,
            fontSize = 17.sp
        )
        Spacer(Modifier.height(32.dp))

        if (mode != Mode.OUBLI) {
            OutlinedButton(
                onClick = {
                    when {
                        authViewModel.googleConfigure -> authViewModel.connecterGoogle(activity)
                        authViewModel.googleDeTest -> dialogueGoogleTest = true
                        else -> {}
                    }
                },
                enabled = !enCours && (authViewModel.googleConfigure || authViewModel.googleDeTest),
                shape = Forme,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
                border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Text("Continuer avec Google", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            if (!authViewModel.googleConfigure && !authViewModel.googleDeTest) {
                Text(
                    "Connexion Google bientôt disponible. Utilisez votre email.",
                    color = WhiteSub,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Text(
                "En continuant, vous acceptez les Conditions d'utilisation et la Politique de confidentialité.",
                color = WhiteSub,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = BlackBorder)
                Text("  ou avec votre email  ", color = WhiteSub, fontSize = 13.sp)
                HorizontalDivider(Modifier.weight(1f), color = BlackBorder)
            }
        }

        Champ("Adresse email", email, KeyboardType.Email) { email = it }
        if (mode != Mode.OUBLI) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = motDePasse,
                onValueChange = { motDePasse = it },
                label = { Text(if (mode == Mode.CREATION) "Mot de passe (8 caractères au moins)" else "Mot de passe") },
                singleLine = true,
                visualTransformation = if (voirMotDePasse) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { voirMotDePasse = !voirMotDePasse }) {
                        Text(if (voirMotDePasse) "Masquer" else "Voir", color = GoldPrimary, fontSize = 12.sp)
                    }
                },
                shape = Forme,
                colors = couleursChamp(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (mode == Mode.CREATION) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = conditionsAcceptees,
                    onCheckedChange = { conditionsAcceptees = it },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = BlackDeep)
                )
                Text("J'accepte les Conditions d'utilisation et la Politique de confidentialité", color = WhiteSub, fontSize = 13.sp)
            }
        }

        when (val etat = uiState) {
            is AuthUiState.Error -> Message(etat.message, RedAlert)
            is AuthUiState.Info -> Message(etat.message, GreenPrimary)
            else -> {}
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                when (mode) {
                    Mode.CONNEXION -> authViewModel.connecterEmail(email, motDePasse)
                    Mode.CREATION -> authViewModel.creerCompte(email, motDePasse)
                    Mode.OUBLI -> authViewModel.motDePasseOublie(email)
                }
            },
            enabled = peutValider,
            shape = Forme,
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = BlackDeep),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (enCours) {
                CircularProgressIndicator(Modifier.size(20.dp), color = BlackDeep, strokeWidth = 2.dp)
            } else {
                Text(
                    when (mode) {
                        Mode.CONNEXION -> "Se connecter"
                        Mode.CREATION -> "Créer mon compte"
                        Mode.OUBLI -> "Recevoir le lien"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        fun changer(nouveau: Mode) {
            mode = nouveau
            authViewModel.resetState()
        }
        if (mode != Mode.CREATION) Lien("Pas de compte ? Créer un compte") { changer(Mode.CREATION) }
        if (mode != Mode.CONNEXION) Lien("Déjà un compte ? Se connecter") { changer(Mode.CONNEXION) }
        if (mode != Mode.OUBLI) Lien("Mot de passe oublié ?") { changer(Mode.OUBLI) }
    }

    if (dialogueGoogleTest) DialogueGoogleDeTest(
        onChoix = { adresse ->
            dialogueGoogleTest = false
            authViewModel.connecterGoogleDeTest(adresse)
        },
        onFermer = { dialogueGoogleTest = false }
    )
}

@Composable
private fun Champ(libelle: String, valeur: String, clavier: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(libelle) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = clavier),
        shape = Forme,
        colors = couleursChamp(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun couleursChamp() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = GoldPrimary,
    unfocusedBorderColor = BlackBorder,
    focusedLabelColor = GoldPrimary,
    unfocusedLabelColor = WhiteSub,
    focusedTextColor = WhitePrimary,
    unfocusedTextColor = WhitePrimary,
    cursorColor = GoldPrimary
)

@Composable
private fun Message(texte: String, couleur: Color) {
    Text(texte, color = couleur, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
}

@Composable
private fun Lien(texte: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(texte, color = GoldPrimary, fontSize = 14.sp) }
}

/** Émulateurs uniquement : remplace le sélecteur de comptes Google, qui exige le vrai projet. */
@Composable
private fun DialogueGoogleDeTest(onChoix: (String) -> Unit, onFermer: () -> Unit) {
    var adresse by remember { mutableStateOf("distributeur.test@gmail.com") }
    AlertDialog(
        onDismissRequest = onFermer,
        containerColor = BlackCard,
        title = { Text("Compte Google de test", color = WhitePrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Émulateurs : aucun vrai compte Google n'est utilisé.", color = WhiteSub, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Champ("Adresse Gmail fictive", adresse, KeyboardType.Email) { adresse = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoix(adresse.trim()) }, enabled = adresse.contains("@")) {
                Text("Continuer", color = GoldPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler", color = WhiteSub) } }
    )
}
