package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.REGION_FUNCTIONS
import com.benjo.fboplus.ui.theme.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

private data class Message(val id: String, val texte: String, val duCentre: Boolean, val heure: String, val commandeId: String?)

/**
 * Conversation du distributeur avec le centre d'appel de son pays. Ouverte depuis le suivi d'une
 * commande, le premier message cite cette commande.
 */
@Composable
fun MessagerieScreen(commandeId: String?, onRetour: () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var texte by remember { mutableStateOf("") }
    var envoi by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val portee = rememberCoroutineScope()
    val liste = rememberLazyListState()

    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose {}
        val db = FirebaseFirestore.getInstance()
        val ecoute = db.collection("conversations/$uid/messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { r, _ ->
                messages = r?.documents.orEmpty().map {
                    Message(
                        id = it.id,
                        texte = it.getString("texte").orEmpty(),
                        duCentre = it.getBoolean("duCentre") == true,
                        heure = (it.get("createdAt") as? Timestamp)?.toDate()
                            ?.let { d -> SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(d) }.orEmpty(),
                        commandeId = it.getString("commandeId")
                    )
                }
                // Ouvrir la conversation vaut lecture.
                db.document("conversations/$uid").update("nonLusFbo", 0)
            }
        onDispose { ecoute.remove() }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) liste.animateScrollToItem(messages.lastIndex) }

    Column(Modifier.fillMaxSize().background(BlackDeep).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 40.dp, start = 4.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = GoldPrimary) }
            Column {
                Text("Centre d'appel", color = WhitePrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                if (commandeId != null) Text("À propos de votre commande", color = WhiteSub, fontSize = 12.sp)
            }
        }
        LazyColumn(
            state = liste,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) item {
                Text(
                    "Posez votre question : le centre d'appel de votre pays vous répond ici, et vous recevez une notification.",
                    color = WhiteSub,
                    fontSize = 14.sp
                )
            }
            items(messages, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.duCentre) Arrangement.Start else Arrangement.End) {
                    Column(
                        Modifier
                            .widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (m.duCentre) BlackCard else GoldPrimary.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (m.duCentre) Text("Centre d'appel", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(m.texte, color = WhitePrimary, fontSize = 15.sp)
                        Text(m.heure, color = WhiteSub, fontSize = 11.sp)
                    }
                }
            }
        }
        erreur?.let { Text(it, color = RedAlert, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
        Row(Modifier.fillMaxWidth().background(BlackCard).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = texte,
                onValueChange = { texte = it.take(1000) },
                placeholder = { Text("Votre message") },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            IconButton(
                enabled = texte.isNotBlank() && !envoi,
                onClick = {
                    envoi = true
                    erreur = null
                    val aEnvoyer = texte
                    portee.launch {
                        try {
                            FirebaseFunctions.getInstance(REGION_FUNCTIONS).getHttpsCallable("envoyerMessage").call(
                                buildMap {
                                    put("texte", aEnvoyer)
                                    // Seul le premier message cite la commande d'où la conversation a été ouverte.
                                    if (commandeId != null && messages.none { it.commandeId == commandeId }) put("commandeId", commandeId)
                                }
                            ).await()
                            texte = ""
                        } catch (e: Exception) {
                            erreur = e.message ?: "Message non envoyé. Réessayez."
                        } finally {
                            envoi = false
                        }
                    }
                }
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Envoyer", tint = GoldPrimary) }
        }
    }
}
