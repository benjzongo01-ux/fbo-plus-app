package com.benjo.fboplus.data

import android.content.Context
import com.google.gson.Gson

object ProfilRepository {

    private const val PREFS_NAME = "fboplus_profil"
    private const val KEY_PROFIL = "profil"
    private val gson = Gson()

    fun sauvegarderProfil(context: Context, profil: Profil) {
        val json = gson.toJson(profil)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PROFIL, json).apply()
    }

    fun getProfil(context: Context): Profil {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROFIL, null) ?: return Profil()
        return try {
            gson.fromJson(json, Profil::class.java)
        } catch (e: Exception) {
            Profil()
        }
    }
}