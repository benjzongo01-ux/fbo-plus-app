package com.benjo.fboplus.data

data class Profil(
    val nom: String = "",
    val prenom: String = "",
    val pays: String = "",
    val ville: String = "",
    val grade: String = "Client Privilégié (CP)",
    val idDistributeur: String = "",
    val indicatifTelephone: String = "",
    val telephone: String = "",
    val indicatifWhatsapp: String = "",
    val whatsapp: String = "",
    val objectifCC: Double = 0.0,
    val objectifCA: Int = 0,
    val profilComplet: Boolean = false,
    val rappelActif: Boolean = true,
    val heureRappel: String = "19:00"
)

val listeGrades = listOf(
    "Client Privilégié (CP)",
    "Animateur Adjoint (AA)",
    "Animateur (A)",
    "Manager Adjoint (MA)",
    "Manager (M)"
)

data class PaysInscription(
    val nom: String,
    val drapeau: String,
    val indicatif: String
)

val paysAutorises = listOf(
    PaysInscription("Burkina Faso", "🇧🇫", "+226"),
    PaysInscription("Côte d'Ivoire", "🇨🇮", "+225"),
    PaysInscription("Sénégal", "🇸🇳", "+221")
)