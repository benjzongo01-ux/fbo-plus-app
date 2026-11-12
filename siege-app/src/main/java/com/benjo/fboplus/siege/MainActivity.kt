package com.benjo.fboplus.siege

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE5B53A), background = Color(0xFF08090C), surface = Color(0xFF14161C))) {
                val connexion: ConnexionViewModel = viewModel()
                val connecte by connexion.connecte.collectAsState()
                if (connecte) EspaceSiege(onDeconnexion = { connexion.deconnecter() }) else ConnexionEcran(connexion)
            }
        }
    }
}
