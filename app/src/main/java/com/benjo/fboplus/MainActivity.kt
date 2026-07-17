package com.benjo.fboplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.benjo.fboplus.ui.theme.FBOPlusTheme
import com.benjo.fboplus.ui.navigation.FBONavigation

class MainActivity : ComponentActivity() {
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
