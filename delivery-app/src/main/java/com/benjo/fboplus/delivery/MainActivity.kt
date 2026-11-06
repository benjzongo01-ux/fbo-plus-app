package com.benjo.fboplus.delivery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Onglet(val titre: String, val icone: ImageVector) {
    MISSIONS("Missions", Icons.Filled.LocalShipping),
    HISTORIQUE("Historique", Icons.Filled.History),
    REVENUS("Revenus", Icons.Filled.Payments)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = Couleurs) {
                Application()
            }
        }
    }
}

@Composable
private fun Application() {
    val connexion: ConnexionViewModel = viewModel()
    val etat by connexion.etat.collectAsState()

    if (etat == EtatConnexion.Connecte) {
        // Recréé à chaque connexion : les missions suivent toujours le compte courant.
        key(etat) { Accueil(onDeconnexion = connexion::deconnecter) }
    } else {
        ConnexionEcran(connexion)
    }
}

@Composable
private fun Accueil(onDeconnexion: () -> Unit) {
    val missions: MissionsViewModel = viewModel()
    var onglet by remember { mutableStateOf(Onglet.MISSIONS) }
    var ouverte by remember { mutableStateOf<String?>(null) }

    ouverte?.let { id ->
        BackHandler { ouverte = null }
        MissionEcran(id, missions, onRetour = { ouverte = null })
        return
    }

    Scaffold(
        containerColor = Fond,
        bottomBar = {
            NavigationBar(containerColor = Carte) {
                Onglet.entries.forEach { o ->
                    NavigationBarItem(
                        selected = o == onglet,
                        onClick = { onglet = o },
                        icon = { Icon(o.icone, contentDescription = null) },
                        label = { Text(o.titre) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Or,
                            selectedTextColor = Or,
                            unselectedIconColor = TexteDiscret,
                            unselectedTextColor = TexteDiscret,
                            indicatorColor = Surface2
                        )
                    )
                }
            }
        }
    ) { marges ->
        Box(Modifier.padding(marges)) {
            when (onglet) {
                Onglet.MISSIONS -> MissionsEcran(missions, onOuvrir = { ouverte = it }, onDeconnexion)
                Onglet.HISTORIQUE -> HistoriqueEcran(missions)
                Onglet.REVENUS -> RevenusEcran(missions)
            }
        }
    }
}
