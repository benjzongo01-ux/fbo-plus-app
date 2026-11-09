package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.ui.theme.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

private data class LigneValidation(val nom: String, val quantite: Int, val cc: Double)

private data class Reserve(
    val idForever: String,
    val nom: String,
    val montant: Int,
    val minimum: Int,
    val cc: Double,
    val lignes: List<LigneValidation>
)

private data class LotValidation(
    val id: String,
    val idForever: String,
    val montant: Int,
    val cc: Double,
    val valide: Boolean,
    val date: String,
    val reference: String
)

private val Forme = RoundedCornerShape(16.dp)
private fun fcfa(n: Int) = "%,d FCFA".format(Locale.FRANCE, n)
private fun cc(n: Double) = "%.3f CC".format(Locale.FRANCE, n)

@Suppress("UNCHECKED_CAST")
private fun lignesDe(valeur: Any?): List<LigneValidation> =
    (valeur as? List<Map<String, Any?>>).orEmpty().map {
        LigneValidation(
            nom = it["nom"] as? String ?: "",
            quantite = (it["quantite"] as? Number)?.toInt() ?: 0,
            cc = (it["cc"] as? Number)?.toDouble() ?: 0.0
        )
    }

/**
 * Validation au siège Forever : ce qui attend l'achat minimum (réserve) et ce qui est envoyé ou validé.
 * Les CC ne sont crédités qu'après la validation au siège.
 */
@Composable
fun ValidationsScreen(onRetour: () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var reserves by remember { mutableStateOf<List<Reserve>>(emptyList()) }
    var lots by remember { mutableStateOf<List<LotValidation>?>(null) }

    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose {}
        val db = FirebaseFirestore.getInstance()
        val e1 = db.collection("reservesValidation").whereEqualTo("fboId", uid).addSnapshotListener { r, _ ->
            reserves = r?.documents.orEmpty().map { d ->
                Reserve(
                    idForever = d.getString("idForever").orEmpty(),
                    nom = d.getString("nom").orEmpty(),
                    montant = d.getLong("montant")?.toInt() ?: 0,
                    minimum = d.getLong("minimum")?.toInt() ?: 36_000,
                    cc = d.getDouble("ccTotal") ?: 0.0,
                    lignes = lignesDe(d.get("lignes"))
                )
            }.filter { it.lignes.isNotEmpty() }
        }
        val e2 = db.collection("lotsValidation").whereEqualTo("fboId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { r, _ ->
                lots = r?.documents.orEmpty().map { d ->
                    LotValidation(
                        id = d.id,
                        idForever = d.getString("idForever").orEmpty(),
                        montant = d.getLong("montant")?.toInt() ?: 0,
                        cc = d.getDouble("ccTotal") ?: 0.0,
                        valide = d.getString("statut") == "valide",
                        date = (d.get("createdAt") as? Timestamp)?.toDate()
                            ?.let { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(it) }.orEmpty(),
                        reference = d.getString("reference").orEmpty()
                    )
                }
            }
        onDispose {
            e1.remove()
            e2.remove()
        }
    }

    Column(Modifier.fillMaxSize().background(BlackDeep)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 40.dp, start = 4.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = GoldPrimary) }
            Text("Validations au siège", color = WhitePrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Vos ventes sont validées au siège Forever sur l'ID du bénéficiaire, dès que l'achat minimum est atteint. " +
                        "Les CC sont crédités après cette validation.",
                    color = WhiteSub,
                    fontSize = 13.sp
                )
            }
            if (reserves.isNotEmpty()) item { Titre("En attente de l'achat minimum") }
            items(reserves) { CarteReserve(it) }
            item { Titre("Envoyés au siège") }
            val liste = lots
            when {
                liste == null -> item { CircularProgressIndicator(color = GoldPrimary) }
                liste.isEmpty() -> item { Text("Aucun envoi pour le moment.", color = WhiteSub) }
                else -> items(liste, key = { it.id }) { CarteLot(it) }
            }
        }
    }
}

@Composable
private fun Titre(texte: String) {
    Text(texte, color = GoldPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun CarteReserve(r: Reserve) {
    val manque = (r.minimum - r.montant).coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().clip(Forme).background(BlackCard).border(1.dp, BlackBorder, Forme).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("ID ${r.idForever} · ${r.nom}", color = WhitePrimary, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { (r.montant.toFloat() / r.minimum).coerceIn(0f, 1f) },
            color = GoldPrimary,
            trackColor = BlackBorder,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
        Text("${fcfa(r.montant)} sur ${fcfa(r.minimum)} · ${cc(r.cc)}", color = WhiteSub, fontSize = 13.sp)
        Text(
            "Il manque ${fcfa(manque)} : votre prochaine vente complétera ce lot, rien n'est perdu.",
            color = GoldPrimary,
            fontSize = 13.sp
        )
        r.lignes.groupBy { it.nom }.forEach { (nom, l) ->
            Text("• $nom × ${l.sumOf { it.quantite }}", color = WhiteSub, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CarteLot(l: LotValidation) {
    Row(
        Modifier.fillMaxWidth().clip(Forme).background(BlackCard).border(1.dp, BlackBorder, Forme).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("ID ${l.idForever} · ${l.date}", color = WhitePrimary, fontWeight = FontWeight.SemiBold)
            Text("${fcfa(l.montant)} · ${cc(l.cc)}", color = WhiteSub, fontSize = 13.sp)
            if (l.reference.isNotBlank()) Text("Reçu ${l.reference}", color = WhiteSub, fontSize = 12.sp)
        }
        Text(
            if (l.valide) "Validé" else "En cours au siège",
            color = if (l.valide) GreenPrimary else GoldPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
