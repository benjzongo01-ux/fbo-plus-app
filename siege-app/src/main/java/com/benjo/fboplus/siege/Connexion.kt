package com.benjo.fboplus.siege

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Connexion à FBO+ Siège : adresse email puis code reçu par email (ou Google de test sur les
 * émulateurs). Seuls les correspondants invités par le super administrateur entrent.
 */
class ConnexionViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val fonctions = FirebaseFunctions.getInstance(REGION_FUNCTIONS)

    private val _connecte = MutableStateFlow(auth.currentUser != null)
    val connecte: StateFlow<Boolean> = _connecte
    private val _enCours = MutableStateFlow(false)
    val enCours: StateFlow<Boolean> = _enCours
    private val _erreur = MutableStateFlow<String?>(null)
    val erreur: StateFlow<String?> = _erreur
    private val _emailCode = MutableStateFlow<String?>(null)
    val emailCode: StateFlow<String?> = _emailCode
    private val _numeroEnvoi = MutableStateFlow(0)
    val numeroEnvoi: StateFlow<Int> = _numeroEnvoi

    val googleDeTest: Boolean get() = BuildConfig.EMULATEUR_FIREBASE

    private fun executer(action: suspend () -> Unit) {
        _erreur.value = null
        _enCours.value = true
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auth.signOut()
                _erreur.value = (e as? FirebaseFunctionsException)?.message ?: "Connexion impossible. Réessayez."
            } finally {
                _enCours.value = false
            }
        }
    }

    /** L'invitation est appliquée ; tout autre rôle que correspondant est refusé. */
    private suspend fun terminer() {
        try {
            fonctions.getHttpsCallable("initialiserProfil").call(mapOf("personnel" to true)).await()
        } catch (e: FirebaseFunctionsException) {
            if (e.code == FirebaseFunctionsException.Code.PERMISSION_DENIED) runCatching { auth.currentUser?.delete()?.await() }
            throw e
        }
        val role = auth.currentUser?.getIdToken(true)?.await()?.claims?.get("role")
        if (role !in setOf("correspondant", "admin", "super_admin")) {
            auth.signOut()
            _erreur.value = "Ce compte n'est pas un compte du siège. Contactez l'administration."
            return
        }
        _connecte.value = true
    }

    fun demanderCode(email: String) = executer {
        val adresse = email.trim().lowercase()
        fonctions.getHttpsCallable("demanderCodeEmail").call(mapOf("email" to adresse)).await()
        _emailCode.value = adresse
        _numeroEnvoi.value++
    }

    fun verifierCode(code: String) {
        val adresse = _emailCode.value ?: return
        executer {
            val r = fonctions.getHttpsCallable("verifierCodeEmail").call(mapOf("email" to adresse, "code" to code)).await().getData() as Map<*, *>
            auth.signInWithCustomToken(r["jeton"] as String).await()
            _emailCode.value = null
            terminer()
        }
    }

    fun connecterGoogleDeTest(email: String) = executer {
        val jeton = JSONObject().put("sub", "test-" + email.lowercase()).put("email", email.lowercase()).put("email_verified", true).toString()
        auth.signInWithCredential(GoogleAuthProvider.getCredential(jeton, null)).await()
        terminer()
    }

    fun changerEmail() {
        _emailCode.value = null
        _erreur.value = null
    }

    fun deconnecter() {
        auth.signOut()
        _connecte.value = false
    }
}

@Composable
fun ConnexionEcran(vm: ConnexionViewModel) {
    val enCours by vm.enCours.collectAsState()
    val erreur by vm.erreur.collectAsState()
    val emailCode by vm.emailCode.collectAsState()
    val envoi by vm.numeroEnvoi.collectAsState()
    var dialogueTest by remember { mutableStateOf(false) }
    BackHandler(enabled = emailCode != null) { vm.changerEmail() }

    EcranConnexion(
        etat = EtatEcranConnexion(
            livreur = true,
            emailCode = emailCode,
            numeroEnvoi = envoi,
            enCours = enCours,
            message = erreur,
            messageErreur = erreur != null,
            googleDisponible = vm.googleDeTest
        ),
        onGoogle = { dialogueTest = true },
        onEnvoyerCode = { vm.demanderCode(it) },
        onVerifierCode = { vm.verifierCode(it) },
        onRenvoyerCode = { emailCode?.let { vm.demanderCode(it) } },
        onChangerEmail = { vm.changerEmail() }
    )

    if (dialogueTest) {
        var adresse by remember { mutableStateOf("correspondant.bf@fboplus.test") }
        AlertDialog(
            onDismissRequest = { dialogueTest = false },
            containerColor = Color(0xFF14161C),
            title = { Text("Compte Google de test", color = Color.White) },
            text = { OutlinedTextField(value = adresse, onValueChange = { adresse = it }, label = { Text("Adresse (émulateurs)") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { dialogueTest = false; vm.connecterGoogleDeTest(adresse.trim()) }) { Text("Continuer", color = Color(0xFFE5B53A)) } },
            dismissButton = { TextButton(onClick = { dialogueTest = false }) { Text("Annuler", color = Color(0xFF9AA0AC)) } }
        )
    }
}
