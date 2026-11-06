package com.benjo.fboplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjo.fboplus.data.NotificationsRepository
import com.benjo.fboplus.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(onOuvrirCommande: (String) -> Unit, onRetour: () -> Unit) {
    val notifications by NotificationsRepository.notifications.collectAsState()
    val format = remember { SimpleDateFormat("dd/MM à HH:mm", Locale.FRANCE) }

    Column(Modifier.fillMaxSize().background(BlackDeep)) {
        EnTeteSimple("Notifications", onRetour)
        if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucune notification", color = WhiteSub, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { n ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BlackCard)
                            .clickable {
                                if (!n.lu) NotificationsRepository.marquerLue(n.id)
                                if (n.commandeId.isNotEmpty()) onOuvrirCommande(n.commandeId)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(if (n.lu) BlackSurface else GoldPrimary, CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                n.corps,
                                color = WhitePrimary,
                                fontSize = 14.sp,
                                fontWeight = if (n.lu) FontWeight.Normal else FontWeight.SemiBold
                            )
                            Text(format.format(Date(n.dateMillis)), color = WhiteSub, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
