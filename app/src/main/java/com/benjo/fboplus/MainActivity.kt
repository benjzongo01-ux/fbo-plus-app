package com.benjo.fboplus

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.benjo.fboplus.data.ProfilRepository
import com.benjo.fboplus.ui.theme.FBOPlusTheme
import com.benjo.fboplus.ui.navigation.FBONavigation

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Respecte le choix fait dans le profil (activé ou non, heure)
        val profil = ProfilRepository.getProfil(this)
        RappelScheduler.appliquer(this, profil.rappelActif, profil.heureRappel)
        setContent {
            FBOPlusTheme {
                FBONavigation()
            }
        }
    }
}