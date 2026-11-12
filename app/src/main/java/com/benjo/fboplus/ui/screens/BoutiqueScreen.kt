package com.benjo.fboplus.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as CouleurAndroid
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.Profil
import com.benjo.fboplus.ui.theme.*
import com.benjo.fboplus.viewmodel.ProfilViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Domaine attribué par Firebase Hosting au projet, actif une fois le site déployé. */
private const val DOMAINE_BOUTIQUE = "https://fbo-plus.web.app"

private fun qrCode(contenu: String, taille: Int = 720): Bitmap {
    val matrice = QRCodeWriter().encode(contenu, BarcodeFormat.QR_CODE, taille, taille)
    val pixels = IntArray(taille * taille) { i ->
        if (matrice.get(i % taille, i / taille)) CouleurAndroid.BLACK else CouleurAndroid.WHITE
    }
    return Bitmap.createBitmap(pixels, taille, taille, Bitmap.Config.RGB_565)
}

/** Seules ces informations deviennent publiques. */
private fun publierBoutique(uid: String, profil: Profil) {
    FirebaseFirestore.getInstance().document("boutiques/$uid").set(
        mapOf(
            "nom" to listOf(profil.prenom, profil.nom).filter { it.isNotBlank() }.joinToString(" "),
            "ville" to profil.ville,
            "idForever" to profil.idDistributeur,
            "pays" to profil.pays,
            "whatsapp" to "${profil.indicatifWhatsapp}${profil.whatsapp}"
        ),
        SetOptions.merge()
    )
}

@Composable
fun BoutiqueScreen(profilViewModel: ProfilViewModel, onRetour: () -> Unit, avecEnTete: Boolean = true) {
    val context = LocalContext.current
    val presse = LocalClipboardManager.current
    val profil by profilViewModel.profil.collectAsState()
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val lien = "$DOMAINE_BOUTIQUE/boutique/$uid"
    val qr = remember(lien) { qrCode(lien).asImageBitmap() }

    LaunchedEffect(profil) {
        if (uid.isNotEmpty()) publierBoutique(uid, profil)
    }

    Column(Modifier.fillMaxSize().background(BlackDeep).verticalScroll(rememberScrollState())) {
        if (avecEnTete) EnTeteSimple("Ma boutique", onRetour)
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Vos clients scannent ce code pour voir le catalogue et vous écrire sur WhatsApp.",
                color = WhiteSub,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
                Image(
                    bitmap = qr,
                    contentDescription = "QR code de ma boutique",
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(lien, color = GoldPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val partage = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Découvrez ma boutique Forever : $lien")
                    }
                    context.startActivity(Intent.createChooser(partage, "Partager via..."))
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = BlackDeep),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Partager le lien", fontWeight = FontWeight.Bold) }
            TextButton(onClick = {
                presse.setText(AnnotatedString(lien))
                Toast.makeText(context, "Lien copié", Toast.LENGTH_SHORT).show()
            }) { Text("Copier le lien", color = GoldPrimary) }
        }
    }
}
