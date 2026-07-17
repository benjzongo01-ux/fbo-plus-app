package com.benjo.fboplus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.benjo.fboplus.data.Profil
import com.benjo.fboplus.data.ProfilRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ProfilViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext

    private val _profil = MutableStateFlow(ProfilRepository.getProfil(ctx))
    val profil: StateFlow<Profil> = _profil

    fun sauvegarderProfil(profil: Profil) {
        ProfilRepository.sauvegarderProfil(ctx, profil)
        _profil.value = profil
    }
}