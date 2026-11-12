package com.benjo.fboplus

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.benjo.fboplus.data.ProfilRepository
import com.benjo.fboplus.ui.theme.FBOPlusTheme
import com.benjo.fboplus.ui.navigation.FBONavigation
import com.benjo.fboplus.ui.screens.AvecMaintenance

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Alertes d'objectif : selon le choix fait dans le profil
        val profil = ProfilRepository.getProfil(this)
        RappelScheduler.appliquer(this, profil.rappelActif)
        setContent {
            FBOPlusTheme {
                AvecMaintenance { FBONavigation() }
            }
        }
    }
}