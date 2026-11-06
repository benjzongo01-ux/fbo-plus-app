package com.benjo.fboplus

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions

const val REGION_FUNCTIONS = "europe-west1"

/** Adresse de la machine hôte vue depuis l'émulateur Android. */
private const val HOTE_EMULATEUR = "10.0.2.2"

class FboApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.EMULATEUR_FIREBASE) {
            connecterEmulateurs()
        } else {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(fournisseurAppCheck())
        }
    }

    private fun connecterEmulateurs() {
        FirebaseApp.initializeApp(
            this,
            FirebaseOptions.Builder()
                .setProjectId("demo-fbo-plus")
                .setApplicationId("1:1:android:0000000000000000")
                .setApiKey("demo")
                .build()
        )
        FirebaseAuth.getInstance().useEmulator(HOTE_EMULATEUR, 9099)
        FirebaseFunctions.getInstance(REGION_FUNCTIONS).useEmulator(HOTE_EMULATEUR, 5001)
    }
}
