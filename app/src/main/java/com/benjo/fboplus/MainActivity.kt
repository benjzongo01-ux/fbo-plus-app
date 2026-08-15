package com.benjo.fboplus

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.benjo.fboplus.ui.theme.FBOPlusTheme
import com.benjo.fboplus.ui.navigation.FBONavigation

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Planifier le rappel au démarrage
        RappelScheduler.planifierRappelQuotidien(this)
        setContent {
            FBOPlusTheme {
                FBONavigation()
            }
        }
    }
}