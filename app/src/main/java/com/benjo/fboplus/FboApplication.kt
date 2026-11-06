package com.benjo.fboplus

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage

const val REGION_FUNCTIONS = "europe-west1"

/**
 * Les ports des émulateurs Firebase sont redirigés vers le PC avec `adb reverse`,
 * ce qui fonctionne aussi bien sur un téléphone en USB que sur un émulateur Android.
 */
private const val HOTE_EMULATEUR = "127.0.0.1"

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
                // Clé factice : le SDK refuse les appels aux Functions sans ce format, même vers l'émulateur.
                .setApiKey("AIza" + "0".repeat(35))
                .setGcmSenderId("1")
                .build()
        )
        FirebaseAuth.getInstance().useEmulator(HOTE_EMULATEUR, 9099)
        FirebaseFunctions.getInstance(REGION_FUNCTIONS).useEmulator(HOTE_EMULATEUR, 5001)
        FirebaseFirestore.getInstance().useEmulator(HOTE_EMULATEUR, 8080)
        FirebaseStorage.getInstance().useEmulator(HOTE_EMULATEUR, 9199)
    }
}
