package com.benjo.fboplus.delivery

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Tant que l'identité du livreur n'est pas vérifiée, l'application affiche son dossier à la place
 * des missions : pièce d'identité recto et verso, photo du visage, adresse, personne à contacter.
 */
@Composable
fun AvecIdentiteVerifiee(onDeconnexion: () -> Unit, contenu: @Composable () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var statut by remember { mutableStateOf<String?>(null) }
    var motif by remember { mutableStateOf("") }
    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose {}
        val ecoute = FirebaseFirestore.getInstance().document("users/$uid").addSnapshotListener { d, _ ->
            @Suppress("UNCHECKED_CAST")
            val kyc = d?.get("kyc") as? Map<String, Any?>
            statut = kyc?.get("statut") as? String ?: "aucun"
            motif = kyc?.get("motif") as? String ?: ""
        }
        onDispose { ecoute.remove() }
    }
    when (statut) {
        null -> Box(Modifier.fillMaxSize().background(Fond), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Or) }
        "valide" -> contenu()
        "en_attente" -> Attente(onDeconnexion)
        else -> DossierIdentite(motifRefus = motif, onDeconnexion = onDeconnexion)
    }
}

@Composable
private fun Attente(onDeconnexion: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Fond).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dossier envoyé", color = Or, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Merci ! L'administration vérifie votre identité. Vous recevrez une notification dès que vos missions seront ouvertes.",
            color = TexteDoux,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onDeconnexion) { Text("Se déconnecter", color = TexteDoux) }
    }
}

@Composable
private fun DossierIdentite(motifRefus: String, onDeconnexion: () -> Unit) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val photos = remember { mutableStateMapOf<String, Uri>() }
    var numeroPiece by remember { mutableStateOf("") }
    var adresse by remember { mutableStateOf("") }
    var contactNom by remember { mutableStateOf("") }
    var contactTelephone by remember { mutableStateOf("") }
    var envoi by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val portee = rememberCoroutineScope()
    var cleEnCours by remember { mutableStateOf("") }
    val choisir = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) photos[cleEnCours] = uri
    }

    Column(
        Modifier.fillMaxSize().background(Fond).verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(30.dp))
        Text("Vérification de votre identité", color = Or, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Avant votre première mission, l'administration doit vérifier votre identité. Vos documents restent privés : seul le super administrateur peut les voir.",
            color = TexteDoux,
            fontSize = 14.sp
        )
        if (motifRefus.isNotBlank()) Text("Dossier à compléter : $motifRefus", color = Rouge, fontSize = 14.sp)

        listOf("pieceRecto" to "Pièce d'identité (recto)", "pieceVerso" to "Pièce d'identité (verso)", "selfie" to "Photo de votre visage").forEach { (cle, libelle) ->
            OutlinedButton(
                onClick = { cleEnCours = cle; choisir.launch("image/*") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (photos[cle] != null) "✓ $libelle" else "Ajouter : $libelle", color = if (photos[cle] != null) Vert else Or) }
        }
        listOf(
            Triple("Numéro de la pièce d'identité", numeroPiece) { v: String -> numeroPiece = v },
            Triple("Adresse (quartier, ville)", adresse) { v: String -> adresse = v },
            Triple("Personne à contacter (nom)", contactNom) { v: String -> contactNom = v }
        ).forEach { (libelle, valeur, changer) ->
            OutlinedTextField(value = valeur, onValueChange = changer, label = { Text(libelle) }, singleLine = true, colors = couleursChamp(), modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(
            value = contactTelephone,
            onValueChange = { contactTelephone = it },
            label = { Text("Téléphone de cette personne") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = couleursChamp(),
            modifier = Modifier.fillMaxWidth()
        )
        erreur?.let { Text(it, color = Rouge, fontSize = 14.sp) }
        val complet = photos.size == 3 && numeroPiece.isNotBlank() && adresse.isNotBlank() && contactNom.isNotBlank() && contactTelephone.length >= 8
        Button(
            onClick = {
                envoi = true
                erreur = null
                portee.launch {
                    try {
                        val chemins = photos.mapValues { (cle, uri) ->
                            val chemin = "kyc/$uid/$cle-${System.currentTimeMillis()}.jpg"
                            FirebaseStorage.getInstance().reference.child(chemin)
                                .putBytes(photoCompressee(context, uri), StorageMetadata.Builder().setContentType("image/jpeg").build())
                                .await()
                            chemin
                        }
                        FirebaseFunctions.getInstance(REGION_FUNCTIONS).getHttpsCallable("soumettreKyc").call(
                            chemins + mapOf(
                                "numeroPiece" to numeroPiece,
                                "adresse" to adresse,
                                "contactNom" to contactNom,
                                "contactTelephone" to contactTelephone
                            )
                        ).await()
                    } catch (e: Exception) {
                        erreur = e.message ?: "Envoi impossible. Réessayez."
                    } finally {
                        envoi = false
                    }
                }
            },
            enabled = complet && !envoi,
            colors = ButtonDefaults.buttonColors(containerColor = Or, contentColor = Fond),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text(if (envoi) "Envoi…" else "Envoyer mon dossier", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDeconnexion, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Se déconnecter", color = TexteDoux) }
    }
}
