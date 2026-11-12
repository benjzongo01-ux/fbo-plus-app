package com.benjo.fboplus.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await

/*
 * Numéros de dépôt de FBO+ (Wave, Orange, Moov, MTN…), réglés par le super administrateur dans
 * `parametres/numerosDepot`. Le payeur envoie l'argent sur l'un d'eux puis déclare la référence ;
 * l'administration vérifie. Le même fichier est copié dans FBO+ Delivery (seul le package change).
 */

data class NumeroDepot(val operateur: String, val numero: String, val titulaire: String)

private val DOr = Color(0xFFE5B53A)
private val DSurface = Color(0xFF1B1E26)
private val DTrait = Color(0xFF2A2D35)
private val DGris = Color(0xFF9AA0AC)

/** Numéros de dépôt du pays, à jour en temps réel. */
@Composable
fun numerosDepot(pays: String): List<NumeroDepot> {
    var numeros by remember { mutableStateOf<List<NumeroDepot>>(emptyList()) }
    DisposableEffect(pays) {
        val ecoute = FirebaseFirestore.getInstance().document("parametres/numerosDepot").addSnapshotListener { d, _ ->
            numeros = (d?.get(pays) as? List<*>).orEmpty().mapNotNull { n ->
                val m = n as? Map<*, *> ?: return@mapNotNull null
                val numero = m["numero"] as? String ?: return@mapNotNull null
                NumeroDepot(m["operateur"] as? String ?: "", numero, m["titulaire"] as? String ?: "")
            }.filter { it.operateur.isNotBlank() && it.numero.isNotBlank() }
        }
        onDispose { ecoute.remove() }
    }
    return numeros
}

/** Numéro de dépôt mis en avant, avec le montant à envoyer et un bouton copier. */
@Composable
fun CarteNumeroDepot(numero: NumeroDepot, montant: String?) {
    val presse = LocalClipboardManager.current
    val contexte = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DSurface)
            .border(1.dp, DOr.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            if (montant != null) "Envoyer $montant par ${numero.operateur} à" else "Numéro ${numero.operateur} de FBO+",
            color = DGris, fontSize = 13.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(numero.numero, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.ContentCopy, "Copier le numéro", tint = DOr,
                modifier = Modifier.size(22.dp).clickable {
                    presse.setText(AnnotatedString(numero.numero.filter { it.isDigit() || it == '+' }))
                    android.widget.Toast.makeText(contexte, "Numéro copié", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
        if (numero.titulaire.isNotBlank()) Text("Au nom de ${numero.titulaire}", color = DGris, fontSize = 12.sp)
    }
}

/** Capture facultative du SMS ou de l'écran de paiement : choisie dans la galerie. */
@Composable
fun ChoixCapture(choisie: Uri?, onChoix: (Uri?) -> Unit) {
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onChoix(uri) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DTrait, RoundedCornerShape(12.dp))
            .clickable { galerie.launch("image/*") }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (choisie == null) Icons.Filled.AddAPhoto else Icons.Filled.CheckCircle, null, tint = DOr, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(if (choisie == null) "Ajouter la capture du paiement (facultatif)" else "Capture ajoutée · changer", color = Color.White, fontSize = 14.sp)
    }
}

/** Envoie la capture dans le dossier de l'utilisateur et rend son chemin (lu par l'administration). */
suspend fun envoyerCapture(contexte: android.content.Context, uri: Uri): String {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Session expirée")
    val octets = contexte.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Capture illisible")
    require(octets.size < 5 * 1024 * 1024) { "Capture trop lourde (5 Mo au plus)." }
    val chemin = "declarations/$uid/capture-${System.currentTimeMillis()}.jpg"
    FirebaseStorage.getInstance().reference.child(chemin)
        .putBytes(octets, StorageMetadata.Builder().setContentType(contexte.contentResolver.getType(uri) ?: "image/jpeg").build())
        .await()
    return chemin
}
