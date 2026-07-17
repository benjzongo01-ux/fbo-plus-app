package com.benjo.fboplus.data

data class Profil(
    val nom: String = "",
    val prenom: String = "",
    val grade: String = "Client Privilégié (CP)",
    val telephone: String = "",
    val idDistributeur: String = "",
    val objectifCC: Double = 0.0,
    val objectifCA: Int = 0
)

val listeGrades = listOf(
    "Client Privilégié (CP)",
    "Animateur Adjoint (AA)",
    "Animateur (A)",
    "Manager Adjoint (MA)",
    "Manager (M)"
)